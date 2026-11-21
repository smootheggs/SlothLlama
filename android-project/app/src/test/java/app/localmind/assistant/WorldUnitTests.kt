package app.localmind.assistant

import app.localmind.assistant.domain.world.Point
import app.localmind.assistant.domain.world.WorldBundle
import app.localmind.assistant.domain.world.WorldData
import app.localmind.assistant.domain.world.WorldMarket
import app.localmind.assistant.domain.world.WorldYours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.42: World's links, the crowd's history, and settlements. The markets here are made up. */
class WorldUnitTests {

    private fun market(id: String, chance: Double?, status: String = "open", result: String = "", end: String = "") =
        WorldMarket("polymarket", id, "polymarket:treaty", question = "Q $id", chance = chance, status = status, result = result, endDate = end)

    @Test
    fun `links and refs are read into one form`() {
        assertEquals("polymarket:river-treaty-ratified", WorldData.refOf("https://polymarket.com/event/river-treaty-ratified?tid=1"))
        assertEquals("polymarket:river-treaty-ratified", WorldData.refOf("polymarket.com/event/River-Treaty-Ratified/some-market"))
        assertEquals("kalshi:KXRATE-26DEC", WorldData.refOf("https://kalshi.com/markets/kxrate/rate-decision/kxrate-26dec"))
        assertEquals("kalshi:KXRATE-26DEC", WorldData.refOf("kalshi:kxrate-26dec"))
        assertEquals("polymarket:abc", WorldData.refOf("polymarket:ABC"))
        assertNull(WorldData.refOf("https://example.com/event/abc"))
        assertNull(WorldData.refOf("just words"))
    }

    @Test
    fun `the crowd's chance is kept once a day`() {
        val today = LocalDate.of(2026, 11, 21)
        var y = WorldYours(follow = listOf("polymarket:treaty"))
        y = WorldData.take(y, WorldBundle(markets = listOf(market("1", 0.50))), today.minusDays(8), 1)
        y = WorldData.take(y, WorldBundle(markets = listOf(market("1", 0.55))), today, 2)
        y = WorldData.take(y, WorldBundle(markets = listOf(market("1", 0.62))), today, 3)
        val h = y.history.getValue("polymarket:1")
        assertEquals(listOf(Point(today.minusDays(8).toString(), 0.50), Point(today.toString(), 0.62)), h)
        assertEquals(0.12, WorldData.weekChange(h, today)!!, 1e-9)
        assertEquals(3L, y.lastTakenAt)
        assertEquals(listOf("Q 1"), WorldData.followed(y).map { it.question })
        // Older than the history kept: gone.
        val later = WorldData.take(y, WorldBundle(markets = listOf(market("1", 0.6))), today.plusDays(WorldData.KEEP_DAYS), 4)
        assertFalse(later.history.getValue("polymarket:1").any { it.on == today.minusDays(8).toString() })
    }

    @Test
    fun `a settled market proposes what happened`() {
        assertEquals(0, WorldData.settlement(market("1", 1.0, "settled", "yes")))
        assertEquals(1, WorldData.settlement(market("1", 0.0, "settled", "no")))
        assertNull(WorldData.settlement(market("1", 0.99, "closed")))
        assertNull(WorldData.settlement(market("1", 0.5)))
        assertNull(WorldData.settlement(null))
    }

    @Test
    fun `a call is checked when trading ends, or in three months`() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals("2026-12-17", WorldData.checkOnFor(market("1", 0.6, end = "2026-12-17T18:59:00Z"), today))
        assertEquals("2027-02-20", WorldData.checkOnFor(market("1", 0.6, end = "2026-11-01T00:00:00Z"), today))
        assertEquals("2027-02-20", WorldData.checkOnFor(market("1", 0.6), today))
    }

    @Test
    fun `the Lobster is asked again only when prices are old`() {
        val now = 100L * 3_600_000L
        assertFalse(WorldData.stale(WorldYours(), now))
        assertTrue(WorldData.stale(WorldYours(follow = listOf("kalshi:X")), now))
        assertFalse(WorldData.stale(WorldYours(follow = listOf("kalshi:X"), lastAskedAt = now - 3_600_000L), now))
        assertTrue(WorldData.stale(WorldYours(follow = listOf("kalshi:X"), lastTakenAt = now - 7 * 3_600_000L), now))
    }
}
