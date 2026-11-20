package app.slothlobster

import app.slothlobster.jobs.CoverBatch
import app.slothlobster.jobs.CoverFound
import app.slothlobster.jobs.Covers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.7: SlothBooks' covers orders. */
class CoversTests {

    @Test
    fun `an order lists books by id, title and author`() {
        val (choices, asks) = Covers.parse("SlothBooks covers\nchoices: 1\nabc123def456\tThe Lantern Road\tMira Hale\nbad line\n")
        assertEquals(1, choices)
        assertEquals(1, asks.size)
        assertEquals("abc123def456", asks[0].id)
        assertEquals("The Lantern Road", asks[0].title)
        assertEquals("Mira Hale", asks[0].author)
    }

    @Test
    fun `choices are kept between one and three and ids stay plain`() {
        val (choices, asks) = Covers.parse("choices: 9\nab/../c\tTide Clock\t")
        assertEquals(3, choices)
        assertEquals("abc", asks.single().id)
        assertEquals("", asks.single().author)
    }

    @Test
    fun `a batch is written in the format SlothBooks reads`() {
        val text = Covers.encode(CoverBatch(mode = "pick", covers = listOf(CoverFound("abc", "Open Library", listOf("AAAA"))), missing = listOf("def")))
        assertTrue("\"format\":\"slothbooks-covers\"" in text)
        assertTrue("\"mode\":\"pick\"" in text)
        assertTrue("\"missing\":[\"def\"]" in text)
    }
}
