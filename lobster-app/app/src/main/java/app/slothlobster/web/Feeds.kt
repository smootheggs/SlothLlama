package app.slothlobster.web

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder

data class FeedItem(val title: String, val link: String, val summary: String = "")

data class Feed(val title: String, val items: List<FeedItem>)

/** RSS and Atom news feeds, read as plain data. */
object Feeds {

    fun parse(xml: String, fallbackTitle: String): Feed {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val title = (doc.selectFirst("channel > title") ?: doc.selectFirst("feed > title"))?.text()?.trim()
            .takeUnless { it.isNullOrBlank() } ?: fallbackTitle
        val rss = doc.select("item").map { item ->
            FeedItem(
                title = clean(item.selectFirst("title")?.text()),
                link = item.selectFirst("link")?.text()?.trim().orEmpty(),
                summary = plain(item.selectFirst("description")?.text())
            )
        }
        val atom = doc.select("entry").map { entry ->
            val link = entry.select("link").firstOrNull { it.attr("rel").isBlank() || it.attr("rel") == "alternate" }
            FeedItem(
                title = clean(entry.selectFirst("title")?.text()),
                link = link?.attr("href")?.trim().orEmpty(),
                summary = plain((entry.selectFirst("summary") ?: entry.selectFirst("content"))?.text())
            )
        }
        return Feed(title, (rss + atom).filter { it.title.isNotBlank() })
    }

    /**
     * The feeds list as typed in Settings: one link per line, and a line like "# News" (or
     * "News:") starts a group. Returns (group, link) pairs; "" = no group.
     */
    fun parseList(text: String): List<Pair<String, String>> {
        var group = ""
        val out = ArrayList<Pair<String, String>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#") -> group = line.trimStart('#').trim()
                line.endsWith(":") && "://" !in line -> group = line.removeSuffix(":").trim()
                else -> Web.safeUrl(line)?.let { url -> if (out.none { it.second == url }) out += group to url }
            }
        }
        return out
    }

    /** The feeds list back as text for editing, grouped, in the order given. */
    fun formatList(feeds: List<String>, groups: Map<String, String>): String {
        val byGroup = LinkedHashMap<String, MutableList<String>>()
        feeds.forEach { url -> byGroup.getOrPut(groups[url].orEmpty()) { mutableListOf() } += url }
        val ungrouped = byGroup.remove("").orEmpty()
        return buildString {
            ungrouped.forEach { append(it).append('\n') }
            byGroup.forEach { (g, urls) ->
                if (isNotEmpty()) append('\n')
                append("# ").append(g).append('\n')
                urls.forEach { append(it).append('\n') }
            }
        }.trim()
    }

    /** Feeds from an OPML file (what news readers export): (folder, link) pairs. */
    fun parseOpml(xml: String): List<Pair<String, String>> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("outline[xmlUrl]").mapNotNull { o ->
            val url = Web.safeUrl(o.attr("xmlUrl")) ?: return@mapNotNull null
            val parent = o.parent()
            val group = if (parent != null && parent.tagName() == "outline") parent.attr("title").ifBlank { parent.attr("text") } else ""
            group.trim() to url
        }.distinctBy { it.second }
    }

    /** A Google News search as a feed: the free way to follow a topic. */
    fun newsSearch(topic: String, days: Int = 0): String {
        val q = topic.trim() + if (days > 0) " when:${days}d" else ""
        return "https://news.google.com/rss/search?q=" + URLEncoder.encode(q, "UTF-8") + "&hl=en-US&gl=US&ceid=US:en"
    }

    /** Words that only narrow a news search ("updates", "top news", "etc."). */
    private val FILLER = setOf(
        "update", "updates", "top", "news", "latest", "headline", "headlines", "etc", "etcetera", "today", "todays",
        "daily", "brief", "briefing", "roundup", "round-up", "summary", "info", "information", "stuff", "things",
        "volumes", "volume", "prices", "price", "a", "an", "the", "on", "about", "of", "for", "and", "&", "please", "me", "my"
    )

    /**
     * A brief's topic as a news search: key words only, in order, repeats dropped. Filler like
     * "updates", "top news" or "prices" goes (Google News treats every word as required), unless
     * nothing else is left. Quoted phrases and OR are kept as written.
     */
    fun newsQuery(topic: String): String {
        val t = topic.trim()
        if ('"' in t || Regex("""\bOR\b""").containsMatchIn(t)) return t
        val words = t.split(Regex("""[\s,;/]+""")).map { it.trim('.', '!', '?', ':', '(', ')') }.filter { it.isNotBlank() }
        val kept = words.filter { it.lowercase() !in FILLER }.distinctBy { it.lowercase() }
        return (kept.ifEmpty { words }).joinToString(" ")
    }

    private fun clean(s: String?): String = s.orEmpty().trim().replace(Regex("\\s+"), " ")

    /** Feed summaries are often HTML: keep the words, at most two sentences' worth. */
    private fun plain(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val text = Jsoup.parse(s).text().trim().replace(Regex("\\s+"), " ")
        return if (text.length <= 220) text else text.take(217).substringBeforeLast(' ') + "…"
    }
}
