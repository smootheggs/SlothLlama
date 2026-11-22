package app.localmind.assistant.data.desk

import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.Line
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * A SlothDesk prices file: the Lobster's Prices catch, or one written by the Colab notebook
 * (export_prices). Plain numbers only; the Desk checks every row before using it.
 *
 * Series: "bars" (daily adjusted open and close, and from 1.50 high and low where the source
 * has them), "rate" (decimal, daily or monthly), "vol" (a volatility index, decimal), and
 * "series" (1.50: any other series, such as one of FRED's, divided by 100 like a rate; daily,
 * weekly or monthly). Names the shelf reads: GLD, SLV, USDJPY, DTB3, JP3M, GVZ.
 */
@Serializable
data class PriceBundle(
    val format: String = FORMAT,
    val v: Int = 1,
    @SerialName("made_at") val madeAt: String = "",
    @SerialName("made_by") val madeBy: String = "",
    val series: Map<String, PriceSeries> = emptyMap()
) {
    companion object {
        const val FORMAT = "slothdesk-prices"
        private const val MAX_SERIES = 40
        private const val MAX_ROWS = 20_000
        private val NAME = Regex("^[A-Z0-9_.^-]{1,16}$")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(text: String): PriceBundle {
            val b = json.decodeFromString(serializer(), text)
            require(b.format == FORMAT) { "Not a SlothDesk prices file" }
            require(b.v == 1) { "A newer prices file than this version of SlothDesk reads (v${b.v})" }
            return b
        }

        fun encode(b: PriceBundle): String = json.encodeToString(serializer(), b)

        /**
         * The bundle with only sound series and rows, and what was dropped or looks wrong. Rows
         * with bad dates or numbers are dropped; a series that's mostly bad is dropped whole.
         */
        fun validate(b: PriceBundle): Pair<PriceBundle, List<String>> {
            val problems = ArrayList<String>()
            val out = LinkedHashMap<String, PriceSeries>()
            for ((name, s) in b.series.entries.take(MAX_SERIES)) {
                if (!NAME.matches(name)) { problems += "Skipped a series with an odd name"; continue }
                if (s.dates.size > MAX_ROWS) { problems += "$name: too long, skipped"; continue }
                val clean = when (s.kind) {
                    "bars" -> cleanBars(name, s, problems)
                    "rate" -> cleanLine(name, s, -0.05, 0.5, problems)
                    "vol" -> cleanLine(name, s, 0.01, 3.0, problems)
                    "series" -> cleanLine(name, s, -1e7, 1e7, problems)
                    else -> { problems += "$name: unknown kind ${s.kind.take(20)}"; null }
                }
                if (clean != null) out[name] = clean
            }
            if (b.series.size > MAX_SERIES) problems += "Only the first $MAX_SERIES series were read"
            return b.copy(series = out) to problems
        }

        private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

        private fun cleanBars(name: String, s: PriceSeries, problems: MutableList<String>): PriceSeries? {
            if (s.opens.size != s.dates.size || s.closes.size != s.dates.size) {
                problems += "$name: dates, opens and closes don't line up; skipped"
                return null
            }
            val rows = java.util.TreeMap<LocalDate, Pair<Double, Double>>()
            var bad = 0
            s.dates.indices.forEach { i ->
                val d = date(s.dates[i])
                val o = s.opens[i]
                val c = s.closes[i]
                if (d == null || !o.isFinite() || !c.isFinite() || o <= 0 || c <= 0) bad++ else rows[d] = o to c
            }
            if (rows.size < s.dates.size / 2) { problems += "$name: mostly unreadable; skipped"; return null }
            if (bad > 0) problems += "$name: $bad bad row(s) dropped"
            val closes = rows.values.map { it.second }
            var run = 1
            var worst = 1
            var jumps = 0
            for (i in 1 until closes.size) {
                run = if (closes[i] == closes[i - 1]) run + 1 else 1
                worst = maxOf(worst, run)
                if (kotlin.math.abs(closes[i] / closes[i - 1] - 1) > 0.40) jumps++
            }
            if (worst >= 5) problems += "$name: the close stays the same for $worst days in a row (a frozen feed?)"
            if (jumps > 0) problems += "$name: $jumps one-day move(s) over 40% (an unadjusted split?)"
            val (highs, lows) = cleanRange(name, s, rows, problems)
            return s.copy(dates = rows.keys.map { it.toString() }, opens = rows.values.map { it.first },
                closes = closes, values = emptyList(), highs = highs, lows = lows)
        }

        /**
         * The highs and lows for the days kept ([rows]: day → open and close), or none: they're
         * kept only when every kept day has a sound pair (high at least the open, close and low;
         * low at most them).
         */
        private fun cleanRange(
            name: String,
            s: PriceSeries,
            rows: java.util.TreeMap<LocalDate, Pair<Double, Double>>,
            problems: MutableList<String>
        ): Pair<List<Double>, List<Double>> {
            if (s.highs.isEmpty() && s.lows.isEmpty()) return emptyList<Double>() to emptyList()
            if (s.highs.size != s.dates.size || s.lows.size != s.dates.size) {
                problems += "$name: highs and lows don't line up; left out"
                return emptyList<Double>() to emptyList()
            }
            val byDay = HashMap<LocalDate, Pair<Double, Double>>(s.dates.size * 2)
            s.dates.indices.forEach { i -> date(s.dates[i])?.let { byDay[it] = s.highs[i] to s.lows[i] } }
            val h = ArrayList<Double>(rows.size)
            val l = ArrayList<Double>(rows.size)
            var bad = 0
            // Adjusted highs and lows can miss the open or close by a rounding hair: within
            // 0.5% they're widened to fit; a day further off gets the open and close as its range.
            val tol = 0.005
            for ((d, oc) in rows) {
                val (hi0, lo0) = byDay[d] ?: (Double.NaN to Double.NaN)
                val (o, c) = oc
                val top = maxOf(o, c)
                val bottom = minOf(o, c)
                val sound = hi0.isFinite() && lo0.isFinite() && lo0 > 0 && hi0 >= lo0 && hi0 >= top * (1 - tol) && lo0 <= bottom * (1 + tol)
                if (sound) { h += maxOf(hi0, top); l += minOf(lo0, bottom) } else { bad++; h += top; l += bottom }
            }
            if (bad > rows.size / 100) {
                problems += "$name: $bad day(s) with highs and lows that don't fit the open and close; highs and lows left out"
                return emptyList<Double>() to emptyList()
            }
            if (bad > 0) problems += "$name: $bad day(s) without a sound high and low, given the open and close as their range"
            return h to l
        }

        private fun cleanLine(name: String, s: PriceSeries, lo: Double, hi: Double, problems: MutableList<String>): PriceSeries? {
            if (s.values.size != s.dates.size) { problems += "$name: dates and values don't line up; skipped"; return null }
            val rows = java.util.TreeMap<LocalDate, Double>()
            var bad = 0
            s.dates.indices.forEach { i ->
                val d = date(s.dates[i])
                val v = s.values[i]
                if (d == null || !v.isFinite() || v < lo || v > hi) bad++ else rows[d] = v
            }
            if (rows.size < s.dates.size / 2) { problems += "$name: mostly out of range; skipped"; return null }
            if (bad > 0) problems += "$name: $bad value(s) out of range dropped"
            return s.copy(dates = rows.keys.map { it.toString() }, values = rows.values.toList(), opens = emptyList(), closes = emptyList(),
                highs = emptyList(), lows = emptyList())
        }

        /** [newer]'s series replace [older]'s of the same name; the rest of [older] is kept. */
        fun merge(older: PriceBundle?, newer: PriceBundle): PriceBundle =
            if (older == null) newer else newer.copy(series = older.series + newer.series)

        /** What the strategies read, or null without the T-bill rate and at least one price series. */
        fun toDeskData(b: PriceBundle): DeskData? {
            val prices = b.series.filterValues { it.kind == "bars" }.mapValues { (_, s) ->
                val ranged = s.highs.size == s.dates.size && s.lows.size == s.dates.size && s.dates.isNotEmpty()
                Bars(s.dates.map(LocalDate::parse), s.opens.toDoubleArray(), s.closes.toDoubleArray(),
                    if (ranged) s.highs.toDoubleArray() else null, if (ranged) s.lows.toDoubleArray() else null)
            }
            fun line(name: String): Line = b.series[name]?.let { s ->
                Line(s.dates.map(LocalDate::parse), s.values.toDoubleArray())
            } ?: Line(emptyList(), DoubleArray(0))
            val us = line("DTB3")
            if (prices.isEmpty() || us.size == 0) return null
            // Every other series, as it happened and as it was known: rates when published, indices at the close.
            val more = b.series.filter { (k, s) -> s.kind != "bars" && k !in setOf("DTB3", "JP3M", "GVZ") }.mapValues { (k, s) ->
                val l = line(k)
                l to when {
                    s.kind != "rate" && s.kind != "series" -> l
                    s.freq == "monthly" -> DeskData.publishedMonthly(l)
                    s.freq == "weekly" -> DeskData.publishedWeekly(l)
                    else -> DeskData.publishedDaily(l)
                }
            }
            return DeskData.assemble(prices, us, line("JP3M"), line("GVZ"), more)
        }
    }
}

@Serializable
data class PriceSeries(
    val kind: String,
    val source: String = "",
    val adjusted: Boolean = false,
    val freq: String = "",
    val dates: List<String> = emptyList(),
    @SerialName("open") val opens: List<Double> = emptyList(),
    @SerialName("close") val closes: List<Double> = emptyList(),
    val values: List<Double> = emptyList(),
    @SerialName("high") val highs: List<Double> = emptyList(),
    @SerialName("low") val lows: List<Double> = emptyList()
)
