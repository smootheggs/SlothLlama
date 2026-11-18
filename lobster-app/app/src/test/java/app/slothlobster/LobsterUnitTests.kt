package app.slothlobster

import app.slothlobster.jobs.Changes
import app.slothlobster.jobs.Plans
import app.slothlobster.jobs.Repeats
import app.slothlobster.jobs.Watches
import app.slothlobster.mailbox.LobsterCatch
import app.slothlobster.mailbox.LobsterOrder
import app.slothlobster.mailbox.MailboxCrypto
import app.slothlobster.web.Feeds
import app.slothlobster.web.Hit
import app.slothlobster.web.Readable
import app.slothlobster.web.Search
import app.slothlobster.web.Web
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.Calendar

class LobsterUnitTests {

    // --- the mailbox: must stay byte-compatible with SlothLlama ---

    @Test
    fun sealedFilesOpenWithTheSameKeyAndType() {
        val key = MailboxCrypto.newKey()
        val sealed = MailboxCrypto.seal(key, "catch", "hello".toByteArray())
        assertEquals("SLM1", String(sealed.copyOfRange(0, 4)))
        assertEquals("hello", String(MailboxCrypto.open(key, "catch", sealed)!!))
        // A catch can't be passed off as an order, and another key opens nothing.
        assertNull(MailboxCrypto.open(key, "order", sealed))
        assertNull(MailboxCrypto.open(MailboxCrypto.newKey(), "catch", sealed))
    }

    @Test
    fun pairingCodeIs43CharactersAndRoundTrips() {
        val key = MailboxCrypto.newKey()
        val code = MailboxCrypto.pairingCode(key)
        assertEquals(43, code.length)
        val spaced = code.chunked(4).joinToString(" ")
        assertTrue(MailboxCrypto.fromPairingCode(spaced)!!.contentEquals(key))
        assertNull(MailboxCrypto.fromPairingCode("not a code"))
    }

    @Test
    fun ordersAndCatchMatchTheSpec() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val order = json.decodeFromString(
            LobsterOrder.serializer(),
            """{"v":1,"id":"abc","kind":"research","text":"Why?","depth":"quick","deliver":"library","createdAt":1}"""
        )
        assertEquals("research", order.kind)
        val catch = LobsterCatch(id = "c1", orderId = "abc", title = "T", text = "x", createdAt = 2)
        val back = json.decodeFromString(LobsterCatch.serializer(), json.encodeToString(LobsterCatch.serializer(), catch))
        assertEquals(catch, back)
    }

    // --- only public https sites ---

    @Test
    fun linksAreUpgradedToHttpsAndLocalOnesRefused() {
        assertEquals("https://example.com/a", Web.safeUrl("http://example.com/a"))
        assertEquals("https://example.com/", Web.safeUrl("example.com"))
        assertNull(Web.safeUrl("https://localhost:8080/"))
        assertNull(Web.safeUrl("https://127.0.0.1/"))
        assertNull(Web.safeUrl("https://192.168.1.1/admin"))
        assertNull(Web.safeUrl("https://10.0.0.5/"))
        assertNull(Web.safeUrl("https://[::1]/"))
        assertNull(Web.safeUrl("ftp://example.com/file"))
        assertNull(Web.safeUrl("https://printer.local/"))
        assertNotNull(Web.safeUrl("https://8.8.8.8/"))
    }

    @Test
    fun privateAddressesAreNotPublic() {
        assertFalse(Web.isPublic(InetAddress.getByAddress(byteArrayOf(100, 64, 0, 1))))
        assertFalse(Web.isPublic(InetAddress.getByAddress(byteArrayOf(172.toByte(), 16, 0, 1))))
        assertTrue(Web.isPublic(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))))
    }

    @Test
    fun findsTheLinkInAnOrder() {
        assertEquals("https://arxiv.org/abs/2611.0412", Web.firstLink("please fetch https://arxiv.org/abs/2611.0412."))
        assertEquals("example.org/page", Web.firstLink("look at example.org/page for me"))
        assertNull(Web.firstLink("no link here"))
    }

    // --- searches ---

    @Test
    fun duckDuckGoResultsAreUnwrappedAndAdsSkipped() {
        val html = """
            <div class="result result--ad"><a class="result__a" href="//duckduckgo.com/y.js?ad_domain=x.com">Ad</a></div>
            <div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.ecb.europa.eu%2Fpress%2F&amp;rut=1">ECB</a></div>
            <div class="result"><a class="result__a" href="https://www.youtube.com/watch?v=1">Video</a></div>
        """
        val hits = Search.usable(Search.duckDuckGo(html))
        assertEquals(listOf(Hit("ECB", "https://www.ecb.europa.eu/press/")), hits)
    }

    @Test
    fun bingResultsAreRead() {
        val html = """<ol><li class="b_algo"><h2><a href="https://example.com/a">Example A</a></h2></li></ol>"""
        assertEquals(listOf(Hit("Example A", "https://example.com/a")), Search.bing(html))
    }

    @Test
    fun wikipediaAnswersAreRead() {
        val search = """{"query":{"search":[{"title":"European Central Bank"},{"title":"Base rate"}]}}"""
        assertEquals(listOf("European Central Bank", "Base rate"), Search.wikiTitles(search))
        val page = """{"query":{"pages":{"1":{"extract":"Intro.\n\n== History ==\nFounded in 1993."}}}}"""
        assertEquals("Intro.\n\n## History\nFounded in 1993.", Search.wikiExtract(page))
        assertEquals("zh", Search.wikiLanguage("中央银行"))
        assertEquals("ja", Search.wikiLanguage("ひらがな"))
        assertEquals("en", Search.wikiLanguage("rate cut"))
    }

    // --- reading pages and feeds ---

    @Test
    fun readableKeepsTheArticleAndDropsTheMenus() {
        val html = """
            <html><head><title>Small models</title></head><body>
            <nav><ul><li>Home</li><li>About</li></ul></nav>
            <article><h1>The case for small models</h1>
            <p>${"Small models run on a tablet without a connection. ".repeat(8)}</p>
            <p>${"They keep what you ask private, on your own device. ".repeat(6)}</p>
            <ul><li>Fast</li><li>Private</li></ul></article>
            <footer><p>Copyright and cookie notice</p></footer>
            <script>alert('x')</script></body></html>
        """
        val page = Readable.extract(html, "https://example.com/small")
        assertEquals("Small models", page.title)
        assertEquals("# The case for small models", page.lines.first())
        assertTrue(page.lines.contains("- Private"))
        assertFalse(page.text.contains("Home"))
        assertFalse(page.text.contains("cookie"))
        assertFalse(page.text.contains("alert"))
    }

    @Test
    fun rssAndAtomFeedsAreParsed() {
        val rss = """<?xml version="1.0"?><rss><channel><title>World</title>
            <item><title>First</title><link>https://n.example/1</link><description>&lt;p&gt;One &lt;b&gt;bold&lt;/b&gt;&lt;/p&gt;</description></item>
            <item><title>Second</title><link>https://n.example/2</link></item></channel></rss>"""
        val feed = Feeds.parse(rss, "fallback")
        assertEquals("World", feed.title)
        assertEquals(listOf("First", "Second"), feed.items.map { it.title })
        assertEquals("One bold", feed.items.first().summary)

        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><title>Releases</title>
            <entry><title>b4100</title><link rel="alternate" href="https://github.com/x/releases/b4100"/></entry></feed>"""
        val releases = Feeds.parse(atom, "fallback")
        assertEquals("Releases", releases.title)
        assertEquals("https://github.com/x/releases/b4100", releases.items.single().link)
    }

    // --- watching ---

    @Test
    fun pageChangesAreFoundAndTinyOnesIgnored() {
        val before = Changes.normalise(listOf("Jobs", "- Office Manager, Lisbon", "- Data Analyst, Berlin"))
        val after = Changes.normalise(listOf("Jobs", "- Product Designer, Madrid", "- Data Analyst, Berlin"))
        val change = Changes.between(before, after, topic = false)
        assertEquals(listOf("- Product Designer, Madrid"), change.added)
        assertEquals(listOf("- Office Manager, Lisbon"), change.removed)
        assertTrue(Changes.describe(change, "https://x").contains("**New**"))

        val clock = Changes.between(listOf("Updated 10:40"), listOf("Updated 10:41"), topic = false)
        assertTrue(clock.isEmpty)
    }

    @Test
    fun topicsOnlyCountNewHeadlines() {
        val change = Changes.between(listOf("Old story"), listOf("New story"), topic = true)
        assertEquals(listOf("New story"), change.added)
        assertTrue(change.removed.isEmpty())
        assertTrue(Changes.between(listOf("A story", "B story"), listOf("A story"), topic = true).isEmpty)
    }

    // --- 1.1: narrowed watches ---

    @Test
    fun aWatchCanFollowOnePartOfAPage() {
        val lines = listOf("# Careers", "## Open roles", "- Data Analyst, Berlin", "- Office Manager, Lisbon", "## Benefits", "- Free lunch")
        assertEquals(listOf("Careers", "Open roles", "Benefits"), Watches.headings(lines))
        assertEquals(listOf("## Open roles", "- Data Analyst, Berlin", "- Office Manager, Lisbon"), Watches.section(lines, "open roles"))
        assertNull(Watches.section(lines, "Pensions"))
        assertTrue(Watches.matches("- Data Analyst, Berlin", listOf("berlin")))
        assertFalse(Watches.matches("- Office Manager, Lisbon", listOf("berlin")))
    }

    @Test
    fun watchWordsComeFromTheOrder() {
        assertEquals(listOf("Berlin", "remote"), Watches.keywordsFrom("watch https://example.com/jobs for Berlin, remote"))
        assertEquals(listOf("ECB", "rates"), Watches.split("ECB and rates"))
        assertEquals("the ECB rate decision", Watches.topicFrom("watch the ECB rate decision"))
        assertEquals("Formula 1", Watches.topicFrom("keep an eye on news about Formula 1"))
    }

    // --- 1.1: repeating orders ---

    @Test
    fun repeatsAreReadFromTheOrder() {
        val weekly = Repeats.parse("every Monday at 7:30 research AI chips")!!
        assertEquals(7, weekly.everyDays)
        assertEquals(Calendar.MONDAY, weekly.weekday)
        assertEquals(7, weekly.hour)
        assertEquals(30, weekly.minute)
        assertEquals("research AI chips", Repeats.strip("every Monday at 7:30 research AI chips"))
        val daily = Repeats.parse("a brief on F1 every morning")!!
        assertEquals(1, daily.everyDays)
        assertEquals(7, daily.hour)
        assertEquals(18, Repeats.parse("daily at 6pm brief on markets")!!.hour)
        assertNull(Repeats.parse("what happened at 9 in the morning"))
    }

    @Test
    fun aRepeatIsDueOncePerDayAfterItsTime() {
        val monday9 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 16, 9, 0) }
        assertTrue(Repeats.due(7, Calendar.MONDAY, 7, 0, null, monday9))
        assertFalse(Repeats.due(7, Calendar.MONDAY, 7, 0, "2026-11-16", monday9))
        assertFalse(Repeats.due(7, Calendar.TUESDAY, 7, 0, null, monday9))
        assertFalse(Repeats.due(1, Calendar.MONDAY, 10, 0, null, monday9))
    }

    // --- 1.1: several searches ---

    @Test
    fun extraLinesBecomeSearches() {
        val (q, searches) = Plans.searches("How do ECB rates work?\n- ECB deposit facility\nsearch: ECB main refinancing rate", emptyList())
        assertEquals("How do ECB rates work?", q)
        assertEquals(listOf("ECB deposit facility", "ECB main refinancing rate"), searches)
        assertEquals(listOf("one question"), Plans.searches("one question", emptyList()).second)
        assertEquals(listOf("planned"), Plans.searches("q", listOf("planned")).second)
        assertEquals(2, Plans.perSearch(4, 3))
        assertEquals(4, Plans.perSearch(7, 2))
    }

    // --- 1.1: feeds and search ---

    @Test
    fun feedListsKeepTheirGroups() {
        val list = Feeds.parseList("https://a.example/rss\n# F1\nhttps://f1.example/feed\nNews:\nhttp://n.example/rss")
        assertEquals(listOf("" to "https://a.example/rss", "F1" to "https://f1.example/feed", "News" to "https://n.example/rss"), list)
        val text = Feeds.formatList(list.map { it.second }, list.filter { it.first.isNotBlank() }.associate { it.second to it.first })
        assertEquals(list, Feeds.parseList(text))
        val opml = """<opml><body><outline text="Sport" title="Sport"><outline text="F1" xmlUrl="https://f1.example/feed"/></outline><outline text="Top" xmlUrl="https://n.example/rss"/></body></opml>"""
        assertEquals(listOf("Sport" to "https://f1.example/feed", "" to "https://n.example/rss"), Feeds.parseOpml(opml))
    }

    @Test
    fun anyResultsPageIsReadWithoutKnowingItsLayout() {
        val html = """<a href="https://mojeek.com/about">About Mojeek search engine</a>
            <a href="https://example.org/ecb-rates">How the ECB sets interest rates</a>
            <a href="https://example.org/x">Short</a>"""
        assertEquals(listOf(Hit("How the ECB sets interest rates", "https://example.org/ecb-rates")), Search.generic(html, "https://www.mojeek.com/", "mojeek.com"))
    }
}
