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
    fun `a tap reads clean whenever the text is here, unless the outlet is set to the page`() {
        val bbc = story("https://www.bbc.co.uk/news/1", outlet = "BBC")
        var setup = EditionSetup()
        assertTrue(Editions.tapsClean(setup, bbc))
        assertTrue(Editions.tapsClean(setup, story("https://www.npr.org/1", outlet = "NPR")))
        // Without clean text, a tap opens the page whatever the setting.
        assertFalse(Editions.tapsClean(setup, story("https://news.google.com/x", outlet = "Reuters", clean = false, why = "Google News links open only in a browser")))
        setup = Editions.withTap(setup, " bbc ", false)
        assertEquals(listOf("bbc"), setup.tapPage)
        assertFalse(Editions.tapsClean(setup, bbc))
        assertTrue(Editions.tapsClean(setup, story("https://www.npr.org/1", outlet = "NPR")))
        setup = Editions.withTap(setup, "BBC", false)
        assertEquals(listOf("bbc"), setup.tapPage)
        setup = Editions.withTap(setup, "BBC", true)
        assertTrue(setup.tapPage.isEmpty())
        assertEquals(setup, Editions.withTap(setup, "  ", false))
    }

    @Test
    fun `the tap setting is the tablet's own, not part of the Lobster's order`() {
        val setup = Editions.withTap(EditionSetup(), "BBC", false)
        val order = Editions.planOrder(setup, now = false, at = 1)!!
        assertFalse("tapPage" in order)
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

    // --- 1.51.1: the page's own lines around an article, kept and folded ---

    private fun bbc() = EdStory(
        title = "Rates held", url = "https://www.bbc.co.uk/news/2", outlet = "BBC",
        paras = listOf(
            EdPara("By A. Writer", aside = true),
            EdPara(text),
            EdPara("Image source, Getty Images", aside = true),
            EdPara("The bank said inflation was falling faster than expected."),
            EdPara("Mortgage rates fall again", aside = true),
            EdPara("Published 2 days ago", aside = true)
        )
    )

    @Test
    fun `the paper keeps which lines were the page's own`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val e = EditionOut(id = "e", name = "Sloth Express", made = 0, top = listOf(bbc()))
        val read = Editions.parse(json.encodeToString(EditionOut.serializer(), e))
        assertEquals(listOf(true, false, true, false, true, true), read.top.single().paras.map { it.aside })
        // A paper from before 1.51.1 has none.
        assertEquals(false, json.decodeFromString(EdPara.serializer(), """{"t":"Words"}""").aside)
    }

    @Test
    fun `the reader gets them marked, a chat and reading time without them`() {
        val s = bbc()
        val all = Editions.cleanText(s)
        assertTrue(all.startsWith(Editions.ASIDE_MARK + "By A. Writer\n\n$text"))
        assertTrue(all.contains(Editions.ASIDE_MARK + "Published 2 days ago"))
        val article = "$text\n\nThe bank said inflation was falling faster than expected."
        assertEquals(article, Editions.cleanText(s, asides = false))
        assertEquals(article, Editions.withoutAsides(all))
        // A text with no marks (a note, a book) is left exactly as it is.
        assertEquals("a\n\n\n\nb", Editions.withoutAsides("a\n\n\n\nb"))
    }

    @Test
    fun `a page can't fold its own article by writing the mark`() {
        val s = EdStory(title = "x", url = "https://example.org/x", paras = listOf(EdPara(Editions.ASIDE_TOKEN + " The whole article.")))
        assertEquals("The whole article.", Editions.cleanText(s))
        assertEquals("The whole article.", Editions.withoutAsides(Editions.cleanText(s)))
    }

    @Test
    fun `the page's lines never crowd out the article`() {
        val paras = List(100) { listOf(EdPara("Related story $it", aside = true), EdPara("Paragraph $it of the article.")) }.flatten()
        val kept = Editions.cleanParas(paras)
        assertEquals(100, kept.count { !it.aside })
        assertEquals(Editions.MAX_ASIDES, kept.count { it.aside })
        assertEquals("Paragraph 99 of the article.", kept.last { !it.aside }.t)
    }
}
