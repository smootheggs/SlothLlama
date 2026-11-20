package app.localmind.assistant

import app.localmind.assistant.domain.trading.Idea
import app.localmind.assistant.domain.trading.IdeaCard
import app.localmind.assistant.domain.trading.IdeaStats
import app.localmind.assistant.domain.trading.MetalsMath
import app.localmind.assistant.domain.trading.MetalsMath.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Trading book's sums and its idea book. */
class TradingUnitTests {

    @Test
    fun `metal forwards, swap rates and lease rates`() {
        val f = MetalsMath.forward(2000.0, MetalsMath.swapRate(0.05, 0.01), 90)
        assertEquals(2020.0, f, 1e-9)
        assertEquals(0.04, MetalsMath.impliedSwapRate(2000.0, 2020.0, 90), 1e-12)
        assertEquals(0.01, MetalsMath.impliedLease(2000.0, 2020.0, 0.05, 90), 1e-12)
        // Lease above the money rate: backwardation, the forward under spot.
        assertTrue(MetalsMath.forward(2000.0, MetalsMath.swapRate(0.02, 0.05), 90) < 2000.0)
    }

    @Test
    fun `FX outrights and metal in another currency`() {
        val eur = MetalsMath.fxForward(1.10, 0.03, 360, 0.05, 360, 90)
        assertEquals(1.10 * 1.0125 / 1.0075, eur, 1e-12)
        assertEquals(1.1050, MetalsMath.fxFromPoints(1.10, 50.0, 0.0001), 1e-12)
        assertEquals(150.5, MetalsMath.fxFromPoints(150.0, 50.0, 0.01), 1e-12)
        val fUsd = MetalsMath.forward(2000.0, 0.04, 90)
        val c = MetalsMath.crossForward(2000.0, fUsd, 1.10, eur, usdIsBase = false, days = 90, localBasis = 360)
        assertEquals(2000.0 / 1.10, c.spotLocal, 1e-9)
        // Gold's swap rate in EUR comes out near the EUR rate minus the lease rate (3% − 1%).
        assertEquals(0.02, c.localSwapRate, 0.0005)
        assertEquals(2000.0 * 150.0, MetalsMath.metalIn(2000.0, 150.0, usdIsBase = true), 1e-9)
        assertFalse(MetalsMath.usdIsBase("EUR"))
        assertTrue(MetalsMath.usdIsBase("JPY"))
        assertEquals(365, MetalsMath.basisFor("HKD"))
    }

    @Test
    fun `local prices against London, and weights`() {
        val premium = MetalsMath.localPremium(500.0, 7.2, 2100.0)
        assertEquals(500.0 * MetalsMath.GRAMS_PER_TROY_OUNCE / 7.2 - 2100.0, premium, 1e-9)
        assertTrue(premium > 0)
        assertEquals(32.1507, MetalsMath.convert(1.0, WeightUnit.KILOGRAM, WeightUnit.TROY_OUNCE), 1e-4)
        assertEquals(1.20337, MetalsMath.convert(1.0, WeightUnit.HK_TAEL, WeightUnit.TROY_OUNCE), 1e-5)
        assertEquals(398.0, MetalsMath.fineWeight(400.0, 0.995), 1e-9)
        assertEquals(10.0, MetalsMath.contracts(1000.0, MetalsMath.Contract.GC), 1e-12)
    }

    @Test
    fun `positions and R`() {
        assertEquals(1000.0, MetalsMath.pnl(100.0, 2000.0, 2010.0, long = true), 1e-9)
        assertEquals(-1000.0, MetalsMath.pnl(100.0, 2000.0, 2010.0, long = false), 1e-9)
        assertEquals(2.0, MetalsMath.rMultiple(true, 100.0, 95.0, 110.0)!!, 1e-12)
        assertEquals(-1.0, MetalsMath.rMultiple(false, 100.0, 105.0, 105.0)!!, 1e-12)
        assertNull(MetalsMath.rMultiple(true, 100.0, 100.0, 110.0))
    }

    @Test
    fun `the Trader's idea card is read from a reply`() {
        val reply = """
            Here's how I'd frame it.

            **Instrument:** Gold spot (XAU/USD)
            **Direction:** Long
            **Entry:** around 2,385 on a dip
            **Stop:** 2,340
            **Target:** 2,480
            **Horizon:** 3–6 weeks
            **Size:** half a normal clip
            **Thesis:** Real yields are rolling over while central banks keep buying.
            **Wrong if:** 10-year real yields break back above 2.2%.
        """.trimIndent()
        val d = IdeaCard.parse(reply)
        assertNotNull(d)
        d!!
        assertEquals("Gold spot (XAU/USD)", d.instrument)
        assertEquals("long", d.direction)
        assertEquals("2385", d.entry)
        assertEquals("2340", d.stop)
        assertEquals("2480", d.target)
        assertEquals("trader", d.source)
        assertTrue(d.wrongIf.startsWith("10-year"))
        assertNull(IdeaCard.parse("Gold looks strong this week."))
        assertEquals("short", IdeaCard.parse("Instrument: Silver\nDirection: Sell\nThesis: Too far, too fast.")!!.direction)
    }

    @Test
    fun `the idea book's mirror`() {
        fun idea(id: String, conviction: Int, outcome: String, reason: String = "") = Idea(
            id = id, createdAt = 1, instrument = "Gold", direction = "long", entry = 100.0, stop = 95.0, target = 110.0,
            conviction = conviction, closedAt = 2, outcome = outcome, reason = reason, exit = if (outcome == "right") 110.0 else 95.0
        )
        val today = LocalDate.of(2026, 12, 1)
        val ideas = listOf(
            idea("a", 80, "right", "right reason"), idea("b", 80, "wrong"), idea("c", 60, "right", "wrong reason"),
            Idea(id = "d", createdAt = 3, instrument = "Silver", direction = "short", reviewOn = "2026-11-30")
        )
        val s = IdeaStats.of(ideas, today)
        assertEquals(1, s.open)
        assertEquals(1, s.due)
        assertEquals(3, s.closed)
        assertEquals(2, s.right)
        assertEquals(1, s.rightForReason)
        assertEquals(1.0, s.averageR!!, 1e-12) // (2 − 1 + 2) / 3
        assertEquals(2 to 1, s.calibration[80])
        assertEquals(1 to 1, s.calibration[60])
        assertEquals(2.0, ideas[0].rewardToRisk!!, 1e-12)
        assertTrue(ideas[0].asText().contains("Wrong if").not()) // no "wrong if" given, none printed
    }
}
