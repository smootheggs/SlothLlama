package app.slothlobster.web

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder

data class FeedItem(
    val title: String,
    val link: String,
    val summary: String = "",
    /** When it was published (epoch ms), or 0 if the feed doesn't say. */
    val at: Long = 0,
    /** Who published it, when the feed names a different outlet per item (Google News does). */
    val outlet: String = "",
    /** Small print worth keeping: "113 points · 19 comments" from Hacker News. */
    val meta: String = ""
)

data class Feed(val title: String, val items: List<FeedItem>)

/** RSS and Atom news feeds, read as plain data. */
object Feeds {

    fun parse(xml: String, fallbackTitle: String): Feed {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val title = (doc.selectFirst("channel > title") ?: doc.selectFirst("feed > title"))?.text()?.trim()
            .takeUnless { it.isNullOrBlank() } ?: fallbackTitle
        val rss = doc.select("item").map { item ->
            tidy(
                FeedItem(
                    title = clean(item.selectFirst("title")?.text()),
                    link = item.selectFirst("link")?.text()?.trim().orEmpty(),
                    summary = plain(item.selectFirst("description")?.text()),
                    at = date(item.selectFirst("pubDate")?.text() ?: item.selectFirst("dc|date")?.text()),
                    outlet = clean(item.selectFirst("source")?.text())
                )
            )
        }
        val atom = doc.select("entry").map { entry ->
            val link = entry.select("link").firstOrNull { it.attr("rel").isBlank() || it.attr("rel") == "alternate" }
            tidy(
                FeedItem(
                    title = clean(entry.selectFirst("title")?.text()),
                    link = link?.attr("href")?.trim().orEmpty(),
                    summary = plain((entry.selectFirst("summary") ?: entry.selectFirst("content"))?.text()),
                    at = date(entry.selectFirst("published")?.text() ?: entry.selectFirst("updated")?.text())
                )
            )
        }
        return Feed(title, (rss + atom).filter { it.title.isNotBlank() })
    }

    /**
     * An item as a reader wants it. Google News ends each title with " - Outlet": the outlet moves
     * to its own field. Hacker News's summary is only "Article URL: … Comments URL: … Points: 113
     * # Comments: 19": that becomes small print, and the summary goes.
     */
    fun tidy(item: FeedItem): FeedItem {
        var title = item.title
        var outlet = item.outlet
        if (outlet.isNotEmpty() && title.endsWith(" - $outlet")) title = title.removeSuffix(" - $outlet").trim()
        var summary = item.summary
        var meta = item.meta
        if (HN_SUMMARY.containsMatchIn(summary)) {
            val points = Regex("""Points:\s*(\d+)""").find(summary)?.groupValues?.get(1)
            val comments = Regex("""#\s*Comments:\s*(\d+)""").find(summary)?.groupValues?.get(1)
            meta = listOfNotNull(points?.let { "$it points" }, comments?.let { "$it comments" }).joinToString(" · ")
            summary = ""
        }
        // A summary that only repeats the title says nothing; a long one is cut to two sentences' worth.
        if (summary.trim().equals(title.trim(), ignoreCase = true)) summary = ""
        if (summary.length > 220) summary = summary.take(217).substringBeforeLast(' ') + "…"
        if (outlet == item.outlet && title == item.title && summary == item.summary && meta == item.meta) return item
        return item.copy(title = title, outlet = outlet, summary = summary, meta = meta)
    }

    private val HN_SUMMARY = Regex("""Article URL:|Comments URL:""")

    /** A feed's date (RSS's "Fri, 20 Nov 2026 07:00:00 GMT" or Atom's ISO time) in epoch ms; 0 if unreadable. */
    fun date(text: String?): Long {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return 0
        return runCatching { java.time.ZonedDateTime.parse(t, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(t).toEpochMilli() }
            .getOrDefault(0L)
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

    /** A Google News search as a feed: the free way to follow a topic. [lang] picks the edition ("fr", "zh-Hant"…). */
    fun newsSearch(topic: String, days: Int = 0, lang: String = "en"): String {
        val q = topic.trim() + if (days > 0) " when:${days}d" else ""
        return "https://news.google.com/rss/search?q=" + URLEncoder.encode(q, "UTF-8") + "&" + googleEdition(lang)
    }

    /** Google News's own top stories for a language's home region. */
    fun topStories(lang: String): String = "https://news.google.com/rss?" + googleEdition(lang)

    /** hl, gl and ceid for a language: where its "most important news" is read. */
    private fun googleEdition(lang: String): String = when (lang) {
        "zh", "zh-Hans" -> "hl=zh-CN&gl=CN&ceid=CN:zh-Hans"
        "yue", "zh-Hant" -> "hl=zh-HK&gl=HK&ceid=HK:zh-Hant"
        "ja" -> "hl=ja&gl=JP&ceid=JP:ja"
        "fr" -> "hl=fr&gl=FR&ceid=FR:fr"
        "de" -> "hl=de&gl=DE&ceid=DE:de"
        "es" -> "hl=es-419&gl=MX&ceid=MX:es-419"
        "it" -> "hl=it&gl=IT&ceid=IT:it"
        else -> "hl=en-US&gl=US&ceid=US:en"
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

    /** Feed summaries are often HTML: keep the words ([tidy] shortens them, once it has read them). */
    private fun plain(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return Jsoup.parse(s).text().trim().replace(Regex("\\s+"), " ").take(4_000)
    }
}
