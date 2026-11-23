package app.localmind.assistant

import app.localmind.assistant.data.later.LaterItem
import app.localmind.assistant.data.later.ReadingList
import app.localmind.assistant.ui.reader.ReadAloud
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The Later shelf's text handling, and how read-aloud picks a voice. */
class LaterUnitTests {

    @Test
    fun `the Lobster's header comes off a saved article`() {
        val text = "# Slow is smooth\n\nSource: https://example.org/slow\n\nFirst paragraph.\n\nSecond."
        assertEquals("First paragraph.\n\nSecond.", ReadingList.cleanArticle(text, "Slow is smooth"))
        // A different first heading is part of the article and stays.
        assertEquals("# Chapter one\n\nText.", ReadingList.cleanArticle("# Chapter one\n\nText.", "Book"))
    }

    @Test
    fun `a page's folded lines don't count toward its reading time`() {
        val m = app.localmind.assistant.domain.edition.Editions.ASIDE_MARK
        val article = List(460) { "word" }.joinToString(" ")
        val folded = (1..40).joinToString("\n\n") { "$m Related story number $it with several words in its headline" }
        assertEquals(2, ReadingList.readingMinutes("$article\n\n$folded"))
    }

    @Test
    fun `sites and reading times`() {
        assertEquals("example.org", ReadingList.siteOf("https://www.example.org/a/b?c=1"))
        assertEquals("", ReadingList.siteOf("not a link"))
        assertEquals(1, ReadingList.readingMinutes("A few words."))
        assertEquals(4, ReadingList.readingMinutes(List(920) { "word" }.joinToString(" ")))
        assertEquals(2, ReadingList.readingMinutes("中".repeat(1000)))
    }

    @Test
    fun `minutes left follow progress`() {
        val item = LaterItem(id = "a", title = "T", minutes = 10, progress = 0.4f)
        assertEquals(6, item.minutesLeft)
        assertEquals(1, item.copy(progress = 1f).minutesLeft)
    }

    @Test
    fun `read-aloud hears words, not markup, in the right voice`() {
        assertEquals("Bold and a link", ReadAloud.spoken("**Bold** and a link https://example.org/x"))
        assertEquals(Locale.SIMPLIFIED_CHINESE, ReadAloud.localeFor("今天的天气很好。"))
        assertEquals(Locale.JAPANESE, ReadAloud.localeFor("今日はいい天気です。"))
        assertEquals(Locale.getDefault(), ReadAloud.localeFor("A plain English sentence about 北京."))
    }
}
