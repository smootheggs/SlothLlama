package app.localmind.assistant.domain.desk

import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Settings near a rule's own, for "do nearby settings agree?", and a rule's identity in the
 * trial log. The shelf's four keep their own grids (as in Colab); any other rule is varied on
 * its first two numbers: the days of a signal (everywhere that signal is read), a threshold, a
 * percentile, an exit.
 */
object RuleTweaks {

    /** A number in the rule and how to set it. [days]: a whole number of days. */
    class Knob(val label: String, val value: Double, val days: Boolean, val apply: (Rule, Double) -> Rule)

    /** The rule's identity among its tries: its content and the costs, not its name. */
    fun hash(r: Rule, costs: Costs): String {
        val canon = RuleFiles.encode(r.copy(name = "-")) + "|" + costs.toString()
        val d = MessageDigest.getInstance("SHA-1").digest(canon.toByteArray())
        return "r" + d.joinToString("") { "%02x".format(it) }.take(12)
    }

    fun knobs(r: Rule): List<Knob> {
        val out = ArrayList<Knob>()
        val seen = HashSet<Signal>()
        fun daysOf(s: Signal): Int? = when (s) {
            is Signal.Average -> s.days; is Signal.Momentum -> s.days; is Signal.High -> s.days
            is Signal.Low -> s.days; is Signal.OffHigh -> s.days; is Signal.Stretch -> s.days
            is Signal.Rsi -> s.days; is Signal.Volatility -> s.days; is Signal.Change -> s.days
            else -> null
        }
        fun signalKnob(s: Signal) {
            val d = daysOf(s) ?: return
            if (!seen.add(s)) return
            out += Knob(RuleWords.signal(s), d.toDouble(), true) { rule, v -> replaceSignal(rule, s, withDays(s, v.roundToInt())) }
        }
        r.branches.forEachIndexed { bi, b ->
            b.conditions.forEachIndexed { ci, c ->
                if (c !is Condition.Compare) return@forEachIndexed
                signalKnob(c.left)
                when (val a = c.right) {
                    is Against.Of -> signalKnob(a.signal)
                    is Against.Value -> if (a.x != 0.0) out += Knob("the threshold for ${RuleWords.signal(c.left)}", a.x, false) { rule, v ->
                        setCondition(rule, bi, ci) { (it as Condition.Compare).copy(right = Against.Value(v)) }
                    }
                    is Against.Percentile -> out += Knob("the percentile for ${RuleWords.signal(c.left)}", a.q, false) { rule, v ->
                        setCondition(rule, bi, ci) { (it as Condition.Compare).copy(right = a.copy(q = v)) }
                    }
                }
            }
            (b.action as? Action.Rotate)?.let { signalKnob(it.score) }
        }
        r.exits.forEachIndexed { ei, e ->
            when (e) {
                is Exit.Within -> { signalKnob(e.signal); out += Knob("back within", e.band, false) { rule, v -> setExit(rule, ei, e.copy(band = v)) } }
                is Exit.After -> out += Knob("the time stop", e.days.toDouble(), true) { rule, v -> setExit(rule, ei, e.copy(days = v.roundToInt())) }
                is Exit.Stop -> out += Knob("the stop", e.pct, false) { rule, v -> setExit(rule, ei, e.copy(pct = v)) }
                is Exit.Target -> out += Knob("the profit target", e.pct, false) { rule, v -> setExit(rule, ei, e.copy(pct = v)) }
            }
        }
        (r.overlay as? Overlay.CoveredCall)?.let { o ->
            out += Knob("the call's delta", o.delta, false) { rule, v -> rule.copy(overlay = o.copy(delta = v)) }
            out += Knob("the call's days to expiry", o.dte.toDouble(), true) { rule, v -> rule.copy(overlay = o.copy(dte = v.roundToInt())) }
        }
        return out
    }

    /**
     * The nearby rules: the first knob at five values, the second at three (where the rule has
     * them), the rule itself among them. Only rules inside the limits are kept.
     */
    fun grid(r: Rule): List<Rule> {
        val k = knobs(r)
        if (k.isEmpty()) return listOf(r)
        val a = values(k[0], listOf(0.75, 0.875, 1.0, 1.125, 1.25), listOf(-0.10, -0.05, 0.0, 0.05, 0.10))
        val b = k.getOrNull(1)?.let { values(it, listOf(0.85, 1.0, 1.15), listOf(-0.05, 0.0, 0.05)) }
        val out = LinkedHashMap<String, Rule>()
        for (x in a) {
            val r1 = k[0].apply(r, x)
            val cells = if (b == null) listOf(r1) else b.map { y -> knobs(r1).getOrNull(1)?.apply?.invoke(r1, y) ?: r1 }
            for (c in cells) if (RuleCheck.problems(c).isEmpty()) out[RuleFiles.encode(c)] = c
        }
        out.putIfAbsent(RuleFiles.encode(r), r)
        return out.values.toList()
    }

    private fun values(k: Knob, mult: List<Double>, add: List<Double>): List<Double> {
        val pct = k.label.startsWith("the percentile") || k.label == "the call's delta"
        return when {
            k.days -> mult.map { (k.value * it).roundToInt().coerceIn(2, RuleCheck.MAX_DAYS).toDouble() }
            pct -> add.map { ((k.value + it) * 100).roundToInt() / 100.0 }.filter { it in 0.05..0.95 }
            else -> mult.map { k.value * it }
        }.distinct()
    }

    // --- changing one part of a rule ---

    fun withDays(s: Signal, d: Int): Signal = when (s) {
        is Signal.Average -> s.copy(days = d); is Signal.Momentum -> s.copy(days = d); is Signal.High -> s.copy(days = d)
        is Signal.Low -> s.copy(days = d); is Signal.OffHigh -> s.copy(days = d); is Signal.Stretch -> s.copy(days = d)
        is Signal.Rsi -> s.copy(days = d); is Signal.Volatility -> s.copy(days = d); is Signal.Change -> s.copy(days = d)
        else -> s
    }

    /** The rule with every use of [old] read as [new]: conditions, scores, scales and exits. */
    fun replaceSignal(r: Rule, old: Signal, new: Signal): Rule {
        fun s(x: Signal) = if (x == old) new else x
        fun c(x: Condition): Condition = when (x) {
            is Condition.Compare -> x.copy(left = s(x.left), right = (x.right as? Against.Of)?.let { Against.Of(s(it.signal)) } ?: x.right)
            is Condition.Calendar -> x
            is Condition.AnyOf -> x.copy(of = x.of.map(::c))
            is Condition.Not -> x.copy(of = c(x.of))
        }
        fun a(x: Action): Action = when (x) {
            is Action.Hold -> x
            is Action.Rotate -> x.copy(score = s(x.score))
            is Action.Scale -> x.copy(signal = s(x.signal))
        }
        return r.copy(
            branches = r.branches.map { b -> b.copy(conditions = b.conditions.map(::c), action = a(b.action)) },
            exits = r.exits.map { e -> if (e is Exit.Within) e.copy(signal = s(e.signal)) else e }
        )
    }

    fun setCondition(r: Rule, branch: Int, index: Int, change: (Condition) -> Condition): Rule =
        r.copy(branches = r.branches.mapIndexed { i, b ->
            if (i != branch) b else b.copy(conditions = b.conditions.mapIndexed { j, c -> if (j == index) change(c) else c })
        })

    fun setExit(r: Rule, index: Int, e: Exit): Rule = r.copy(exits = r.exits.mapIndexed { i, x -> if (i == index) e else x })
}
