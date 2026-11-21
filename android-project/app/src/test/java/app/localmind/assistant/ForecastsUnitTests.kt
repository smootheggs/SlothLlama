package app.localmind.assistant

import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.odds.Odds
import app.localmind.assistant.domain.trading.Idea
import app.localmind.assistant.ui.forecasts.marketChance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** 1.37: odds worked out in code, and Forecasts' scores. */
class ForecastsUnitTests {

    private val eps = 1e-3

    @Test
    fun `prices convert between the three styles`() {
        assertEquals(1.8696, Odds.decimalFromAmerican(-115), eps)
        assertEquals(2.10, Odds.decimalFromAmerican(110), eps)
        assertEquals(-115, Odds.americanFromDecimal(1.87))
        assertEquals(110, Odds.americanFromDecimal(2.10))
        assertEquals(100, Odds.americanFromDecimal(2.0))
        assertEquals(20 to 23, Odds.fractionFromDecimal(1.87))
        assertEquals(5 to 6, Odds.fractionFromDecimal(1.83))
        assertEquals(10 to 11, Odds.fractionFromDecimal(1.91))
        assertEquals(11 to 10, Odds.fractionFromDecimal(2.10))
        assertEquals(20 to 21, Odds.fractionFromDecimal(Odds.decimalFromAmerican(-105)))
    }

    @Test
    fun `prices are read as written`() {
        assertEquals(2.10, Odds.parse("+110")!!.decimal, eps)
        assertEquals(1.8696, Odds.parse("−115")!!.decimal, eps)
        assertEquals(1.87, Odds.parse("1,87")!!.decimal, eps)
        assertEquals(1.8696, Odds.parse("20/23")!!.decimal, eps)
        assertEquals(2.0, Odds.parse("Evens")!!.decimal, eps)
        assertEquals(2.5, Odds.parse("150")!!.decimal, eps) // a bare 150 is American
        assertNull(Odds.parse("1"))
        assertNull(Odds.parse("abc"))
        assertNull(Odds.parse("+50"))
    }

    @Test
    fun `the margin comes out in proportion`() {
        val book = listOf(Odds.parse("1.87")!!, Odds.parse("1.95")!!)
        assertEquals(0.0476, Odds.margin(book), 1e-4)
        val fair = Odds.fair(book)
        assertEquals(0.5105, fair[0], 1e-4)
        assertEquals(1.0, fair.sum(), 1e-9)
        assertEquals(1.795, Odds.fairOdds(0.557), eps)
        assertEquals(4.7, Odds.edgePoints(0.557, 0.510), 1e-9)
    }

    @Test
    fun `a market's chance is a percent or a price`() {
        assertEquals(0.33, marketChance("33%")!!, 1e-9)
        assertEquals(0.33, marketChance("33")!!, 1e-9)
        assertEquals(0.5348, marketChance("1.87")!!, 1e-4)
        assertEquals(0.5349, marketChance("-115")!!, 1e-4)
        assertNull(marketChance("0"))
        assertNull(marketChance("hello"))
    }

    private fun call(vararg est: Pair<String, Double>, happened: Int? = null, marked: String = "", check: String = "2026-11-30") = Call(
        id = "c", question = "Q", checkOn = check, createdAt = 0,
        estimates = est.map { (day, p) -> Chance(day, 0, Forecasts.binary(p)) },
        happened = happened, markedOn = marked
    )

    @Test
    fun `brier is the squared distance, halved across outcomes`() {
        assertEquals(0.09, Forecasts.brier(listOf(0.7, 0.3), 0), 1e-9)
        assertEquals(0.25, Forecasts.brier(listOf(0.5, 0.5), 1), 1e-9)
        assertEquals(0.19, Forecasts.brier(listOf(0.5, 0.3, 0.2), 0), 1e-9)
    }

    @Test
    fun `the score averages the chance held on each day`() {
        // 40% for two days, then 80% for two; it happened.
        val c = call("2026-11-01" to 0.4, "2026-11-03" to 0.8, happened = 0, marked = "2026-11-05")
        assertEquals(0.20, Forecasts.score(c)!!, 1e-9)
        // Marked the day it was made: just the last chance.
        assertEquals(0.04, Forecasts.score(call("2026-11-01" to 0.8, happened = 0, marked = "2026-11-01"))!!, 1e-9)
        assertNull(Forecasts.score(call("2026-11-01" to 0.8)))
        assertNull(Forecasts.score(call("2026-11-01" to 0.8, happened = 0, marked = "2026-11-02").copy(void = true)))
    }

    @Test
    fun `calibration counts each call by its favourite`() {
        val calls = listOf(
            call("2026-11-01" to 0.70, happened = 0),
            call("2026-11-01" to 0.30, happened = 1), // a 70% no, and it was no
            call("2026-11-01" to 0.75, happened = 1),
            call("2026-11-01" to 0.55, happened = 0),
            call("2026-11-01" to 0.95, happened = 0),
            call("2026-11-01" to 0.80) // open: not counted
        )
        val bands = Forecasts.bands(calls).associateBy { it.from }
        assertEquals(setOf(50, 70, 90), bands.keys)
        assertEquals(3, bands.getValue(70).count)
        assertEquals(2.0 / 3, bands.getValue(70).happened, 1e-9)
        assertEquals((0.7 + 0.7 + 0.75) / 3, bands.getValue(70).said, 1e-9)
        assertEquals(1.0, bands.getValue(90).happened, 1e-9)
    }

    @Test
    fun `due, summary and chances adding to one`() {
        val today = LocalDate.of(2026, 11, 21)
        val open = call("2026-11-01" to 0.6, check = "2026-11-21")
        val later = call("2026-11-01" to 0.6, check = "2026-12-01")
        assertTrue(Forecasts.due(open, today))
        assertFalse(Forecasts.due(later, today))
        val s = Forecasts.summary(listOf(open, later, call("2026-11-01" to 0.7, happened = 0, marked = "2026-11-01")), today)
        assertEquals(1, s.checked)
        assertEquals(1, s.toCheck)
        assertEquals(2, s.open)
        assertEquals(0.09, s.brier!!, 1e-9)
        val split = Forecasts.normalise(listOf(60.0, 30.0, 15.0))!!
        assertEquals(1.0, split.sum(), 1e-9)
        assertEquals(0.5714, split[0], 1e-4)
        assertNull(Forecasts.normalise(listOf(0.0, 0.0)))
        assertEquals(listOf(0.99, 0.01).map { Math.round(it * 100) }, Forecasts.binary(1.2).map { Math.round(it * 100) })
        assertEquals("in 9 days", Forecasts.relative(LocalDate.of(2026, 11, 30), today))
        assertEquals("2 days ago", Forecasts.relative(LocalDate.of(2026, 11, 19), today))
    }

    @Test
    fun `outcomes follow the kind`() {
        val base = call("2026-11-01" to 0.6)
        assertEquals(listOf("Yes", "No"), base.outcomes)
        assertEquals(listOf("Above 2,650", "Below 2,650"), base.copy(kind = CallKind.NUMBER, line = "2,650").outcomes)
        assertEquals(listOf("A", "B", "C"), base.copy(kind = CallKind.ONE_OF, options = listOf("A", "B", "C")).outcomes)
    }

    @Test
    fun `a SlothDesk idea is read in at its conviction`() {
        val made = LocalDate.of(2026, 11, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val idea = Idea(id = "i1", createdAt = made, instrument = "Gold", direction = "long", entry = 2600.0, target = 2650.0, reviewOn = "2026-12-04", conviction = 60)
        val c = Forecasts.fromIdea(idea, ZoneOffset.UTC)!!
        assertEquals(Area.MARKETS, c.area)
        assertEquals("idea:i1", c.id)
        assertTrue(c.readOnly)
        assertTrue(c.question.startsWith("Gold long from 2,600, to 2,650"))
        assertEquals(0.60, c.estimates.single().chances[0], 1e-9)
        assertEquals("2026-11-02", c.estimates.single().on)
        assertTrue(c.open)
        val right = Forecasts.fromIdea(idea.copy(closedAt = made + 86_400_000L * 5, outcome = "right"), ZoneOffset.UTC)!!
        assertEquals(0, right.happened)
        assertEquals("2026-11-07", right.markedOn)
        val mixed = Forecasts.fromIdea(idea.copy(closedAt = made + 1, outcome = "mixed"), ZoneOffset.UTC)!!
        assertTrue(mixed.void)
        assertNull(Forecasts.fromIdea(idea.copy(reviewOn = ""), ZoneOffset.UTC))
    }

    @Test
    fun `calls round-trip, and a backup only adds what's missing`() {
        val c = call("2026-11-01" to 0.6).copy(area = Area.POLITICS, market = listOf(Chance("2026-11-02", 5, listOf(0.33, 0.67), "an exchange")))
        assertEquals(listOf(c), Forecasts.parse(Forecasts.encode(listOf(c))))
        val merged = Forecasts.merge(listOf(c.copy(question = "Mine")), listOf(c.copy(question = "Theirs"), c.copy(id = "d")))
        assertEquals(listOf("Mine", "Q"), merged.map { it.question })
    }
}
