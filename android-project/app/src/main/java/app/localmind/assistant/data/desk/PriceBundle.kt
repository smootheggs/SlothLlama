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
 * Series: "bars" (daily adjusted open and close), "rate" (decimal, daily or monthly), "vol"
 * (a volatility index, decimal). Names the strategies read: GLD, SLV, USDJPY, DTB3, JP3M, GVZ.
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
            return s.copy(dates = rows.keys.map { it.toString() }, opens = rows.values.map { it.first },
                closes = closes, values = emptyList())
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
            return s.copy(dates = rows.keys.map { it.toString() }, values = rows.values.toList(), opens = emptyList(), closes = emptyList())
        }

        /** [newer]'s series replace [older]'s of the same name; the rest of [older] is kept. */
        fun merge(older: PriceBundle?, newer: PriceBundle): PriceBundle =
            if (older == null) newer else newer.copy(series = older.series + newer.series)

        /** What the strategies read, or null without the T-bill rate and at least one price series. */
        fun toDeskData(b: PriceBundle): DeskData? {
            val prices = b.series.filterValues { it.kind == "bars" }.mapValues { (_, s) ->
                Bars(s.dates.map(LocalDate::parse), s.opens.toDoubleArray(), s.closes.toDoubleArray())
            }
            fun line(name: String): Line = b.series[name]?.let { s ->
                Line(s.dates.map(LocalDate::parse), s.values.toDoubleArray())
            } ?: Line(emptyList(), DoubleArray(0))
            val us = line("DTB3")
            if (prices.isEmpty() || us.size == 0) return null
            return DeskData.assemble(prices, us, line("JP3M"), line("GVZ"))
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
    val values: List<Double> = emptyList()
)
