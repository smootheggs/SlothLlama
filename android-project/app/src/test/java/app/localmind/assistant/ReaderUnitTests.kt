package app.localmind.assistant

import app.localmind.assistant.ui.reader.ReaderBlock
import app.localmind.assistant.ui.reader.ReaderViewModel
import app.localmind.assistant.ui.reader.allLinks
import app.localmind.assistant.ui.reader.findMatches
import app.localmind.assistant.ui.reader.parseInline
import app.localmind.assistant.ui.reader.readerBlocks
import app.localmind.assistant.ui.reader.readingMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reader's text handling: blocks, links, bold, find and note titles. */
class ReaderUnitTests {

    @Test
    fun `a brief splits into headings and linked bullets`() {
        val brief = """
            ## Markets

            ### Example Wire

            - [Stocks rise](https://news.example.com/a) — a calm day
            - [Gold dips](https://news.example.com/b)
        """.trimIndent()
        val blocks = readerBlocks(brief)
        assertEquals(4, blocks.size)
        assertEquals(ReaderBlock.Heading::class, blocks[0]::class)
        assertEquals(3, (blocks[1] as ReaderBlock.Heading).level)
        val first = blocks[2] as ReaderBlock.Bullet
        assertEquals("Stocks rise — a calm day", first.text)
        assertEquals("https://news.example.com/a", first.links.single().url)
        assertEquals("Stocks rise", first.text.substring(first.links[0].start, first.links[0].end))
        assertEquals(listOf("https://news.example.com/a", "https://news.example.com/b"), allLinks(blocks))
    }

    @Test
    fun `bare addresses are links without their trailing full stop`() {
        val inline = parseInline("Source: https://example.org/page. More text")
        assertEquals("Source: https://example.org/page. More text", inline.text)
        val link = inline.links.single()
        assertEquals("https://example.org/page", link.url)
        assertEquals("https://example.org/page", inline.text.substring(link.start, link.end))
    }

    @Test
    fun `bold markers are removed and links after them keep their place`() {
        val inline = parseInline("Fetched **report.pdf** from [the site](https://example.org/r)")
        assertEquals("Fetched report.pdf from the site", inline.text)
        assertEquals("report.pdf", inline.text.substring(inline.bold[0].start, inline.bold[0].end))
        assertEquals("the site", inline.text.substring(inline.links[0].start, inline.links[0].end))
    }

    @Test
    fun `only web links count`() {
        val inline = parseInline("[click](javascript:alert(1)) and [file](file:///sdcard/x)")
        assertTrue(inline.links.isEmpty())
    }

    @Test
    fun `paragraphs, numbered items, quotes, rules and code`() {
        val text = """
            First line
            second line

            1. One
            2) Two

            > Quoted
            > on two lines

            ---

            ```
            val x = 1
            ```
        """.trimIndent()
        val blocks = readerBlocks(text)
        assertEquals("First line\nsecond line", (blocks[0] as ReaderBlock.Paragraph).text)
        assertEquals("1", (blocks[1] as ReaderBlock.Numbered).number)
        assertEquals("Two", (blocks[2] as ReaderBlock.Numbered).text)
        assertEquals("Quoted\non two lines", (blocks[3] as ReaderBlock.Quote).text)
        assertEquals("val x = 1", (blocks[4] as ReaderBlock.Code).text)
        assertEquals(5, blocks.size)
    }

    @Test
    fun `find returns the blocks that match, ignoring case`() {
        val blocks = readerBlocks("Gold rose.\n\nSilver fell.\n\nGOLD again.")
        assertEquals(listOf(0, 2), findMatches(blocks, "gold"))
        assertTrue(findMatches(blocks, "  ").isEmpty())
    }

    @Test
    fun `reading time is at least a minute`() {
        assertEquals(1, readingMinutes("short"))
        assertEquals(2, readingMinutes(List(460) { "word" }.joinToString(" ")))
    }

    @Test
    fun `a note's short first line is its title`() {
        assertEquals("Morning brief" to "Body", ReaderViewModel.splitNote("Morning brief\n\nBody"))
        assertEquals("Note" to "Just one line", ReaderViewModel.splitNote("Just one line"))
    }
}
