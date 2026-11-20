package app.slothlobster

import app.slothlobster.jobs.EdSection
import app.slothlobster.jobs.EdStory
import app.slothlobster.jobs.EditionOut
import app.slothlobster.jobs.EditionPlan
import app.slothlobster.jobs.EditionTime
import app.slothlobster.jobs.Editions
import app.slothlobster.web.FeedItem
import app.slothlobster.web.Feeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 1.8: the edition SlothLlama plans and the Lobster makes. */
class EditionTests {

    @Test
    fun `a plan is kept within bounds and only readable sources stay`() {
        val text = """{"format":"slothexpress-plan","v":1,"times":[{"name":"Morning","h":31,"m":-5}],"top":40,
            "sections":[{"name":"World","sources":["https://example.org/feed.xml","http://example.org/other","ftp://x","search:  gold OR silver ","TOP"],"count":99},
                        {"name":"Empty","sources":["not a link at all"]}],
            "corners":[{"lang":"zh","sources":["https://easymandarin.news/en"],"count":9},{"lang":"xx","sources":["top"]}],
            "mute":[" football ","football",""]}"""
        val p = Editions.parsePlan(text)
        assertEquals(EditionTime("Morning", 23, 0), p.times.single())
        assertEquals(6, p.top)
        assertEquals(1, p.sections.size)
        assertEquals(listOf("https://example.org/feed.xml", "https://example.org/other", "search: gold OR silver", "top"), p.sections[0].sources)
        assertEquals(12, p.sections[0].count)
        assertEquals("zh", p.corners.single().lang)
        assertEquals(4, p.corners.single().count)
        assertEquals(listOf("football"), p.mute)
    }

    @Test(expected = Exception::class)
    fun `something that isn't a plan is refused`() {
        Editions.parsePlan("""{"format":"slothbooks-covers"}""")
    }

    private fun at(h: Int, m: Int, weekday: Int = Calendar.WEDNESDAY): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 18, h, m, 0)
        while (get(Calendar.DAY_OF_WEEK) != weekday) add(Calendar.DAY_OF_MONTH, 1)
    }

    @Test
    fun `the latest time passed today is due once`() {
        val plan = EditionPlan(times = listOf(EditionTime("Morning", 7, 0), EditionTime("Evening", 18, 30)))
        assertNull(Editions.due(plan, at(6, 59), emptyList()))
        assertEquals("Morning", Editions.due(plan, at(7, 0), emptyList())?.name)
        val now = at(19, 0)
        val evening = Editions.due(plan, now, emptyList())
        assertEquals("Evening", evening?.name)
        val made = Editions.madeKeys(plan, now, evening!!)
        assertEquals(2, made.size)
        assertNull(Editions.due(plan, now, made))
    }

    @Test
    fun `no edition on weekends unless asked, and none when off`() {
        val plan = EditionPlan(weekends = false)
        assertNull(Editions.due(plan, at(9, 0, Calendar.SATURDAY), emptyList()))
        assertEquals("Morning", Editions.due(plan.copy(weekends = true), at(9, 0, Calendar.SATURDAY), emptyList())?.name)
        assertNull(Editions.due(plan.copy(on = false), at(9, 0), emptyList()))
    }

    @Test
    fun `the same story from two outlets becomes a top story`() {
        val items = listOf(
            "BBC" to FeedItem("Senate passes bipartisan energy bill amid environmental concerns", "https://a.example/1", at = 2),
            "NPR" to FeedItem("Senate passes energy bill after bipartisan deal", "https://b.example/2", summary = "Speeds up approvals.", at = 1),
            "BBC" to FeedItem("New York City unveils plan to move jails off Rikers Island", "https://a.example/3"),
            "Hacker News" to FeedItem("If you stop writing code, you also get worse at reviewing it", "https://c.example/4")
        )
        val top = Editions.topStories(items, 3)
        assertEquals(1, top.size)
        val story = Editions.topStory(top.single())
        assertEquals(2, story.outlets)
        assertEquals("Speeds up approvals.", story.summary)
        assertEquals(listOf("BBC"), story.also)
    }

    @Test
    fun `one outlet saying it twice isn't a top story`() {
        val items = listOf(
            "BBC" to FeedItem("Senate passes bipartisan energy bill", "https://a.example/1"),
            "bbc" to FeedItem("Senate passes bipartisan energy bill in late vote", "https://a.example/2")
        )
        assertTrue(Editions.topStories(items, 3).isEmpty())
    }

    @Test
    fun `muted stories go and lifted ones come first`() {
        val items = listOf(FeedItem("Football final tonight", "1"), FeedItem("Markets calm", "2"), FeedItem("Gold edges higher", "3"))
        val out = Editions.arrange(items, lift = listOf("gold"), mute = listOf("football")) { it }
        assertEquals(listOf("3", "2"), out.map { it.link })
        // A word, not part of a word: "golden" isn't "gold".
        assertEquals(listOf("x"), Editions.arrange(listOf(FeedItem("Golden Gate fog", "x")), listOf("gold"), emptyList()) { it }.map { it.link })
    }

    @Test
    fun `a corner's topic puts its stories first`() {
        val items = listOf(FeedItem("Weather turns cool", "1"), FeedItem("Trade talks resume", "2"))
        assertEquals(listOf("2", "1"), Editions.preferTopic(items, "economy, trade") { it }.map { it.link })
    }

    @Test
    fun `a page without a feed gives the stories it links to`() {
        val html = """<html><body>
            <a href="/en/about">About us</a>
            <a href="https://easymandarin.news/en/articles/fe8e828c66fe46d08f543317e6eba793">The chip story in simple Chinese</a>
            <a href="/en/articles/b7a7484fdadc4163b2421eafa00a70d1">Leaders meet to talk about risk</a>
            <a href="/en?page=2">Next page</a>
            <a href="https://elsewhere.example/en/articles/abcdefghij">Another site's story</a>
            <a href="/en/articles/b7a7484fdadc4163b2421eafa00a70d1">Leaders meet to talk about risk</a>
        </body></html>"""
        val found = Editions.listing(html, "https://easymandarin.news/en", 5)
        assertEquals(2, found.size)
        assertEquals("https://easymandarin.news/en/articles/fe8e828c66fe46d08f543317e6eba793", found[0].link)
        assertEquals("Leaders meet to talk about risk", found[1].title)
    }

    @Test
    fun `pinyin printed beside the characters is taken out, names stay`() {
        assertEquals("美国科技公司英伟达", Editions.withoutPinyin("měi guó美国 kē jì科技 gōng sī公司 yīng wěi dá英伟达"))
        assertEquals("他的AI很好", Editions.withoutPinyin("tā他 de的AI hěn很 hǎo好"))
        assertEquals("Nvidia卖芯片", Editions.withoutPinyin("Nvidia mài卖 xīn piàn芯片"))
    }

    @Test
    fun `a Chinese story keeps each sentence's English`() {
        val lines = listOf(
            "# zhōng wén中文 biāo tí标题",
            "měi guó美国 hěn很 dà大。",
            "The United States is very big.",
            "天气转凉。 The weather turns cool. 市民注意保暖。 People should keep warm."
        )
        assertEquals("中文标题", Editions.chineseTitle(lines))
        val paras = Editions.chineseParas(lines)
        assertEquals("中文标题", paras[0].t)
        assertEquals("美国很大。", paras[1].t)
        assertEquals("The United States is very big.", paras[1].en)
        assertEquals("天气转凉。市民注意保暖。", paras[2].t.replace(" ", ""))
        assertTrue("The weather turns cool." in paras[2].en)
        assertTrue("People should keep warm." in paras[2].en)
    }

    @Test
    fun `other languages keep their paragraphs, not the menus`() {
        val paras = Editions.paras(listOf("# Titre", "- Accueil", "Le gouvernement a présenté son budget ce matin à Paris.", "Partager"))
        assertEquals(1, paras.size)
    }

    @Test
    fun `Hacker News's link lines become small print`() {
        val item = Feeds.tidy(
            FeedItem(
                "If you stop writing code, you also get worse at reviewing it", "https://cekrem.github.io/x",
                summary = "Article URL: https://cekrem.github.io/x Comments URL: https://news.ycombinator.com/item?id=1 Points: 113 # Comments: 19"
            )
        )
        assertEquals("", item.summary)
        assertEquals("113 points · 19 comments", item.meta)
    }

    @Test
    fun `Google News titles lose their outlet suffix`() {
        val item = Feeds.tidy(FeedItem("Senate passes energy bill - Reuters", "https://news.google.com/x", outlet = "Reuters"))
        assertEquals("Senate passes energy bill", item.title)
        assertEquals("Reuters", item.outlet)
    }

    @Test
    fun `feed dates are read in both formats`() {
        assertEquals(1_795_158_000_000L, Feeds.date("Fri, 20 Nov 2026 07:00:00 GMT"))
        assertEquals(1_795_158_000_000L, Feeds.date("2026-11-20T07:00:00Z"))
        assertEquals(0L, Feeds.date("yesterday"))
    }

    @Test
    fun `brackets in a title don't break its link`() {
        val e = EditionOut(
            id = "e1", name = "Morning", made = 0,
            sections = listOf(EdSection("Tech", listOf(EdStory("A paper [pdf]", "https://x.example/p.pdf", "x.example", meta = "60 points"))))
        )
        val md = Editions.markdown(e, "Fri 20 Nov 2026")
        assertTrue("[A paper (pdf)](https://x.example/p.pdf) — x.example · 60 points" in md)
        assertFalse("[pdf]" in md)
    }

    @Test
    fun `sections interleave their feeds`() {
        assertEquals(listOf(1, 10, 2, 20, 3), Editions.interleave(listOf(listOf(1, 2, 3), listOf(10, 20))))
    }
}
