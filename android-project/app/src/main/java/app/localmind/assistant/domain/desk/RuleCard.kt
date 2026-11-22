package app.localmind.assistant.domain.desk

import java.util.Locale
import kotlin.math.abs

/**
 * A rule as a short card of labelled lines (1.49): what the Trader writes when it drafts a rule
 * for an idea, and what the app writes to show it the shape. For example:
 *
 *     Rule: Gold when real rates fall
 *     When: DFII10 63-day change below 0
 *     Then: hold GLD 100%
 *     Otherwise: cash
 *     Exit: after 60 days
 *
 * A card is read as data: only these forms are understood, every part is checked against the
 * rule's limits ([RuleCheck]), and a card with any line that can't be read gives no rule at all,
 * with the lines it couldn't read, rather than a rule that silently differs from what was
 * written. Nothing in a card is run; it only fills in the rule's form.
 */
object RuleCard {

    /** The longest reply looked at, and the most lines read from it. */
    const val MAX_CHARS = 12_000
    private const val MAX_LINES = 60

    /** Series that are never prices, for telling "GVZ above 0.2" from "GLD above 240". */
    val KNOWN_SERIES = setOf("GVZ", "VIX", "DTB3", "JP3M", "DEXJPUS", "DFII10", "DGS10", "DGS2", "T10Y2Y", "FEDFUNDS", "T10YIE")

    class Read(val rule: Rule?, val problems: List<String>, val name: String)

    /** True when [text] looks like a rule card: a Rule line and at least a When or Then line. */
    fun has(text: String): Boolean {
        val labels = lines(text).map { it.first }
        return "rule" in labels && ("when" in labels || "then" in labels)
    }

    // --- writing ---

    /** The card for [r], or null when it uses a block a card can't say (the calendar, any-of, an overlay…). */
    fun write(r: Rule): String? {
        if (r.overlay != null || r.brake != null || r.band > 0) return null
        val out = ArrayList<String>()
        out += "Rule: ${r.name}"
        r.branches.forEachIndexed { i, b ->
            if (b.conditions.isEmpty()) {
                if (i > 0) return null
            } else {
                b.conditions.forEachIndexed { j, c ->
                    val words = condition(c) ?: return null
                    out += (if (j > 0) "And" else if (i == 0) "When" else "Else when") + ": " + words
                }
            }
            out += "Then: " + (action(b.action) ?: return null)
            if (b.until) out += "Held: until an exit"
        }
        out += "Otherwise: " + weights(r.otherwise)
        r.exits.forEach { out += "Exit: " + exit(it) }
        if (r.check != Check.DAILY) out += "Check: " + r.check.name.lowercase()
        when (val s = r.sizing) {
            is Sizing.AsWritten -> Unit
            is Sizing.Equal -> if (abs(s.gross - 1.0) < 1e-9) out += "Size: equal" else return null
            is Sizing.EqualRisk -> if (abs(s.gross - 1.0) < 1e-9) out += "Size: equal risk over ${s.days} days" else return null
            is Sizing.SteadyRisk -> out += "Size: steady risk ${num(s.target * 100)}% over ${s.days} days"
            is Sizing.RiskPerTrade -> out += "Size: risk ${num(s.risk * 100)}% per trade"
        }
        if (abs(r.maxGross - 1.0) > 1e-9) return null
        r.markets.forEach { (a, m) -> out += "Market: $a ${m.name.lowercase()}" }
        r.carry.forEach { (a, id) ->
            out += "Carry: $a earns " + if (id.startsWith("gap:")) id.removePrefix("gap:").replace(":", " minus ") else id
        }
        out += "Compare with: ${r.benchmark}"
        return out.joinToString("\n")
    }

    private fun condition(c: Condition): String? {
        if (c !is Condition.Compare) return null
        val right = when (val a = c.right) {
            is Against.Value -> value(a.x, c.left)
            is Against.Of -> against(a.signal, c.left)
            is Against.Percentile -> {
                // A card says whole percentiles with the usual minimum history.
                if (a.minDays != a.days / 2 || abs(Math.round(a.q * 100) / 100.0 - a.q) > 1e-9) return null
                "its ${RuleWords.ordinal(Math.round(a.q * 100).toInt())} percentile over ${a.days} days"
            }
        }
        return signal(c.left) + " " + op(c.op) + " " + right +
            (if (c.days > 1) " for ${c.days} days" else "") + if (c.enterOnly) " (to enter)" else ""
    }

    private fun op(o: Op) = when (o) {
        Op.ABOVE -> "above"; Op.BELOW -> "below"; Op.AT_LEAST -> "at least"; Op.AT_MOST -> "at most"
        Op.CROSSES_ABOVE -> "crosses above"; Op.CROSSES_BELOW -> "crosses below"
    }

    /** A signal in card words: "GLD 200-day average", "GLD/SLV 60-day stretch", "DTB3 minus JP3M". */
    fun signal(s: Signal): String = when (s) {
        is Signal.Price -> s.asset
        is Signal.Average -> "${s.asset} ${s.days}-day ${if (s.exp) "exponential " else ""}average"
        is Signal.Momentum -> "${s.asset} ${s.days}-day return" + if (s.skip > 0) " skipping ${s.skip}" else ""
        is Signal.High -> "${s.asset} ${s.days}-day high"
        is Signal.Low -> "${s.asset} ${s.days}-day low"
        is Signal.OffHigh -> "${s.asset} off its ${s.days}-day high"
        is Signal.Stretch -> s.asset + (s.asset2?.let { "/$it" } ?: "") + " ${s.days}-day stretch"
        is Signal.Ratio -> "${s.asset}/${s.asset2}"
        is Signal.Rsi -> "${s.asset} ${s.days}-day RSI"
        is Signal.Volatility -> "${s.asset} ${s.days}-day volatility"
        is Signal.Series -> s.id
        is Signal.Change -> "${s.id} ${s.days}-day change"
        is Signal.Atr -> "${s.asset} ${s.days}-day ATR"
        is Signal.Gap -> "${s.id} minus ${s.id2}"
    }

    private fun against(s: Signal, left: Signal): String {
        val l = left.assets.singleOrNull()
        val a = s.assets.singleOrNull()
        val words = signal(s)
        return if (l != null && a == l && s !is Signal.Price && words.startsWith("$a ")) "its " + words.removePrefix("$a ") else words
    }

    private fun value(x: Double, left: Signal): String =
        if (RuleWords.unit(left) == RuleWords.Unit.PERCENT) num(x * 100) + "%" else num(x)

    private fun action(a: Action): String? = when (a) {
        is Action.Hold -> if (a.weights.isEmpty()) "cash" else "hold " + weights(a.weights)
        is Action.Rotate -> {
            if (abs(a.weight - 1.0) > 1e-9 || a.minScore != null) null
            else "hold the best ${a.top} of ${a.assets.joinToString(", ")} by " + signal(a.score).removePrefix("* ")
        }
        is Action.Scale -> null
    }

    private fun weights(w: Map<String, Double>): String =
        if (w.isEmpty()) "cash" else w.entries.joinToString(", ") { (a, x) -> "$a ${num(x * 100)}%" }

    private fun exit(e: Exit): String = when (e) {
        is Exit.Within -> "${signal(e.signal)} back within ${value(e.band, e.signal)}"
        is Exit.After -> "after ${e.days} days"
        is Exit.Stop -> (if (e.trailing) "trailing stop " else "stop ") +
            if (e.byAtr) "${num(e.atr)} ATR" + (if (e.atrDays != 14) " over ${e.atrDays} days" else "") else num(e.pct * 100) + "%"
        is Exit.Target -> "target ${num(e.pct * 100)}%"
    }

    private fun num(x: Double): String = RuleWords.num(x)

    // --- reading ---

    /** The labelled lines of a card: (label, value), with bullets and bold taken off. */
    private fun lines(text: String): List<Pair<String, String>> =
        text.take(MAX_CHARS).lines().asSequence()
            .map { it.trim().trimStart('-', '*', '•', '>', ' ').replace("**", "").replace("`", "").trim() }
            .mapNotNull { line ->
                val k = line.indexOf(':')
                if (k <= 0 || k > 16) null else line.substring(0, k).trim().lowercase(Locale.ROOT) to line.substring(k + 1).trim()
            }
            .filter { it.first in LABELS }
            .take(MAX_LINES)
            .toList()

    private val LABELS = setOf("rule", "when", "and", "else when", "then", "held", "otherwise", "exit", "check", "size",
        "market", "carry", "compare with", "trades")

    /**
     * Reads a card. [prices] are the tickers there are prices for: in a condition, a bare name
     * that isn't one of them (or is a known series) reads as a series (a rate, an index).
     */
    fun parse(text: String, prices: Set<String> = emptySet()): Read {
        val problems = ArrayList<String>()
        val ls = lines(text)
        val name = ls.firstOrNull { it.first == "rule" }?.second?.take(80)?.ifBlank { null } ?: "The Trader's draft"
        fun isSeries(id: String) = id in KNOWN_SERIES || (prices.isNotEmpty() && id !in prices && !id.contains('/'))

        class B(val conditions: MutableList<Condition> = ArrayList(), var action: Action? = null, var until: Boolean = false)
        val branches = ArrayList<B>()
        var otherwise: Map<String, Double> = emptyMap()
        val exits = ArrayList<Exit>()
        var check = Check.DAILY
        var sizing: Sizing = Sizing.AsWritten
        val markets = LinkedHashMap<String, Market>()
        val carry = LinkedHashMap<String, String>()
        var bench: String? = null

        for ((label, v) in ls) {
            fun bad(why: String) { problems += "\"$label: $v\": $why" }
            when (label) {
                "rule", "trades" -> Unit
                "when", "else when" -> {
                    if (label == "when" && branches.isNotEmpty() && branches.last().action == null) {
                        // A second "When" before any "Then" adds to the same branch.
                    } else branches += B()
                    readCondition(v, ::isSeries)?.let { branches.last().conditions += it } ?: bad("not a condition this card can say")
                }
                "and" -> {
                    if (branches.isEmpty() || branches.last().action != null) bad("an And needs a When before it")
                    else readCondition(v, ::isSeries)?.let { branches.last().conditions += it } ?: bad("not a condition this card can say")
                }
                "then" -> {
                    if (branches.isEmpty() || branches.last().action != null) branches += B()
                    readAction(v)?.let { branches.last().action = it } ?: bad("not an action this card can say")
                }
                "held" -> {
                    if (branches.isEmpty()) bad("Held needs a branch") else when {
                        "until" in v.lowercase(Locale.ROOT) -> branches.last().until = true
                        "while" in v.lowercase(Locale.ROOT) -> branches.last().until = false
                        else -> bad("say \"until an exit\" or \"while it holds\"")
                    }
                }
                "otherwise" -> readWeights(v)?.let { otherwise = it } ?: bad("not weights or cash")
                "exit" -> readExit(v, ::isSeries)?.let { exits += it } ?: bad("not an exit this card can say")
                "check" -> when (v.lowercase(Locale.ROOT).trim().removePrefix("every ").removePrefix("each ")) {
                    "daily", "day" -> check = Check.DAILY
                    "weekly", "week" -> check = Check.WEEKLY
                    "monthly", "month" -> check = Check.MONTHLY
                    else -> bad("daily, weekly or monthly")
                }
                "size" -> readSizing(v)?.let { sizing = it } ?: bad("not a sizing this card can say")
                "market" -> {
                    val p = v.split(Regex("\\s+"))
                    val m = when (p.getOrNull(1)?.lowercase(Locale.ROOT)) {
                        "fx", "currency" -> Market.FX; "crypto" -> Market.CRYPTO; "stock" -> Market.STOCK; "fund" -> Market.FUND; else -> null
                    }
                    val a = p.getOrNull(0)?.uppercase(Locale.ROOT)
                    if (m == null || a == null || !RuleCheck.isTicker(a)) bad("say a ticker and fx, crypto, stock or fund") else markets[a] = m
                }
                "carry" -> {
                    val m = Regex("^([A-Za-z0-9_.^-]{1,16})\\s+earns\\s+(\\S+)(?:\\s+minus\\s+(\\S+))?$", RegexOption.IGNORE_CASE).matchEntire(v.trim())
                    if (m == null) bad("say \"USDJPY earns DTB3 minus JP3M\"") else {
                        val a = m.groupValues[1].uppercase(Locale.ROOT)
                        val x = m.groupValues[2]
                        val y = m.groupValues[3]
                        carry[a] = if (y.isNotBlank()) "gap:$x:$y" else x
                    }
                }
                "compare with" -> v.trim().uppercase(Locale.ROOT).let { if (RuleCheck.isTicker(it)) bench = it else bad("not a ticker") }
            }
        }
        if (branches.isEmpty()) problems += "There's no When or Then line"
        branches.forEachIndexed { i, b -> if (b.action == null) problems += "Branch ${i + 1} has no Then line" }
        if (problems.isNotEmpty()) return Read(null, problems, name)

        val tags = HashSet<String>()
        val built = branches.map { b ->
            var tag = b.conditions.firstOrNull()?.let(::tagOf) ?: "hold"
            var k = 2
            val base = tag
            while (!tags.add(tag)) tag = "$base${k++}"
            Branch(tag, b.conditions.toList(), b.action!!, until = b.until)
        }
        val traded = built.flatMap { b ->
            when (val a = b.action) { is Action.Hold -> a.weights.keys; is Action.Rotate -> a.assets; is Action.Scale -> listOf(a.asset) }
        } + otherwise.keys
        val rule = Rule(
            name = name, benchmark = bench ?: traded.firstOrNull() ?: "GLD", branches = built, otherwise = otherwise,
            exits = exits, sizing = sizing, check = check, markets = markets, carry = carry
        )
        val checks = RuleCheck.problems(rule)
        return if (checks.isEmpty()) Read(rule, emptyList(), name) else Read(null, checks, name)
    }

    private fun tagOf(c: Condition): String = when (c) {
        is Condition.Compare -> {
            val l = c.left
            val r = (c.right as? Against.Of)?.signal
            when {
                l is Signal.Average || r is Signal.Average -> "trend"
                l is Signal.High || l is Signal.Low || r is Signal.High || r is Signal.Low -> "breakout"
                l is Signal.Momentum -> "momentum"
                l is Signal.Stretch || l is Signal.Ratio -> "stretch"
                l is Signal.Rsi -> "rsi"
                l is Signal.Atr -> "range"
                l is Signal.OffHigh -> "drawdown"
                c.right is Against.Percentile -> "filter"
                l is Signal.Gap -> "gap"
                l is Signal.Change -> "change"
                else -> "level"
            }
        }
        else -> "when"
    }

    private val OPS = listOf(
        "crosses above" to Op.CROSSES_ABOVE, "crosses below" to Op.CROSSES_BELOW, "is at least" to Op.AT_LEAST, "is at most" to Op.AT_MOST,
        "at least" to Op.AT_LEAST, "at most" to Op.AT_MOST, "is above" to Op.ABOVE, "is below" to Op.BELOW, "closes above" to Op.ABOVE,
        "closes below" to Op.BELOW, "above" to Op.ABOVE, "below" to Op.BELOW, ">=" to Op.AT_LEAST, "<=" to Op.AT_MOST, ">" to Op.ABOVE, "<" to Op.BELOW
    )

    private fun readCondition(text: String, isSeries: (String) -> Boolean): Condition? {
        var t = text.trim().trimEnd('.')
        var enterOnly = false
        Regex("\\(\\s*to enter\\s*\\)\\s*$", RegexOption.IGNORE_CASE).find(t)?.let { enterOnly = true; t = t.substring(0, it.range.first).trim() }
        var days = 1
        Regex("\\s+for\\s+(\\d{1,3})\\s+days(?:\\s+in\\s+a\\s+row)?$", RegexOption.IGNORE_CASE).find(t)?.let {
            days = it.groupValues[1].toInt(); t = t.substring(0, it.range.first).trim()
        }
        val low = t.lowercase(Locale.ROOT)
        var at = -1
        var op: Op? = null
        var len = 0
        for ((w, o) in OPS) {
            val k = if (w.first().isLetter()) Regex("(?<![A-Za-z])" + Regex.escape(w) + "(?![A-Za-z])").find(low)?.range?.first ?: -1 else low.indexOf(w)
            if (k > 0) { at = k; op = o; len = w.length; break }
        }
        if (op == null || at <= 0) return null
        val left = readSignal(t.substring(0, at).trim(), isSeries) ?: return null
        val rightText = t.substring(at + len).trim()
        val right = readAgainst(rightText, left, isSeries) ?: return null
        return Condition.Compare(left, op, right, days = if (op.crossing) 1 else days, enterOnly = enterOnly)
    }

    private val NUMBER = Regex("^([-+−]?\\d+(?:\\.\\d+)?)\\s*(%)?$")

    private fun readNumber(text: String): Double? {
        val m = NUMBER.matchEntire(text.trim().replace(",", "")) ?: return null
        val v = m.groupValues[1].replace('−', '-').toDoubleOrNull() ?: return null
        return if (m.groupValues[2] == "%") v / 100.0 else v
    }

    private fun readAgainst(text: String, left: Signal, isSeries: (String) -> Boolean): Against? {
        readNumber(text)?.let { return Against.Value(it) }
        Regex("^its\\s+(\\d{1,2})(?:st|nd|rd|th)\\s+percentile\\s+(?:over|of)\\s+(?:the\\s+last\\s+)?(\\d{1,4})\\s+days$", RegexOption.IGNORE_CASE)
            .matchEntire(text.trim())?.let {
                val d = it.groupValues[2].toInt()
                return Against.Percentile(it.groupValues[1].toInt() / 100.0, d, d / 2)
            }
        val own = left.assets.singleOrNull()
        val t = if (text.trim().lowercase(Locale.ROOT).startsWith("its ") && own != null) own + " " + text.trim().substring(4) else text.trim()
        return readSignal(t, isSeries)?.let { Against.Of(it) }
    }

    private val ASSET = "([A-Za-z0-9_.^*-]{1,16})"
    private val PAIR = "([A-Za-z0-9_.^*-]{1,16})/([A-Za-z0-9_.^*-]{1,16})"
    private val ID = "([A-Za-z0-9_.^:-]{1,32})"

    private fun readSignal(text: String, isSeries: (String) -> Boolean): Signal? {
        val t = text.trim().replace(Regex("\\s+"), " ")
        fun m(p: String) = Regex("^$p$", RegexOption.IGNORE_CASE).matchEntire(t)?.groupValues
        fun up(s: String) = s.uppercase(Locale.ROOT)
        fun d(s: String) = s.toIntOrNull()
        m("$PAIR (\\d{1,4})-day stretch")?.let { g -> return Signal.Stretch(up(g[1]), d(g[3]) ?: return null, up(g[2])) }
        m("$PAIR")?.let { g -> return Signal.Ratio(up(g[1]), up(g[2])) }
        m("$ID minus $ID")?.let { g -> return Signal.Gap(g[1], g[2]) }
        m("$ASSET (\\d{1,4})-day (?:exponential|ema) (?:moving )?average")?.let { g -> return Signal.Average(up(g[1]), d(g[2]) ?: return null, exp = true) }
        m("$ASSET (\\d{1,4})-day ema")?.let { g -> return Signal.Average(up(g[1]), d(g[2]) ?: return null, exp = true) }
        m("$ASSET (\\d{1,4})-day (?:simple )?(?:moving )?average")?.let { g -> return Signal.Average(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day sma")?.let { g -> return Signal.Average(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day return(?: skipping (\\d{1,4}))?")?.let { g ->
            return Signal.Momentum(up(g[1]), d(g[2]) ?: return null, if (g[3].isBlank()) 0 else d(g[3]) ?: return null)
        }
        m("$ASSET (\\d{1,4})-day high")?.let { g -> return Signal.High(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day low")?.let { g -> return Signal.Low(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET off its (\\d{1,4})-day high")?.let { g -> return Signal.OffHigh(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day stretch")?.let { g -> return Signal.Stretch(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day rsi")?.let { g -> return Signal.Rsi(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET rsi (\\d{1,4})")?.let { g -> return Signal.Rsi(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day volatility")?.let { g -> return Signal.Volatility(up(g[1]), d(g[2]) ?: return null) }
        m("$ASSET (\\d{1,4})-day atr")?.let { g -> return Signal.Atr(up(g[1]), d(g[2]) ?: return null) }
        m("$ID (\\d{1,4})-day change")?.let { g -> return Signal.Change(g[1], d(g[2]) ?: return null) }
        m(ID)?.let { g ->
            val x = g[1]
            return if (x == "*") Signal.Price("*") else if (isSeries(up(x)) || isSeries(x)) Signal.Series(if (up(x) in KNOWN_SERIES) up(x) else x) else Signal.Price(up(x))
        }
        return null
    }

    /** "hold GLD 100%, SLV -50%", "hold GLD", "short GLD 50%", "cash", "hold the best 1 of GLD, SLV, TLT by 120-day return". */
    private fun readAction(text: String): Action? {
        val t = text.trim().trimEnd('.')
        Regex("^hold the (?:best|top) (\\d{1,2}) of (.+?) by (.+)$", RegexOption.IGNORE_CASE).matchEntire(t)?.let { g ->
            val assets = g.groupValues[2].split(',', ' ').map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotEmpty() && it != "AND" }
            if (assets.any { !RuleCheck.isTicker(it) }) return null
            val score = readSignal("* " + g.groupValues[3].trim().removePrefix("each ").removePrefix("their ")) { false } ?: return null
            return Action.Rotate(assets, score, g.groupValues[1].toInt())
        }
        return readWeights(t)?.let { Action.Hold(it) }
    }

    private fun readWeights(text: String): Map<String, Double>? {
        val t = text.trim().trimEnd('.')
        val low = t.lowercase(Locale.ROOT)
        if (low == "cash" || low == "t-bills" || low == "cash (t-bills)" || low == "nothing" || low == "stay in cash") return emptyMap()
        val body = Regex("^(?:hold\\s+)?(.+)$", RegexOption.IGNORE_CASE).matchEntire(t)?.groupValues?.get(1) ?: return null
        val out = LinkedHashMap<String, Double>()
        for (part in body.split(Regex(",|\\s+and\\s+"))) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val m = Regex("^(short\\s+)?([A-Za-z0-9_.^-]{1,16})(?:\\s+([-+−]?\\d+(?:\\.\\d+)?)\\s*%)?$", RegexOption.IGNORE_CASE).matchEntire(p) ?: return null
            val a = m.groupValues[2].uppercase(Locale.ROOT)
            if (!RuleCheck.isTicker(a)) return null
            var w = if (m.groupValues[3].isBlank()) 1.0 else (m.groupValues[3].replace('−', '-').toDoubleOrNull() ?: return null) / 100.0
            if (m.groupValues[1].isNotBlank()) w = -abs(w)
            out[a] = w
        }
        return if (out.isEmpty()) null else out
    }

    private fun readExit(text: String, isSeries: (String) -> Boolean): Exit? {
        val t = text.trim().trimEnd('.').lowercase(Locale.ROOT)
        Regex("^after (\\d{1,4}) days$").matchEntire(t)?.let { return Exit.After(it.groupValues[1].toInt()) }
        Regex("^(?:a )?(trailing )?stop (?:of )?(\\d+(?:\\.\\d+)?) ?(?:x )?atrs?(?: over (\\d{1,3}) days)?$").matchEntire(t)?.let {
            return Exit.Stop(trailing = it.groupValues[1].isNotBlank(), atr = it.groupValues[2].toDouble(), atrDays = it.groupValues[3].toIntOrNull() ?: 14)
        }
        Regex("^(?:a )?trailing stop (?:of )?(\\d+(?:\\.\\d+)?)%.*$").matchEntire(t)?.let { return Exit.Stop(it.groupValues[1].toDouble() / 100, trailing = true) }
        Regex("^(?:a )?stop (?:of )?(\\d+(?:\\.\\d+)?)%.*$").matchEntire(t)?.let { return Exit.Stop(it.groupValues[1].toDouble() / 100) }
        Regex("^(?:a )?(?:profit )?target (?:of )?(\\d+(?:\\.\\d+)?)%$").matchEntire(t)?.let { return Exit.Target(it.groupValues[1].toDouble() / 100) }
        Regex("^a profit of (\\d+(?:\\.\\d+)?)%$").matchEntire(t)?.let { return Exit.Target(it.groupValues[1].toDouble() / 100) }
        val w = Regex("^(.+?) (?:is )?back within (\\S+?)(?: of zero)?$", RegexOption.IGNORE_CASE).matchEntire(text.trim().trimEnd('.')) ?: return null
        val s = readSignal(w.groupValues[1], isSeries) ?: return null
        val band = readNumber(w.groupValues[2]) ?: return null
        return Exit.Within(s, band)
    }

    private fun readSizing(text: String): Sizing? {
        val t = text.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (t == "as written") return Sizing.AsWritten
        if (t == "equal" || t == "equal money" || t == "the same money in each") return Sizing.Equal()
        Regex("^equal risk(?: over (\\d{1,4}) days)?$").matchEntire(t)?.let { return Sizing.EqualRisk(it.groupValues[1].toIntOrNull() ?: 60) }
        Regex("^steady risk (\\d+(?:\\.\\d+)?)%(?: over (\\d{1,4}) days)?$").matchEntire(t)?.let {
            return Sizing.SteadyRisk(it.groupValues[1].toDouble() / 100, it.groupValues[2].toIntOrNull() ?: 60)
        }
        Regex("^risk (\\d+(?:\\.\\d+)?)% per trade$").matchEntire(t)?.let { return Sizing.RiskPerTrade(it.groupValues[1].toDouble() / 100) }
        return null
    }

    /** The card's forms, for a prompt: what the Trader may write, with one example. */
    val FORMS: String = """
        Write the rule as a card with these lines, and nothing else in them:
        Rule: a short name
        When: a condition      (And: for each further condition; Else when: starts another branch)
        Then: hold TICKER 100%  (or: short TICKER 100%; or: hold A 50%, B -50%; or: cash; or: hold the best 1 of A, B, C by 120-day return)
        Held: until an exit    (only if the position should stay on until an Exit line says so)
        Otherwise: cash        (or weights, as in Then)
        Exit: after 60 days | stop 8% | trailing stop 10% | stop 3 ATR | trailing stop 3 ATR | target 20% | A/B 60-day stretch back within 0.5
        Check: daily | weekly | monthly
        Size: equal | equal risk over 60 days | steady risk 10% over 60 days | risk 1% per trade (only with a stop Exit)
        Compare with: TICKER
        A condition is: SIGNAL above|below|at least|at most|crosses above|crosses below VALUE, optionally "for 3 days" or "(to enter)".
        A SIGNAL is one of: TICKER (its close) · TICKER 200-day average · TICKER 50-day exponential average · TICKER 120-day return ·
        TICKER 50-day high · TICKER 50-day low · TICKER off its 252-day high · TICKER 20-day volatility · TICKER 14-day RSI ·
        TICKER 14-day ATR (a day's range as a share of price) ·
        A/B (the ratio) · A/B 60-day stretch · SERIES (a rate or index) · SERIES 63-day change · SERIES minus SERIES.
        A VALUE is a number (0.5, or 5% for returns, rates and volatility), another SIGNAL ("its 200-day average"),
        or "its 80th percentile over 252 days".
        Example:
        Rule: Gold in a calm uptrend
        When: GLD above its 200-day average
        And: GVZ at most its 80th percentile over 252 days (to enter)
        Then: hold GLD 100%
        Otherwise: cash
        Compare with: GLD
    """.trimIndent()
}
