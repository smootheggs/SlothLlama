package app.slothlobster

import app.slothlobster.jobs.Picks
import app.slothlobster.jobs.Repeats
import app.slothlobster.web.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 1.3: picking pages across searches, and when repeats and briefs next run. */
class DeckAndPicksTests {

    private fun c(url: String, title: String, search: Int = 0, position: Int = 0) = Picks.Candidate(Hit(title, url), search, position)

    @Test
    fun `one page per site and tracking links are the same page`() {
        val picked = Picks.rank(
            listOf(
                c("https://www.ecb.europa.eu/press/pr/2026/html/rate.en.html?utm_source=x", "ECB rate decision", 0, 0),
                c("https://ecb.europa.eu/press/pr/2026/html/rate.en.html", "ECB rate decision", 1, 0),
                c("https://www.ecb.europa.eu/press/other.html", "Other ECB page", 0, 1),
                c("https://www.reuters.com/markets/ecb-holds-rates", "ECB holds rates", 0, 2)
            ),
            "ECB rate decision", 5
        )
        assertEquals(2, picked.size)
        assertEquals(setOf("ecb.europa.eu", "reuters.com"), picked.map { Picks.site(it.url) }.toSet())
        assertEquals(
            Picks.pageKey("https://ecb.europa.eu/press/pr/2026/html/rate.en.html/"),
            Picks.pageKey("https://www.ecb.europa.eu/press/pr/2026/html/rate.en.html?utm_campaign=y#top")
        )
    }

    @Test
    fun `original sources beat answer farms and index pages`() {
        val picked = Picks.rank(
            listOf(
                c("https://www.quora.com/What-is-the-EU-AI-Act", "What is the EU AI Act?", 0, 0),
                c("https://example-blog.com/tag/ai-act/", "AI Act archives", 0, 1),
                c("https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai", "AI Act | Shaping Europe's digital future", 0, 2)
            ),
            "What is the EU AI Act?", 1
        )
        assertEquals("digital-strategy.ec.europa.eu", Picks.site(picked.single().url))
    }

    @Test
    fun `the engine's order still counts most among ordinary sites`() {
        val picked = Picks.rank(
            listOf(c("https://a-site.com/guide", "Guide to e-ink tablets", 0, 0), c("https://b-site.com/list", "Tablets", 0, 6)),
            "e-ink tablets", 1
        )
        assertEquals("a-site.com", Picks.site(picked.single().url))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }

    @Test
    fun `repeats and briefs know when they next run`() {
        val wed = at(2026, 11, 18, 10, 0)  // a Wednesday
        val brief = Repeats.next(1, 0, 7, 0, Repeats.dayKey(wed), wed)
        assertEquals(19, brief.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, brief.get(Calendar.HOUR_OF_DAY))
        // Past its time and not yet run today: due now.
        assertEquals(wed.timeInMillis, Repeats.next(1, 0, 7, 0, null, wed).timeInMillis)
        // Every Monday at 7: next Monday.
        val monday = Repeats.next(7, Calendar.MONDAY, 7, 0, null, wed)
        assertEquals(Calendar.MONDAY, monday.get(Calendar.DAY_OF_WEEK))
        assertEquals(23, monday.get(Calendar.DAY_OF_MONTH))
        assertTrue(monday.after(wed))
        assertFalse(Repeats.next(1, 0, 18, 30, null, wed).before(wed))
    }
}
