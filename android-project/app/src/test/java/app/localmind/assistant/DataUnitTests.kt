package app.localmind.assistant

import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.data.desk.PriceSeries
import app.localmind.assistant.domain.desk.Action
import app.localmind.assistant.domain.desk.Against
import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.Branch
import app.localmind.assistant.domain.desk.Condition
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.Exit
import app.localmind.assistant.domain.desk.Line
import app.localmind.assistant.domain.desk.Market
import app.localmind.assistant.domain.desk.Op
import app.localmind.assistant.domain.desk.PricesOrder
import app.localmind.assistant.domain.desk.Rule
import app.localmind.assistant.domain.desk.RuleCard
import app.localmind.assistant.domain.desk.RuleCheck
import app.localmind.assistant.domain.desk.RuleEngine
import app.localmind.assistant.domain.desk.RuleFiles
import app.localmind.assistant.domain.desk.Signal
import app.localmind.assistant.domain.desk.Sizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** 1.50: highs and lows, ATR and ATR stops, any series, and the order your rules make. */
class DataUnitTests {

    private class Lcg(var x: Long) {
        fun u(): Double { x = (1103515245L * x + 12345L) % 2147483648L; return x / 2147483648.0 }
        fun g(): Double = (u() + u() + u() + u() - 2.0) * sqrt(3.0)
    }

    private val days: List<LocalDate> = run {
        val out = ArrayList<LocalDate>()
        var d = LocalDate.of(2012, 1, 2)
        while (out.size < 1500) { if (d.dayOfWeek.value <= 5) out += d; d = d.plusDays(1) }
        out
    }

    private fun bars(seed: Long, ranged: Boolean = true): Bars {
        val rng = Lcg(seed)
        var acc = 0.0
        val c = DoubleArray(days.size) { acc += 0.0003 + 0.012 * rng.g(); 100 * exp(acc) }
        val o = DoubleArray(days.size) { if (it == 0) 100.0 else c[it - 1] * (1 + 0.002 * rng.g()) }
        val h = DoubleArray(days.size) { max(o[it], c[it]) * (1 + 0.004 * abs(rng.g())) }
        val l = DoubleArray(days.size) { minOf(o[it], c[it]) * (1 - 0.004 * abs(rng.g())) }
        return if (ranged) Bars(days, o, c, h, l) else Bars(days, o, c)
    }

    private fun data(ranged: Boolean = true): DeskData {
        val line = Line(days, DoubleArray(days.size) { 0.02 })
        return DeskData.assemble(linkedMapOf("GLD" to bars(7, ranged), "SLV" to bars(9, ranged)), line, line, Line(days, DoubleArray(days.size) { 0.15 }))
    }

    @Test
    fun `ATR is Wilder's, as a share of the close`() {
        val h = doubleArrayOf(10.0, 11.0, 12.0, 11.5, 13.0, 12.5)
        val l = doubleArrayOf(9.0, 10.0, 10.5, 10.0, 11.0, 11.5)
        val c = doubleArrayOf(9.5, 10.5, 11.5, 10.5, 12.5, 12.0)
        val a = RuleEngine.atrShare(h, l, c, 3)
        // True ranges from day 1: 1.5, 1.5, 1.5, 2.5, 1.0.
        assertTrue(a[0].isNaN() && a[2].isNaN())
        assertEquals(1.5 / 10.5, a[3], 1e-12)
        assertEquals(((1.5 * 2 + 2.5) / 3) / 12.5, a[4], 1e-12)
        assertEquals((((1.5 * 2 + 2.5) / 3 * 2 + 1.0) / 3) / 12.0, a[5], 1e-12)
    }

    private fun trend(exits: List<Exit>, sizing: Sizing = Sizing.AsWritten) = Rule(
        name = "t", benchmark = "GLD", exits = exits, sizing = sizing,
        branches = listOf(Branch("trend", listOf(Condition.Compare(Signal.Price("GLD"), Op.CROSSES_ABOVE, Against.Of(Signal.Average("GLD", 50)))),
            Action.Hold(mapOf("GLD" to 1.0)), until = true))
    )

    @Test
    fun `an ATR stop sits at the entry's ATR, a trailing one follows today's`() {
        val d = data()
        val r = trend(listOf(Exit.Stop(atr = 2.0, atrDays = 14), Exit.After(120)))
        assertTrue(RuleCheck.problems(r).toString(), RuleCheck.problems(r).isEmpty())
        val t = RuleEngine.targets(r, d).targets
        val why = t.reasons.filter { "exit_stop" in it }
        assertTrue("some ATR stops fire", why.isNotEmpty())
        // Every stop-out is at least two entry ATRs below the fill.
        val atr = RuleEngine.atrShare(d.prices.getValue("GLD").highs!!, d.prices.getValue("GLD").lows!!, d.prices.getValue("GLD").closes, 14)
        val dates = t.dates
        var entry = -1
        for (i in dates.indices) {
            if (t.reasons[i].startsWith("entry_")) entry = i
            if ("exit_stop" in t.reasons[i]) {
                val fell = Regex("down ([0-9.]+)%").find(t.reasons[i])!!.groupValues[1].toDouble() / 100
                assertTrue("stop at day $i fell ${fell} against ${2 * atr[entry]}", fell >= 2 * atr[entry] - 1e-3)
            }
        }
        val trailing = RuleEngine.targets(trend(listOf(Exit.Stop(trailing = true, atr = 3.0))), d).targets
        assertTrue(trailing.reasons.any { "exit_stop" in it && "best close" in it })
    }

    @Test
    fun `risk per trade sizes against the ATR stop`() {
        val d = data()
        val r = trend(listOf(Exit.Stop(atr = 2.0, atrDays = 14), Exit.After(60)), Sizing.RiskPerTrade(0.01))
        val t = RuleEngine.targets(r, d).targets
        val atr = RuleEngine.atrShare(d.prices.getValue("GLD").highs!!, d.prices.getValue("GLD").lows!!, d.prices.getValue("GLD").closes, 14)
        val i = t.reasons.indexOfFirst { it.startsWith("entry_") && !atr[t.reasons.indexOf(it)].isNaN() }
        assertTrue(i > 0)
        assertEquals(minOf(1.0, 0.01 / (2 * atr[i])), t.weights[0][i], 1e-9)
    }

    @Test
    fun `without highs and lows an ATR rule says what it needs`() {
        val r = trend(listOf(Exit.Stop(atr = 2.0)))
        assertEquals(listOf("GLD ${RuleEngine.HIGHS_LOWS}"), RuleEngine.missing(r, data(ranged = false)))
        assertTrue(RuleEngine.missing(r, data()).isEmpty())
        val cond = Rule(name = "calm", benchmark = "GLD", branches = listOf(Branch("calm",
            listOf(Condition.Compare(Signal.Atr("SLV", 14), Op.BELOW, Against.Value(0.02))), Action.Hold(mapOf("GLD" to 1.0)))))
        assertEquals(listOf("SLV ${RuleEngine.HIGHS_LOWS}"), RuleEngine.missing(cond, data(ranged = false)))
    }

    @Test
    fun `ATR rules are checked, saved and carded`() {
        assertFalse(RuleCheck.problems(trend(listOf(Exit.Stop(atr = 30.0)))).isEmpty())
        assertFalse(RuleCheck.problems(trend(listOf(Exit.Stop(pct = 0.05, atr = 2.0)))).isEmpty())
        val r = trend(listOf(Exit.Stop(trailing = true, atr = 3.0, atrDays = 20), Exit.After(60)), Sizing.RiskPerTrade(0.01))
        assertEquals(r, RuleFiles.parse(RuleFiles.encode(r)).getOrThrow())
        val card = RuleCard.write(r)!!
        assertTrue(card, card.contains("Exit: trailing stop 3 ATR over 20 days"))
        assertEquals(r.exits, RuleCard.parse(card, setOf("GLD")).rule!!.exits)
        val atrCard = RuleCard.parse("Rule: x\nWhen: GLD 14-day ATR below 2%\nThen: hold GLD\nExit: stop 2.5 ATR", setOf("GLD")).rule!!
        assertEquals(Condition.Compare(Signal.Atr("GLD", 14), Op.BELOW, Against.Value(0.02)), atrCard.branches[0].conditions[0])
        assertEquals(Exit.Stop(atr = 2.5), atrCard.exits[0])
        // A stop written before 1.50 still reads.
        assertEquals(Exit.Stop(0.05), RuleFiles.parse("""{"v":1,"name":"o","benchmark":"GLD","branches":[{"tag":"a","conditions":[],"action":{"kind":"hold","weights":{"GLD":1.0}}}],"exits":[{"kind":"stop","pct":0.05}]}""").getOrThrow().exits[0])
    }

    // --- the prices file ---

    private fun bundle(series: Map<String, PriceSeries>) = PriceBundle(series = series)

    @Test
    fun `highs and lows are kept when they fit, widened by a hair, dropped when they don't`() {
        val ds = (1..30).map { LocalDate.of(2024, 1, 1).plusDays(it.toLong()).toString() }
        val o = List(30) { 100.0 + it }
        val c = List(30) { 101.0 + it }
        val good = PriceSeries("bars", dates = ds, opens = o, closes = c, highs = List(30) { 102.0 + it }, lows = List(30) { 99.0 + it })
        val (b1, p1) = PriceBundle.validate(bundle(mapOf("GLD" to good)))
        assertEquals(30, b1.series.getValue("GLD").highs.size)
        assertTrue(p1.isEmpty())
        val hair = good.copy(highs = List(30) { (101.0 + it) * 0.999 })
        assertEquals(c, PriceBundle.validate(bundle(mapOf("GLD" to hair))).first.series.getValue("GLD").highs)
        val wrong = good.copy(highs = List(30) { 50.0 })
        val (b3, p3) = PriceBundle.validate(bundle(mapOf("GLD" to wrong)))
        assertTrue(b3.series.getValue("GLD").highs.isEmpty())
        assertTrue(p3.single().contains("highs and lows left out"))
        val dd = PriceBundle.toDeskData(PriceBundle.validate(bundle(mapOf("GLD" to good,
            "DTB3" to PriceSeries("rate", dates = ds, values = List(30) { 0.05 })))).first)!!
        assertTrue(dd.prices.getValue("GLD").hasRange)
    }

    @Test
    fun `any series reads, from when it was published`() {
        val weeks = (0 until 40).map { LocalDate.of(2024, 1, 5).plusWeeks(it.toLong()).toString() }
        val ds = (1..200).map { LocalDate.of(2024, 1, 1).plusDays(it.toLong()).toString() }
        val b = bundle(mapOf(
            "SPY" to PriceSeries("bars", dates = ds, opens = List(200) { 100.0 }, closes = List(200) { 100.0 + it % 3 }),
            "DTB3" to PriceSeries("rate", dates = ds, values = List(200) { 0.05 }),
            "WALCL" to PriceSeries("series", freq = "weekly", dates = weeks, values = List(40) { 70000.0 + it })
        ))
        val (clean, problems) = PriceBundle.validate(b)
        assertTrue(problems.toString(), clean.series.containsKey("WALCL"))
        val d = PriceBundle.toDeskData(clean)!!
        val known = d.knownLine("WALCL")
        // Dated Friday 5 Jan, known from the next business day after the following Thursday.
        assertEquals(LocalDate.of(2024, 1, 12), known.dates.first())
        assertEquals(70000.0, known.values.first(), 0.0)
    }

    // --- the order ---

    @Test
    fun `the order asks for what your rules read, each from its source`() {
        val crypto = Rule(name = "btc", benchmark = "BTCUSD", markets = mapOf("BTCUSD" to Market.CRYPTO, "EURUSD" to Market.FX),
            branches = listOf(Branch("a", listOf(Condition.Compare(Signal.Change("DFII10", 63), Op.BELOW, Against.Value(0.0))),
                Action.Hold(mapOf("BTCUSD" to 0.5, "EURUSD" to 0.5)))))
        val stocks = Rule(name = "spy", benchmark = "SPY", carry = mapOf("SPY" to "DTB3"),
            branches = listOf(Branch("a", listOf(Condition.Compare(Signal.Price("SPY"), Op.ABOVE, Against.Of(Signal.Average("SPY", 200)))),
                Action.Hold(mapOf("SPY" to 1.0)))))
        val names = PricesOrder.names(listOf(crypto, stocks))
        assertEquals(PricesOrder.BASE, names.take(PricesOrder.BASE.size))
        assertEquals(listOf("crypto:BTCUSD", "fx:EURUSD", "fred:DFII10", "SPY"), names.drop(PricesOrder.BASE.size))
        assertEquals("Prices for SlothDesk: GLD SLV USDJPY DTB3 JP3M GVZ DEXJPUS from 2008-01-01", PricesOrder.text(emptyList()))
        val many = (1..40).map { i -> stocks.copy(benchmark = "T$i", branches = listOf(stocks.branches[0].copy(action = Action.Hold(mapOf("T$i" to 1.0))))) }
        assertEquals(PricesOrder.MAX, PricesOrder.names(many).size)
        assertNotNull(PricesOrder.names(many).firstOrNull { it == "GLD" })
    }
}
