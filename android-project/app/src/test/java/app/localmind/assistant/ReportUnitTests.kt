package app.localmind.assistant

import app.localmind.assistant.domain.desk.Action
import app.localmind.assistant.domain.desk.Against
import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.Branch
import app.localmind.assistant.domain.desk.Condition
import app.localmind.assistant.domain.desk.Costs
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.DeskReports
import app.localmind.assistant.domain.desk.DeskStats
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Exit
import app.localmind.assistant.domain.desk.Line
import app.localmind.assistant.domain.desk.Market
import app.localmind.assistant.domain.desk.Metrics
import app.localmind.assistant.domain.desk.Op
import app.localmind.assistant.domain.desk.Robust
import app.localmind.assistant.domain.desk.Rule
import app.localmind.assistant.domain.desk.RuleCheck
import app.localmind.assistant.domain.desk.RuleEngine
import app.localmind.assistant.domain.desk.RuleFiles
import app.localmind.assistant.domain.desk.RuleTweaks
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.desk.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The report (1.48): six plain questions that pass exactly when the six gates did, stats on each
 * rule's own calendar, nearby settings for any rule, and rules in words.
 */
class ReportUnitTests {

    private class Lcg(var x: Long) {
        fun u(): Double {
            x = (1103515245L * x + 12345L) % 2147483648L
            return x / 2147483648.0
        }
        fun g(): Double = (u() + u() + u() + u() - 2.0) * sqrt(3.0)
    }

    private fun days(start: LocalDate, n: Int, weekends: Boolean): List<LocalDate> {
        val out = ArrayList<LocalDate>(n)
        var d = start
        while (out.size < n) {
            if (weekends || (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    private fun walk(rng: Lcg, on: List<LocalDate>, mu: Double, vol: Double, start: Double, perYear: Double): Bars {
        val r = DoubleArray(on.size) { mu / perYear + vol / sqrt(perYear) * rng.g() }
        val close = DoubleArray(on.size)
        var acc = 0.0
        for (i in on.indices) { acc += r[i]; close[i] = start * exp(acc) }
        val opens = DoubleArray(on.size) { i -> if (i == 0) start else close[i - 1] }
        return Bars(on, opens, close)
    }

    private val weekdays = days(LocalDate.of(2008, 1, 2), 3200, weekends = false)

    private val data: DeskData by lazy {
        val rng = Lcg(424242L)
        val prices = linkedMapOf("GLD" to walk(rng, weekdays, 0.06, 0.16, 80.0, 252.0), "SLV" to walk(rng, weekdays, 0.04, 0.28, 15.0, 252.0))
        val every = days(LocalDate.of(2015, 1, 1), 3000, weekends = true)
        prices["BTCUSD"] = walk(rng, every, 0.4, 0.7, 300.0, 365.0)
        prices["ETHUSD"] = walk(rng, every, 0.5, 0.9, 10.0, 365.0)
        val gz = DoubleArray(weekdays.size) { 0.12 + 0.08 * ((it * 37) % 101) / 101.0 }
        val us = Line(weekdays, DoubleArray(weekdays.size) { 0.02 })
        val months = ArrayList<LocalDate>()
        var m = LocalDate.of(2007, 12, 1)
        while (m <= weekdays.last()) { months += m; m = m.plusMonths(1) }
        DeskData.assemble(prices, us, Line(months, DoubleArray(months.size) { 0.001 }), Line(weekdays, gz))
    }

    // --- the six questions ---

    private fun metrics(sharpe: Double, dd: Double = -0.2, cagr: Double = 0.05, years: Double = 12.0) =
        Metrics(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1), years, cagr, 0.15, sharpe, sharpe, dd, 100, 0.25)

    private fun robust(dsr: Double, trials: Int = 7) = Robust(0.04, 0.0, 3.0, 2000, 0.9, trials, true, 0.35, dsr, 9.0)

    @Test
    fun `the six questions pass exactly when the six gates did`() {
        val sharpes = listOf(Double.NaN, -0.2, 0.0, 0.3, 0.6)
        val nearbys = listOf(null, "broad: most nearby settings land close to the default", "uneven: results swing", "a lone peak: …")
        val papers = listOf(null, DeskStats.Paper(30, 20, 0.9), DeskStats.Paper(60, 50, 0.9), DeskStats.Paper(80, 70, 0.5))
        var cases = 0
        for (entries in listOf(10, 30)) for (years in listOf(5.0, 9.0)) for (hs in sharpes) for (vs in sharpes) for (xs in listOf(Double.NaN, -0.1, 0.2))
            for (dsr in listOf(Double.NaN, 0.5, 0.95)) for (nb in nearbys) for (pp in papers) {
                val full = metrics(0.4, years = years)
                val held = metrics(hs)
                val vm = metrics(vs)
                val x2 = metrics(xs)
                val rb = robust(dsr)
                val old = LegacyDesk.gates(entries, full, held, vm, x2, rb, nb, pp)
                val q = DeskStats.questions(entries, full, metrics(0.5), held, metrics(0.2), vm, x2, rb, nb, pp, "GLD")
                assertEquals(6, q.size)
                assertEquals("held-out", old[1].passed, q[0].passed == true && q[1].passed == true)
                assertEquals("luck", old[0].passed == true && old[3].passed == true, q[2].passed == true)
                assertEquals("costs", old[2].passed, q[3].passed)
                assertEquals("nearby", old[4].passed, q[4].passed)
                assertEquals("paper", old[5].passed == true, q[5].passed == true)
                assertEquals("all six", old.all { it.passed == true }, q.all { it.passed == true })
                q.forEach { assertTrue(it.detail.isNotBlank() && it.more.isNotBlank()) }
                cases++
            }
        assertTrue(cases > 10_000)
    }

    @Test
    fun `paper days still coming in are under way, not failed`() {
        val q = DeskStats.questions(40, metrics(0.4), metrics(0.5), metrics(0.6), metrics(0.2), metrics(0.4), metrics(0.5),
            robust(0.95), "broad: yes", DeskStats.Paper(23, 20, 0.9), "GLD")
        assertNull(q[5].passed)
        assertTrue(q[5].detail, q[5].detail.startsWith("Under way: 23 of 60 days"))
        assertEquals("Ready to paper-trade", DeskStats.verdict(q).first)
        val none = DeskStats.questions(40, metrics(0.4), metrics(0.5), metrics(0.6), metrics(0.2), metrics(0.4), metrics(0.5),
            robust(0.95), null, null, "GLD")
        assertEquals("Nearly there", DeskStats.verdict(none).first)
        val weak = DeskStats.questions(40, metrics(0.4), metrics(0.5), metrics(0.6), metrics(0.2), metrics(0.4), metrics(-0.1),
            robust(0.95), null, null, "GLD")
        assertEquals("Research only", DeskStats.verdict(weak).first)
        assertTrue(DeskStats.verdict(weak).second.contains("doubled costs"))
    }

    @Test
    fun `beating holding is said in words`() {
        val s = metrics(0.6, dd = -0.11, cagr = 0.055)
        val b = metrics(0.4, dd = -0.24, cagr = 0.07)
        assertEquals("its worst fall was about half as deep, for a little less growth", DeskStats.compareWords(s, b))
        assertEquals("its worst fall was about as deep, with more growth", DeskStats.compareWords(metrics(1.0, -0.2, 0.1), metrics(0.4, -0.21, 0.05)))
    }

    // --- each rule's own calendar ---

    @Test
    fun `crypto alone is annualised over 365 days, a mix over 252`() {
        val crypto = Rule(name = "Bitcoin trend", benchmark = "BTCUSD", markets = mapOf("BTCUSD" to Market.CRYPTO, "ETHUSD" to Market.CRYPTO),
            branches = listOf(Branch("trend", listOf(Condition.Compare(Signal.Price("BTCUSD"), Op.ABOVE, Against.Of(Signal.Average("BTCUSD", 50)))),
                Action.Hold(mapOf("BTCUSD" to 1.0)))))
        val run = RuleEngine.run(crypto, data)
        assertEquals(365.0, run.periodsPerYear, 0.0)
        val rep = DeskReports.buildFor("my:test", "Bitcoin trend", crypto, data, listOf(null), null, null, run = run)
        val eq = run.result.equity
        val r = DoubleArray(eq.size - 1) { eq[it + 1] / eq[it] - 1 }
        val mu = r.average()
        val sd = sqrt(r.sumOf { (it - mu) * (it - mu) } / (r.size - 1))
        assertEquals(sd * sqrt(365.0), rep.full!!.vol, 1e-9)
        val mix = crypto.copy(benchmark = "GLD", branches = listOf(Branch("hold", emptyList(), Action.Hold(mapOf("BTCUSD" to 0.5, "GLD" to 0.5)))))
        assertEquals(252.0, RuleEngine.run(mix, data).periodsPerYear, 0.0)
    }

    @Test
    fun `a shelf report is the same through either door`() {
        val key = "gold_trend"
        val p = DeskStrategies.template(key).defaults
        val a = DeskReports.build(key, data, p, listOf(0.03), null, null)
        val b = DeskReports.buildFor(key, "x", DeskStrategies.rule(key, p), data, listOf(0.03), null, null)
        assertEquals(a.heldOut!!.sharpe, b.heldOut!!.sharpe, 1e-12)
        assertEquals(a.robustInSample!!.dsr, b.robustInSample!!.dsr, 1e-12)
        assertEquals(a.ticket, b.ticket)
        assertEquals(a.gates.map { it.passed }, b.gates.map { it.passed })
    }

    @Test
    fun `the covered call's ticket comes from its rule`() {
        val key = "gld_covered_call"
        val p = DeskStrategies.template(key).defaults
        val rep = DeskReports.build(key, data, p, listOf(null), null, null)
        assertTrue(rep.ticket.nextOpen.isNotBlank())
        assertTrue(rep.ticket.positionNow.startsWith("Long GLD"))
    }

    // --- nearby settings and trial ids ---

    @Test
    fun `nearby settings vary the rule's first two numbers`() {
        val r = DeskStrategies.rule("gold_trend", DeskStrategies.template("gold_trend").defaults)
        val k = RuleTweaks.knobs(r)
        assertEquals(200.0, k[0].value, 0.0)
        assertEquals(0.8, k[1].value, 1e-12)
        val g = RuleTweaks.grid(r)
        assertEquals(15, g.size)
        assertTrue(g.contains(r))
        g.forEach { assertTrue(RuleCheck.problems(it).isEmpty()) }
        val days = g.map { ((it.branches[0].conditions[0] as Condition.Compare).right as Against.Of).signal as Signal.Average }.map { it.days }.toSet()
        assertEquals(setOf(150, 175, 200, 225, 250), days)
    }

    @Test
    fun `a signal's days change everywhere it's read`() {
        val r = DeskStrategies.rule("gold_silver_ratio", DeskStrategies.template("gold_silver_ratio").defaults)
        val z = Signal.Stretch("GLD", 60, "SLV")
        val r2 = RuleTweaks.replaceSignal(r, z, z.copy(days = 90))
        assertTrue(r2.branches.all { ((it.conditions[0] as Condition.Compare).left as Signal.Stretch).days == 90 })
        assertEquals(90, ((r2.exits[0] as Exit.Within).signal as Signal.Stretch).days)
    }

    @Test
    fun `a rule's trial id is its content and costs, not its name`() {
        val r = DeskStrategies.rule("gold_trend", DeskStrategies.template("gold_trend").defaults)
        val c = Costs()
        assertEquals(RuleTweaks.hash(r, c), RuleTweaks.hash(r.copy(name = "Renamed"), c))
        assertNotEquals(RuleTweaks.hash(r, c), RuleTweaks.hash(r.copy(band = 0.05), c))
        assertNotEquals(RuleTweaks.hash(r, c), RuleTweaks.hash(r, c.copy(slippageBp = 9.0)))
    }

    @Test
    fun `any rule can check its nearby settings`() {
        val r = DeskStrategies.rule("gold_trend", DeskStrategies.template("gold_trend").defaults)
        val (verdict, cells) = DeskReports.nearbyRule(r, data)
        assertEquals(15, cells.size)
        assertTrue(verdict, listOf("broad", "uneven", "a lone peak").any { verdict.startsWith(it) })
        assertEquals(cells.map { it.first }.toSet().size, 15)
        val plain = Rule(name = "Hold", benchmark = "GLD", branches = listOf(Branch("hold", emptyList(), Action.Hold(mapOf("GLD" to 1.0)))))
        assertTrue(DeskReports.nearbyRule(plain, data).first.startsWith("broad"))
    }

    // --- words ---

    @Test
    fun `rules read as sentences`() {
        val r = DeskStrategies.rule("gold_trend", DeskStrategies.template("gold_trend").defaults)
        val words = r.branches[0].conditions.map { RuleWords.condition(it) }
        assertEquals("GLD closes above its 200-day average", words[0])
        assertEquals("Gold volatility (GVZ) is at most its 80th percentile of the last 252 days (checked only before entering)", words[1])
        val text = RuleWords.rule(DeskStrategies.rule("gold_silver_ratio", DeskStrategies.template("gold_silver_ratio").defaults))
        assertTrue(text, text.contains("how stretched GLD/SLV is over 60 days is above 2: hold GLD short 50%, SLV 50% (held until an exit)"))
        assertTrue(text, text.contains("Leave on: how stretched GLD/SLV is over 60 days is back within 0.5 of zero; after 30 days"))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "80th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 80).map { RuleWords.ordinal(it) })
        assertEquals("5%", RuleWords.value(0.05, RuleWords.unit(Signal.Momentum("GLD", 120))))
        assertEquals("2.25", RuleWords.num(2.25))
        assertEquals("each one's 120-day return", RuleWords.signal(Signal.Momentum("*", 120)))
    }

    @Test
    fun `saved rules come back as they went`() {
        for (t in DeskStrategies.templates) {
            val r = DeskStrategies.rule(t.key, t.defaults)
            assertEquals(r, RuleFiles.parse(RuleFiles.encode(r)).getOrThrow())
        }
        assertFalse(RuleFiles.parse("{\"v\":1,\"name\":\"x\",\"branches\":[],\"benchmark\":\"GLD\"}").isSuccess)
    }
}
