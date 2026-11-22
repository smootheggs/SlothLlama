package app.slothlobster.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A web page boiled down to its title and its readable text. [asides] (1.13.3) are the lines that
 * look like the page around the article rather than the article: related-story links, dates,
 * captions and bylines. They're marked, never dropped, so nothing of the article can be lost.
 */
data class ReadablePage(val title: String, val lines: List<String>, val asides: Set<Int> = emptySet()) {
    /** Markdown: headings as #, list items as -, paragraphs between blank lines. */
    val text: String get() = lines.joinToString("\n\n")
}

/**
 * Turns a page's HTML into the words a person would read: the article, without menus, ads,
 * cookie notices or scripts. Plain text only; nothing from the page is ever run.
 */
object Readable {

    private const val MAX_CHARS = 60_000
    private const val BLOCKS = "h1, h2, h3, h4, p, li, pre, blockquote, dd"

    private val JUNK = listOf(
        "script", "style", "noscript", "iframe", "svg", "canvas", "form", "nav", "body > header", "footer",
        "aside", "button", "input", "select", "textarea", "template", "dialog",
        "[role=navigation]", "[role=banner]", "[role=contentinfo]", "[role=dialog]", "[aria-hidden=true]", "[hidden]",
        ".ad", ".ads", ".advert", ".advertisement", ".cookie", ".cookies", ".consent", ".newsletter", ".share",
        ".social", ".related", ".comments", ".breadcrumb", ".breadcrumbs", ".sidebar", ".menu", ".promo", ".subscribe",
        "#comments", "#sidebar", "#cookie-banner"
    ).joinToString(", ")

    fun extract(html: String, baseUrl: String): ReadablePage {
        val doc = Jsoup.parse(html, baseUrl)
        val title = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }.trim()
            .ifBlank { doc.selectFirst("h1")?.text().orEmpty() }
            .ifBlank { Web.site(baseUrl) }
        doc.select(JUNK).remove()
        val root = mainPart(doc)
        val (lines, asides) = blocks(root)
        return ReadablePage(title, lines, asides)
    }

    /** The part of the page with the most paragraph text: the article itself. */
    private fun mainPart(doc: Document): Element {
        val body = doc.body() ?: return doc
        val marked = doc.select("article, main, [role=main], [itemprop=articleBody], .article-body, .entry-content, .post-content, #content")
            .maxByOrNull { paragraphChars(it) }
        if (marked != null && paragraphChars(marked) >= 500) return marked
        // Otherwise score each element by the paragraph text directly inside it (and half for
        // its grandchildren), the usual way readers find the article.
        val scores = HashMap<Element, Int>()
        for (p in body.select("p")) {
            val n = p.text().length
            if (n < 25) continue
            p.parent()?.let { parent ->
                scores[parent] = (scores[parent] ?: 0) + n
                parent.parent()?.let { grand -> scores[grand] = (scores[grand] ?: 0) + n / 2 }
            }
        }
        return scores.maxByOrNull { it.value }?.key?.takeIf { (scores[it] ?: 0) >= 300 } ?: body
    }

    private fun paragraphChars(e: Element): Int = e.select("p").sumOf { it.text().length }

    private fun blocks(root: Element): Pair<List<String>, Set<Int>> {
        val out = ArrayList<String>()
        val asides = HashSet<Int>()
        var chars = 0
        for (el in root.select(BLOCKS)) {
            // Only the innermost blocks, so a list item holding a paragraph isn't read twice.
            if (hasBlockChild(el)) continue
            val text = el.text().trim().replace(Regex("\\s+"), " ")
            if (text.length < 2) continue
            val line = when (el.tagName()) {
                "h1" -> "# $text"
                "h2" -> "## $text"
                "h3", "h4" -> "### $text"
                "li" -> "- $text"
                "blockquote" -> "> $text"
                else -> text
            }
            if (out.lastOrNull() == line) continue
            if (isAside(el, text, root)) asides += out.size
            out += line
            chars += line.length
            if (chars > MAX_CHARS) break
        }
        if (out.isEmpty()) {
            val all = root.text().trim()
            if (all.isNotEmpty()) out += all.take(MAX_CHARS)
        }
        // When in doubt, show everything: rules that would set aside half the text have misread the page.
        val asideChars = asides.sumOf { out[it].length }
        return out to if (asideChars * 2 > out.sumOf { it.length }) emptySet() else asides
    }

    /**
     * (1.13.3) A line from the page around the article, by three signs readers such as Readability
     * use: it sits in a box the page itself calls related, promo, byline, caption and so on; it is
     * mostly link text (a teaser headline is all link, a paragraph mostly words); or it is a date
     * or caption line ("Published 2 days ago", "Image source, …").
     */
    fun isAside(el: Element, text: String, root: Element): Boolean {
        if (META.matches(text)) return true
        if (el.closest("figure, figcaption") != null) return true
        var e: Element? = el
        while (e != null && e != root) {
            val labels = (e.className() + " " + e.id() + " " + e.attr("data-component") + " " + e.attr("data-testid")).lowercase()
            if (ASIDE_BOX.containsMatchIn(labels)) return true
            e = e.parent()
        }
        val linked = if (el.closest("a") != null) text.length else el.select("a").sumOf { it.text().length }
        val density = linked.toDouble() / text.length.coerceAtLeast(1)
        return density >= 0.9 || (density >= 0.6 && text.length < 160)
    }

    /** Labels pages give the boxes around an article (whole words, so "figcaption" or "promotional-…" don't match by accident). */
    private val ASIDE_BOX = Regex(
        "(^|[^a-z])(links?-block|tag-list|topic-list|related|promo|teaser|recommended|recommendations|more-stories|" +
            "more-on-this|read-?more|newsletter|outbrain|taboola|most-?read|trending|byline|byline-block|image-block|caption)([^a-z]|$)"
    )

    /** Date, caption and label lines. */
    private val META = Regex(
        "^(published|updated|posted|last updated|first published|modified)\\b[^.]{0,60}$" +
            "|^\\d{1,3} (seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?) ago$" +
            "|^(image|media|video|photo) (source|caption|credit)s?\\b.{0,240}$" +
            "|^(related topics|related|more on this story|more from \\S+|read more|see also|share|share this( article)?|listen|watch|advertisement|sponsored)\\s*:?$",
        RegexOption.IGNORE_CASE
    )

    private fun hasBlockChild(el: Element): Boolean =
        el.getAllElements().drop(1).any { it.tagName() in BLOCK_TAGS }

    private val BLOCK_TAGS = setOf("h1", "h2", "h3", "h4", "p", "li", "pre", "blockquote", "dd")
}
