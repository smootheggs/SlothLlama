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

/**
 * One of the six questions: [name] asks it, [detail] answers it in a line, [more] gives the
 * numbers behind the answer. [passed] null: not checked yet, or still under way (paper days).
 */
data class Gate(val name: String, val passed: Boolean?, val detail: String, val more: String = "")

object DeskStats {

    const val SPLIT_FRACTION = 0.70
    private const val EULER = 0.5772156649015329

    /**
     * Excess daily returns over the T-bill. [ppy] is the periods in a year of the run's calendar:
     * 252 trading days, or 365 for a rule on crypto alone, which trades every day.
     */
    fun excess(dates: List<LocalDate>, eq: DoubleArray, rf: Line, ppy: Double = 252.0): DoubleArray {
        if (eq.size < 2) return DoubleArray(0)
        val rd = rf.asOf(dates.subList(1, dates.size))
        return DoubleArray(eq.size - 1) { i ->
            val r = eq[i + 1] / eq[i] - 1
            r - (if (rd[i].isNaN()) 0.0 else rd[i]) / ppy
        }
    }

    fun metrics(dates: List<LocalDate>, eq: DoubleArray, rf: Line, ppy: Double = 252.0): Metrics? {
        if (eq.size < 30) return null
        val ret = DoubleArray(eq.size - 1) { eq[it + 1] / eq[it] - 1 }
        val ex = excess(dates, eq, rf, ppy)
        val years = ChronoUnit.DAYS.between(dates.first(), dates.last()) / 365.25
        val cagr = if (years > 0) (eq.last() / eq.first()).pow(1 / years) - 1 else Double.NaN
        val vol = DeskMath.std(ret) * sqrt(ppy)
        val exSd = DeskMath.std(ex)
        val mean = ex.average()
        val sharpe = if (exSd > 0) mean / exSd * sqrt(ppy) else Double.NaN
        val down = DeskMath.std(ex.filter { it < 0 }.toDoubleArray())
        val sortino = if (!down.isNaN() && down > 0) mean / down * sqrt(ppy) else Double.NaN
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
    fun volMatched(dates: List<LocalDate>, strat: DoubleArray, bench: DoubleArray, rf: Line, ppy: Double = 252.0): Pair<List<LocalDate>, DoubleArray> {
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
            acc *= 1 + k * rb[i] + (1 - k) * (if (rfd[i].isNaN()) 0.0 else rfd[i]) / ppy
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
    fun robust(dates: List<LocalDate>, eq: DoubleArray, rf: Line, trialSharpes: List<Double?> = emptyList(), ppy: Double = 252.0): Robust? {
        val m = moments(excess(dates, eq, rf, ppy))
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
            trials, measured, hurdle * sqrt(ppy),
            psr(m.sharpe, hurdle, m.n, m.skew, m.kurtosis),
            if (mt.isFinite()) mt / ppy else null
        )
    }

    /** Stationary block bootstrap: daily returns reshuffled in runs of about [block] days. */
    fun bootstrap(dates: List<LocalDate>, eq: DoubleArray, rf: Line, sims: Int = 400, block: Int = 20, seed: Long = 7, ppy: Double = 252.0): Bootstrap? {
        if (eq.size < 61) return null
        val n = eq.size - 1
        val r = DoubleArray(n) { eq[it + 1] / eq[it] - 1 }
        val rfd = rf.asOf(dates.subList(1, dates.size)).map { (if (it.isNaN()) 0.0 else it) / ppy }
        val rng = SplittableRandom(seed)
        val p = 1.0 / block
        val sharpe = DoubleArray(sims)
        val cagr = DoubleArray(sims)
        val dd = DoubleArray(sims)
        var actualDd = 0.0
        var pk = eq[0]
        for (x in eq) { if (x > pk) pk = x; actualDd = minOf(actualDd, x / pk - 1) }
        val years = n / ppy
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
            sharpe[s] = if (sd > 0) mean / sd * sqrt(ppy) else Double.NaN
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

    // --- the six questions ---

    data class GateRules(
        val minEntries: Int = 30, val minYears: Double = 7.0, val dsrMin: Double = 0.90,
        val paperDays: Int = 60, val paperFollowed: Double = 0.80
    )

    /** The paper journal for one strategy: days journalled, days tagged, share followed. */
    data class Paper(val days: Int, val tagged: Int, val followedShare: Double)

    /**
     * The six questions a test answers, in plain words (1.48). They ask what the six gates did
     * (enough trades now counts toward "could luck explain it?", and the held-out gate is two
     * questions: did it beat holding, did it hold up), so passing all six is passing all six
     * gates. [benchLabel] names what plain holding means ("GLD").
     */
    fun questions(
        entries: Int,
        full: Metrics?,
        inSample: Metrics?,
        heldOut: Metrics?,
        benchHeldOut: Metrics?,
        volMatchedHeldOut: Metrics?,
        costsDoubledHeldOut: Metrics?,
        robustIn: Robust?,
        nearby: String?,
        paper: Paper?,
        benchLabel: String,
        rules: GateRules = GateRules()
    ): List<Gate> {
        val years = full?.years ?: 0.0
        val hs = heldOut?.sharpe?.takeIf { !it.isNaN() }
        val vs = volMatchedHeldOut?.sharpe?.takeIf { !it.isNaN() }
        val xs = costsDoubledHeldOut?.sharpe?.takeIf { !it.isNaN() }
        val ins = inSample?.sharpe?.takeIf { !it.isNaN() }
        val dsr = robustIn?.dsr?.takeIf { !it.isNaN() }
        val tries = robustIn?.trials ?: 1
        val triesWords = if (tries == 1) "1 try" else "$tries tries"

        // 1. Did it beat simply holding?
        val beat = hs != null && (vs == null || hs >= vs)
        val sameRisk = "Held back: Sharpe ${fmt(hs)} against ${fmt(vs)} for holding $benchLabel at the same risk"
        val beside = if (heldOut != null && benchHeldOut != null) {
            "; worst fall ${pctWord(heldOut.maxDrawdown)} against ${pctWord(benchHeldOut.maxDrawdown)}, " +
                "growth ${pctWord(heldOut.cagr, sign = true)} a year against ${pctWord(benchHeldOut.cagr, sign = true)}."
        } else "."
        val q1 = Gate("Did it beat simply holding $benchLabel?", beat,
            when {
                hs == null -> "Can't tell yet: too few held-back days."
                !beat -> "No: holding $benchLabel at the same risk did better."
                heldOut != null && benchHeldOut != null -> "On risk, yes: " + compareWords(heldOut, benchHeldOut) + "."
                else -> "On risk, yes."
            }, sameRisk + beside)

        // 2. Did it hold up on years it never saw?
        val held = hs != null && hs > 0
        val q2 = Gate("Did it hold up on years it never saw?", held,
            when {
                hs == null -> "Can't tell yet: too few held-back days."
                !held -> "No: on the held-back years it did no better than T-bills."
                ins != null && ins > 0 && hs < ins * 0.6 -> "Yes, though weaker than on the years it was built on."
                else -> "Yes: the held-back years look like the rest."
            },
            "Sharpe ${fmt(hs)} on the held-back years from ${heldOut?.start ?: "–"}, ${fmt(ins)} before.")

        // 3. Could luck explain it? (with too few trades, it always could)
        val enough = entries >= rules.minEntries && years >= rules.minYears
        val lucky = enough && dsr != null && dsr >= rules.dsrMin
        val tradesLine = "$entries entries over ${"%.1f".format(years)} years (needs ${rules.minEntries} over ${rules.minYears.toInt()})."
        val q3 = Gate("Could luck explain it?", lucky,
            when {
                !enough -> "Can't rule it out: too few trades to judge."
                dsr == null -> "Can't tell yet: too few days."
                lucky -> "Unlikely, even after $triesWords."
                dsr >= 0.5 -> "Possibly: after $triesWords, chance could do about as well."
                else -> "Likely: after $triesWords, chance alone does as well."
            },
            buildString {
                append("Deflated Sharpe ${fmt(dsr)} (needs ${fmt(rules.dsrMin)}) after $triesWords")
                robustIn?.let { rb ->
                    append(". Chance alone could reach a Sharpe of about ${fmt(rb.luckHurdleAnnual)} after that many tries; this one is ${fmt(ins)}")
                    append(rb.minTrackRecordYears?.let { y -> ". Tells it from zero after about ${fmt(y, 0)} years of results" }
                        ?: ". At this Sharpe no track record tells it from zero")
                    if (!rb.trialSpreadMeasured) append(" (spread of tries assumed)")
                }
                append(". ")
                append(tradesLine)
            })

        // 4. Does it survive doubled costs?
        val survives = xs != null && xs > 0
        val q4 = Gate("Does it survive doubled costs?", survives,
            when {
                xs == null -> "Can't tell yet: too few held-back days."
                !survives -> "No: with every cost doubled it no longer beats T-bills."
                hs != null && hs > 0 && xs >= hs * 0.8 -> "Yes, easily."
                hs != null && hs > 0 && xs >= hs * 0.5 -> "Yes, with a little room to spare."
                else -> "Only just."
            },
            "Held-back Sharpe ${fmt(xs)} with every cost doubled (${fmt(hs)} as tested).")

        // 5. Do nearby settings agree?
        val q5 = Gate("Do nearby settings agree?", nearby?.startsWith("broad"),
            when {
                nearby == null -> "Not checked yet."
                nearby.startsWith("broad") -> "Yes: most nearby settings do about as well."
                nearby.startsWith("a lone peak") -> "No: this setting stands alone, a sign of fitting the past."
                else -> "Not really: results swing with small changes."
            },
            nearby?.replaceFirstChar { it.uppercase() }?.let { "$it." } ?: "Check nearby settings to find out.")

        // 6. Have you followed it on paper? (under way until the days are in)
        val paperDone = paper != null && paper.days >= rules.paperDays
        val followedPct = paper?.let { (it.followedShare * 100).toInt() }
        val q6 = Gate("Have you followed it on paper?",
            if (paperDone) paper!!.followedShare >= rules.paperFollowed else null,
            when {
                paper == null || paper.days == 0 -> "Not yet: ${rules.paperDays} days, following ${(rules.paperFollowed * 10).toInt()} tickets in 10."
                !paperDone -> "Under way: ${paper.days} of ${rules.paperDays} days" + (if (paper.tagged > 0) ", following $followedPct% so far." else ".")
                paper.followedShare >= rules.paperFollowed -> "Yes: ${paper.days} days, following $followedPct% of tickets."
                else -> "Not closely enough: $followedPct% of tickets followed (needs ${(rules.paperFollowed * 100).toInt()}%)."
            },
            if (paper == null) "No paper journal yet."
            else "${paper.days} days journalled, ${paper.tagged} marked, rules followed ${followedPct}% of marked days " +
                "(needs ${rules.paperDays} days and ${(rules.paperFollowed * 100).toInt()}%).")

        return listOf(q1, q2, q3, q4, q5, q6)
    }

    /** "its worst fall was about half as deep, for a little less growth" (held back, against holding). */
    fun compareWords(s: Metrics, b: Metrics): String {
        val ds = abs(s.maxDrawdown)
        val db = abs(b.maxDrawdown)
        val depth = when {
            db <= 1e-9 -> "about as deep"
            ds / db < 0.4 -> "less than half as deep"
            ds / db < 0.6 -> "about half as deep"
            ds / db < 0.85 -> "shallower"
            ds / db <= 1.15 -> "about as deep"
            else -> "deeper"
        }
        val g = s.cagr - b.cagr
        val growth = when {
            g.isNaN() -> ""
            g >= 0.01 -> ", with more growth"
            g > -0.01 -> ", for about the same growth"
            g > -0.03 -> ", for a little less growth"
            else -> ", for less growth"
        }
        return "its worst fall was $depth$growth"
    }

    /** The verdict over the six questions: a headline and a line under it. */
    fun verdict(q: List<Gate>): Pair<String, String> {
        val testable = q.take(5)
        return when {
            q.all { it.passed == true } ->
                "Passed all six" to "It held up in testing and on paper. Still a practice desk: no promise about what comes next."
            testable.all { it.passed == true } ->
                "Ready to paper-trade" to "It passed every test a back-test can give. Only following it for real, on paper, is left."
            testable.count { it.passed == true } == 4 && testable.any { it.passed == null } ->
                "Nearly there" to "Check nearby settings: it's the one test left before paper."
            else -> {
                val first = testable.first { it.passed != true }
                "Research only" to "Not ready for paper yet. ${first.name} ${first.detail}"
            }
        }
    }

    private fun pctWord(x: Double, sign: Boolean = false): String =
        if (x.isNaN()) "–" else (if (sign) "%+.1f%%" else "%.1f%%").format(java.util.Locale.US, x * 100).replace("-", "−")

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
