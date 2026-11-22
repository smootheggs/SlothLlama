package app.localmind.assistant.domain.desk

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A rule in words: the pieces of the editor's sentences ("GLD" "closes above" "its 200-day
 * average"), and the whole rule as plain text for a chat to explain. Words only; nothing here
 * changes a rule.
 */
object RuleWords {

    /** Percent signals are kept as fractions (0.05) and shown as 5%; the rest as they are. */
    enum class Unit { PLAIN, PERCENT }

    fun unit(s: Signal): Unit = when (s) {
        is Signal.Momentum, is Signal.OffHigh, is Signal.Volatility, is Signal.Series, is Signal.Change, is Signal.Gap -> Unit.PERCENT
        else -> Unit.PLAIN
    }

    private val seriesNames = mapOf(
        "GVZ" to "gold volatility (GVZ)",
        "VIX" to "stock volatility (VIX)",
        "DTB3" to "the US 3-month rate",
        "JP3M" to "Japan's 3-month rate",
        "DEXJPUS" to "the yen rate (DEXJPUS)"
    )

    fun seriesName(id: String): String = seriesNames[id] ?: id

    private fun who(a: String) = if (a == "*") "each one" else a
    private fun whose(a: String) = if (a == "*") "each one's" else "$a's"

    /** A signal as the subject of a sentence: "GLD", "GLD's 120-day return". */
    fun signal(s: Signal): String = when (s) {
        is Signal.Price -> who(s.asset)
        is Signal.Average -> "${whose(s.asset)} ${s.days}-day ${if (s.exp) "exponential " else ""}average"
        is Signal.Momentum -> "${whose(s.asset)} ${s.days}-day return" + if (s.skip > 0) ", skipping the last ${s.skip}" else ""
        is Signal.High -> "${whose(s.asset)} ${s.days}-day high"
        is Signal.Low -> "${whose(s.asset)} ${s.days}-day low"
        is Signal.OffHigh -> "how far ${who(s.asset)} is off its ${s.days}-day high"
        is Signal.Stretch -> "how stretched ${who(s.asset)}${s.asset2?.let { "/$it" } ?: ""} is over ${s.days} days"
        is Signal.Ratio -> "${who(s.asset)}/${s.asset2}"
        is Signal.Rsi -> "${whose(s.asset)} RSI (${s.days} days)"
        is Signal.Volatility -> "${whose(s.asset)} ${s.days}-day volatility"
        is Signal.Series -> seriesName(s.id).replaceFirstChar { it.uppercase() }
        is Signal.Change -> "the ${s.days}-day change in ${seriesName(s.id)}"
        is Signal.Gap -> "${seriesName(s.id)} minus ${seriesName(s.id2)}"
    }

    /** A signal when it's what something is compared with: "its 200-day average" when it reads the same asset. */
    fun signalAgainst(s: Signal, left: Signal): String {
        val same = s.assets.size == 1 && left.assets.size == 1 && s.assets[0] == left.assets[0]
        if (!same) return signal(s)
        return when (s) {
            is Signal.Average -> "its ${s.days}-day ${if (s.exp) "exponential " else ""}average"
            is Signal.High -> "its ${s.days}-day high"
            is Signal.Low -> "its ${s.days}-day low"
            else -> signal(s)
        }
    }

    fun op(op: Op, left: Signal): String {
        val price = left is Signal.Price
        return when (op) {
            Op.ABOVE -> if (price) "closes above" else "is above"
            Op.BELOW -> if (price) "closes below" else "is below"
            Op.AT_LEAST -> "is at least"
            Op.AT_MOST -> "is at most"
            Op.CROSSES_ABOVE -> "crosses above"
            Op.CROSSES_BELOW -> "crosses below"
        }
    }

    fun value(x: Double, u: Unit): String = when (u) {
        Unit.PERCENT -> num(x * 100) + "%"
        Unit.PLAIN -> num(x)
    }

    fun against(a: Against, left: Signal): String = when (a) {
        is Against.Value -> value(a.x, unit(left))
        is Against.Of -> signalAgainst(a.signal, left)
        is Against.Percentile -> "its ${ordinal((a.q * 100).roundToInt())} percentile of the last ${a.days} days"
    }

    fun condition(c: Condition): String = when (c) {
        is Condition.Compare -> buildString {
            append(signal(c.left)).append(' ').append(op(c.op, c.left)).append(' ').append(against(c.right, c.left))
            if (c.days > 1) append(", ${c.days} days in a row")
            if (c.enterOnly) append(" (checked only before entering)")
        }
        is Condition.Calendar -> calendar(c)
        is Condition.AnyOf -> "any of: " + c.of.joinToString("; ") { condition(it) }
        is Condition.Not -> "not (" + condition(c.of) + ")"
    }

    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun month(m: Int): String = monthNames.getOrElse(m - 1) { "?" }
    fun weekday(d: Int): String = dayNames.getOrElse(d - 1) { "?" }

    fun calendar(c: Condition.Calendar): String {
        val parts = ArrayList<String>()
        if (c.months.isNotEmpty()) parts += "in " + c.months.sorted().joinToString(", ") { month(it) }
        if (c.weekdays.isNotEmpty()) parts += "on " + c.weekdays.sorted().joinToString(", ") { weekday(it) }
        if (c.dates.isNotEmpty()) parts += (if (c.around > 0) "within ${c.around} days of " else "on ") + c.dates.joinToString(", ")
        return if (parts.isEmpty()) "any day" else "it's " + parts.joinToString(" and ")
    }

    fun weight(w: Double): String = "${if (w < 0) "short " else ""}${num(abs(w) * 100)}%"

    fun weights(w: Map<String, Double>): String =
        if (w.isEmpty()) "cash (T-bills)" else w.entries.joinToString(", ") { (a, x) -> "$a ${weight(x)}" }

    fun action(a: Action): String = when (a) {
        is Action.Hold -> "hold " + weights(a.weights)
        is Action.Rotate -> "hold the best ${a.top} of ${a.assets.joinToString(", ")} by ${signal(a.score)}" +
            (a.minScore?.let { " (only those above ${value(it, unit(a.score))})" } ?: "") +
            if (abs(a.weight - 1.0) > 1e-9) ", ${num(a.weight * 100)}% in all" else ""
        is Action.Scale -> "hold ${a.asset}, from none at ${value(a.from, unit(a.signal))} to ${weight(a.max)} at " +
            "${value(a.to, unit(a.signal))} of ${signal(a.signal)}"
    }

    fun exit(e: Exit): String = when (e) {
        is Exit.Within -> "${signal(e.signal)} is back within ${value(e.band, unit(e.signal))} of zero"
        is Exit.After -> "after ${e.days} days"
        is Exit.Stop -> "${if (e.trailing) "a trailing stop" else "a stop"} ${num(e.pct * 100)}% " +
            if (e.trailing) "below the best close" else "below the entry"
        is Exit.Target -> "a profit of ${num(e.pct * 100)}%"
    }

    fun sizing(s: Sizing): String = when (s) {
        is Sizing.AsWritten -> "as written"
        is Sizing.Equal -> "the same money in each" + gross(s.gross)
        is Sizing.EqualRisk -> "equal risk over ${s.days} days" + gross(s.gross)
        is Sizing.SteadyRisk -> "aim for ${num(s.target * 100)}% volatility a year"
        is Sizing.RiskPerTrade -> "risk ${num(s.risk * 100)}% at the stop"
    }

    private fun gross(g: Double) = if (abs(g - 1.0) < 1e-9) "" else ", ${num(g * 100)}% in all"

    fun check(c: Check): String = when (c) {
        Check.DAILY -> "every day"
        Check.WEEKLY -> "each week"
        Check.MONTHLY -> "each month"
    }

    fun market(m: Market): String = when (m) {
        Market.FUND -> "a fund"
        Market.STOCK -> "a stock"
        Market.FX -> "a currency pair"
        Market.CRYPTO -> "crypto"
    }

    fun brake(b: Brake): String =
        "after a ${num(b.drawdown * 100)}% fall from its peak, hold ${num(b.cut * 100)}% of the size for ${b.days} days"

    fun overlay(o: Overlay): String = when (o) {
        is Overlay.CoveredCall -> "sell a ${num(o.delta * 100)}-delta call about ${o.dte} days out on ${o.asset}, " +
            "priced from ${seriesName(o.vol)} (modelled, not quotes)"
    }

    /** The whole rule, a line per part, for a chat or a summary. */
    fun rule(r: Rule): String = buildString {
        appendLine("Rule: ${r.name}")
        r.branches.forEachIndexed { i, b ->
            val head = if (i == 0) "When" else "Else, when"
            val conds = if (b.conditions.isEmpty()) "always" else b.conditions.joinToString(" and ") { condition(it) }
            appendLine("$head $conds: ${action(b.action)}" + if (b.until) " (held until an exit)" else "")
        }
        appendLine("Otherwise: ${weights(r.otherwise)}")
        if (r.exits.isNotEmpty()) appendLine("Leave on: " + r.exits.joinToString("; ") { exit(it) })
        appendLine("Sizing: ${sizing(r.sizing)}; total up to ${num(r.maxGross)}x; checked ${check(r.check)}" +
            if (r.band > 0) "; resizes under ${num(r.band * 100)}% aren't traded" else "")
        r.brake?.let { appendLine("Brake: ${brake(it)}") }
        r.overlay?.let { appendLine("Options: ${overlay(it)}") }
        if (r.carry.isNotEmpty()) appendLine("Carry: " + r.carry.entries.joinToString(", ") { (a, id) -> "$a earns $id" })
        if (r.markets.isNotEmpty()) appendLine("Markets: " + r.markets.entries.joinToString(", ") { (a, m) -> "$a is ${market(m)}" })
        append("Compared with holding ${r.benchmark}")
    }.trim()

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /** Up to four significant places, no trailing zeros: 200, 0.5, 2.25. */
    fun num(x: Double): String {
        if (x.isNaN()) return "–"
        if (x == x.roundToInt().toDouble() && abs(x) < 1e9) return x.roundToInt().toString()
        return String.format(Locale.US, "%.4f", x).trimEnd('0').trimEnd('.')
    }
}
