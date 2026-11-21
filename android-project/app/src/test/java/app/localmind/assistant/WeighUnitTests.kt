package app.localmind.assistant

import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Closes
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.forecasts.Place
import app.localmind.assistant.domain.forecasts.PriceCalls
import app.localmind.assistant.domain.forecasts.PriceTerms
import app.localmind.assistant.domain.forecasts.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.41: the record by place, the reference, reasoning against result, and price calls checked from closes. */
class WeighUnitTests {

    private fun call(
        id: String, area: Area, yours: Double, market: Double? = null, happened: Int? = null,
        reasoning: String = "", source: String = "you", void: Boolean = false
    ) = Call(
        id = id, question = id, area = area, kind = CallKind.YES_NO, checkOn = "2026-11-20", createdAt = 0,
        estimates = listOf(Chance("2026-11-20", 0, Forecasts.binary(yours))),
        market = market?.let { listOf(Chance("2026-11-20", 0, Forecasts.binary(it))) } ?: emptyList(),
        happened = happened, void = void, markedOn = if (happened != null || void) "2026-11-20" else "",
        reasoning = reasoning, source = source
    )

    @Test
    fun `areas fall into the three places and your own`() {
        assertEquals(Place.MARKETS, Forecasts.place(call("a", Area.MARKETS, 0.6)))
        assertEquals(Place.SPORTS, Forecasts.place(call("b", Area.SPORTS, 0.6)))
        assertEquals(Place.WORLD, Forecasts.place(call("c", Area.POLITICS, 0.6)))
        assertEquals(Place.WORLD, Forecasts.place(call("d", Area.WATCHLIST, 0.6)))
        assertEquals(Place.OWN, Forecasts.place(call("e", Area.OTHER, 0.6)))
    }

    @Test
    fun `beating the reference compares your score with the market's`() {
        // You said 70%, the market 60%, it happened: (0.3)² < (0.4)², you beat it.
        assertEquals(true, Forecasts.beat(call("a", Area.SPORTS, 0.7, 0.6, happened = 0)))
        // Same chances, it didn't happen: (0.7)² > (0.6)², you didn't.
        assertEquals(false, Forecasts.beat(call("b", Area.SPORTS, 0.7, 0.6, happened = 1)))
        // A tie doesn't beat it; no market, open or void: nothing to compare.
        assertEquals(false, Forecasts.beat(call("c", Area.SPORTS, 0.6, 0.6, happened = 0)))
        assertNull(Forecasts.beat(call("d", Area.SPORTS, 0.7, happened = 0)))
        assertNull(Forecasts.beat(call("e", Area.SPORTS, 0.7, 0.6)))
        assertNull(Forecasts.beat(call("f", Area.SPORTS, 0.7, 0.6, void = true)))
    }

    @Test
    fun `the record by place counts calls, scores and references`() {
        val calls = listOf(
            call("s1", Area.SPORTS, 0.7, 0.6, happened = 0),
            call("s2", Area.SPORTS, 0.7, 0.6, happened = 1),
            call("s3", Area.SPORTS, 0.6),
            call("w1", Area.WATCHLIST, 0.3, happened = 1)
        )
        val places = Forecasts.byPlace(calls)
        assertEquals(listOf(Place.SPORTS, Place.WORLD), places.map { it.place })
        val sports = places.first()
        assertEquals(2, sports.checked)
        assertEquals(1, sports.open)
        assertEquals(1, sports.beat)
        assertEquals(2, sports.withReference)
        assertEquals((0.09 + 0.49) / 2, sports.brier!!, 1e-9)
        assertEquals(0, places[1].withReference)
        val summary = Forecasts.summary(calls, LocalDate.of(2026, 11, 21))
        assertEquals(1, summary.beat)
        assertEquals(2, summary.withReference)
    }

    @Test
    fun `reasoning is kept apart from the result`() {
        val calls = listOf(
            call("a", Area.OTHER, 0.7, happened = 0, reasoning = "sound"),
            call("b", Area.OTHER, 0.7, happened = 1, reasoning = "sound"),
            call("c", Area.OTHER, 0.3, happened = 1, reasoning = "partly"),
            call("d", Area.OTHER, 0.3, happened = 0, reasoning = "no"),
            call("e", Area.OTHER, 0.7, happened = 0)
        )
        val l = Forecasts.luck(calls)
        assertEquals(1, l.soundRight)
        assertEquals(1, l.soundWrong)
        // A 30% yes leans no: "no" happening is the way you leaned.
        assertEquals(1, l.shakyRight)
        assertEquals(1, l.shakyWrong)
        assertEquals(4, l.total)
    }

    // --- price calls ---

    private val gld = Closes(
        dates = listOf("2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "2027-01-04"),
        closes = listOf(250.0, 252.0, 254.0, 256.2, 257.0),
        source = "tiingo:GLD"
    )
    private val terms = PriceTerms("GLD", above = true, level = 255.0, on = "2026-12-31")

    @Test
    fun `a price call waits for its day and its prices`() {
        assertNull(PriceCalls.check(terms, gld, null, LocalDate.of(2026, 12, 31)))
        val short = gld.copy(dates = gld.dates.take(2), closes = gld.closes.take(2))
        // The prices stop before the day: wait a few days, then ask.
        assertNull(PriceCalls.check(terms, short, null, LocalDate.of(2027, 1, 2)))
        assertEquals(Verdict.ASK, PriceCalls.check(terms, short, null, LocalDate.of(2027, 1, 10))!!.verdict)
        assertEquals(Verdict.ASK, PriceCalls.check(terms, null, null, LocalDate.of(2027, 1, 10))!!.verdict)
    }

    @Test
    fun `the close on the day settles it, or the last one before`() {
        val c = PriceCalls.check(terms, gld, null, LocalDate.of(2027, 1, 5))!!
        assertEquals(Verdict.HAPPENED, c.verdict)
        assertEquals("2026-12-31", c.day)
        assertEquals(256.2, c.close!!, 1e-9)
        // New Year's Day is shut: a call on 1 Jan takes 31 Dec's close.
        val holiday = PriceCalls.check(terms.copy(on = "2027-01-01"), gld, null, LocalDate.of(2027, 1, 5))!!
        assertEquals("2026-12-31", holiday.day)
        // Below 255 on 30 Dec: 254 is below, so it happened.
        assertEquals(Verdict.HAPPENED, PriceCalls.check(terms.copy(above = false, on = "2026-12-30"), gld, null, LocalDate.of(2027, 1, 5))!!.verdict)
        assertEquals(Verdict.DIDNT, PriceCalls.check(terms.copy(on = "2026-12-30"), gld, null, LocalDate.of(2027, 1, 5))!!.verdict)
        assertTrue(c.evidence.startsWith("256.2 on 31 Dec 2026 · Tiingo"))
    }

    @Test
    fun `too near the level or sources that disagree ask first`() {
        val near = PriceCalls.check(terms.copy(level = 256.0), gld, null, LocalDate.of(2027, 1, 5))!!
        assertEquals(Verdict.ASK, near.verdict)
        val fred = Closes(listOf("2026-12-31"), listOf(254.9), "fred:DEXJPUS")
        assertEquals(Verdict.ASK, PriceCalls.check(terms, gld, fred, LocalDate.of(2027, 1, 5))!!.verdict)
        val agrees = PriceCalls.check(terms, gld, fred.copy(closes = listOf(256.0)), LocalDate.of(2027, 1, 5))!!
        assertEquals(Verdict.HAPPENED, agrees.verdict)
        assertEquals(256.0, agrees.second!!, 1e-9)
        // The same source twice isn't a second opinion.
        val same = PriceCalls.check(terms, gld, gld.copy(closes = gld.closes.map { 200.0 }), LocalDate.of(2027, 1, 5))!!
        assertEquals(Verdict.HAPPENED, same.verdict)
        assertNull(same.second)
    }

    @Test
    fun `price calls read plainly`() {
        assertEquals("GLD above 255 on 31 Dec 2026", PriceCalls.question(terms))
        assertEquals("USD/JPY below 158.5 on 15 Jan 2027", PriceCalls.question(PriceTerms("USDJPY", false, 158.5, "2027-01-15")))
        assertFalse(PriceCalls.distance(terms, 251.3) < 0)
    }
}
