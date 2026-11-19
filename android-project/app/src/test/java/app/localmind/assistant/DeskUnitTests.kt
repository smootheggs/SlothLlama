package app.localmind.assistant

import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.DeskEngine
import app.localmind.assistant.domain.desk.DeskMath
import app.localmind.assistant.domain.desk.DeskReports
import app.localmind.assistant.domain.desk.DeskStats
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * SlothDesk on the tablet must give the Colab engine's numbers. The expected values below come
 * from slothdesk.py 0.6 run on the same synthetic data, which both sides rebuild from an integer
 * random generator (so no library differences creep in).
 */
class DeskUnitTests {

    private class Lcg(var x: Long) {
        fun u(): Double {
            x = (1103515245L * x + 12345L) % 2147483648L
            return x / 2147483648.0
        }
        fun g(): Double = (u() + u() + u() + u() - 2.0) * sqrt(3.0)
    }

    private fun weekdays(start: LocalDate, n: Int): List<LocalDate> {
        val out = ArrayList<LocalDate>(n)
        var d = start
        while (out.size < n) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) out += d
            d = d.plusDays(1)
        }
        return out
    }

    private val n = 3200
    private val dates = weekdays(LocalDate.of(2008, 1, 2), n)

    private val data: DeskData by lazy {
        val rng = Lcg(20261119L)
        fun bars(mu: Double, vol: Double, start: Double): Bars {
            val r = DoubleArray(n) { mu / 252 + vol / sqrt(252.0) * rng.g() }
            val close = DoubleArray(n)
            var acc = 0.0
            for (i in 0 until n) { acc += r[i]; close[i] = start * exp(acc) }
            val gap = DoubleArray(n) { 0.3 * vol / sqrt(252.0) * rng.g() }
            val opens = DoubleArray(n) { i -> (if (i == 0) start else close[i - 1]) * exp(gap[i]) }
            return Bars(dates, opens, close)
        }
        val prices = linkedMapOf("GLD" to bars(0.06, 0.16, 80.0), "SLV" to bars(0.04, 0.28, 15.0), "USDJPY" to bars(0.01, 0.09, 110.0))
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
        DeskData.assemble(prices, us, jp, Line(dates, gz))
    }

    private fun close(expected: Double, actual: Double, rel: Double = 1e-7, what: String = "") =
        assertTrue("$what: expected $expected, got $actual", abs(actual / expected - 1) < rel)

    private fun annualSharpe(d: List<LocalDate>, eq: DoubleArray): Double {
        val ex = DeskStats.excess(d, eq, data.rf)
        return ex.average() / DeskMath.std(ex) * sqrt(252.0)
    }

    private fun check(key: String, final: Double, orders: Int, entries: Int, costs: Double, bench: Double,
                      x2: Double, sharpeFull: Double, sharpeHeld: Double, isDaily: Double) {
        val p = DeskStrategies.template(key).defaults
        val run = DeskStrategies.run(key, data, p)
        val r = run.result
        close(final, r.equity.last(), what = "$key final")
        assertEquals("$key orders", orders, r.orders)
        assertEquals("$key entries", entries, run.entries)
        close(costs, r.costsPaid, 1e-6, "$key costs")
        close(bench, run.bench.equity.last(), what = "$key benchmark")
        close(x2, DeskStrategies.run(key, data, p, costMult = 2.0).result.equity.last(), what = "$key costs doubled")
        assertEquals("$key full Sharpe", sharpeFull, annualSharpe(r.dates, r.equity), 1e-6)
        val cut = DeskStats.splitIndex(r.equity.size)
        assertEquals("$key held-out Sharpe", sharpeHeld,
            annualSharpe(r.dates.subList(cut, r.dates.size), r.equity.copyOfRange(cut, r.equity.size)), 1e-6)
        assertEquals("$key in-sample daily Sharpe", isDaily, DeskReports.inSampleSharpe(run, data), 1e-8)
    }

    @Test
    fun `normal distribution matches Python`() {
        assertEquals(0.9750021048517796, DeskMath.normCdf(1.96), 1e-15)
        assertEquals(1.9599639845400536, DeskMath.normInv(0.975), 1e-14)
        assertEquals(-4.753424308822899, DeskMath.normInv(1e-6), 1e-12)
        assertEquals(0.6954415533455531, DeskEngine.bsCall(100.0, 105.0, 30 / 365.0, 0.03, 0.2), 1e-12)
        assertEquals(103.0, DeskEngine.strikeForDelta(100.0, 30 / 365.0, 0.03, 0.2, 0.3, 1.0), 0.0)
    }

    @Test
    fun `luck statistics match Python`() {
        assertEquals(0.5, DeskStats.psr(0.0, 0.0, 1000, 0.0, 3.0), 1e-12)
        assertEquals(2.5306028932016846, DeskStats.expectedMaxSharpe(100, 1.0), 1e-12)
        assertEquals(684.1497221590915, DeskStats.minTrackRecordDays(1 / sqrt(252.0), 0.0, 3.0), 1e-9)
        assertEquals(0.9618372955905441, DeskStats.psr(0.05, 0.01, 2000, -0.3, 6.0), 1e-12)
        assertEquals(0.0, DeskStats.expectedMaxSharpe(1, 1.0), 0.0)
    }

    @Test
    fun `rates are lagged to when they were published`() {
        assertEquals(3237, data.gapPublished.size)
        assertEquals(0.02497, data.gapPublished.values.last(), 1e-12)
        assertEquals(0.02026, data.gapPublished.asOf(listOf(LocalDate.of(2008, 3, 3)))[0], 1e-12)
    }

    @Test
    fun `gold trend matches Colab`() = check("gold_trend", 238888.12665825203, 88, 44, 8484.543235362533,
        316210.60205654264, 228634.703718957, 0.3921651530658301, -0.2019023801516927, 0.037785107033468684)

    @Test
    fun `gold silver ratio matches Colab`() = check("gold_silver_ratio", 128036.9174227735, 236, 59, 8237.544514041396,
        316210.60205654264, 118919.53046936665, -0.028964459368744356, 0.38979297444454764, -0.011278918985412972)

    @Test
    fun `usdjpy carry matches Colab`() = check("usdjpy_carry", 165854.85705773655, 168, 84, 4559.761923364755,
        195720.0290728663, 156724.9608444904, 0.2244809500192937, 0.5567987286999619, 0.004654254400019185)

    @Test
    fun `covered call matches Colab`() = check("gld_covered_call", 109758.20464192267, 144, 143, 11208.17256617024,
        316210.60205654264, 96939.28579359077, -0.004806197738796977, -0.38704488703647744, 0.021051275356117557)

    @Test
    fun `a full report has six gates and a ticket`() {
        val key = "gold_trend"
        val rep = DeskReports.build(key, data, DeskStrategies.template(key).defaults, listOf(0.03), null, null)
        assertEquals(6, rep.gates.size)
        assertNull(rep.gates[4].passed)
        assertNull(rep.gates[5].passed)
        assertTrue(rep.ticket.nextOpen.isNotBlank())
        val b = rep.bootstrap!!
        assertTrue(b.sharpe.first <= b.sharpe.second && b.sharpe.second <= b.sharpe.third)
        assertTrue(rep.robustInSample!!.dsr in 0.0..1.0)
    }

    @Test
    fun `guardrail fires on a drawdown beyond testing`() {
        val up = (0 until 300).map { 100 + 20.0 * it / 299 } + (0 until 100).map { 120 - 12.0 * it / 99 } +
            (0 until 300).map { 108 + 17.0 * it / 299 }
        val crash = up + (0 until 300).map { 125 - 35.0 * it / 299 }
        assertTrue(DeskStats.guardrail(crash.toDoubleArray())!!.startsWith("Pause"))
        assertNull(DeskStats.guardrail((up + (0 until 300).map { 125 - 12.0 * it / 299 }).toDoubleArray()))
    }

    @Test
    fun `quantiles and rolling windows match pandas`() {
        val x = doubleArrayOf(3.0, 1.0, Double.NaN, 4.0, 1.0, 5.0)
        val q = DeskMath.rollingQuantile(x, 4, 2, 0.8)
        assertTrue(q[0].isNaN())
        assertEquals(2.6, q[1], 1e-12)      // [3, 1]
        assertEquals(3.6, q[3], 1e-12)      // [3, 1, 4]
        assertEquals(4.6, q[5], 1e-12)      // [4, 1, 5]
        val s = DeskMath.rollingStd(doubleArrayOf(1.0, 2.0, 4.0), 3)
        assertEquals(1.5275252316519468, s[2], 1e-12)
    }

    @Test
    fun `a prices file round-trips`() {
        val json = """{"format":"slothdesk-prices","v":1,"made_at":"2026-11-19T00:00:00Z","made_by":"test",
            "series":{"GLD":{"kind":"bars","dates":["2024-01-02","2024-01-03"],"open":[1.0,2.0],"close":[1.5,2.5]},
            "DTB3":{"kind":"rate","freq":"daily","dates":["2024-01-02"],"values":[0.05]},
            "BAD":{"kind":"bars","dates":["2024-01-02"],"open":[1.0,2.0],"close":[1.5]}}}"""
        val b = PriceBundle.parse(json)
        val (clean, problems) = PriceBundle.validate(b)
        assertTrue(clean.series.containsKey("GLD"))
        assertTrue("BAD" !in clean.series)
        assertTrue(problems.any { "BAD" in it })
        assertEquals(clean.series.keys, PriceBundle.parse(PriceBundle.encode(clean)).series.keys)
    }
}
