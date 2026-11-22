package app.localmind.assistant

import app.localmind.assistant.domain.desk.Action
import app.localmind.assistant.domain.desk.Against
import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.Brake
import app.localmind.assistant.domain.desk.Branch
import app.localmind.assistant.domain.desk.Check
import app.localmind.assistant.domain.desk.Condition
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Exit
import app.localmind.assistant.domain.desk.Line
import app.localmind.assistant.domain.desk.Market
import app.localmind.assistant.domain.desk.Op
import app.localmind.assistant.domain.desk.Overlay
import app.localmind.assistant.domain.desk.Params
import app.localmind.assistant.domain.desk.Rule
import app.localmind.assistant.domain.desk.RuleCheck
import app.localmind.assistant.domain.desk.RuleEngine
import app.localmind.assistant.domain.desk.RuleFiles
import app.localmind.assistant.domain.desk.Signal
import app.localmind.assistant.domain.desk.Sizing
import app.localmind.assistant.domain.desk.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Rules (1.47): the shelf's strategies written as rules must decide exactly what the hand-written
 * ones did, day by day; every block must do what it says, never look ahead, and refuse what's
 * out of range.
 */
class RuleUnitTests {

    private class Lcg(var x: Long) {
        fun u(): Double {
            x = (1103515245L * x + 12345L) % 2147483648L
            return x / 2147483648.0
        }
        fun g(): Double = (u() + u() + u() + u() - 2.0) * sqrt(3.0)
    }

    private fun days(start: LocalDate, n: Int, weekends: Boolean = false): List<LocalDate> {
        val out = ArrayList<LocalDate>(n)
        var d = start
        while (out.size < n) {
            if (weekends || (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    private val n = 3200
    private val dates = days(LocalDate.of(2008, 1, 2), n)

    private fun walk(rng: Lcg, on: List<LocalDate>, mu: Double, vol: Double, start: Double, perYear: Double = 252.0): Bars {
        val m = on.size
        val r = DoubleArray(m) { mu / perYear + vol / sqrt(perYear) * rng.g() }
        val close = DoubleArray(m)
        var acc = 0.0
        for (i in 0 until m) { acc += r[i]; close[i] = start * exp(acc) }
        val gap = DoubleArray(m) { 0.3 * vol / sqrt(perYear) * rng.g() }
        val opens = DoubleArray(m) { i -> (if (i == 0) start else close[i - 1]) * exp(gap[i]) }
        return Bars(on, opens, close)
    }

    /** The same synthetic market the Colab checks use, plus a few more assets for the new blocks. */
    private val data: DeskData by lazy {
        val rng = Lcg(20261119L)
        val prices = linkedMapOf("GLD" to walk(rng, dates, 0.06, 0.16, 80.0), "SLV" to walk(rng, dates, 0.04, 0.28, 15.0),
            "USDJPY" to walk(rng, dates, 0.01, 0.09, 110.0))
        val gz = DoubleArray(n)
        var acc = 0.0
        for (i in 0 until n) {
            acc += 0.03 * rng.g()
            gz[i] = minOf(0.6, maxOf(0.08, 0.16 + acc * 0.01))
        }
        val us = Line(dates, DoubleArray(n) { 0.02 + 0.015 * (it % 500) / 500.0 })
        val months = ArrayList<LocalDate>()
        var m = LocalDate.of(2007, 12, 1)
        while (m <= dates.last()) { months += m; m = m.plusMonths(1) }
        val jp = Line(months, DoubleArray(months.size) { 0.001 })
        val more = Lcg(7L)
        prices["SPY"] = walk(more, dates, 0.08, 0.18, 100.0)
        prices["TLT"] = walk(more, dates, 0.03, 0.12, 90.0)
        prices["EEM"] = walk(more, dates, 0.05, 0.24, 40.0)
        val week = days(LocalDate.of(2016, 1, 1), 2500, weekends = true)
        prices["BTCUSD"] = walk(more, week, 0.5, 0.7, 400.0, 365.0)
        DeskData.assemble(prices, us, jp, Line(dates, gz))
    }

    private fun tag(reason: String) = reason.substringBefore(":").trim()

    private fun same(what: String, legacy: Targets, rule: Targets) {
        assertEquals("$what dates", legacy.dates, rule.dates)
        assertEquals("$what assets", legacy.assets, rule.assets)
        for (k in legacy.assets.indices) for (i in legacy.dates.indices) {
            val a = legacy.weights[k][i]
            val b = rule.weights[k][i]
            assertTrue("$what ${legacy.assets[k]} on ${legacy.dates[i]}: $a vs $b", a == b || abs(a - b) < 1e-15)
        }
        for (i in legacy.dates.indices) {
            assertEquals("$what reason on ${legacy.dates[i]}: '${legacy.reasons[i]}' vs '${rule.reasons[i]}'",
                tag(legacy.reasons[i]), tag(rule.reasons[i]))
        }
    }

    private fun grid(key: String): List<Params> {
        val t = DeskStrategies.template(key)
        val (a, b) = t.grid
        return a.second.flatMap { x -> b.second.map { y -> t.defaults.with(a.first, x).with(b.first, y) } }
    }

    // --- the shelf, as rules, decides exactly as before ---

    @Test
    fun `gold trend as a rule decides as the hand-written one did`() {
        val settings = grid("gold_trend") + DeskStrategies.template("gold_trend").defaults.with("vol_filter", 0.0)
        for (p in settings) same("gold_trend $p", LegacyDesk.goldTrend(data.prices, data.gvz, p),
            RuleEngine.targets(DeskStrategies.rule("gold_trend", p), data).targets)
    }

    @Test
    fun `gold silver ratio as a rule decides as the hand-written one did`() {
        val settings = grid("gold_silver_ratio") + DeskStrategies.template("gold_silver_ratio").defaults.with("max_days", 5.0).with("z_exit", 0.0)
        for (p in settings) same("gold_silver_ratio $p", LegacyDesk.goldSilverRatio(data.prices, p),
            RuleEngine.targets(DeskStrategies.rule("gold_silver_ratio", p), data).targets)
    }

    @Test
    fun `usdjpy carry as a rule decides as the hand-written one did`() {
        for (p in grid("usdjpy_carry")) same("usdjpy_carry $p", LegacyDesk.usdJpyCarry(data.prices, data.gapPublished, p),
            RuleEngine.targets(DeskStrategies.rule("usdjpy_carry", p), data).targets)
    }

    @Test
    fun `the shelf's rules are within the limits and survive saving`() {
        for (t in DeskStrategies.templates) {
            val r = DeskStrategies.rule(t.key, t.defaults)
            assertEquals("${t.key}: ${RuleCheck.problems(r)}", emptyList<String>(), RuleCheck.problems(r))
            assertEquals(t.key, r, RuleFiles.parse(RuleFiles.encode(r)).getOrThrow())
        }
    }

    // --- never looking ahead ---

    private fun cut(d: DeskData, last: LocalDate): DeskData {
        val prices = d.prices.mapValues { (_, b) -> b.on(b.dates.filter { it <= last }) }
        fun l(x: Line) = Line(x.dates.filter { it <= last }, x.values.copyOfRange(0, x.dates.count { it <= last }))
        return DeskData.assemble(prices, l(d.rf), l(d.japan), l(d.gvz))
    }

    private val everything = Rule(
        name = "Everything at once", benchmark = "SPY",
        branches = listOf(
            Branch("risk_on", listOf(
                Condition.Compare(Signal.Price("SPY"), Op.ABOVE, Against.Of(Signal.Average("SPY", 150, exp = true))),
                Condition.AnyOf(listOf(
                    Condition.Compare(Signal.Momentum("SPY", 126, 21), Op.ABOVE, Against.Value(0.0)),
                    Condition.Compare(Signal.Rsi("SPY", 14), Op.BELOW, Against.Value(30.0), days = 3)
                )),
                Condition.Compare(Signal.Series("GVZ"), Op.AT_MOST, Against.Percentile(0.9, 252), enterOnly = true)
            ), Action.Rotate(listOf("SPY", "EEM", "GLD"), Signal.Momentum("*", 126, 21), top = 2, minScore = 0.0)),
            Branch("breakout", listOf(
                Condition.Compare(Signal.Price("GLD"), Op.CROSSES_ABOVE, Against.Of(Signal.High("GLD", 55)))
            ), Action.Hold(mapOf("GLD" to 0.6, "TLT" to 0.4)))
        ),
        otherwise = mapOf("TLT" to 0.5),
        exits = listOf(Exit.Stop(0.08, trailing = true), Exit.After(120)),
        sizing = Sizing.EqualRisk(60, 1.0),
        check = Check.WEEKLY,
        band = 0.02,
        brake = Brake(0.12, 0.5, 15)
    )

    @Test
    fun `no rule looks ahead`() {
        val rules = DeskStrategies.templates.filter { it.key != "gld_covered_call" }.map { DeskStrategies.rule(it.key, it.defaults) } + everything
        val full = rules.map { RuleEngine.targets(it, data).targets }
        for (at in listOf(900, 1700, 2600)) {
            val part = cut(data, dates[at])
            rules.forEachIndexed { k, r ->
                val p = RuleEngine.targets(r, part).targets
                val f = full[k]
                for (j in p.dates.indices) {
                    assertEquals("${r.name}: same day", f.dates[j], p.dates[j])
                    for (a in p.assets.indices) assertEquals("${r.name} ${p.assets[a]} on ${p.dates[j]} (cut at $at)",
                        f.weights[a][j], p.weights[a][j], 1e-12)
                }
            }
        }
    }

    @Test
    fun `a rule with every kind of block runs, and saves and reads back the same`() {
        assertEquals(emptyList<String>(), RuleCheck.problems(everything))
        assertEquals(everything, RuleFiles.parse(RuleFiles.encode(everything)).getOrThrow())
        val run = RuleEngine.run(everything, data)
        assertTrue(run.entries > 3)
        assertTrue(run.result.equity.all { it.isFinite() && it > 0 })
        val t = run.targets!!
        val gross = t.dates.indices.map { i -> t.weights.sumOf { abs(it[i]) } }
        assertTrue("never over 1x: ${gross.max()}", gross.all { it <= 1.0 + 1e-9 })
        assertTrue(t.reasons.any { it.startsWith("entry_risk_on") })
        assertTrue(t.reasons.any { it.contains("rebalance") })
    }

    // --- the blocks, one at a time, on a path drawn by hand ---

    private fun handData(closes: List<Double>, asset: String = "AAA"): DeskData {
        val d = days(LocalDate.of(2024, 1, 1), closes.size)
        val bars = Bars(d, DoubleArray(closes.size) { if (it == 0) closes[0] else closes[it - 1] }, closes.toDoubleArray())
        return DeskData.assemble(mapOf(asset to bars), Line(d, DoubleArray(d.size) { 0.0 }), Line(d, DoubleArray(d.size) { 0.0 }), Line(d, DoubleArray(d.size) { 0.2 }))
    }

    private fun hold(vararg c: Condition, exits: List<Exit> = emptyList(), until: Boolean = false, sizing: Sizing = Sizing.AsWritten) =
        Rule(name = "t", benchmark = "AAA", branches = listOf(Branch("t", c.toList(), Action.Hold(mapOf("AAA" to 1.0)), until)), exits = exits, sizing = sizing)

    private fun weights(r: Rule, d: DeskData) = RuleEngine.targets(r, d).targets.weights[0].toList()

    @Test
    fun `a breakout enters the day the close beats the high before it`() {
        val c = listOf(10.0, 11.0, 12.0, 11.5, 11.8, 12.5, 12.2)
        val r = hold(Condition.Compare(Signal.Price("AAA"), Op.CROSSES_ABOVE, Against.Of(Signal.High("AAA", 3))), until = true, exits = listOf(Exit.After(100)))
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0), weights(r, handData(c)))
    }

    @Test
    fun `a crossing marks the day to enter, not a reason to leave`() {
        val c = listOf(10.0, 11.0, 12.0, 11.5, 11.8, 12.5, 12.2, 12.4, 11.0)
        val cross = Condition.Compare(Signal.Price("AAA"), Op.CROSSES_ABOVE, Against.Of(Signal.High("AAA", 3)))
        // Held while above its 3-day average, entered only on the breakout.
        val r = hold(cross, Condition.Compare(Signal.Price("AAA"), Op.ABOVE, Against.Of(Signal.Average("AAA", 3))))
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0), weights(r, handData(c)))
        // A branch entered only on a crossing needs an exit.
        assertTrue(RuleCheck.problems(hold(cross)).isNotEmpty())
    }

    @Test
    fun `stops, trailing stops, targets and time stops close the position`() {
        val c = listOf(100.0, 100.0, 104.0, 110.0, 103.0, 101.0, 99.0, 98.0, 120.0)
        val always = Condition.Compare(Signal.Price("AAA"), Op.ABOVE, Against.Value(0.0))
        // Entry decided day 0, filled at day 1's open (100): 5% stop hits at 94.9 or below, never here.
        assertEquals(List(9) { 1.0 }, weights(hold(always, exits = listOf(Exit.Stop(0.05)), until = true), handData(c)))
        // Trailing 5% from the best close (110 on day 3): 103 on day 4 is 6.4% off.
        val trailing = RuleEngine.targets(hold(always, exits = listOf(Exit.Stop(0.05, trailing = true)), until = true), handData(c)).targets
        assertEquals(0.0, trailing.weights[0][4], 0.0)
        assertTrue(trailing.reasons[4].startsWith("exit_stop"))
        // A 9% target hits at 110.
        val target = RuleEngine.targets(hold(always, exits = listOf(Exit.Target(0.09)), until = true), handData(c)).targets
        assertTrue(target.reasons[3].startsWith("exit_target"))
        // Out after 2 days held; an until branch doesn't come back in the same day.
        val time = weights(hold(always, exits = listOf(Exit.After(2)), until = true), handData(c))
        assertEquals(listOf(1.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 1.0, 0.0), time)
    }

    @Test
    fun `days in a row, any of, not, and the calendar`() {
        val c = listOf(1.0, 2.0, 3.0, 2.0, 3.0, 4.0, 5.0, 4.0)
        val up = Condition.Compare(Signal.Momentum("AAA", 1), Op.ABOVE, Against.Value(0.0), days = 2)
        assertEquals(listOf(0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0), weights(hold(up), handData(c)))
        val either = Condition.AnyOf(listOf(
            Condition.Compare(Signal.Price("AAA"), Op.AT_LEAST, Against.Value(5.0)),
            Condition.Compare(Signal.Price("AAA"), Op.AT_MOST, Against.Value(1.0))))
        assertEquals(listOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0), weights(hold(either), handData(c)))
        assertEquals(listOf(0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0), weights(hold(Condition.Not(either)), handData(c)))
        // 2024-01-01 is a Monday: Mondays only.
        assertEquals(listOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0), weights(hold(Condition.Calendar(weekdays = listOf(1))), handData(c)))
    }

    @Test
    fun `RSI, off its high, exponential averages and change read as they should`() {
        val c = (1..40).map { 100.0 + it }
        val d = handData(c)
        val rsiHigh = hold(Condition.Compare(Signal.Rsi("AAA", 14), Op.AT_LEAST, Against.Value(99.99)))
        assertEquals(1.0, weights(rsiHigh, d).last(), 0.0)
        val atHigh = hold(Condition.Compare(Signal.OffHigh("AAA", 10), Op.AT_LEAST, Against.Value(0.0)))
        assertEquals(1.0, weights(atHigh, d).last(), 0.0)
        // A rising line stays above its exponential average once it has one.
        val ema = weights(hold(Condition.Compare(Signal.Price("AAA"), Op.ABOVE, Against.Of(Signal.Average("AAA", 10, exp = true)))), d)
        assertEquals(List(9) { 0.0 } + List(31) { 1.0 }, ema)
    }

    @Test
    fun `rotation picks the strongest and stays in cash when none qualify`() {
        val r = Rule(name = "rot", benchmark = "SPY", check = Check.MONTHLY,
            branches = listOf(Branch("rotate", emptyList(), Action.Rotate(listOf("SPY", "TLT", "EEM", "GLD"), Signal.Momentum("*", 63), top = 1, minScore = 0.0))))
        val t = RuleEngine.targets(r, data).targets
        val mom = listOf("SPY", "TLT", "EEM", "GLD").associateWith { a ->
            val c = data.prices.getValue(a).on(t.dates).closes
            DoubleArray(c.size) { i -> if (i < 63) Double.NaN else c[i] / c[i - 63] - 1 }
        }
        var checked = 0
        for (i in t.dates.indices) {
            val first = i == 0 || t.dates[i].monthValue != t.dates[i - 1].monthValue
            if (!first || i < 63) continue
            val best = mom.maxBy { it.value[i] }
            val held = t.assets.indices.filter { t.weights[it][i] > 0 }.map { t.assets[it] }
            if (best.value[i] > 0) assertEquals(listOf(best.key), held) else assertEquals(emptyList<String>(), held)
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `sizing by equal money, equal risk, a steady risk, and the total cap`() {
        val pair = Action.Hold(mapOf("GLD" to 0.7, "SLV" to -0.3))
        fun first(s: Sizing, gross: Double = 1.0): Map<String, Double> {
            val r = Rule(name = "s", benchmark = "GLD", sizing = s, maxGross = gross, branches = listOf(Branch("pair", emptyList(), pair)))
            val t = RuleEngine.targets(r, data).targets
            return t.assets.indices.associate { t.assets[it] to t.weights[it][300] }
        }
        assertEquals(mapOf("GLD" to 0.5, "SLV" to -0.5), first(Sizing.Equal(1.0)))
        val er = first(Sizing.EqualRisk(60, 1.0))
        assertTrue("silver moves more, so it gets less: $er", abs(er.getValue("SLV")) < er.getValue("GLD"))
        assertEquals(1.0, er.values.sumOf { abs(it) }, 1e-12)
        val steady = first(Sizing.SteadyRisk(0.05, 60), gross = 3.0)
        assertTrue(steady.values.sumOf { abs(it) } > 0)
        val capped = first(Sizing.SteadyRisk(0.9, 60), gross = 1.5)
        assertEquals(1.5, capped.values.sumOf { abs(it) }, 1e-9)
    }

    @Test
    fun `the brake cuts every weight after a fall, then lets go`() {
        val c = listOf(100.0, 100.0, 100.0, 90.0, 80.0, 80.0, 80.0, 80.0, 80.0)
        val r = hold(Condition.Compare(Signal.Price("AAA"), Op.ABOVE, Against.Value(0.0))).copy(brake = Brake(0.15, 0.5, 3))
        val t = RuleEngine.targets(r, handData(c)).targets
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0, 0.5, 0.5, 0.5, 1.0, 1.0), t.weights[0].toList())
        assertTrue(t.reasons[4].contains("brake"))
    }

    @Test
    fun `crypto alone runs every day of the year, and with stocks only on shared days`() {
        val alone = Rule(name = "btc", benchmark = "BTCUSD", markets = mapOf("BTCUSD" to Market.CRYPTO),
            branches = listOf(Branch("trend", listOf(Condition.Compare(Signal.Price("BTCUSD"), Op.ABOVE, Against.Of(Signal.Average("BTCUSD", 100)))),
                Action.Hold(mapOf("BTCUSD" to 1.0)))))
        val plan = RuleEngine.targets(alone, data)
        assertEquals(365.0, plan.periodsPerYear, 0.0)
        assertTrue(plan.targets.dates.any { it.dayOfWeek == DayOfWeek.SUNDAY })
        val mixed = alone.copy(otherwise = mapOf("SPY" to 1.0))
        val mp = RuleEngine.targets(mixed, data)
        assertEquals(252.0, mp.periodsPerYear, 0.0)
        assertFalse(mp.targets.dates.any { it.dayOfWeek == DayOfWeek.SUNDAY })
        val run = RuleEngine.run(alone, data)
        assertTrue(run.result.equity.last() > 0 && run.result.orders > 0)
    }

    @Test
    fun `a small resize inside the band isn't traded`() {
        val r = Rule(name = "b", benchmark = "SPY", sizing = Sizing.EqualRisk(20), band = 0.05,
            branches = listOf(Branch("mix", emptyList(), Action.Hold(mapOf("SPY" to 0.5, "TLT" to 0.5)))))
        val loose = RuleEngine.run(r.copy(band = 0.0), data).result.orders
        val banded = RuleEngine.run(r, data).result.orders
        assertTrue("$banded orders with a band, $loose without", banded < loose / 3)
    }

    // --- what's refused ---

    @Test
    fun `rules outside the limits are refused, with a reason`() {
        val ok = hold(Condition.Compare(Signal.Price("AAA"), Op.ABOVE, Against.Value(1.0)))
        assertEquals(emptyList<String>(), RuleCheck.problems(ok))
        fun bad(r: Rule) = assertTrue("should be refused: $r", RuleCheck.problems(r).isNotEmpty())
        bad(ok.copy(branches = List(4) { ok.branches[0] }))
        bad(ok.copy(benchmark = "../etc"))
        bad(ok.copy(branches = listOf(Branch("t", emptyList(), Action.Hold(mapOf("AAA" to 0.8, "BBB" to 0.8))))))
        bad(ok.copy(maxGross = 10.0))
        bad(ok.copy(sizing = Sizing.RiskPerTrade(0.01)))
        bad(hold(Condition.Compare(Signal.Series("GVZ"), Op.BELOW, Against.Percentile(1.5, 252))))
        bad(hold(Condition.Compare(Signal.Price("AAA"), Op.CROSSES_ABOVE, Against.Value(1.0), days = 3)))
        bad(hold(*Array(5) { Condition.Compare(Signal.Price("AAA"), Op.ABOVE, Against.Value(1.0)) }))
        bad(hold(Condition.Compare(Signal.Average("AAA", 0), Op.ABOVE, Against.Value(1.0))))
        bad(ok.copy(overlay = Overlay.CoveredCall("AAA", "GVZ", delta = 0.9)))
        assertTrue(RuleFiles.parse("{\"v\":1,\"name\":\"x\"}").isFailure)
        assertTrue(RuleFiles.parse("x".repeat(RuleFiles.MAX_BYTES + 1)).isFailure)
        assertTrue(RuleFiles.parse(RuleFiles.encode(ok).replace("\"v\":1", "\"v\":2").let { if (it.contains("\"v\"")) it else it.replaceFirst("{", "{\"v\":2,") }).isFailure)
    }
}
