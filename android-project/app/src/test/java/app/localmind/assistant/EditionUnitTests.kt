package app.localmind.assistant

import app.localmind.assistant.domain.edition.EdCorner
import app.localmind.assistant.domain.edition.EdSection
import app.localmind.assistant.domain.edition.EdStory
import app.localmind.assistant.domain.edition.EditionOut
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.edition.SavedEdition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.32: the edition, its settings and the help in its language corners. */
class EditionUnitTests {

    @Test
    fun `the default plan fits one order, without the corners switched off`() {
        val text = Editions.planOrder(EditionSetup(), now = true, at = 5)
        assertNotNull(text)
        assertTrue(text!!.length <= Editions.MAX_PLAN_CHARS)
        assertTrue("\"format\":\"slothexpress-plan\"" in text)
        assertTrue("\"now\":true" in text)
        assertTrue("easymandarin.news" in text)
        assertFalse("ansa.it" in text) // Italian starts switched off
    }

    @Test
    fun `a plan too long for one order is refused, not cut short`() {
        val big = EditionSetup().let { s -> s.copy(plan = s.plan.copy(mute = List(400) { "word$it" })) }
        assertEquals(null, Editions.planOrder(big, now = false, at = 0))
    }

    @Test
    fun `typed sources become links, searches or top stories`() {
        assertEquals("top", Editions.source(" Top "))
        assertEquals("search: gold OR silver", Editions.source("search:gold OR silver"))
        assertEquals("https://example.org/feed", Editions.source("http://example.org/feed"))
        assertEquals("https://example.org/rss.xml", Editions.source("example.org/rss.xml"))
        assertEquals("search: Hong Kong economy", Editions.source("Hong Kong economy"))
        assertEquals(null, Editions.source("   "))
        assertEquals("News search: \"gold\"", Editions.sourceLabel("search: gold"))
        assertEquals("bbci.co.uk", Editions.sourceLabel("https://feeds.bbci.co.uk/news/world/rss.xml"))
    }

    @Test
    fun `a story's small print`() {
        val now = 10L * 60 * 60 * 1000
        val s = EdStory("Senate passes energy bill", outlet = "BBC", outlets = 3, at = now - 2 * 60 * 60 * 1000)
        assertEquals("BBC · 3 outlets · 2 h", Editions.smallPrint(s, now))
        assertEquals("cekrem.github.io · 60 points · 12 comments", Editions.smallPrint(EdStory("x", outlet = "cekrem.github.io", meta = "60 points · 12 comments"), now))
        assertEquals("", Editions.ago(0, now))
        assertEquals("35 min", Editions.ago(now - 35 * 60_000, now))
    }

    @Test
    fun `the markets strip is worked out from SlothDesk's series`() {
        val tiles = Editions.marketTiles(
            mapOf(
                "GLD" to Triple("bars", listOf("2026-11-18", "2026-11-19"), listOf(200.0, 202.0)),
                "DTB3" to Triple("rate", listOf("2026-11-18", "2026-11-19"), listOf(0.0420, 0.0418)),
                "SLV" to Triple("bars", listOf("2026-11-19"), listOf(30.0))
            )
        )
        assertEquals(2, tiles.size)
        assertEquals("202.00", tiles[0].value)
        assertEquals("+1.00%", tiles[0].change)
        assertEquals(true, tiles[0].up)
        assertEquals("US 3M", tiles[1].name)
        assertEquals("4.18%", tiles[1].value)
        assertEquals("-2 bp", tiles[1].change)
    }

    @Test
    fun `Chinese key words are above your level, names left out, most used first`() {
        val words = listOf(
            Triple("香港", "Xiānggǎng", "Hong Kong"),
            Triple("天气", "tiānqì", "weather"),
            Triple("提醒", "tíxǐng", "to remind"),
            Triple("保暖", "bǎonuǎn", "to keep warm"),
            Triple("提醒", "tíxǐng", "to remind"),
            Triple("的", "de", "of"),
            Triple("一举两得", "yījǔliǎngdé", "(idiom) one move, two gains")
        )
        val hsk = mapOf("天气" to 1, "提醒" to 4)
        val keys = Editions.chineseKeyWords(words, hsk, cutoff = 2, known = setOf("保暖"))
        assertEquals(listOf("提醒", "保暖", "一举两得"), keys.map { it.word })
        assertEquals("HSK 4", keys[0].note)
        assertEquals("beyond HSK", keys[1].note)
        assertTrue(keys[1].known)
        assertEquals("一举两得", Editions.idiom(words)?.first)
    }

    @Test
    fun `French words are folded out of their articles`() {
        assertEquals(listOf("économie", "gouvernement", "présente"), Editions.frenchTokens("L’économie : le gouvernement présente"))
    }

    @Test
    fun `sections swap and take the length's size`() {
        val plan = Editions.defaultPlan()
        val swapped = Editions.swap(plan.sections, 0, 3)
        assertEquals("Tech", swapped[0].name)
        assertEquals("World", swapped[3].name)
        assertEquals(plan.sections, Editions.swap(plan.sections, 0, 9))
        assertTrue(Editions.withLength(plan, "quick").sections.all { it.count == 3 })
    }

    @Test
    fun `an edition is read within bounds and links stay https`() {
        val text = """{"format":"slothexpress-edition","v":1,"id":"e1","name":"Morning","made":1,"unknown":true,
            "sections":[{"name":"World","stories":[{"title":"A","url":"javascript:alert(1)"},{"title":"B","url":"https://x.example/b"}]}],
            "corners":[{"lang":"zh","name":"Mandarin","stories":[{"title":"香港天气转凉","paras":[{"t":"天气转凉。","en":"It turns cool."}]}]}]}"""
        val e = Editions.parse(text)
        assertEquals("", e.sections[0].stories[0].url)
        assertEquals("https://x.example/b", e.sections[0].stories[1].url)
        assertEquals("It turns cool.", e.corners[0].stories[0].paras[0].en)
        assertEquals(3, Editions.stories(e).size)
    }

    @Test(expected = Exception::class)
    fun `a file that isn't an edition is refused`() {
        Editions.parse("""{"format":"slothbooks-covers","v":1,"id":"x","name":"x","made":0}""")
    }

    @Test
    fun `read marks count by story`() {
        val e = EditionOut(id = "e", name = "Morning", made = 0, sections = listOf(EdSection("World", listOf(EdStory("A", "https://a"), EdStory("B")))))
        val saved = SavedEdition(e, read = listOf(Editions.storyKey(EdStory("A", "https://a"))))
        assertEquals(1, saved.readCount)
        assertEquals(2, saved.stories.size)
    }

    @Test
    fun `the listen version reads corners in their language`() {
        val e = EditionOut(
            id = "e", name = "Morning", made = 0,
            top = listOf(EdStory("Senate passes energy bill", summary = "Speeds up approvals.")),
            corners = listOf(EdCorner("fr", "French", stories = listOf(EdStory("Le budget est présenté"))))
        )
        val blocks = Editions.listen(e)
        assertEquals("Senate passes energy bill. Speeds up approvals.", blocks[2].first)
        assertEquals("fr", blocks.last().second)
        assertEquals(1, Editions.listenMinutes(e))
    }

    @Test
    fun `the desk card says what it is`() {
        val e = EditionOut(id = "e", name = "Morning", made = 0, sections = listOf(EdSection("Markets", listOf(EdStory("Gold edges higher", outlet = "Reuters")))))
        val card = Editions.deskCard(e, emptyList())
        assertTrue("headlines" in card.lowercase())
        assertTrue("1. Gold edges higher (Reuters)" in card)
    }
}
