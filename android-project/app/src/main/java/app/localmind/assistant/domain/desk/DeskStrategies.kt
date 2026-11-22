package app.localmind.assistant.domain.desk

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** A strategy's settings: numbers (yes/no as 1/0) plus the assets it trades. */
class Params(val values: Map<String, Double>, val texts: Map<String, String> = emptyMap()) {
    operator fun get(key: String): Double = values[key] ?: error("No setting $key")
    fun int(key: String): Int = get(key).roundToInt()
    fun flag(key: String): Boolean = get(key) != 0.0
    fun text(key: String, default: String): String = texts[key] ?: default
    fun with(key: String, value: Double): Params = Params(values + (key to value), texts)

    /** The same identity a setting has in Colab's trial log needs the same costs; this one is the tablet's own. */
    fun hash(costs: Costs): String {
        val canon = values.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" } + "|" +
            texts.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" } + "|" + costs.toString()
        val d = MessageDigest.getInstance("SHA-1").digest(canon.toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(10)
    }
}

/** A setting the Desk lets you change, within sensible bounds. */
data class Knob(val key: String, val label: String, val min: Double, val max: Double, val step: Double, val unit: String = "")

/** One strategy on the shelf: what it is, its default rule sheet, and what can be tuned. */
data class StrategyTemplate(
    val key: String,
    val label: String,
    val blurb: String,
    val needs: List<String>,
    val defaults: Params,
    val knobs: List<Knob>,
    /** The two settings the "nearby" check varies, with their values. */
    val grid: Pair<Pair<String, List<Double>>, Pair<String, List<Double>>>,
    val benchmark: String
)

object DeskStrategies {

    val templates: List<StrategyTemplate> = listOf(
        StrategyTemplate(
            "gold_trend", "Gold trend with a GVZ filter",
            "Hold GLD while it closes above its 200-day average; don't start a new position when gold's volatility " +
                "index is unusually high. Out means cash earning the T-bill.",
            listOf("GLD", "GVZ", "DTB3"),
            Params(mapOf("sma_days" to 200.0, "vol_filter" to 1.0, "vol_window" to 252.0, "vol_quantile" to 0.80),
                mapOf("asset" to "GLD")),
            listOf(Knob("sma_days", "Average", 100.0, 300.0, 25.0, " days"), Knob("vol_quantile", "Calm below", 0.6, 0.95, 0.05)),
            ("sma_days" to listOf(150.0, 175.0, 200.0, 225.0, 250.0)) to ("vol_quantile" to listOf(0.7, 0.8, 0.9)),
            "GLD"
        ),
        StrategyTemplate(
            "gold_silver_ratio", "Gold/silver ratio mean reversion",
            "When gold is unusually rich or cheap against silver (a 60-day z-score beyond 2), go long the cheap one " +
                "and short the rich one, half each; close when the z-score is back within 0.5 or after 30 days.",
            listOf("GLD", "SLV", "DTB3"),
            Params(mapOf("lookback" to 60.0, "z_enter" to 2.0, "z_exit" to 0.5, "max_days" to 30.0, "gross" to 1.0),
                mapOf("asset" to "GLD", "asset2" to "SLV")),
            listOf(Knob("lookback", "Look-back", 20.0, 120.0, 10.0, " days"), Knob("z_enter", "Enter beyond z", 1.0, 3.0, 0.25)),
            ("lookback" to listOf(40.0, 60.0, 90.0)) to ("z_enter" to listOf(1.5, 2.0, 2.5)),
            "GLD"
        ),
        StrategyTemplate(
            "usdjpy_carry", "USD/JPY carry with a trend filter",
            "Long USD/JPY, earning the interest gap, while it's above its 100-day average and the US 3-month rate " +
                "beats Japan's by more than 1% (as published at the time).",
            listOf("USDJPY", "DTB3", "JP3M"),
            Params(mapOf("sma_days" to 100.0, "min_rate_gap" to 0.01), mapOf("asset" to "USDJPY")),
            listOf(Knob("sma_days", "Average", 50.0, 250.0, 25.0, " days"), Knob("min_rate_gap", "Rate gap above", 0.0, 0.03, 0.0025)),
            ("sma_days" to listOf(50.0, 100.0, 150.0, 200.0)) to ("min_rate_gap" to listOf(0.005, 0.01, 0.015)),
            "USDJPY"
        ),
        StrategyTemplate(
            "gld_covered_call", "GLD covered call (modelled options)",
            "Hold GLD and sell a 30-delta call about 30 days out, again each time one expires. Option prices are " +
                "modelled from the GVZ index, not real quotes, so treat results as a sketch.",
            listOf("GLD", "GVZ", "DTB3"),
            Params(mapOf("delta" to 0.30, "dte" to 30.0, "skew_vol_pts" to 0.0, "spread_pct" to 3.0,
                "stress_spread_pct" to 8.0, "fee_per_contract" to 0.65, "strike_step" to 1.0), mapOf("asset" to "GLD")),
            listOf(Knob("delta", "Call delta", 0.15, 0.45, 0.05), Knob("dte", "Days to expiry", 14.0, 60.0, 7.0, " days")),
            ("delta" to listOf(0.20, 0.25, 0.30, 0.35)) to ("dte" to listOf(21.0, 30.0, 45.0)),
            "GLD"
        )
    )

    fun template(key: String): StrategyTemplate = templates.first { it.key == key }

    /**
     * The strategy's result at a setting, its buy-and-hold benchmark, the entries it made, and
     * the periods in its year (365 for crypto alone, 252 otherwise) for annualising its stats.
     */
    class Run(val result: SimResult, val bench: SimResult, val entries: Int, val targets: Targets?, val periodsPerYear: Double = 252.0)

    /**
     * The shelf's strategies as rules (1.47): the same blocks anyone's rules are built from, so
     * these four are presets to copy rather than code. [p] is the template's settings.
     */
    fun rule(key: String, p: Params): Rule {
        val t = template(key)
        return when (key) {
            "gold_trend" -> {
                val a = p.text("asset", "GLD")
                val trend = Condition.Compare(Signal.Price(a), Op.ABOVE, Against.Of(Signal.Average(a, p.int("sma_days"))), tag = "trend")
                val calm = Condition.Compare(Signal.Series("GVZ"), Op.AT_MOST,
                    Against.Percentile(p["vol_quantile"], p.int("vol_window"), p.int("vol_window") / 2), enterOnly = true, tag = "calm")
                Rule(name = t.label, benchmark = a,
                    branches = listOf(Branch("trend", if (p.flag("vol_filter")) listOf(trend, calm) else listOf(trend), Action.Hold(mapOf(a to 1.0)))))
            }
            "gold_silver_ratio" -> {
                val g = p.text("asset", "GLD")
                val s = p.text("asset2", "SLV")
                val z = Signal.Stretch(g, p.int("lookback"), s)
                val half = p["gross"] / 2.0
                Rule(name = t.label, benchmark = g,
                    branches = listOf(
                        Branch("rich", listOf(Condition.Compare(z, Op.ABOVE, Against.Value(p["z_enter"]))), Action.Hold(mapOf(g to -half, s to half)), until = true),
                        Branch("cheap", listOf(Condition.Compare(z, Op.BELOW, Against.Value(-p["z_enter"]))), Action.Hold(mapOf(g to half, s to -half)), until = true)
                    ),
                    exits = listOf(Exit.Within(z, p["z_exit"]), Exit.After(p.int("max_days"))))
            }
            "usdjpy_carry" -> {
                val a = p.text("asset", "USDJPY")
                Rule(name = t.label, benchmark = a,
                    branches = listOf(Branch("carry", listOf(
                        Condition.Compare(Signal.Price(a), Op.ABOVE, Against.Of(Signal.Average(a, p.int("sma_days"))), tag = "trend"),
                        Condition.Compare(Signal.Gap("DTB3", "JP3M"), Op.ABOVE, Against.Value(p["min_rate_gap"]), tag = "gap")
                    ), Action.Hold(mapOf(a to 1.0)))),
                    carry = mapOf(a to "gap:DTB3:JP3M"),
                    markets = mapOf(a to Market.FX))
            }
            "gld_covered_call" -> {
                val a = p.text("asset", "GLD")
                Rule(name = t.label, benchmark = a,
                    branches = listOf(Branch("hold", emptyList(), Action.Hold(mapOf(a to 1.0)))),
                    overlay = Overlay.CoveredCall(a, "GVZ", p["delta"], p.int("dte"), p["skew_vol_pts"], p["spread_pct"],
                        p["stress_spread_pct"], p["fee_per_contract"], p["strike_step"]))
            }
            else -> error("Unknown strategy $key")
        }
    }

    private val benchNames = mapOf(
        "gold_trend" to "Buy and hold", "gold_silver_ratio" to "Buy and hold GLD",
        "usdjpy_carry" to "Always long USD/JPY", "gld_covered_call" to "Buy and hold"
    )

    fun run(key: String, data: DeskData, p: Params, costs: Costs = Costs(), costMult: Double = 1.0): Run =
        RuleEngine.run(rule(key, p), data, costs, costMult, benchNames.getValue(key))

    /** Whether [data] has what [key] needs (and which series are missing). */
    fun missing(key: String, data: DeskData?): List<String> {
        val needs = template(key).needs
        if (data == null) return needs
        return needs.filter { n ->
            when (n) {
                "DTB3" -> data.rf.size == 0
                "GVZ" -> data.gvz.size == 0
                "JP3M" -> data.japan.size == 0
                else -> data.prices[n].let { it == null || it.size < 300 }
            }
        }
    }

    // --- tonight's ticket ---

    data class Ticket(val dataDate: LocalDate, val positionNow: String, val nextOpen: String, val trade: Boolean)

    /** Tonight's ticket from a run of [r]: the position now and what to do at the next open. */
    fun ticket(r: Rule, run: Run): Ticket {
        val o = r.overlay
        if (o is Overlay.CoveredCall) {
            val s = run.result.callState ?: error("No call state")
            return if (s.sellNextOpen) {
                val next = DeskMath.nextBusinessDay(s.lastDate)
                var exp = next.plusDays(o.dte.toLong())
                while (exp.dayOfWeek == DayOfWeek.SATURDAY || exp.dayOfWeek == DayOfWeek.SUNDAY) exp = exp.minusDays(1)
                val t = ChronoUnit.DAYS.between(next, exp) / 365.0
                val k = DeskEngine.strikeForDelta(s.lastClose, t, s.lastRate, s.lastVol, o.delta, o.strikeStep)
                val prem = DeskEngine.bsCall(s.lastClose, k, t, s.lastRate, s.lastVol)
                Ticket(s.lastDate, "Long ${o.asset}, no call open",
                    "Sell ${(s.shares / 100).toInt()} call(s), one per 100 ${o.asset} shares: strike about ${"%.0f".format(k)}, " +
                        "expiry $exp (modelled premium about $${"%.2f".format(prem)} a share; re-strike at the open)", true)
            } else {
                Ticket(s.lastDate, "Long ${o.asset}, short ${s.contracts} call(s) at ${"%.0f".format(s.openStrike ?: 0.0)} " +
                    "expiring ${s.openExpiry}", "Hold (no trade)", false)
            }
        }
        val t = run.targets ?: error("No targets")
        val last = t.dates.size - 1
        val now = t.assets.indices.associate { t.assets[it] to (if (last > 0) t.weights[it][last - 1] else 0.0) }
        val next = t.assets.indices.associate { t.assets[it] to t.weights[it][last] }
        val same = t.assets.all { abs(next.getValue(it) - now.getValue(it)) < 1e-9 }
        val why = t.reasons[last].substringAfter(": ", "")
        return Ticket(t.dates[last], describe(now),
            if (same) "Hold (no trade)" else "At the next open: go ${describe(next)}" + if (why.isNotBlank()) " ($why)" else "",
            !same)
    }

    fun describe(w: Map<String, Double>): String {
        val parts = w.filter { abs(it.value) > 1e-9 }
            .map { (a, x) -> "${if (x > 0) "long" else "short"} $a ${(abs(x) * 100).roundToInt()}%" }
        return if (parts.isEmpty()) "Flat (cash)" else parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }
}
