package app.localmind.assistant.domain.desk

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The numbers under SlothDesk, written to give the same results as the Colab engine
 * (slothdesk.py, pandas): rolling windows, sample standard deviations (n − 1), linear
 * quantiles and the normal distribution to full double precision.
 */
object DeskMath {

    /** Standard normal CDF (Hart 1968, as given by West 2005): about 1e-16 accurate. */
    fun normCdf(x: Double): Double {
        val a = abs(x)
        val c: Double
        if (a > 37.0) {
            c = 0.0
        } else {
            val e = exp(-a * a / 2.0)
            if (a < 7.07106781186547) {
                var b = 3.52624965998911E-02 * a + 0.700383064443688
                b = b * a + 6.37396220353165
                b = b * a + 33.912866078383
                b = b * a + 112.079291497871
                b = b * a + 221.213596169931
                b = b * a + 220.206867912376
                val top = e * b
                b = 8.83883476483184E-02 * a + 1.75566716318264
                b = b * a + 16.064177579207
                b = b * a + 86.7807322029461
                b = b * a + 296.564248779674
                b = b * a + 637.333633378831
                b = b * a + 793.826512519948
                b = b * a + 440.413735824752
                c = top / b
            } else {
                var b = a + 0.65
                b = a + 4.0 / b
                b = a + 3.0 / b
                b = a + 2.0 / b
                b = a + 1.0 / b
                c = e / b / 2.506628274631
            }
        }
        return if (x > 0) 1.0 - c else c
    }

    /** Inverse standard normal CDF (Wichura's AS241, the method Python's NormalDist uses). */
    fun normInv(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "p must be between 0 and 1" }
        val q = p - 0.5
        if (abs(q) <= 0.425) {
            val r = 0.180625 - q * q
            val num = (((((((2509.0809287301226727 * r + 33430.575583588128105) * r + 67265.770927008700853) * r +
                45921.953931549871457) * r + 13731.693765509461125) * r + 1971.5909503065514427) * r +
                133.14166789178437745) * r + 3.387132872796366608)
            val den = (((((((5226.495278852545925 * r + 28729.085735721942674) * r + 39307.89580009271061) * r +
                21213.794301586595867) * r + 5394.1960214247511077) * r + 687.1870074920579083) * r +
                42.313330701600911252) * r + 1.0)
            return q * num / den
        }
        var r = if (q < 0) p else 1.0 - p
        r = sqrt(-ln(r))
        val v: Double
        if (r <= 5.0) {
            r -= 1.6
            v = (((((((7.7454501427834140764e-4 * r + 0.0227238449892691845833) * r + 0.24178072517745061177) * r +
                1.27045825245236838258) * r + 3.64784832476320460504) * r + 5.7694972214606914055) * r +
                4.6303378461565452959) * r + 1.42343711074968357734) /
                (((((((1.05075007164441684324e-9 * r + 5.475938084995344946e-4) * r + 0.0151986665636164571966) * r +
                    0.14810397642748007459) * r + 0.68976733498510000455) * r + 1.6763848301838038494) * r +
                    2.05319162663775882187) * r + 1.0)
        } else {
            r -= 5.0
            v = (((((((2.01033439929228813265e-7 * r + 2.71155556874348757815e-5) * r + 0.0012426609473880784386) * r +
                0.026532189526576123093) * r + 0.29656057182850489123) * r + 1.7848265399172913358) * r +
                5.4637849111641143699) * r + 6.6579046435011037772) /
                (((((((2.04426310338993978564e-15 * r + 1.4215117583164458887e-7) * r + 1.8463183175100546818e-5) * r +
                    7.868691311456132591e-4) * r + 0.0148753612908506148525) * r + 0.13692988092273580531) * r +
                    0.59983220655588793769) * r + 1.0)
        }
        return if (q < 0) -v else v
    }

    /** Mean of each [window] values ending at i; NaN until the window is full or if it holds a NaN. */
    fun rollingMean(x: DoubleArray, window: Int): DoubleArray {
        val out = DoubleArray(x.size) { Double.NaN }
        for (i in x.indices) {
            if (i + 1 < window) continue
            var s = 0.0
            var bad = false
            for (j in i - window + 1..i) {
                if (x[j].isNaN()) { bad = true; break }
                s += x[j]
            }
            if (!bad) out[i] = s / window
        }
        return out
    }

    /** Sample standard deviation (n − 1) of each full [window]; NaN otherwise. */
    fun rollingStd(x: DoubleArray, window: Int): DoubleArray {
        val out = DoubleArray(x.size) { Double.NaN }
        for (i in x.indices) {
            if (i + 1 < window) continue
            var s = 0.0
            var bad = false
            for (j in i - window + 1..i) {
                if (x[j].isNaN()) { bad = true; break }
                s += x[j]
            }
            if (bad || window < 2) continue
            val m = s / window
            var ss = 0.0
            for (j in i - window + 1..i) ss += (x[j] - m) * (x[j] - m)
            out[i] = sqrt(ss / (window - 1))
        }
        return out
    }

    /**
     * The [q] quantile (linear between ranks, as pandas does) of the non-NaN values in each
     * [window] ending at i, once there are at least [minPeriods] of them.
     */
    fun rollingQuantile(x: DoubleArray, window: Int, minPeriods: Int, q: Double): DoubleArray {
        val out = DoubleArray(x.size) { Double.NaN }
        val buf = ArrayList<Double>(window)
        for (i in x.indices) {
            buf.clear()
            for (j in maxOf(0, i - window + 1)..i) if (!x[j].isNaN()) buf.add(x[j])
            if (buf.size < minPeriods || buf.isEmpty()) continue
            buf.sort()
            out[i] = quantile(buf, q)
        }
        return out
    }

    /** Linear quantile of sorted values. */
    fun quantile(sorted: List<Double>, q: Double): Double {
        if (sorted.size == 1) return sorted[0]
        val pos = q * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** Day-on-day change, NaN for the first value. */
    fun pctChange(x: DoubleArray): DoubleArray =
        DoubleArray(x.size) { i -> if (i == 0) Double.NaN else x[i] / x[i - 1] - 1.0 }

    /** Sample standard deviation (n − 1) of the non-NaN values. */
    fun std(x: DoubleArray): Double {
        val v = x.filter { !it.isNaN() }
        if (v.size < 2) return Double.NaN
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    fun isWeekday(d: LocalDate): Boolean = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    /** The next weekday after [d] (pandas BDay(1)). */
    fun nextBusinessDay(d: LocalDate): LocalDate {
        var x = d.plusDays(1)
        while (!isWeekday(x)) x = x.plusDays(1)
        return x
    }

    /** Weekdays after [from] up to and including [to] (0 when [to] isn't later). */
    fun businessDaysBetween(from: LocalDate, to: LocalDate): Int {
        var n = 0
        var d = from
        while (d < to) {
            d = d.plusDays(1)
            if (isWeekday(d)) n++
        }
        return n
    }
}
