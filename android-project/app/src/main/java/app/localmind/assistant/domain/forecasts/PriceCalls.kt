package app.localmind.assistant.domain.forecasts

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** A price call's terms: [series] above (or below) [level] at the close on [on] (yyyy-MM-dd). */
@Serializable
data class PriceTerms(val series: String, val above: Boolean, val level: Double, val on: String)

/** One series of daily closes, oldest first, and where it came from. */
data class Closes(val dates: List<String>, val closes: List<Double>, val source: String)

enum class Verdict { HAPPENED, DIDNT, ASK }

/**
 * What the prices say about a price call: the close it settles on and its source, a second
 * source's close where there is an independent one, and the verdict. ASK means look before it
 * counts, with [why]; nothing is ever marked without your confirming.
 */
data class PriceCheck(
    val verdict: Verdict,
    val day: String = "",
    val close: Double? = null,
    val source: String = "",
    val secondDay: String = "",
    val second: Double? = null,
    val secondSource: String = "",
    val why: String = ""
) {
    /** The line kept on the call when you confirm: "251.30 on 31 Dec 2026 · Tiingo; 251.10 · FRED". */
    val evidence: String
        get() = buildString {
            close?.let { append(PriceCalls.level(it)).append(" on ").append(PriceCalls.showDate(day)).append(" · ").append(PriceCalls.sourceName(source)) }
            second?.let { append("; ").append(PriceCalls.level(it)).append(" · ").append(PriceCalls.sourceName(secondSource)) }
        }
}

/**
 * Price calls: "GLD above 255 on 31 Dec", settled from the closes SlothDesk keeps (the Lobster's
 * daily prices). The close used is the one on the day, or the last trading day before it when
 * markets were shut. Where SlothDesk has a second, independent source for the series (FRED's daily
 * rate for USD/JPY), both must land on the same side. A close within 0.1% of the level, sources that
 * disagree, or prices that never reach the day: it asks instead of proposing.
 */
object PriceCalls {
    /** The series a call can be on (SlothDesk's bars). */
    val SERIES = listOf("GLD", "SLV", "USDJPY")

    /** A second source for a series, where SlothDesk's prices have one. */
    val SECOND = mapOf("USDJPY" to "DEXJPUS")

    /** Within this share of the level, a close is too near to call without a look. */
    const val NEAR = 0.001

    /** How far back a close may be from the day (a long weekend, a holiday). */
    const val LOOKBACK_DAYS = 6L

    /** How long after the day to wait for the prices before asking. */
    const val WAIT_DAYS = 5L

    private val SHOWN = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun label(series: String): String = if (series == "USDJPY") "USD/JPY" else series

    /** 255 → "255"; 160.425 → "160.425"; 251.3 → "251.3". */
    fun level(v: Double): String = runCatching { BigDecimal.valueOf(v).stripTrailingZeros().toPlainString() }.getOrDefault(v.toString())

    fun showDate(text: String): String = runCatching { LocalDate.parse(text).format(SHOWN) }.getOrDefault(text)

    /** "tiingo:GLD (adjusted)" → "Tiingo"; "fred:DEXJPUS (noon rate)" → "FRED". */
    fun sourceName(source: String): String {
        val s = source.lowercase(Locale.ENGLISH)
        return when {
            s.startsWith("tiingo") -> "Tiingo"
            s.startsWith("fred") -> "FRED"
            s.startsWith("cboe") -> "Cboe"
            s.isBlank() -> "SlothDesk's prices"
            else -> source.substringBefore(' ').take(24)
        }
    }

    fun question(t: PriceTerms): String = "${label(t.series)} ${if (t.above) "above" else "below"} ${level(t.level)} on ${showDate(t.on)}"

    /** How far the level is from [now], as a share of [now]: positive when the level is above. */
    fun distance(t: PriceTerms, now: Double): Double = if (now == 0.0) 0.0 else (t.level - now) / now

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** The close on [day], or the last one in the week before it; null if there's none that recent. */
    fun closeOn(s: Closes, day: LocalDate): Pair<String, Double>? {
        var bestDay: LocalDate? = null
        var best = -1
        for (i in s.dates.indices) {
            val d = date(s.dates[i]) ?: continue
            if (d.isAfter(day)) continue
            if (bestDay == null || d.isAfter(bestDay)) { bestDay = d; best = i }
        }
        if (best < 0 || bestDay == null) return null
        if (ChronoUnit.DAYS.between(bestDay, day) > LOOKBACK_DAYS) return null
        val c = s.closes.getOrNull(best) ?: return null
        if (!c.isFinite() || c <= 0.0) return null
        return s.dates[best] to c
    }

    private fun lastDay(s: Closes): LocalDate? = s.dates.mapNotNull { date(it) }.maxOrNull()

    private fun lands(t: PriceTerms, close: Double): Boolean = if (t.above) close > t.level else close < t.level

    /**
     * The check for [t] on [today]. Null while it isn't time yet: on or before the day, or while the
     * prices haven't reached the day and it's still within [WAIT_DAYS] of it.
     */
    fun check(t: PriceTerms, primary: Closes?, second: Closes?, today: LocalDate): PriceCheck? {
        val day = date(t.on) ?: return PriceCheck(Verdict.ASK, why = "The call's date can't be read.")
        if (!today.isAfter(day)) return null
        val waited = ChronoUnit.DAYS.between(day, today) > WAIT_DAYS
        if (primary == null || primary.dates.isEmpty()) {
            return if (waited) PriceCheck(Verdict.ASK, why = "SlothDesk has no ${label(t.series)} prices. Ask the Lobster for prices, or mark it yourself.") else null
        }
        val last = lastDay(primary)
        if (last == null || last.isBefore(day)) {
            return if (waited) PriceCheck(Verdict.ASK, why = "SlothDesk's prices stop at ${last?.let { showDate(it.toString()) } ?: "an unknown day"}, before the call's day.") else null
        }
        val (d1, c1) = closeOn(primary, day)
            ?: return PriceCheck(Verdict.ASK, why = "No close in the week before the day.")
        val base = PriceCheck(if (lands(t, c1)) Verdict.HAPPENED else Verdict.DIDNT, d1, c1, primary.source)
        if (t.level > 0 && abs(c1 - t.level) / t.level < NEAR) {
            return base.copy(verdict = Verdict.ASK, why = "The close is within 0.1% of the level: worth a look before it counts.")
        }
        if (second != null && second.source != primary.source) {
            val s = closeOn(second, day)
            if (s != null) {
                val both = base.copy(secondDay = s.first, second = s.second, secondSource = second.source)
                return if (lands(t, s.second) == lands(t, c1)) both
                else both.copy(verdict = Verdict.ASK, why = "The two sources land on different sides of the level.")
            }
        }
        return base
    }
}
