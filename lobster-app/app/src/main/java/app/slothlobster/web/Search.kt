package app.slothlobster.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder

data class Hit(val title: String, val url: String)

data class WikiArticle(val title: String, val url: String, val text: String)

/**
 * Free searches that need no account or key: Wikipedia's own API, and the plain-HTML pages of
 * DuckDuckGo (with Bing as a fallback). Results are only links and titles; the pages are read
 * separately.
 */
object Search {

    private val json = Json { ignoreUnknownKeys = true }

    /** Sites whose pages are videos, logins or apps rather than text worth reading. */
    private val SKIP_SITES = listOf(
        "youtube.com", "youtu.be", "facebook.com", "instagram.com", "tiktok.com", "x.com", "twitter.com",
        "pinterest.com", "linkedin.com", "duckduckgo.com", "bing.com", "msn.com", "brave.com", "mojeek.com"
    )

    // --- Wikipedia ---

    /** The Wikipedia to ask, from the question's script: Chinese, Japanese, Korean or English. */
    fun wikiLanguage(query: String): String = when {
        query.any { it in '぀'..'ヿ' } -> "ja"
        query.any { it in '가'..'힯' } -> "ko"
        query.any { it in '一'..'鿿' } -> "zh"
        else -> "en"
    }

    suspend fun wikipedia(query: String, count: Int, maxChars: Int = 8_000): List<WikiArticle> {
        val lang = wikiLanguage(query)
        val api = "https://$lang.wikipedia.org/w/api.php"
        val found = Web.get(
            "$api?action=query&list=search&format=json&utf8=1&srlimit=$count&srsearch=" + enc(query),
            asBrowser = false
        )
        val titles = wikiTitles(found.text)
        return titles.mapNotNull { title ->
            runCatching {
                val page = Web.get(
                    "$api?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&titles=" + enc(title),
                    asBrowser = false
                )
                val text = wikiExtract(page.text).take(maxChars)
                if (text.isBlank()) null
                else WikiArticle(title, "https://$lang.wikipedia.org/wiki/" + enc(title.replace(' ', '_')).replace("%2F", "/"), text)
            }.getOrNull()
        }
    }

    fun wikiTitles(body: String): List<String> = runCatching {
        val results = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("search") as? JsonArray
        results.orEmpty().mapNotNull { (it as? JsonObject)?.get("title")?.jsonPrimitive?.contentOrNull }
    }.getOrDefault(emptyList())

    fun wikiExtract(body: String): String = runCatching {
        val pages = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject
        pages?.values?.firstOrNull()?.jsonObject?.get("extract")?.jsonPrimitive?.contentOrNull.orEmpty()
            // Wikipedia's == Heading == style becomes ## Heading.
            .replace(Regex("""(?m)^(=+)\s*(.+?)\s*=+\s*$""")) { m -> "#".repeat(m.groupValues[1].length.coerceIn(2, 4)) + " " + m.groupValues[2] }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }.getOrDefault("")

    // --- the web ---

    /** The engines, tried in this order unless one has been failing. */
    val ENGINES = listOf("DuckDuckGo", "DuckDuckGo Lite", "Bing", "Brave", "Mojeek")

    /**
     * Up to [count] links for the question, from the first engine (in [order]) that answers with
     * any. [report] hears how each engine tried did, for the Log and the engines' health.
     */
    suspend fun web(
        query: String,
        count: Int,
        order: List<String> = ENGINES,
        report: (engine: String, ok: Boolean) -> Unit = { _, _ -> }
    ): Pair<String, List<Hit>> {
        var problem: Exception? = null
        for (name in order) {
            try {
                val hits = usable(engine(name, query)).take(count)
                report(name, hits.isNotEmpty())
                if (hits.isNotEmpty()) return name to hits
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                report(name, false)
                problem = e
            }
        }
        if (problem != null) throw WebException("The search engines didn't answer (${problem.message})")
        return "" to emptyList()
    }

    private suspend fun engine(name: String, query: String): List<Hit> = when (name) {
        "DuckDuckGo" -> duckDuckGo(Web.get("https://html.duckduckgo.com/html/?q=" + enc(query)).text)
        "DuckDuckGo Lite" -> duckDuckGoLite(Web.get("https://lite.duckduckgo.com/lite/?q=" + enc(query)).text)
        "Brave" -> generic(Web.get("https://search.brave.com/search?source=web&q=" + enc(query)).text, "https://search.brave.com/", "brave.com")
        "Mojeek" -> generic(Web.get("https://www.mojeek.com/search?q=" + enc(query)).text, "https://www.mojeek.com/", "mojeek.com")
        else -> bing(Web.get("https://www.bing.com/search?setlang=en&q=" + enc(query)).text)
    }

    /**
     * Any results page, read without knowing its layout: links to other sites whose words look
     * like a title. Used for engines whose pages change too often to read by their classes.
     */
    fun generic(html: String, base: String, engineSite: String): List<Hit> {
        val doc = Jsoup.parse(html, base)
        return doc.select("a[href]").mapNotNull { a ->
            val url = a.absUrl("href")
            val text = a.text().trim().replace(Regex("\\s+"), " ")
            if (!url.startsWith("https://") || text.length < 15) return@mapNotNull null
            val site = Web.site(url).lowercase()
            if (site == engineSite || site.endsWith(".$engineSite")) return@mapNotNull null
            Hit(text.take(160), url)
        }
    }

    fun duckDuckGo(html: String): List<Hit> {
        val doc = Jsoup.parse(html, "https://html.duckduckgo.com/")
        return doc.select("div.result:not(.result--ad) a.result__a").map { a ->
            Hit(a.text().trim(), unwrapDuck(a.attr("href")))
        }
    }

    fun duckDuckGoLite(html: String): List<Hit> {
        val doc = Jsoup.parse(html, "https://lite.duckduckgo.com/")
        return doc.select("a.result-link").map { a -> Hit(a.text().trim(), unwrapDuck(a.attr("href"))) }
    }

    fun bing(html: String): List<Hit> {
        val doc = Jsoup.parse(html, "https://www.bing.com/")
        return doc.select("li.b_algo h2 a").map { a -> Hit(a.text().trim(), a.absUrl("href")) }
    }

    /** DuckDuckGo wraps results as //duckduckgo.com/l/?uddg=<the real link>. */
    fun unwrapDuck(href: String): String {
        val uddg = Regex("""[?&]uddg=([^&]+)""").find(href)?.groupValues?.get(1)
        val raw = if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else href
        return if (raw.startsWith("//")) "https:$raw" else raw
    }

    /** Real, public pages only, each site once, without video and social sites. */
    fun usable(hits: List<Hit>): List<Hit> {
        val seen = HashSet<String>()
        return hits.mapNotNull { hit ->
            val url = Web.safeUrl(hit.url) ?: return@mapNotNull null
            val site = Web.site(url).lowercase()
            if (SKIP_SITES.any { site == it || site.endsWith(".$it") }) return@mapNotNull null
            if (url.contains("/y.js") || url.contains("ad_domain=")) return@mapNotNull null
            if (!seen.add(site)) return@mapNotNull null
            Hit(hit.title.ifBlank { Web.site(url) }, url)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
