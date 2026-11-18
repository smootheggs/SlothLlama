package app.slothlobster.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A web page boiled down to its title and its readable text. */
data class ReadablePage(val title: String, val lines: List<String>) {
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
        return ReadablePage(title, blocks(root))
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

    private fun blocks(root: Element): List<String> {
        val out = ArrayList<String>()
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
            out += line
            chars += line.length
            if (chars > MAX_CHARS) break
        }
        if (out.isEmpty()) {
            val all = root.text().trim()
            if (all.isNotEmpty()) out += all.take(MAX_CHARS)
        }
        return out
    }

    private fun hasBlockChild(el: Element): Boolean =
        el.getAllElements().drop(1).any { it.tagName() in BLOCK_TAGS }

    private val BLOCK_TAGS = setOf("h1", "h2", "h3", "h4", "p", "li", "pre", "blockquote", "dd")
}
