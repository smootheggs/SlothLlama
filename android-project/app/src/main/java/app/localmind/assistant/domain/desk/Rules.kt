package app.localmind.assistant.domain.desk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A SlothDesk rule: a strategy written as data, from the blocks the Desk offers (signals,
 * conditions, actions, sizing, exits, an options overlay), never as code. The four strategies on
 * the shelf are rules too ([DeskPresets]); [RuleEngine] turns any rule into daily target weights
 * and runs them through the same simulator.
 *
 * How a day goes (decided at the close, traded at the next open, as before):
 *  1. A held branch is checked first: data it needs missing → out; then [exits] in order; then,
 *     for a branch held *while* its conditions hold, the first condition no longer true → out.
 *  2. With nothing held (and not just out of an *until* branch), on a check day, the first
 *     branch whose conditions are all true is entered.
 *  3. Its action (sized by [sizing], capped at [maxGross]) sets the weights; with nothing held,
 *     [otherwise] does (empty = cash). Small resizes inside [band] aren't traded.
 *  4. The [brake], when set, cuts every weight after a fall on the rule's own paper path.
 *
 * Every number has a range ([RuleCheck]); a rule outside them is refused, not clamped.
 */
@Serializable
data class Rule(
    val v: Int = 1,
    val name: String,
    val branches: List<Branch>,
    /** Weights with no branch held; empty is cash earning the T-bill. */
    val otherwise: Map<String, Double> = emptyMap(),
    val exits: List<Exit> = emptyList(),
    val sizing: Sizing = Sizing.AsWritten,
    val check: Check = Check.DAILY,
    /** Don't trade a resize smaller than this (in weight); entries and exits always trade. */
    val band: Double = 0.0,
    /** Total weight, long plus short: 1 is no leverage. */
    val maxGross: Double = 1.0,
    val brake: Brake? = null,
    val overlay: Overlay? = null,
    /** Daily carry earned (or paid, short) per asset: the id of a rate series, such as FX's rate gap. */
    val carry: Map<String, String> = emptyMap(),
    /** What each asset is, for its costs and calendar; unnamed ones are funds or stocks. */
    val markets: Map<String, Market> = emptyMap(),
    val benchmark: String
)

/**
 * One "when … then" of a rule. [tag] names its entries in the log ("entry_trend"). Held
 * [until] an exit fires, or (by default) only while its conditions stay true.
 */
@Serializable
data class Branch(
    val tag: String,
    val conditions: List<Condition>,
    val action: Action,
    val until: Boolean = false
)

@Serializable
enum class Check { DAILY, WEEKLY, MONTHLY }

@Serializable
enum class Market { FUND, STOCK, FX, CRYPTO }

@Serializable
enum class Op {
    ABOVE, BELOW, AT_LEAST, AT_MOST, CROSSES_ABOVE, CROSSES_BELOW;

    val crossing: Boolean get() = this == CROSSES_ABOVE || this == CROSSES_BELOW
}

// --- signals: a number for each day, from data known at that day's close ---

@Serializable
sealed class Signal {
    /** The traded assets it reads prices of ("*" in a rotation's score is each asset in turn). */
    abstract val assets: List<String>

    @Serializable @SerialName("price")
    data class Price(val asset: String) : Signal() {
        override val assets get() = listOf(asset)
    }

    /** The N-day average of closes; [exp]: exponential (span N) instead of simple. */
    @Serializable @SerialName("average")
    data class Average(val asset: String, val days: Int, val exp: Boolean = false) : Signal() {
        override val assets get() = listOf(asset)
    }

    /** The return over [days], ending [skip] days ago (12-1 momentum: 252 and 21). */
    @Serializable @SerialName("momentum")
    data class Momentum(val asset: String, val days: Int, val skip: Int = 0) : Signal() {
        override val assets get() = listOf(asset)
    }

    /** The highest close of the [days] before today (a breakout is a close above it). */
    @Serializable @SerialName("high")
    data class High(val asset: String, val days: Int) : Signal() {
        override val assets get() = listOf(asset)
    }

    @Serializable @SerialName("low")
    data class Low(val asset: String, val days: Int) : Signal() {
        override val assets get() = listOf(asset)
    }

    /** How far below its highest close of [days] (today included) it is: 0 at a high, −0.2 when 20% off. */
    @Serializable @SerialName("off_high")
    data class OffHigh(val asset: String, val days: Int) : Signal() {
        override val assets get() = listOf(asset)
    }

    /**
     * How stretched, in standard deviations over [days]: the log price, or with [asset2] the
     * log ratio of the two (gold against silver, one stock against another).
     */
    @Serializable @SerialName("stretch")
    data class Stretch(val asset: String, val days: Int, val asset2: String? = null) : Signal() {
        override val assets get() = listOfNotNull(asset, asset2)
    }

    @Serializable @SerialName("ratio")
    data class Ratio(val asset: String, val asset2: String) : Signal() {
        override val assets get() = listOf(asset, asset2)
    }

    /** Wilder's relative strength index, 0 to 100. */
    @Serializable @SerialName("rsi")
    data class Rsi(val asset: String, val days: Int = 14) : Signal() {
        override val assets get() = listOf(asset)
    }

    /** Realised volatility over [days], a year's worth (0.16 is 16%). */
    @Serializable @SerialName("volatility")
    data class Volatility(val asset: String, val days: Int = 20) : Signal() {
        override val assets get() = listOf(asset)
    }

    /** Any other series (a rate, a volatility index, a macro series) as known at the close. */
    @Serializable @SerialName("series")
    data class Series(val id: String) : Signal() {
        override val assets get() = emptyList<String>()
    }

    /** A series' change over [days] trading days (in its own units: rate points, index points). */
    @Serializable @SerialName("change")
    data class Change(val id: String, val days: Int) : Signal() {
        override val assets get() = emptyList<String>()
    }

    /** One series minus another, each as published at the time (a rate gap). */
    @Serializable @SerialName("gap")
    data class Gap(val id: String, val id2: String) : Signal() {
        override val assets get() = emptyList<String>()
    }
}

/** What a signal is compared with. */
@Serializable
sealed class Against {
    @Serializable @SerialName("value")
    data class Value(val x: Double) : Against()

    @Serializable @SerialName("signal")
    data class Of(val signal: Signal) : Against()

    /**
     * The signal's own [q] percentile over the last [days] (from [minDays] of history). With too
     * little history it doesn't block: the comparison holds.
     */
    @Serializable @SerialName("percentile")
    data class Percentile(val q: Double, val days: Int, val minDays: Int = days / 2) : Against()
}

@Serializable
sealed class Condition {
    /** Checked only before entering; once in, it no longer matters (a calm-markets filter). */
    abstract val enterOnly: Boolean

    /** [left] [op] [right], true [days] days in a row. */
    @Serializable @SerialName("compare")
    data class Compare(
        val left: Signal,
        val op: Op,
        val right: Against,
        val days: Int = 1,
        override val enterOnly: Boolean = false,
        /** Names its exits in the log ("exit_trend"); worked out from the signals when blank. */
        val tag: String = ""
    ) : Condition()

    /** True in these months (1–12), on these weekdays (1 Monday – 7 Sunday), and within [around] days of [dates]. */
    @Serializable @SerialName("calendar")
    data class Calendar(
        val months: List<Int> = emptyList(),
        val weekdays: List<Int> = emptyList(),
        val dates: List<String> = emptyList(),
        val around: Int = 0,
        override val enterOnly: Boolean = false
    ) : Condition()

    @Serializable @SerialName("any")
    data class AnyOf(val of: List<Condition>, override val enterOnly: Boolean = false) : Condition()

    @Serializable @SerialName("not")
    data class Not(val of: Condition, override val enterOnly: Boolean = false) : Condition()
}

@Serializable
sealed class Action {
    /** Fixed weights, long (+) or short (−): a single asset, a mix, or a pair. */
    @Serializable @SerialName("hold")
    data class Hold(val weights: Map<String, Double>) : Action()

    /**
     * The [top] best of [assets] by [score] (a signal with asset "*"), re-picked on check days;
     * only those scoring above [minScore], when set (the rest of the weight stays in cash).
     */
    @Serializable @SerialName("rotate")
    data class Rotate(val assets: List<String>, val score: Signal, val top: Int, val minScore: Double? = null, val weight: Double = 1.0) : Action()

    /** Size with a signal: 0 at [from], [max] at [to] (either may be the larger), clamped. */
    @Serializable @SerialName("scale")
    data class Scale(val asset: String, val signal: Signal, val from: Double, val to: Double, val max: Double = 1.0) : Action()
}

@Serializable
sealed class Sizing {
    /** The weights as the action gives them. */
    @Serializable @SerialName("as_written")
    data object AsWritten : Sizing()

    /** The same money in each asset of the action (its signs kept), [gross] in all. */
    @Serializable @SerialName("equal")
    data class Equal(val gross: Double = 1.0) : Sizing()

    /** Less in what moves more: weights in proportion to 1 / its [days]-day volatility. */
    @Serializable @SerialName("equal_risk")
    data class EqualRisk(val days: Int = 60, val gross: Double = 1.0) : Sizing()

    /** Scaled so the whole moves about [target] a year (0.10 is 10%), measured over [days]. */
    @Serializable @SerialName("steady_risk")
    data class SteadyRisk(val target: Double = 0.10, val days: Int = 60) : Sizing()

    /** Sized to lose [risk] of the account (0.01 is 1%) at the rule's stop. Needs a stop exit. */
    @Serializable @SerialName("risk_per_trade")
    data class RiskPerTrade(val risk: Double = 0.01) : Sizing()
}

@Serializable
sealed class Exit {
    /** Back to normal: out once |signal| is within [band] (a stretch back near zero). */
    @Serializable @SerialName("within")
    data class Within(val signal: Signal, val band: Double) : Exit()

    /** A time stop: out after [days] trading days held. */
    @Serializable @SerialName("after")
    data class After(val days: Int) : Exit()

    /** Out after losing [pct] (0.05 is 5%) from the entry, or, [trailing], from the best close since. */
    @Serializable @SerialName("stop")
    data class Stop(val pct: Double, val trailing: Boolean = false) : Exit()

    /** Out after gaining [pct]. */
    @Serializable @SerialName("target")
    data class Target(val pct: Double) : Exit()
}

/** After the rule's own paper path falls [drawdown] from its peak, hold [cut] of every weight for [days]. */
@Serializable
data class Brake(val drawdown: Double = 0.15, val cut: Double = 0.5, val days: Int = 20)

@Serializable
sealed class Overlay {
    /** Sell a call against the holding, again each time one expires; modelled, not quoted. */
    @Serializable @SerialName("covered_call")
    data class CoveredCall(
        val asset: String,
        val vol: String,
        val delta: Double = 0.30,
        val dte: Int = 30,
        val skewVolPts: Double = 0.0,
        val spreadPct: Double = 3.0,
        val stressSpreadPct: Double = 8.0,
        val feePerContract: Double = 0.65,
        val strikeStep: Double = 1.0
    ) : Overlay()
}

/** Reading, writing and checking rules: only what [RuleCheck] passes is ever run. */
object RuleFiles {
    const val MAX_BYTES = 64 * 1024
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = false; classDiscriminator = "kind" }

    fun encode(r: Rule): String = json.encodeToString(Rule.serializer(), r)

    /** A rule from its saved text, or the reason it can't be read or isn't allowed. */
    fun parse(text: String): Result<Rule> = runCatching {
        require(text.length <= MAX_BYTES) { "This rule is too long to read" }
        val r = json.decodeFromString(Rule.serializer(), text)
        require(r.v == 1) { "A newer rule than this version of SlothDesk reads (v${r.v})" }
        val problems = RuleCheck.problems(r)
        require(problems.isEmpty()) { problems.first() }
        r
    }
}

/** The limits every rule must keep; a rule outside them isn't run. */
object RuleCheck {
    const val MAX_BRANCHES = 3
    const val MAX_CONDITIONS = 4
    const val MAX_LIST = 20
    const val MAX_DAYS = 1000
    const val MAX_GROSS = 3.0
    private val TICKER = Regex("^[A-Z0-9_.^-]{1,16}$")
    private val SERIES = Regex("^[A-Za-z0-9_.^:-]{1,32}$")

    /** What's wrong with a signal on its own (in a rotation's score, "*" is each asset). */
    fun signalProblems(s: Signal, rotation: Boolean = false): List<String> {
        val probe = if (rotation) {
            Rule(name = "probe", benchmark = "AA", branches = listOf(Branch("probe", emptyList(), Action.Rotate(listOf("AA", "BB"), s, 1))))
        } else {
            Rule(name = "probe", benchmark = "AA", branches = listOf(Branch("probe",
                listOf(Condition.Compare(s, Op.ABOVE, Against.Value(0.0))), Action.Hold(mapOf("AA" to 1.0)))))
        }
        return problems(probe)
    }

    fun isTicker(a: String): Boolean = TICKER.matches(a)
    fun isSeries(id: String): Boolean = SERIES.matches(id)

    fun problems(r: Rule): List<String> {
        val out = ArrayList<String>()
        fun need(ok: Boolean, why: String) { if (!ok) out += why }
        fun days(n: Int, what: String) = need(n in 1..MAX_DAYS, "$what must be 1 to $MAX_DAYS days")
        fun asset(a: String, rotation: Boolean = false) =
            need(TICKER.matches(a) || (rotation && a == "*"), "\"${a.take(20)}\" isn't a ticker the Desk reads")
        fun signal(s: Signal, rotation: Boolean = false) {
            s.assets.forEach { asset(it, rotation) }
            when (s) {
                is Signal.Average -> days(s.days, "An average")
                is Signal.Momentum -> { days(s.days, "Momentum"); need(s.skip in 0..MAX_DAYS, "Momentum's skip is out of range") }
                is Signal.High -> days(s.days, "A high")
                is Signal.Low -> days(s.days, "A low")
                is Signal.OffHigh -> days(s.days, "Off its high")
                is Signal.Stretch -> need(s.days in 5..MAX_DAYS, "A stretch needs 5 to $MAX_DAYS days")
                is Signal.Rsi -> need(s.days in 2..250, "RSI takes 2 to 250 days")
                is Signal.Volatility -> need(s.days in 5..MAX_DAYS, "Volatility needs 5 to $MAX_DAYS days")
                is Signal.Series -> need(SERIES.matches(s.id), "Unknown series")
                is Signal.Change -> { need(SERIES.matches(s.id), "Unknown series"); days(s.days, "A change") }
                is Signal.Gap -> need(SERIES.matches(s.id) && SERIES.matches(s.id2), "Unknown series")
                is Signal.Price, is Signal.Ratio -> Unit
            }
        }
        fun condition(c: Condition, depth: Int = 0): Int {
            need(depth <= 2, "Conditions nest too deep")
            return when (c) {
                is Condition.Compare -> {
                    signal(c.left)
                    when (val a = c.right) {
                        is Against.Value -> need(a.x.isFinite(), "A value isn't a number")
                        is Against.Of -> signal(a.signal)
                        is Against.Percentile -> {
                            need(a.q in 0.01..0.99, "A percentile must be 1 to 99")
                            days(a.days, "A percentile's history")
                            need(a.minDays in 1..a.days, "A percentile's minimum history is out of range")
                        }
                    }
                    need(c.days in 1..250, "\"In a row\" must be 1 to 250 days")
                    need(!(c.op.crossing && c.days > 1), "A crossing happens on one day; it can't be in a row")
                    1
                }
                is Condition.Calendar -> {
                    need(c.months.all { it in 1..12 } && c.weekdays.all { it in 1..7 }, "A month or weekday is out of range")
                    need(c.dates.size <= 50 && c.dates.all { runCatching { java.time.LocalDate.parse(it) }.isSuccess }, "A date isn't a date")
                    need(c.around in 0..30, "\"Around\" must be 0 to 30 days")
                    1
                }
                is Condition.AnyOf -> c.of.sumOf { condition(it, depth + 1) }
                is Condition.Not -> condition(c.of, depth + 1)
            }
        }

        need(r.name.isNotBlank() && r.name.length <= 80, "A rule needs a name (up to 80 letters)")
        need(r.branches.size in 1..MAX_BRANCHES, "A rule has 1 to $MAX_BRANCHES branches")
        asset(r.benchmark)
        need(r.maxGross > 0 && r.maxGross <= MAX_GROSS, "Total weight must be above 0 and at most ${MAX_GROSS.toInt()}x")
        need(r.band in 0.0..0.5, "The no-trade band must be 0 to 50%")
        r.otherwise.forEach { (a, w) -> asset(a); need(w.isFinite() && kotlin.math.abs(w) <= r.maxGross, "A weight is out of range") }
        for (b in r.branches) {
            need(Regex("^[a-z][a-z0-9_]{0,23}$").matches(b.tag), "A branch's name must be short lowercase words")
            val leaves = b.conditions.sumOf { condition(it) }
            need(leaves <= MAX_CONDITIONS, "Up to $MAX_CONDITIONS conditions in a branch")
            need(b.conditions.isEmpty() || b.until || RuleEngine.staying(b).isNotEmpty() || r.exits.isNotEmpty(),
                "A branch entered only on a crossing or a filter needs an exit to leave it")
            need(!b.until || r.exits.isNotEmpty(), "A branch held until an exit needs an exit")
            when (val a = b.action) {
                is Action.Hold -> {
                    need(a.weights.isNotEmpty() && a.weights.size <= MAX_LIST, "Hold 1 to $MAX_LIST assets")
                    a.weights.forEach { (k, w) -> asset(k); need(w.isFinite() && kotlin.math.abs(w) <= r.maxGross, "A weight is out of range") }
                    need(a.weights.values.sumOf { kotlin.math.abs(it) } <= r.maxGross + 1e-9, "The weights add up to more than the total allowed")
                }
                is Action.Rotate -> {
                    need(a.assets.size in 2..MAX_LIST, "Rotate among 2 to $MAX_LIST assets")
                    a.assets.forEach { asset(it) }
                    need(a.top in 1 until a.assets.size, "Hold fewer of the list than it has")
                    need(a.score.assets.contains("*"), "A rotation's score must read each asset (\"*\")")
                    signal(a.score, rotation = true)
                    need(a.weight > 0 && a.weight <= r.maxGross, "A rotation's weight is out of range")
                    need(a.minScore?.isFinite() ?: true, "A minimum score isn't a number")
                }
                is Action.Scale -> {
                    asset(a.asset); signal(a.signal)
                    need(a.from.isFinite() && a.to.isFinite() && a.from != a.to, "A scale needs two different ends")
                    need(kotlin.math.abs(a.max) in 0.01..r.maxGross, "A scale's size is out of range")
                }
            }
        }
        for (e in r.exits) when (e) {
            is Exit.Within -> { signal(e.signal); need(e.band > 0 && e.band <= 10, "\"Back within\" is out of range") }
            is Exit.After -> days(e.days, "A time stop")
            is Exit.Stop -> need(e.pct in 0.005..0.9, "A stop must be 0.5% to 90%")
            is Exit.Target -> need(e.pct in 0.005..10.0, "A profit target must be 0.5% to 1000%")
        }
        when (val s = r.sizing) {
            is Sizing.AsWritten -> Unit
            is Sizing.Equal -> need(s.gross > 0 && s.gross <= r.maxGross, "Equal sizing's total is out of range")
            is Sizing.EqualRisk -> { need(s.days in 5..MAX_DAYS, "Equal risk needs 5 to $MAX_DAYS days"); need(s.gross > 0 && s.gross <= r.maxGross, "Equal risk's total is out of range") }
            is Sizing.SteadyRisk -> { need(s.target in 0.01..1.0, "A steady risk must be 1% to 100% a year"); need(s.days in 5..MAX_DAYS, "Steady risk needs 5 to $MAX_DAYS days") }
            is Sizing.RiskPerTrade -> {
                need(s.risk in 0.001..0.1, "Risk per trade must be 0.1% to 10%")
                need(r.exits.any { it is Exit.Stop }, "Risk per trade needs a stop")
            }
        }
        r.brake?.let { b ->
            need(b.drawdown in 0.02..0.9 && b.cut in 0.0..0.99 && b.days in 1..250, "The drawdown brake is out of range")
        }
        r.carry.forEach { (a, id) -> asset(a); need(SERIES.matches(id), "Unknown carry series") }
        r.markets.keys.forEach { asset(it) }
        when (val o = r.overlay) {
            null -> Unit
            is Overlay.CoveredCall -> {
                asset(o.asset)
                need(SERIES.matches(o.vol), "Unknown volatility series")
                need(o.delta in 0.05..0.6 && o.dte in 7..120 && o.strikeStep > 0 && o.spreadPct in 0.0..50.0 &&
                    o.stressSpreadPct in 0.0..50.0 && o.feePerContract in 0.0..20.0 && o.skewVolPts in -20.0..20.0,
                    "The covered call's settings are out of range")
                need(r.branches.size == 1 && r.branches[0].action == Action.Hold(mapOf(o.asset to 1.0)) && r.branches[0].conditions.isEmpty(),
                    "A covered call holds its asset outright")
            }
        }
        return out.distinct()
    }
}
