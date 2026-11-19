package app.localmind.assistant.domain.desk

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.SplittableRandom
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Performance over a stretch of an equity curve. Rates are fractions (0.05 = 5%). */
data class Metrics(
    val start: LocalDate,
    val end: LocalDate,
    val years: Double,
    val cagr: Double,
    val vol: Double,
    val sharpe: Double,
    val sortino: Double,
    val maxDrawdown: Double,
    val longestDrawdownDays: Long,
    val calmar: Double
)

/** Is it luck? (Bailey and López de Prado.) Daily Sharpes, as the formulas need. */
data class Robust(
    val sharpeDaily: Double,
    val skew: Double,
    val kurtosis: Double,
    val days: Int,
    /** Probability the true Sharpe is above zero. */
    val psr: Double,
    val trials: Int,
    val trialSpreadMeasured: Boolean,
    /** The best annual Sharpe pure chance would give after this many settings. */
    val luckHurdleAnnual: Double,
    /** Deflated Sharpe ratio: probability the true Sharpe beats that hurdle. */
    val dsr: Double,
    /** Years of results needed to tell this Sharpe from zero at 95% (null: never). */
    val minTrackRecordYears: Double?
)

data class Bootstrap(
    val sims: Int,
    val sharpe: Triple<Double, Double, Double>,
    val cagr: Triple<Double, Double, Double>,
    val maxDrawdown: Triple<Double, Double, Double>,
    val sharpeBelowZeroPct: Double,
    val worseDrawdownPct: Double
)

/** One of the six checks; [passed] null = not checked yet. */
data class Gate(val name: String, val passed: Boolean?, val detail: String)

object DeskStats {

    const val SPLIT_FRACTION = 0.70
    private const val EULER = 0.5772156649015329

    /** Excess daily returns over the T-bill. */
    fun excess(dates: List<LocalDate>, eq: DoubleArray, rf: Line): DoubleArray {
        if (eq.size < 2) return DoubleArray(0)
        val rd = rf.asOf(dates.subList(1, dates.size))
        return DoubleArray(eq.size - 1) { i ->
            val r = eq[i + 1] / eq[i] - 1
            r - (if (rd[i].isNaN()) 0.0 else rd[i]) / 252.0
        }
    }

    fun metrics(dates: List<LocalDate>, eq: DoubleArray, rf: Line): Metrics? {
        if (eq.size < 30) return null
        val ret = DoubleArray(eq.size - 1) { eq[it + 1] / eq[it] - 1 }
        val ex = excess(dates, eq, rf)
        val years = ChronoUnit.DAYS.between(dates.first(), dates.last()) / 365.25
        val cagr = if (years > 0) (eq.last() / eq.first()).pow(1 / years) - 1 else Double.NaN
        val vol = DeskMath.std(ret) * sqrt(252.0)
        val exSd = DeskMath.std(ex)
        val mean = ex.average()
        val sharpe = if (exSd > 0) mean / exSd * sqrt(252.0) else Double.NaN
        val down = DeskMath.std(ex.filter { it < 0 }.toDoubleArray())
        val sortino = if (!down.isNaN() && down > 0) mean / down * sqrt(252.0) else Double.NaN
        var peak = eq[0]
        var maxDd = 0.0
        var longest = 0L
        var peakDate = dates[0]
        for (i in eq.indices) {
            if (eq[i] > peak) peak = eq[i]
            val dd = eq[i] / peak - 1
            if (dd < maxDd) maxDd = dd
            if (dd >= 0) peakDate = dates[i] else longest = maxOf(longest, ChronoUnit.DAYS.between(peakDate, dates[i]))
        }
        return Metrics(dates.first(), dates.last(), years, cagr, vol, sharpe, sortino, maxDd, longest,
            if (maxDd < 0) cagr / abs(maxDd) else Double.NaN)
    }

    /** Index of the first held-out day: the first 70% is in-sample. */
    fun splitIndex(n: Int): Int = (n * SPLIT_FRACTION).toInt()

    /**
     * Buy-and-hold scaled to the strategy's volatility over the same days, the rest in T-bills:
     * a yardstick for "could plain holding have done as well at the same risk?" Its scale is
     * set with hindsight, so it's a yardstick only. [bench] is aligned to [dates] (NaN where missing).
     */
    fun volMatched(dates: List<LocalDate>, strat: DoubleArray, bench: DoubleArray, rf: Line): Pair<List<LocalDate>, DoubleArray> {
        val keep = dates.indices.filter { !bench[it].isNaN() }
        val d = keep.map { dates[it] }
        val b = keep.map { bench[it] }.toDoubleArray()
        val s = keep.map { strat[it] }.toDoubleArray()
        if (d.size < 2) return d to s
        val rb = DoubleArray(b.size) { if (it == 0) 0.0 else b[it] / b[it - 1] - 1 }
        val rs = DoubleArray(s.size - 1) { s[it + 1] / s[it] - 1 }
        val sdB = DeskMath.std(rb)
        val k = if (sdB > 0) minOf(1.0, DeskMath.std(rs) / sdB) else 1.0
        val rfd = rf.asOf(d)
        val out = DoubleArray(d.size)
        var acc = 1.0
        for (i in d.indices) {
            acc *= 1 + k * rb[i] + (1 - k) * (if (rfd[i].isNaN()) 0.0 else rfd[i]) / 252.0
            out[i] = acc * s[0]
        }
        return d to out
    }

    // --- robustness ---

    /** (daily Sharpe, skew, kurtosis with normal = 3, observations). */
    fun moments(x: DoubleArray): Robustness {
        val v = x.filter { !it.isNaN() }
        val n = v.size
        if (n < 3) return Robustness(Double.NaN, 0.0, 3.0, n)
        val mu = v.average()
        val sd = sqrt(v.sumOf { (it - mu) * (it - mu) } / (n - 1))
        val m2 = v.sumOf { (it - mu) * (it - mu) } / n
        if (sd <= 0 || m2 <= 0) return Robustness(Double.NaN, 0.0, 3.0, n)
        val m3 = v.sumOf { (it - mu).pow(3) } / n
        val m4 = v.sumOf { (it - mu).pow(4) } / n
        return Robustness(mu / sd, m3 / m2.pow(1.5), m4 / (m2 * m2), n)
    }

    data class Robustness(val sharpe: Double, val skew: Double, val kurtosis: Double, val n: Int)

    fun psr(sr: Double, srStar: Double, n: Int, skew: Double, kurt: Double): Double {
        if (n < 3 || sr.isNaN()) return Double.NaN
        val den = 1.0 - skew * sr + (kurt - 1.0) / 4.0 * sr * sr
        if (den <= 0) return Double.NaN
        return DeskMath.normCdf((sr - srStar) * sqrt((n - 1).toDouble()) / sqrt(den))
    }

    fun expectedMaxSharpe(trials: Int, variance: Double): Double {
        if (trials <= 1 || variance <= 0) return 0.0
        return sqrt(variance) * ((1 - EULER) * DeskMath.normInv(1 - 1.0 / trials) +
            EULER * DeskMath.normInv(1 - 1.0 / (trials * E)))
    }

    fun minTrackRecordDays(sr: Double, skew: Double, kurt: Double, srStar: Double = 0.0, alpha: Double = 0.05): Double {
        if (sr.isNaN() || sr <= srStar) return Double.POSITIVE_INFINITY
        val z = DeskMath.normInv(1 - alpha)
        return 1 + (1 - skew * sr + (kurt - 1) / 4 * sr * sr) * (z / (sr - srStar)).pow(2)
    }

    /**
     * PSR against zero and the deflated Sharpe ratio given every setting tried ([trialSharpes]:
     * in-sample daily Sharpes, null where unknown). With fewer than five known, their spread is
     * taken as what pure chance gives over this many days (1 / days).
     */
    fun robust(dates: List<LocalDate>, eq: DoubleArray, rf: Line, trialSharpes: List<Double?> = emptyList()): Robust? {
        val m = moments(excess(dates, eq, rf))
        if (m.n < 30) return null
        val known = trialSharpes.filterNotNull().filter { !it.isNaN() }
        val trials = maxOf(1, trialSharpes.size)
        val measured = known.size >= 5
        val variance = if (measured) {
            val mu = known.average()
            known.sumOf { (it - mu) * (it - mu) } / (known.size - 1)
        } else 1.0 / m.n
        val hurdle = expectedMaxSharpe(trials, variance)
        val mt = minTrackRecordDays(m.sharpe, m.skew, m.kurtosis)
        return Robust(
            m.sharpe, m.skew, m.kurtosis, m.n,
            psr(m.sharpe, 0.0, m.n, m.skew, m.kurtosis),
            trials, measured, hurdle * sqrt(252.0),
            psr(m.sharpe, hurdle, m.n, m.skew, m.kurtosis),
            if (mt.isFinite()) mt / 252 else null
        )
    }

    /** Stationary block bootstrap: daily returns reshuffled in runs of about [block] days. */
    fun bootstrap(dates: List<LocalDate>, eq: DoubleArray, rf: Line, sims: Int = 400, block: Int = 20, seed: Long = 7): Bootstrap? {
        if (eq.size < 61) return null
        val n = eq.size - 1
        val r = DoubleArray(n) { eq[it + 1] / eq[it] - 1 }
        val rfd = rf.asOf(dates.subList(1, dates.size)).map { (if (it.isNaN()) 0.0 else it) / 252.0 }
        val rng = SplittableRandom(seed)
        val p = 1.0 / block
        val sharpe = DoubleArray(sims)
        val cagr = DoubleArray(sims)
        val dd = DoubleArray(sims)
        var actualDd = 0.0
        var pk = eq[0]
        for (x in eq) { if (x > pk) pk = x; actualDd = minOf(actualDd, x / pk - 1) }
        val years = n / 252.0
        for (s in 0 until sims) {
            var j = rng.nextInt(n)
            var sum = 0.0
            var sumSq = 0.0
            var g = 1.0
            var peak = 1.0
            var worst = 0.0
            for (t in 0 until n) {
                if (t > 0) j = if (rng.nextDouble() < p) rng.nextInt(n) else (j + 1) % n
                val ex = r[j] - rfd[j]
                sum += ex
                sumSq += ex * ex
                g *= 1 + r[j]
                if (g > peak) peak = g
                worst = minOf(worst, g / peak - 1)
            }
            val mean = sum / n
            val sd = sqrt(maxOf(0.0, (sumSq - n * mean * mean) / (n - 1)))
            sharpe[s] = if (sd > 0) mean / sd * sqrt(252.0) else Double.NaN
            cagr[s] = g.pow(1 / years) - 1
            dd[s] = worst
        }
        fun q(a: DoubleArray): Triple<Double, Double, Double> {
            val v = a.filter { !it.isNaN() }.sorted()
            if (v.isEmpty()) return Triple(Double.NaN, Double.NaN, Double.NaN)
            return Triple(DeskMath.quantile(v, 0.05), DeskMath.quantile(v, 0.5), DeskMath.quantile(v, 0.95))
        }
        return Bootstrap(sims, q(sharpe), q(cagr), q(dd),
            sharpe.count { it < 0 } * 100.0 / sims, dd.count { it < actualDd } * 100.0 / sims)
    }

    // --- the six gates ---

    data class GateRules(
        val minEntries: Int = 30, val minYears: Double = 7.0, val dsrMin: Double = 0.90,
        val paperDays: Int = 60, val paperFollowed: Double = 0.80
    )

    /** The paper journal for one strategy: days journalled, days tagged, share followed. */
    data class Paper(val days: Int, val tagged: Int, val followedShare: Double)

    fun gates(
        entries: Int,
        full: Metrics?,
        heldOut: Metrics?,
        volMatchedHeldOut: Metrics?,
        costsDoubledHeldOut: Metrics?,
        inSample: Robust?,
        nearby: String?,
        paper: Paper?,
        rules: GateRules = GateRules()
    ): List<Gate> {
        val years = full?.years ?: 0.0
        val hs = heldOut?.sharpe
        val vs = volMatchedHeldOut?.sharpe
        val xs = costsDoubledHeldOut?.sharpe
        val dsr = inSample?.dsr
        return listOf(
            Gate("Enough trades", entries >= rules.minEntries && years >= rules.minYears,
                "$entries entries over ${"%.1f".format(years)} years (needs ${rules.minEntries} over ${rules.minYears.toInt()})"),
            Gate("Holds up on the held-out years",
                hs != null && !hs.isNaN() && hs > 0 && (vs == null || vs.isNaN() || hs >= vs),
                "held-out Sharpe ${fmt(hs)} against ${fmt(vs)} for buy-and-hold at the same risk"),
            Gate("Survives doubled costs", xs != null && !xs.isNaN() && xs > 0,
                "held-out Sharpe ${fmt(xs)} with every cost doubled"),
            Gate("Not explained by trying many settings", dsr != null && !dsr.isNaN() && dsr >= rules.dsrMin,
                "deflated Sharpe ${fmt(dsr, 2)} after ${inSample?.trials ?: 1} setting(s) (needs ${fmt(rules.dsrMin, 2)})"),
            Gate("Nearby settings agree", nearby?.startsWith("broad"), nearby ?: "not checked: tap Check nearby settings"),
            Gate("Paper-traded by the rules",
                paper?.let { it.days >= rules.paperDays && it.followedShare >= rules.paperFollowed },
                if (paper == null) "no paper journal yet"
                else "${paper.days} days journalled, rules followed ${(paper.followedShare * 100).toInt()}% of tagged days " +
                    "(needs ${rules.paperDays} days and ${(rules.paperFollowed * 100).toInt()}%)")
        )
    }

    /**
     * Today's drawdown deeper than [times] the worst in the test period (at least 5%): the
     * rules may have stopped working. Null when fine.
     */
    fun guardrail(eq: DoubleArray, times: Double = 1.5): String? {
        if (eq.isEmpty()) return null
        val cut = splitIndex(eq.size)
        var peak = eq[0]
        var worst = 0.0
        for (i in 0 until cut) {
            if (eq[i] > peak) peak = eq[i]
            worst = minOf(worst, eq[i] / peak - 1)
        }
        worst = minOf(worst, -0.05)
        val now = eq.last() / eq.max() - 1
        return if (now < worst * times) {
            "Pause and review: down %.1f%% from its peak, beyond %.1f× the worst drawdown in testing (%.1f%%)".format(
                now * 100, times, worst * 100)
        } else null
    }

    /** The sensitivity verdict for a grid of in-sample Sharpes around [default] (same wording as Colab). */
    fun nearbyVerdict(default: Double, others: List<Double>): String {
        val all = others.filter { !it.isNaN() }
        if (default.isNaN() || all.isEmpty()) return "uneven: not enough data to compare nearby settings"
        val near = all.count { abs(it - default) <= 0.10 } + 1
        val total = all.size + 1
        val sorted = all.sorted()
        val median = DeskMath.quantile(sorted, 0.5)
        return when {
            default - median > 0.15 && default >= sorted.last() ->
                "a lone peak: the default beats its neighbours by a wide margin, a sign of curve-fitting"
            near >= total * 0.6 -> "broad: most nearby settings land close to the default"
            else -> "uneven: results swing with small changes, so treat any one setting with care"
        }
    }

    fun fmt(x: Double?, digits: Int = 2): String =
        if (x == null || x.isNaN()) "–" else "%.${digits}f".format(x)
}
