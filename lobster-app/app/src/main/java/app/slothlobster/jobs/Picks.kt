package app.slothlobster.jobs

import app.slothlobster.web.Hit
import java.net.URI
import java.util.Locale

/**
 * Picking which pages a research job reads, from everything its searches found. The search
 * engine's own order counts most; then pages whose titles share the question's words, original
 * and reference sources (public bodies, universities, journals, documentation, established news)
 * over answer farms, paywalled document sites and index pages. One page per site, and the same
 * page never twice (tracking parameters and #anchors don't make a page new).
 */
object Picks {

    /** A search result with where it came from: which search, and its place in that search's list. */
    data class Candidate(val hit: Hit, val search: Int, val position: Int)

    fun rank(candidates: List<Candidate>, question: String, take: Int): List<Hit> {
        val terms = words(question)
        val seenPages = HashSet<String>()
        val seenSites = HashSet<String>()
        return candidates
            .filter { seenPages.add(pageKey(it.hit.url)) }
            .sortedByDescending { score(it, terms) }
            .filter { seenSites.add(site(it.hit.url)) }
            .take(take)
            .map { it.hit }
    }

    fun score(c: Candidate, terms: Set<String>): Double {
        var s = 3.0 / (1 + c.position)                     // the engine's own order
        if (c.position == 0) s += 0.5                       // each search's best result
        val title = words(c.hit.title)
        if (terms.isNotEmpty()) s += 2.0 * terms.count { it in title } / terms.size
        s += siteScore(site(c.hit.url))
        s += pathScore(c.hit.url)
        return s
    }

    private fun siteScore(site: String): Double {
        fun ends(vararg xs: String) = xs.any { site == it || site.endsWith(".$it") }
        return when {
            ends("gov", "gov.uk", "gc.ca", "gov.au", "europa.eu", "int", "admin.ch", "bund.de", "gouv.fr", "gob.es", "gov.hk", "go.jp") -> 1.5
            ends("edu", "ac.uk", "ac.jp", "edu.au", "edu.hk") -> 1.2
            ends("nature.com", "science.org", "nih.gov", "arxiv.org", "who.int", "oecd.org", "imf.org", "worldbank.org", "ecb.europa.eu") -> 1.2
            site.startsWith("docs.") || site.startsWith("developer.") || ends("github.com", "readthedocs.io") -> 0.8
            ends(
                "reuters.com", "apnews.com", "bbc.co.uk", "bbc.com", "npr.org", "theguardian.com", "ft.com",
                "economist.com", "nytimes.com", "washingtonpost.com", "bloomberg.com", "dw.com", "lemonde.fr",
                "spiegel.de", "elpais.com", "scmp.com", "nhk.or.jp", "aljazeera.com", "politico.eu"
            ) -> 0.6
            ends("scribd.com", "coursehero.com", "chegg.com", "studocu.com", "slideshare.net") -> -2.5
            ends("quora.com", "answers.com", "ehow.com", "wikihow.com", "brainly.com") -> -2.0
            ends("medium.com", "substack.com", "reddit.com") -> -0.3
            else -> 0.0
        }
    }

    private fun pathScore(url: String): Double {
        val lower = url.lowercase(Locale.ROOT)
        var s = 0.0
        if (INDEX_PAGES.any { it in lower }) s -= 1.2
        if (lower.endsWith(".pdf")) s -= 0.4           // often long and hard to read on a tablet
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        if (path.isEmpty() || path == "/") s -= 0.6    // a home page rarely answers a question
        return s
    }

    private val INDEX_PAGES = listOf("/tag/", "/tags/", "/category/", "/categories/", "/search", "?s=", "/topics/", "/author/")

    /** The site a link belongs to, without "www.". */
    fun site(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault(url)

    /** The same page whatever its tracking parameters, #anchor or trailing slash. */
    fun pageKey(url: String): String = runCatching {
        val u = URI(url)
        val query = u.rawQuery?.split('&')
            ?.filterNot { p -> TRACKING.any { p.lowercase(Locale.ROOT).startsWith(it) } }
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()
        site(url) + u.rawPath.orEmpty().trimEnd('/') + if (query.isNotEmpty()) "?$query" else ""
    }.getOrDefault(url)

    private val TRACKING = listOf("utm_", "fbclid", "gclid", "mc_cid", "mc_eid", "ref=", "ref_src", "igshid")

    private val STOP = setOf(
        "the", "a", "an", "of", "and", "or", "in", "on", "at", "to", "for", "with", "by", "from", "is", "are", "was",
        "were", "be", "what", "which", "who", "how", "why", "when", "where", "does", "do", "did", "have", "has",
        "most", "about", "into", "than", "that", "this", "these", "those", "it", "its", "as"
    )

    fun words(text: String): Set<String> =
        text.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 2 && it !in STOP }
            .toSet()
}
