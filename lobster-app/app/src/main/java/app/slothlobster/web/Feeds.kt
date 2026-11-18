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

    /** A Google News search as a feed: the free way to follow a topic. */
    fun newsSearch(topic: String): String =
        "https://news.google.com/rss/search?q=" + URLEncoder.encode(topic.trim(), "UTF-8") + "&hl=en-US&gl=US&ceid=US:en"

    private fun clean(s: String?): String = s.orEmpty().trim().replace(Regex("\\s+"), " ")

    /** Feed summaries are often HTML: keep the words, at most two sentences' worth. */
    private fun plain(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val text = Jsoup.parse(s).text().trim().replace(Regex("\\s+"), " ")
        return if (text.length <= 220) text else text.take(217).substringBeforeLast(' ') + "…"
    }
}
