package app.localmind.assistant

import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.DeskEngine
import app.localmind.assistant.domain.desk.DeskMath
import app.localmind.assistant.domain.desk.Line
import app.localmind.assistant.domain.desk.Params
import app.localmind.assistant.domain.desk.Targets
import kotlin.math.abs
import kotlin.math.ln

/**
 * The shelf's three strategies as they were written by hand before 1.47 (a line-by-line port of
 * the Colab engine). They're kept here, verbatim, as the reference the same strategies written
 * as rules are checked against, day by day.
 */
object LegacyDesk {

    fun goldTrend(prices: Map<String, Bars>, gvz: Line, p: Params): Targets {
        val a = p.text("asset", "GLD")
        val bars = prices.getValue(a)
        val px = bars.closes
        val dates = bars.dates
        val sma = DeskMath.rollingMean(px, p.int("sma_days"))
        val vol = gvz.asOf(dates)
        val window = p.int("vol_window")
        val cap = DeskMath.rollingQuantile(vol, window, window / 2, p["vol_quantile"])
        val w = DoubleArray(dates.size)
        val why = Array(dates.size) { "" }
        var inPos = false
        val days = p.int("sma_days")
        for (i in dates.indices) {
            if (sma[i].isNaN()) continue
            val above = px[i] > sma[i]
            if (inPos && !above) {
                inPos = false
                why[i] = "exit_trend: close %.2f fell below the %d-day average %.2f".format(px[i], days, sma[i])
            } else if (!inPos && above) {
                val calm = !p.flag("vol_filter") || cap[i].isNaN() || vol[i] <= cap[i]
                if (calm) {
                    inPos = true
                    why[i] = "entry_trend: close %.2f above the %d-day average %.2f".format(px[i], days, sma[i])
                }
            }
            w[i] = if (inPos) 1.0 else 0.0
        }
        return Targets(dates, listOf(a), listOf(w), why)
    }

    fun goldSilverRatio(prices: Map<String, Bars>, p: Params): Targets {
        val g = p.text("asset", "GLD")
        val s = p.text("asset2", "SLV")
        val dates = DeskEngine.commonDates(prices.getValue(g), prices.getValue(s))
        val gc = prices.getValue(g).on(dates).closes
        val sc = prices.getValue(s).on(dates).closes
        val lr = DoubleArray(dates.size) { ln(gc[it] / sc[it]) }
        val look = p.int("lookback")
        val mean = DeskMath.rollingMean(lr, look)
        val sd = DeskMath.rollingStd(lr, look)
        val half = p["gross"] / 2.0
        val wg = DoubleArray(dates.size)
        val ws = DoubleArray(dates.size)
        val why = Array(dates.size) { "" }
        var side = 0
        var held = 0
        val zEnter = p["z_enter"]
        val zExit = p["z_exit"]
        val maxDays = p.int("max_days")
        for (i in dates.indices) {
            val z = (lr[i] - mean[i]) / sd[i]
            if (z.isNaN()) {
                if (side != 0) why[i] = "exit_data: not enough data for the z-score"
                side = 0
            } else if (side == 0) {
                if (z > zEnter) {
                    side = -1; held = 0
                    why[i] = "entry_rich: z %+.2f, gold rich against silver".format(z)
                } else if (z < -zEnter) {
                    side = 1; held = 0
                    why[i] = "entry_cheap: z %+.2f, gold cheap against silver".format(z)
                }
            } else {
                held++
                if (abs(z) < zExit) {
                    side = 0
                    why[i] = "exit_revert: z back to %+.2f".format(z)
                } else if (held >= maxDays) {
                    side = 0
                    why[i] = "exit_time: time stop after %d days, z %+.2f".format(maxDays, z)
                }
            }
            wg[i] = side * half
            ws[i] = -side * half
        }
        return Targets(dates, listOf(g, s), listOf(wg, ws), why)
    }

    fun usdJpyCarry(prices: Map<String, Bars>, gapPublished: Line, p: Params): Targets {
        val a = p.text("asset", "USDJPY")
        val bars = prices.getValue(a)
        val px = bars.closes
        val dates = bars.dates
        val days = p.int("sma_days")
        val sma = DeskMath.rollingMean(px, days)
        val gap = gapPublished.asOf(dates)
        val minGap = p["min_rate_gap"]
        val w = DoubleArray(dates.size) { i ->
            if (sma[i].isNaN() || gap[i].isNaN()) 0.0 else if (px[i] > sma[i] && gap[i] > minGap) 1.0 else 0.0
        }
        val why = Array(dates.size) { i ->
            val prev = if (i == 0) 0.0 else w[i - 1]
            when {
                w[i] > prev -> "entry_carry: above the %d-day average, rate gap %.2f%%".format(days, gap[i] * 100)
                w[i] < prev -> if (!(px[i] > sma[i])) "exit_trend: fell below the %d-day average".format(days)
                else "exit_gap: rate gap down to %.2f%%".format(gap[i] * 100)
                else -> ""
            }
        }
        return Targets(dates, listOf(a), listOf(w), why)
    }
}
