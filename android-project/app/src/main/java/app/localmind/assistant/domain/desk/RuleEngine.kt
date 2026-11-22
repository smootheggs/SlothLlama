package app.localmind.assistant.domain.desk

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns a [Rule] into daily target weights (decided at each close from data known then) and
 * runs them through [DeskEngine.simulate], or the covered-call model for that overlay. See
 * [Rule] for how a day goes.
 */
object RuleEngine {

    /** Extra costs for markets [Costs] doesn't name (kept apart so settings' trial ids don't change). */
    data class MarketCosts(val cryptoSlippageBp: Double = 10.0)

    /**
     * A rule's targets, on its own calendar (365 days a year for crypto alone, 252 trading days
     * otherwise), and how many times a branch was entered.
     */
    class Plan(val targets: Targets, val periodsPerYear: Double, val entries: Int)

    /**
     * What keeps a held branch: its conditions that aren't only for entering. A crossing marks
     * a moment, not a state, so it only ever counts for entering.
     */
    fun staying(b: Branch): List<Condition> = b.conditions.filter { !it.enterOnly && !(it is Condition.Compare && it.op.crossing) }

    private enum class T { YES, NO, UNKNOWN }

    fun run(
        r: Rule,
        data: DeskData,
        costs: Costs = Costs(),
        costMult: Double = 1.0,
        benchName: String = "Buy and hold ${r.benchmark}",
        market: MarketCosts = MarketCosts()
    ): DeskStrategies.Run {
        val o = r.overlay
        if (o is Overlay.CoveredCall) {
            val p = Params(
                mapOf("delta" to o.delta, "dte" to o.dte.toDouble(), "skew_vol_pts" to o.skewVolPts, "spread_pct" to o.spreadPct,
                    "stress_spread_pct" to o.stressSpreadPct, "fee_per_contract" to o.feePerContract, "strike_step" to o.strikeStep),
                mapOf("asset" to o.asset)
            )
            val vol = data.raw[o.vol] ?: data.knownLine(o.vol)
            val res = DeskEngine.coveredCall(data.prices, vol, data.rf, p, r.name, costs, costMult = costMult)
            val bars = data.prices.getValue(r.benchmark)
            return DeskStrategies.Run(res,
                DeskEngine.simulate(data.prices, DeskEngine.buyAndHold(bars.dates, r.benchmark), data.rf, benchName, costs),
                res.episodes.size, null)
        }
        val plan = targets(r, data)
        val tg = plan.targets
        val carry = r.carry.mapValues { data.accrual(it.value) }
        val fx = r.markets.filterValues { it == Market.FX }.keys.ifEmpty { setOf("USDJPY") }
        val crypto = r.markets.filterValues { it == Market.CRYPTO }.keys
        val res = DeskEngine.simulate(data.prices, tg, data.rf, r.name, costs, costMult = costMult, carry = carry, fxAssets = fx,
            cryptoAssets = crypto, cryptoSlippageBp = market.cryptoSlippageBp, periodsPerYear = plan.periodsPerYear)
        val bench = DeskEngine.simulate(data.prices, DeskEngine.buyAndHold(tg.dates, r.benchmark), data.rf, benchName, costs,
            carry = carry, fxAssets = fx, cryptoAssets = crypto, cryptoSlippageBp = market.cryptoSlippageBp, periodsPerYear = plan.periodsPerYear)
        return DeskStrategies.Run(res, bench, plan.entries, tg, plan.periodsPerYear)
    }

    /** Every asset whose prices the rule reads: traded, compared, or the benchmark. */
    fun priceAssets(r: Rule): List<String> {
        val out = LinkedHashSet<String>()
        out += traded(r)
        out += r.benchmark
        fun sig(s: Signal) { s.assets.filter { it != "*" }.forEach { out += it } }
        fun cond(c: Condition) {
            when (c) {
                is Condition.Compare -> { sig(c.left); (c.right as? Against.Of)?.let { sig(it.signal) } }
                is Condition.AnyOf -> c.of.forEach(::cond)
                is Condition.Not -> cond(c.of)
                is Condition.Calendar -> Unit
            }
        }
        r.branches.forEach { b ->
            b.conditions.forEach(::cond)
            when (val a = b.action) {
                is Action.Scale -> sig(a.signal)
                is Action.Rotate -> a.assets.forEach { x -> sig(forAsset(a.score, x)) }
                is Action.Hold -> Unit
            }
        }
        r.exits.forEach { if (it is Exit.Within) sig(it.signal) }
        return out.toList()
    }

    /** What the rule can hold, in a fixed order. */
    fun traded(r: Rule): List<String> {
        val out = LinkedHashSet<String>()
        r.branches.forEach { b ->
            when (val a = b.action) {
                is Action.Hold -> out += a.weights.keys
                is Action.Rotate -> out += a.assets
                is Action.Scale -> out += a.asset
            }
        }
        out += r.otherwise.keys
        return out.toList()
    }

    /** The data a rule needs that [data] doesn't have. */
    fun missing(r: Rule, data: DeskData?): List<String> {
        val prices = priceAssets(r) + listOfNotNull((r.overlay as? Overlay.CoveredCall)?.asset)
        val series = LinkedHashSet<String>()
        fun sig(s: Signal) {
            when (s) {
                is Signal.Series -> series += s.id
                is Signal.Change -> series += s.id
                is Signal.Gap -> { series += s.id; series += s.id2 }
                else -> Unit
            }
        }
        fun cond(c: Condition) {
            when (c) {
                is Condition.Compare -> { sig(c.left); (c.right as? Against.Of)?.let { sig(it.signal) } }
                is Condition.AnyOf -> c.of.forEach(::cond)
                is Condition.Not -> cond(c.of)
                is Condition.Calendar -> Unit
            }
        }
        r.branches.forEach { b -> b.conditions.forEach(::cond); (b.action as? Action.Scale)?.let { sig(it.signal) } }
        r.exits.forEach { if (it is Exit.Within) sig(it.signal) }
        r.carry.values.forEach { id -> if (id.startsWith("gap:")) id.split(":").drop(1).forEach { series += it } else series += id }
        (r.overlay as? Overlay.CoveredCall)?.let { series += it.vol }
        if (data == null) return prices.distinct() + series
        return prices.distinct().filter { (data.prices[it]?.size ?: 0) < 300 } + series.filter { data.knownLine(it).size == 0 }
    }

    private fun forAsset(s: Signal, a: String): Signal = when (s) {
        is Signal.Price -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.Average -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.Momentum -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.High -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.Low -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.OffHigh -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.Stretch -> s.copy(asset = if (s.asset == "*") a else s.asset, asset2 = if (s.asset2 == "*") a else s.asset2)
        is Signal.Ratio -> s.copy(asset = if (s.asset == "*") a else s.asset, asset2 = if (s.asset2 == "*") a else s.asset2)
        is Signal.Rsi -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.Volatility -> if (s.asset == "*") s.copy(asset = a) else s
        is Signal.Series, is Signal.Change, is Signal.Gap -> s
    }

    /** The days every price the rule reads has, oldest first. */
    private fun axis(r: Rule, data: DeskData): List<LocalDate> {
        val assets = priceAssets(r)
        val sets = assets.map { a -> (data.prices[a] ?: error("No prices for $a")).dates }
        if (sets.size == 1) return sets[0]
        val common = sets.drop(1).fold(sets[0].toHashSet()) { acc, d -> acc.apply { retainAll(d.toHashSet()) } }
        return sets[0].filter { it in common }.sorted()
    }

    // --- the signals, each a number per day, cached ---

    private class Ctx(val r: Rule, val data: DeskData, val dates: List<LocalDate>, val ppy: Double) {
        val n = dates.size
        private val barsOn = HashMap<String, Bars>()
        private val cache = HashMap<Signal, DoubleArray>()
        private val thresholds = HashMap<Pair<Signal, Against.Percentile>, DoubleArray>()

        fun bars(a: String): Bars = barsOn.getOrPut(a) { (data.prices[a] ?: error("No prices for $a")).on(dates) }
        fun closes(a: String): DoubleArray = bars(a).closes
        fun opens(a: String): DoubleArray = bars(a).opens

        fun values(s: Signal): DoubleArray = cache.getOrPut(s) { compute(s) }

        fun percentile(s: Signal, p: Against.Percentile): DoubleArray =
            thresholds.getOrPut(s to p) { DeskMath.rollingQuantile(values(s), p.days, p.minDays, p.q) }

        private fun nan() = DoubleArray(n) { Double.NaN }

        private fun compute(s: Signal): DoubleArray = when (s) {
            is Signal.Price -> closes(s.asset)
            is Signal.Average -> if (s.exp) ema(closes(s.asset), s.days) else DeskMath.rollingMean(closes(s.asset), s.days)
            is Signal.Momentum -> {
                val c = closes(s.asset)
                DoubleArray(n) { i -> val j = i - s.skip; val k = j - s.days; if (k < 0) Double.NaN else c[j] / c[k] - 1 }
            }
            is Signal.High -> {
                val c = closes(s.asset)
                DoubleArray(n) { i -> if (i < s.days) Double.NaN else (i - s.days until i).maxOf { c[it] } }
            }
            is Signal.Low -> {
                val c = closes(s.asset)
                DoubleArray(n) { i -> if (i < s.days) Double.NaN else (i - s.days until i).minOf { c[it] } }
            }
            is Signal.OffHigh -> {
                val c = closes(s.asset)
                DoubleArray(n) { i -> if (i < s.days - 1) Double.NaN else c[i] / (i - s.days + 1..i).maxOf { c[it] } - 1 }
            }
            is Signal.Stretch -> {
                val a = closes(s.asset)
                val b = s.asset2?.let { closes(it) }
                val lr = DoubleArray(n) { i -> if (b == null) ln(a[i]) else ln(a[i] / b[i]) }
                val mean = DeskMath.rollingMean(lr, s.days)
                val sd = DeskMath.rollingStd(lr, s.days)
                DoubleArray(n) { i -> (lr[i] - mean[i]) / sd[i] }
            }
            is Signal.Ratio -> {
                val a = closes(s.asset)
                val b = closes(s.asset2)
                DoubleArray(n) { a[it] / b[it] }
            }
            is Signal.Rsi -> rsi(closes(s.asset), s.days)
            is Signal.Volatility -> DeskMath.rollingStd(DeskMath.pctChange(closes(s.asset)), s.days).map { it * sqrt(ppy) }.toDoubleArray()
            is Signal.Series -> data.knownLine(s.id).asOf(dates)
            is Signal.Change -> {
                val v = data.knownLine(s.id).asOf(dates)
                DoubleArray(n) { i -> if (i < s.days) Double.NaN else v[i] - v[i - s.days] }
            }
            is Signal.Gap -> DeskData.gapOf(data.knownLine(s.id), data.knownLine(s.id2)).asOf(dates)
        }.let { if (it.size == n) it else nan() }

        /** Exponential average with span N (weight 2/(N+1)), known from the N-th day. */
        private fun ema(x: DoubleArray, span: Int): DoubleArray {
            val a = 2.0 / (span + 1)
            val out = DoubleArray(x.size) { Double.NaN }
            var e = Double.NaN
            for (i in x.indices) {
                e = if (e.isNaN()) x[i] else a * x[i] + (1 - a) * e
                if (i >= span - 1) out[i] = e
            }
            return out
        }

        /** Wilder's RSI: average gain over average loss, smoothed over [days]. */
        private fun rsi(c: DoubleArray, days: Int): DoubleArray {
            val out = DoubleArray(c.size) { Double.NaN }
            if (c.size <= days) return out
            var g = 0.0
            var l = 0.0
            for (i in 1..days) { val d = c[i] - c[i - 1]; if (d > 0) g += d else l -= d }
            g /= days; l /= days
            fun value() = if (l == 0.0) 100.0 else 100.0 - 100.0 / (1 + g / l)
            out[days] = value()
            for (i in days + 1 until c.size) {
                val d = c[i] - c[i - 1]
                g = (g * (days - 1) + max(d, 0.0)) / days
                l = (l * (days - 1) + max(-d, 0.0)) / days
                out[i] = value()
            }
            return out
        }
    }

    private fun cmp(l: Double, r: Double, op: Op): Boolean = when (op) {
        Op.ABOVE, Op.CROSSES_ABOVE -> l > r
        Op.BELOW, Op.CROSSES_BELOW -> l < r
        Op.AT_LEAST -> l >= r
        Op.AT_MOST -> l <= r
    }

    private fun Ctx.eval(c: Condition, i: Int): T = when (c) {
        is Condition.Compare -> {
            fun once(k: Int): T {
                if (k < 0) return T.UNKNOWN
                val l = values(c.left)
                val rv: DoubleArray? = when (val a = c.right) {
                    is Against.Value -> null
                    is Against.Of -> values(a.signal)
                    is Against.Percentile -> percentile(c.left, a)
                }
                fun right(j: Int) = (c.right as? Against.Value)?.x ?: rv!![j]
                val isPct = c.right is Against.Percentile
                if (c.op.crossing) {
                    if (k == 0) return T.NO
                    val l0 = l[k - 1]; val r0 = right(k - 1); val l1 = l[k]; val r1 = right(k)
                    if (l0.isNaN() || r0.isNaN() || l1.isNaN() || r1.isNaN()) return if (isPct) T.NO else T.UNKNOWN
                    val before = if (c.op == Op.CROSSES_ABOVE) l0 <= r0 else l0 >= r0
                    return if (before && cmp(l1, r1, c.op)) T.YES else T.NO
                }
                val r1 = right(k)
                // A percentile without enough history doesn't block; a missing value does.
                if (isPct && r1.isNaN()) return T.YES
                if (isPct && l[k].isNaN()) return T.NO
                if (l[k].isNaN() || r1.isNaN()) return T.UNKNOWN
                return if (cmp(l[k], r1, c.op)) T.YES else T.NO
            }
            if (c.days == 1) once(i) else {
                val all = (i - c.days + 1..i).map { once(it) }
                when {
                    T.NO in all -> T.NO
                    T.UNKNOWN in all -> T.UNKNOWN
                    else -> T.YES
                }
            }
        }
        is Condition.Calendar -> {
            val d = dates[i]
            val ok = (c.months.isEmpty() || d.monthValue in c.months) &&
                (c.weekdays.isEmpty() || d.dayOfWeek.value in c.weekdays) &&
                (c.dates.isEmpty() || c.dates.any { abs(ChronoUnit.DAYS.between(LocalDate.parse(it), d)) <= c.around })
            if (ok) T.YES else T.NO
        }
        is Condition.AnyOf -> {
            val all = c.of.map { eval(it, i) }
            when {
                T.YES in all -> T.YES
                T.UNKNOWN in all -> T.UNKNOWN
                else -> T.NO
            }
        }
        is Condition.Not -> when (eval(c.of, i)) {
            T.YES -> T.NO
            T.NO -> T.YES
            T.UNKNOWN -> T.UNKNOWN
        }
    }

    // --- words for the log ---

    private fun name(s: Signal): String = when (s) {
        is Signal.Price -> "${s.asset} close"
        is Signal.Average -> "${s.asset}'s ${s.days}-day ${if (s.exp) "exponential " else ""}average"
        is Signal.Momentum -> "${s.asset}'s ${s.days}-day return" + if (s.skip > 0) " (skipping ${s.skip})" else ""
        is Signal.High -> "${s.asset}'s ${s.days}-day high"
        is Signal.Low -> "${s.asset}'s ${s.days}-day low"
        is Signal.OffHigh -> "${s.asset} off its ${s.days}-day high"
        is Signal.Stretch -> (if (s.asset2 != null) "${s.asset}/${s.asset2}" else s.asset) + " stretch (z)"
        is Signal.Ratio -> "${s.asset}/${s.asset2}"
        is Signal.Rsi -> "${s.asset}'s RSI(${s.days})"
        is Signal.Volatility -> "${s.asset}'s ${s.days}-day volatility"
        is Signal.Series -> s.id
        is Signal.Change -> "${s.id}'s ${s.days}-day change"
        is Signal.Gap -> "${s.id} − ${s.id2}"
    }

    private fun num(x: Double): String = when {
        x.isNaN() -> "?"
        abs(x) >= 100 -> "%.2f".format(x)
        abs(x) >= 1 -> "%.3f".format(x)
        else -> "%.4f".format(x)
    }

    private fun word(op: Op) = when (op) {
        Op.ABOVE -> "above"; Op.BELOW -> "below"; Op.AT_LEAST -> "at least"; Op.AT_MOST -> "at most"
        Op.CROSSES_ABOVE -> "crossed above"; Op.CROSSES_BELOW -> "crossed below"
    }

    private fun opposite(op: Op) = when (op) {
        Op.ABOVE, Op.CROSSES_ABOVE -> "no longer above"; Op.BELOW, Op.CROSSES_BELOW -> "no longer below"
        Op.AT_LEAST -> "fell below"; Op.AT_MOST -> "rose above"
    }

    private fun tag(c: Condition): String = when (c) {
        is Condition.Compare -> c.tag.ifBlank {
            val l = c.left
            val r = (c.right as? Against.Of)?.signal
            when {
                l is Signal.Average || r is Signal.Average -> "trend"
                l is Signal.High || l is Signal.Low || r is Signal.High || r is Signal.Low -> "breakout"
                l is Signal.Momentum -> "momentum"
                l is Signal.Stretch || l is Signal.Ratio -> "stretch"
                l is Signal.Rsi -> "rsi"
                l is Signal.OffHigh -> "drawdown"
                c.right is Against.Percentile -> "filter"
                l is Signal.Gap -> "gap"
                l is Signal.Change -> "change"
                else -> "level"
            }
        }
        is Condition.Calendar -> "calendar"
        is Condition.AnyOf -> c.of.firstOrNull()?.let(::tag) ?: "any"
        is Condition.Not -> tag(c.of)
    }

    private fun Ctx.say(c: Condition, i: Int, held: Boolean): String = when (c) {
        is Condition.Compare -> {
            val l = values(c.left)[i]
            val right = when (val a = c.right) {
                is Against.Value -> num(a.x)
                is Against.Of -> "${name(a.signal)} ${num(values(a.signal)[i])}"
                is Against.Percentile -> "its ${(a.q * 100).toInt()}th percentile ${num(percentile(c.left, a)[i])}"
            }
            "${name(c.left)} ${num(l)} ${if (held) opposite(c.op) else word(c.op)} $right" + if (c.days > 1 && !held) " for ${c.days} days" else ""
        }
        is Condition.Calendar -> if (held) "out of its dates" else "in its dates"
        is Condition.AnyOf -> c.of.firstOrNull { eval(it, i) == (if (held) T.NO else T.YES) }?.let { say(it, i, held) } ?: "none of its conditions"
        is Condition.Not -> "not (" + say(c.of, i, !held) + ")"
    }

    // --- tonight, in words ---

    /** One of a rule's conditions at the last close: in words, with the numbers, and whether it held (null: no data). */
    class Today(val branch: String, val text: String, val met: Boolean?)

    /** Each condition of each branch as it stood at the last close, for "why tonight's ticket says this". */
    fun today(r: Rule, data: DeskData): List<Today> {
        if (missing(r, data).isNotEmpty()) return emptyList()
        val dates = axis(r, data)
        if (dates.isEmpty()) return emptyList()
        val crypto = priceAssets(r).all { r.markets[it] == Market.CRYPTO }
        val ctx = Ctx(r, data, dates, if (crypto) 365.0 else 252.0)
        val i = dates.size - 1
        return r.branches.flatMap { b ->
            b.conditions.map { c ->
                val met = when (ctx.eval(c, i)) { T.YES -> true; T.NO -> false; T.UNKNOWN -> null }
                Today(b.tag, RuleWords.condition(c).replaceFirstChar { it.uppercase() } + ctx.now(c, i), met)
            }
        }
    }

    /** " · now 245.10 against 231.20", in the left signal's units. */
    private fun Ctx.now(c: Condition, i: Int): String {
        if (c !is Condition.Compare) return ""
        val u = RuleWords.unit(c.left)
        fun f(x: Double) = if (x.isNaN()) "–" else if (u == RuleWords.Unit.PERCENT) "%.2f%%".format(java.util.Locale.US, x * 100) else "%.2f".format(java.util.Locale.US, x)
        val l = values(c.left)[i]
        val r = when (val a = c.right) {
            is Against.Value -> a.x
            is Against.Of -> values(a.signal)[i]
            is Against.Percentile -> percentile(c.left, a)[i]
        }
        return " · now ${f(l)} against ${f(r)}"
    }

    // --- the day-by-day machine ---

    fun targets(r: Rule, data: DeskData): Plan {
        val dates = axis(r, data)
        val crypto = priceAssets(r).all { r.markets[it] == Market.CRYPTO }
        val ctx = Ctx(r, data, dates, if (crypto) 365.0 else 252.0)
        val n = dates.size
        val assets = traded(r)
        val w = assets.map { DoubleArray(n) }
        val why = Array(n) { "" }
        val stop = r.exits.filterIsInstance<Exit.Stop>().firstOrNull()

        var active = -1
        var held = 0
        var entryAt = -1
        var entryW: Map<String, Double> = emptyMap()
        var best = 0.0
        var last: Map<String, Double> = r.otherwise
        var pick: List<String> = emptyList()
        var paper = 1.0
        var peak = 1.0
        var braking = 0
        var entries = 0

        fun checkDay(i: Int): Boolean = when (r.check) {
            Check.DAILY -> true
            Check.WEEKLY -> i == 0 || dates[i].get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != dates[i - 1].get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) ||
                dates[i].get(IsoFields.WEEK_BASED_YEAR) != dates[i - 1].get(IsoFields.WEEK_BASED_YEAR)
            Check.MONTHLY -> i == 0 || dates[i].monthValue != dates[i - 1].monthValue || dates[i].year != dates[i - 1].year
        }

        /** The position's return since its fill (the open after the entry's close). */
        fun positionReturn(i: Int): Double {
            if (i <= entryAt) return 0.0
            val fill = entryAt + 1
            return entryW.entries.sumOf { (a, x) -> x * (ctx.closes(a)[i] / ctx.opens(a)[fill] - 1) }
        }

        fun sized(b: Branch, i: Int, fresh: Boolean): Map<String, Double> {
            val raw: Map<String, Double> = when (val a = b.action) {
                is Action.Hold -> a.weights
                is Action.Rotate -> {
                    if (fresh || pick.isEmpty()) {
                        val scored = a.assets.map { x -> x to ctx.values(forAsset(a.score, x))[i] }.filter { !it.second.isNaN() }
                        pick = scored.sortedByDescending { it.second }.take(a.top)
                            .filter { s -> a.minScore == null || s.second > a.minScore }.map { it.first }
                    }
                    pick.associateWith { a.weight / a.top }
                }
                is Action.Scale -> {
                    val v = ctx.values(a.signal)[i]
                    val f = if (v.isNaN()) 0.0 else ((v - a.from) / (a.to - a.from)).coerceIn(0.0, 1.0)
                    mapOf(a.asset to f * a.max)
                }
            }
            val nz = raw.filterValues { it != 0.0 }
            val out: Map<String, Double> = when (val s = r.sizing) {
                is Sizing.AsWritten -> raw
                is Sizing.Equal -> nz.mapValues { (_, x) -> Math.signum(x) * s.gross / nz.size }
                is Sizing.EqualRisk -> {
                    val inv = nz.mapValues { (a, _) -> ctx.values(Signal.Volatility(a, s.days))[i].let { if (it > 0) 1 / it else Double.NaN } }
                    if (inv.values.any { it.isNaN() }) emptyMap()
                    else nz.mapValues { (a, x) -> Math.signum(x) * s.gross * inv.getValue(a) / inv.values.sum() }
                }
                is Sizing.SteadyRisk -> {
                    if (i < s.days) emptyMap() else {
                        val rets = DoubleArray(s.days) { k ->
                            val j = i - s.days + 1 + k
                            nz.entries.sumOf { (a, x) -> x * (ctx.closes(a)[j] / ctx.closes(a)[j - 1] - 1) }
                        }
                        val vol = DeskMath.std(rets) * sqrt(ctx.ppy)
                        if (!(vol > 0)) emptyMap() else nz.mapValues { (_, x) -> x * s.target / vol }
                    }
                }
                is Sizing.RiskPerTrade -> {
                    val gross = nz.values.sumOf { abs(it) }
                    val want = s.risk / (stop?.pct ?: 1.0)
                    if (gross == 0.0) emptyMap() else nz.mapValues { (_, x) -> x * want / gross }
                }
            }
            val g = out.values.sumOf { abs(it) }
            return if (g > r.maxGross) out.mapValues { it.value * r.maxGross / g } else out
        }

        for (i in 0 until n) {
            val check = checkDay(i)
            val notes = ArrayList<String>()
            var exitedUntil = false
            var changed = false

            if (active >= 0) {
                val b = r.branches[active]
                var out: String? = null
                // Data it needs gone missing: out.
                val missing = if (b.until) r.exits.filterIsInstance<Exit.Within>().firstOrNull { ctx.values(it.signal)[i].isNaN() }?.let { name(it.signal) }
                else staying(b).firstOrNull { ctx.eval(it, i) == T.UNKNOWN }?.let { tag(it) }
                if (missing != null) out = "exit_data: not enough data for $missing"
                if (out == null) {
                    held++
                    val pr = positionReturn(i)
                    best = max(best, pr)
                    for (e in r.exits) {
                        out = when (e) {
                            is Exit.Within -> ctx.values(e.signal)[i].let { v -> if (abs(v) < e.band) "exit_revert: ${name(e.signal)} back to %+.2f".format(v) else null }
                            is Exit.After -> if (held >= e.days) "exit_time: time stop after ${e.days} days" else null
                            is Exit.Stop -> {
                                val from = if (e.trailing) (1 + pr) / (1 + best) - 1 else pr
                                if (from <= -e.pct) "exit_stop: down %.1f%% from the ${if (e.trailing) "best close" else "entry"}".format(-from * 100) else null
                            }
                            is Exit.Target -> if (pr >= e.pct) "exit_target: up %.1f%%".format(pr * 100) else null
                        }
                        if (out != null) break
                    }
                }
                if (out == null && !b.until && check) {
                    staying(b).firstOrNull { ctx.eval(it, i) == T.NO }?.let { c ->
                        out = "exit_${tag(c)}: ${ctx.say(c, i, held = true)}"
                    }
                }
                if (out != null) {
                    notes += out!!
                    exitedUntil = b.until
                    active = -1
                    pick = emptyList()
                    changed = true
                }
            }

            var entered = false
            if (active < 0 && !exitedUntil && check) {
                for ((k, b) in r.branches.withIndex()) {
                    if (b.conditions.all { ctx.eval(it, i) == T.YES }) {
                        active = k
                        held = 0
                        entries++
                        entered = true
                        changed = true
                        val because = b.conditions.joinToString(", ") { ctx.say(it, i, held = false) }
                        notes += "entry_${b.tag}: " + because.ifBlank { "no conditions" }
                        break
                    }
                }
            }

            var want: Map<String, Double> = if (active >= 0) {
                if (entered || check) sized(r.branches[active], i, fresh = true) else last
            } else r.otherwise
            if (active >= 0 && entered) {
                entryAt = i
                entryW = want
                best = 0.0
            }
            // Small resizes inside the band aren't traded.
            if (!changed && r.band > 0 && assets.all { abs((want[it] ?: 0.0) - (last[it] ?: 0.0)) <= r.band }) want = last
            if (!changed && active >= 0 && (r.branches[active].action is Action.Rotate) && want.keys != last.keys) {
                notes += "rebalance: now " + want.keys.joinToString(", ").ifBlank { "cash" }
            }
            last = want

            // The brake, on the rule's own paper path (close to close, before costs).
            var scale = 1.0
            r.brake?.let { br ->
                if (i > 0) paper *= 1 + assets.indices.sumOf { k -> w[k][i - 1] * (ctx.closes(assets[k])[i] / ctx.closes(assets[k])[i - 1] - 1) }
                if (braking > 0) {
                    braking--
                    if (braking == 0) { peak = paper; notes += "brake_off: back to full size" }
                } else {
                    peak = max(peak, paper)
                    if (paper / peak - 1 <= -br.drawdown) {
                        braking = br.days
                        notes += "brake: down %.1f%% from its peak, holding %d%% for %d days".format((1 - paper / peak) * 100, (br.cut * 100).toInt(), br.days)
                    }
                }
                if (braking > 0) scale = br.cut
            }
            for (k in assets.indices) w[k][i] = (want[assets[k]] ?: 0.0) * scale
            why[i] = notes.joinToString("; ")
        }
        return Plan(Targets(dates, assets, w, why), ctx.ppy, entries)
    }
}
