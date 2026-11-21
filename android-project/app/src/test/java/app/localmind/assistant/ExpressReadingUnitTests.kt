package app.localmind.assistant

import app.localmind.assistant.data.edition.StoryPictures
import app.localmind.assistant.domain.edition.EdCorner
import app.localmind.assistant.domain.edition.EdPara
import app.localmind.assistant.domain.edition.EdSection
import app.localmind.assistant.domain.edition.EdStory
import app.localmind.assistant.domain.edition.EditionOut
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.Editions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.43: reading the Express's stories clean, their page through the Lobster, and what a tap does. */
class ExpressReadingUnitTests {

    private val text = "The central bank held rates steady on Tuesday, citing slower growth."

    private fun story(url: String, outlet: String = "BBC", clean: Boolean = true, why: String = "") = EdStory(
        title = "Rates held", url = url, outlet = outlet,
        paras = if (clean) listOf(EdPara(text), EdPara("## What it means"), EdPara("- Mortgages stay put")) else emptyList(),
        pageOnly = why
    )

    @Test
    fun `a story read clean has its text, a page-only one says why`() {
        val clean = story("https://www.bbc.co.uk/news/1")
        assertTrue(Editions.hasClean(clean))
        assertEquals("$text\n\n## What it means\n\n- Mortgages stay put", Editions.cleanText(clean))
        val shut = story("https://news.google.com/rss/articles/x", clean = false, why = "Google News links open only in a browser")
        assertFalse(Editions.hasClean(shut))
        // Text with a reason not to read it (the Lobster thought it shut) isn't offered either.
        assertFalse(Editions.hasClean(story("https://example.org/a", why = "a paywall")))
        // A paper from before the Lobster read stories clean: page only, with no reason given.
        assertFalse(Editions.hasClean(story("https://example.org/b", clean = false)))
    }

    @Test
    fun `a tap reads clean only for outlets set that way, and only with the text here`() {
        val bbc = story("https://www.bbc.co.uk/news/1", outlet = "BBC")
        var setup = EditionSetup()
        assertFalse(Editions.tapsClean(setup, bbc))
        setup = Editions.withTap(setup, " bbc ", true)
        assertEquals(listOf("bbc"), setup.tapClean)
        assertTrue(Editions.tapsClean(setup, bbc))
        assertFalse(Editions.tapsClean(setup, story("https://www.bbc.co.uk/news/2", outlet = "BBC", clean = false)))
        assertFalse(Editions.tapsClean(setup, story("https://www.npr.org/1", outlet = "NPR")))
        setup = Editions.withTap(setup, "BBC", true)
        assertEquals(listOf("bbc"), setup.tapClean)
        setup = Editions.withTap(setup, "BBC", false)
        assertTrue(setup.tapClean.isEmpty())
        assertEquals(setup, Editions.withTap(setup, "  ", true))
    }

    @Test
    fun `the tap setting is the tablet's own, not part of the Lobster's order`() {
        val setup = Editions.withTap(EditionSetup(), "BBC", true)
        val order = Editions.planOrder(setup, now = false, at = 1)!!
        assertFalse("tapClean" in order)
    }

    @Test
    fun `news stories keep a whole article and their picture, corners stay as they were`() {
        val long = List(200) { EdPara("p$it " + "w".repeat(300)) }
        val e = EditionOut(
            id = "e1", name = "Morning", made = 1,
            top = listOf(EdStory(title = "T", url = "https://a.org/1", paras = long, pic = "AAAA", pageOnly = "x\u0007y")),
            sections = listOf(EdSection("World", listOf(EdStory(title = "S", url = "https://a.org/2", pic = "B".repeat(Editions.MAX_PIC_CHARS))))),
            corners = listOf(EdCorner("fr", "Le coin", "Le Monde", listOf(EdStory(title = "C", url = "https://lemonde.fr/1", paras = long, pic = "AAAA"))))
        )
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(EditionOut.serializer(), e)
        val back = Editions.parse(text)
        val top = back.top.single()
        assertTrue(top.paras.size <= Editions.MAX_CLEAN_PARAS)
        assertTrue(top.paras.size > 20)
        assertTrue(top.paras.sumOf { it.t.length } <= Editions.MAX_CLEAN_CHARS)
        assertEquals("AAAA", top.pic)
        assertEquals("x y", top.pageOnly)
        // A picture too big to be what the Lobster draws is dropped.
        assertEquals("", back.sections.single().stories.single().pic)
        val corner = back.corners.single().stories.single()
        assertEquals(20, corner.paras.size)
        assertEquals("", corner.pic)
    }

    @Test
    fun `a story is found in its paper by its link, news only`() {
        val e = EditionOut(
            id = "e1", name = "Morning", made = 1,
            top = listOf(story("https://www.bbc.co.uk/news/1")),
            sections = listOf(EdSection("Tech", listOf(story("https://example.org/Tech/2")))),
            corners = listOf(EdCorner("fr", "Le coin", "Le Monde", listOf(story("https://lemonde.fr/3"))))
        )
        // As handed back (case and spaces aside), the story as the paper has it.
        assertEquals("https://example.org/Tech/2", Editions.findNews(e, "https://example.org/tech/2 ")?.url)
        assertEquals("https://www.bbc.co.uk/news/1", Editions.findNews(e, " https://www.bbc.co.uk/news/1")?.url)
        assertNull(Editions.findNews(e, "https://lemonde.fr/3"))
        assertNull(Editions.findNews(e, ""))
    }

    @Test
    fun `picture names belong to their paper and nothing else gets through`() {
        val paper = "0001731000000000-abc123.json"
        val name = StoryPictures.nameFor(paper, 7)
        assertEquals("0001731000000000-abc123~7.jpg", name)
        assertTrue(StoryPictures.safe(name))
        assertEquals("0001731000000000-abc123", StoryPictures.base(paper))
        assertFalse(StoryPictures.safe("../setup.json"))
        assertFalse(StoryPictures.safe("a~1.png"))
        assertFalse(StoryPictures.safe("a/b~1.jpg"))
    }
}
