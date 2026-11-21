package app.slothlobster

import app.slothlobster.jobs.Clean
import app.slothlobster.jobs.EdPara
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.11: stories read clean with the paper, and the hand-off with SlothLlama. */
class CleanTests {

    @Test
    fun `google news links are page only before trying, others are tried`() {
        assertEquals(Clean.WHY_GOOGLE, Clean.before("https://news.google.com/rss/articles/abc?oc=5"))
        assertNull(Clean.before("https://www.bbc.co.uk/news/world-123"))
        assertEquals("there's no link", Clean.before(""))
    }

    @Test
    fun `the story's paragraphs keep headings and lists and leave out its title and scraps`() {
        val sentence = "The central bank held rates steady on Tuesday, citing slower growth in the second half."
        val lines = listOf(
            "# Rates held steady",
            "By A. Writer",
            "Share",
            sentence,
            sentence,
            "## What it means",
            "- Mortgages stay where they are",
            "Short, but a sentence."
        )
        val paras = Clean.paras(lines, "Rates held steady")
        assertEquals(listOf(sentence, "## What it means", "- Mortgages stay where they are", "Short, but a sentence."), paras.map { it.t })
    }

    @Test
    fun `paragraphs stop at the caps`() {
        val long = "x".repeat(300) + "."
        val paras = Clean.paras(List(500) { "$long $it" }, "")
        assertTrue(paras.size <= Clean.MAX_PARAS)
        assertTrue(paras.sumOf { it.t.length } <= Clean.MAX_CHARS + 4_000)
    }

    @Test
    fun `a page with only a few lines is shut, a whole article isn't`() {
        assertEquals(Clean.WHY_SHUT, Clean.shut(listOf(EdPara("Subscribe to keep reading this article today."))))
        assertNull(Clean.shut(List(10) { EdPara("A full sentence of the article, long enough to count as reading text.") }))
        // Headings alone don't make an article.
        assertEquals(Clean.WHY_SHUT, Clean.shut(List(40) { EdPara("## A heading that goes on for a while, but only a heading") }))
    }

    @Test
    fun `the lead picture is the page's card image, made absolute, over https`() {
        val page = """<html><head><meta property="og:image" content="/img/lead.jpg"></head><body></body></html>"""
        assertEquals("https://example.org/img/lead.jpg", Clean.leadPicture(page, "https://example.org/news/1"))
        val twitter = """<html><head><meta name="twitter:image" content="https://cdn.example.org/a.png"></head></html>"""
        assertEquals("https://cdn.example.org/a.png", Clean.leadPicture(twitter, "https://example.org/"))
        // Plain http is asked for over https, as everything the Lobster opens; a private address never.
        val plain = """<html><head><meta property="og:image" content="http://example.org/a.jpg"></head></html>"""
        assertEquals("https://example.org/a.jpg", Clean.leadPicture(plain, "https://example.org/"))
        val local = """<html><head><meta property="og:image" content="https://192.168.1.2/a.jpg"></head></html>"""
        assertNull(Clean.leadPicture(local, "https://example.org/"))
        assertNull(Clean.leadPicture("<html><head></head></html>", "https://example.org/"))
    }

    @Test
    fun `the lobster opens only links it brought`() {
        val sent = listOf("https://www.bbc.co.uk/news/world-123", "a headline without a link")
        assertTrue(HandOff.brought("https://www.bbc.co.uk/news/world-123", "https://www.bbc.co.uk/news/world-123", sent))
        assertTrue(HandOff.brought("HTTPS://WWW.BBC.CO.UK/news/world-123", "https://www.bbc.co.uk/news/world-123", sent))
        assertFalse(HandOff.brought("https://example.org/elsewhere", "https://example.org/elsewhere", sent))
    }

    @Test
    fun `dev builds pair with dev builds`() {
        assertEquals("app.localmind.assistant", HandOff.llamaPackage("app.slothlobster"))
        assertEquals("app.localmind.assistant.debug", HandOff.llamaPackage("app.slothlobster.debug"))
    }
}
