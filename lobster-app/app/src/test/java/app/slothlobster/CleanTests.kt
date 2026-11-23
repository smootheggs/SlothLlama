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
        assertEquals(listOf(sentence, "## What it means", "- Mortgages stay where they are", "Short, but a sentence."), paras.filterNot { it.aside }.map { it.t })
        // Scraps are kept, marked aside, never dropped (1.13.3).
        assertEquals(listOf("By A. Writer", "Share"), paras.filter { it.aside }.map { it.t })
    }

    private val para = "The measures agreed in Brussels include scrapping most checks on British food exports, in return for following some rules."

    /** A page shaped like a BBC story: text blocks, an inline link, a related-links box, a picture with its caption. */
    private fun page(extra: String = "") = """<html><head><title>Deal agreed</title></head><body><article>
        <h1>Deal agreed</h1>
        <div data-component="byline-block"><p>By A. Correspondent, Political reporter</p></div>
        <div data-component="text-block"><p>$para</p></div>
        <div data-component="text-block"><p>A second paragraph, with <a href="/news/1">a link to more</a> inside an ordinary sentence of the article.</p></div>
        <figure><img src="a.jpg"><figcaption><p>Image caption, Leaders at the press conference in Brussels</p></figcaption></figure>
        <div data-component="links-block"><ul>
          <li><a href="/news/2"><p>What is the youth experience scheme and when will it start?</p></a><ul><li>Published 2 days ago</li></ul></li>
          <li><a href="/news/3"><p>Burnham gets his first taste of EU dealmaking and seems hungry for more</p></a><ul><li>Published 2 days ago</li></ul></li>
        </ul></div>
        <div data-component="text-block"><p>Ministers said it was absolutely clear the agreements on food exports, energy and travel would stand on their own.</p></div>
        $extra
        </article></body></html>"""

    @Test
    fun `related links, dates, captions and bylines are set aside, and the article's own paragraphs all stay`() {
        val r = app.slothlobster.web.Readable.extract(page(), "https://www.bbc.co.uk/news/articles/x")
        val paras = Clean.paras(r.lines, r.title, r.asides)
        val kept = paras.filterNot { it.aside }.map { it.t }
        val aside = paras.filter { it.aside }.map { it.t }
        assertEquals(listOf(
            para,
            "A second paragraph, with a link to more inside an ordinary sentence of the article.",
            "Ministers said it was absolutely clear the agreements on food exports, energy and travel would stand on their own."
        ), kept)
        assertTrue(aside.contains("What is the youth experience scheme and when will it start?"))
        assertTrue(aside.contains("- Published 2 days ago"))
        assertTrue(aside.any { it.startsWith("Image caption") })
        assertTrue(aside.any { it.startsWith("By A. Correspondent") })
        // In order, as the page has them: nothing moves.
        assertEquals(r.lines.filterNot { it == "# Deal agreed" }, paras.map { it.t })
    }

    @Test
    fun `a teaser outside any labelled box is still known by its links`() {
        val teaser = """<p><a href="/news/9">Faisal Islam: This cost-cutting deal doesn't mean lower prices will follow</a></p>"""
        // A story of a realistic length around it: the teaser is a small part of the page.
        val more = (1..6).joinToString("") { """<div data-component="text-block"><p>$para Paragraph $it goes on with the story.</p></div>""" }
        val r = app.slothlobster.web.Readable.extract(page(teaser + more), "https://www.bbc.co.uk/news/articles/x")
        val i = r.lines.indexOf("Faisal Islam: This cost-cutting deal doesn't mean lower prices will follow")
        assertTrue(i >= 0 && i in r.asides)
    }

    @Test
    fun `when the rules would set aside half the page, nothing is set aside`() {
        val links = (1..12).joinToString("") { """<p><a href="/n/$it">A headline about something else entirely, number $it of many</a></p>""" }
        val html = "<html><body><article><p>$para</p>$links</article></body></html>"
        val r = app.slothlobster.web.Readable.extract(html, "https://example.org/a")
        assertTrue(r.asides.isEmpty())
        assertTrue(r.lines.size >= 13)
    }

    @Test
    fun `the shut check counts only the article's own text`() {
        val scraps = List(30) { EdPara("Related: a headline that is long enough to look like reading text.", aside = true) }
        assertEquals(Clean.WHY_SHUT, Clean.shut(scraps))
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
    fun `the page's lines never crowd out the article`() {
        val lines = List(100) { listOf("Related story $it", "Paragraph $it of the article, long enough to count as one.") }.flatten()
        val paras = Clean.paras(lines, "A title")
        assertEquals(100, paras.count { !it.aside })
        assertEquals(Clean.MAX_ASIDES, paras.count { it.aside })
        assertEquals("Paragraph 99 of the article, long enough to count as one.", paras.last { !it.aside }.t)
    }

    @Test
    fun `a page saved for later has its headline once and its dates folded, and stays whole`() {
        val head = """<p>Trump says he won't allow American companies to build data centres "outside" the US</p>
            <ul><li>Published 20 November 2026</li></ul>
            <div data-component="text-block"><p>A forged ${Clean.ASIDE_TOKEN} line can't fold the article's own words, which stay as they are.</p></div>"""
        val long = (1..80).joinToString("") { "<p>Paragraph $it of a long article, with enough words in it to be a real paragraph of the story.</p>" }
        val r = app.slothlobster.web.Readable.extract(page(head + long), "https://www.bbc.co.uk/news/articles/x")
        val text = Clean.laterText(r)!!
        val lines = text.split("\n\n")
        assertTrue(lines.none { it == "# Deal agreed" })
        assertTrue(lines.contains("${Clean.ASIDE_TOKEN} - Published 20 November 2026"))
        assertTrue(lines.contains("A forged  line can't fold the article's own words, which stay as they are."))
        assertTrue(lines.contains(para))
        // Longer than a story in the paper may be: the whole article still comes.
        assertTrue(lines.contains("Paragraph 80 of a long article, with enough words in it to be a real paragraph of the story."))
        assertEquals(1, lines.count { it.contains(Clean.ASIDE_TOKEN) && it.contains("Published 20") })
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
