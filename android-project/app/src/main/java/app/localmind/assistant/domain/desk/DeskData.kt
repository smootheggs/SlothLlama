package app.localmind.assistant.domain.desk

import java.time.LocalDate

/** Daily opens and closes (adjusted for splits and dividends), oldest first. */
class Bars(
    val dates: List<LocalDate>,
    val opens: DoubleArray,
    val closes: DoubleArray,
    /** Daily highs and lows (1.50), when the source has them; null for open-and-close data. */
    val highs: DoubleArray? = null,
    val lows: DoubleArray? = null
) {
    init {
        require(dates.size == opens.size && dates.size == closes.size) { "Bars: lengths differ" }
        require((highs == null) == (lows == null)) { "Bars: highs without lows" }
        require(highs == null || (highs.size == dates.size && lows!!.size == dates.size)) { "Bars: highs and lows don't line up" }
    }
    val size: Int get() = dates.size
    val last: LocalDate get() = dates.last()
    val hasRange: Boolean get() = highs != null

    /** Only the dates in [keep] (which must all be present), in order. */
    fun on(keep: List<LocalDate>): Bars {
        val at = HashMap<LocalDate, Int>(dates.size * 2)
        dates.forEachIndexed { i, d -> at[d] = i }
        val ix = keep.map { at[it] ?: error("Bars: missing $it") }
        val h = highs
        val l = lows
        return Bars(keep, DoubleArray(ix.size) { opens[ix[it]] }, DoubleArray(ix.size) { closes[ix[it]] },
            h?.let { DoubleArray(ix.size) { k -> h[ix[k]] } }, l?.let { DoubleArray(ix.size) { k -> l[ix[k]] } })
    }
}

/** A dated series (a rate, a volatility index), oldest first. */
class Line(val dates: List<LocalDate>, val values: DoubleArray) {
    init {
        require(dates.size == values.size) { "Line: lengths differ" }
    }
    val size: Int get() = dates.size

    /**
     * The latest value known on or before each of [on] (NaN before the first): pandas
     * `s.reindex(s.index.union(on)).ffill().reindex(on)`. [on] must be sorted.
     */
    fun asOf(on: List<LocalDate>): DoubleArray {
        val out = DoubleArray(on.size) { Double.NaN }
        var j = -1
        for (i in on.indices) {
            while (j + 1 < dates.size && dates[j + 1] <= on[i]) j++
            if (j >= 0) out[i] = values[j]
        }
        return out
    }

    fun shifted(move: (LocalDate) -> LocalDate): Line {
        val pairs = dates.indices.map { move(dates[it]) to values[it] }.sortedBy { it.first }
        // Two dates landing on the same day keep the later value, as pandas' duplicated(keep="last").
        val merged = LinkedHashMap<LocalDate, Double>()
        pairs.forEach { (d, v) -> merged[d] = v }
        return Line(merged.keys.toList(), merged.values.toDoubleArray())
    }

    companion object {
        /** From unsorted (date, value) pairs; NaNs dropped, the last of duplicate dates kept. */
        fun of(pairs: List<Pair<LocalDate, Double>>): Line {
            val m = java.util.TreeMap<LocalDate, Double>()
            pairs.forEach { (d, v) -> if (!v.isNaN()) m[d] = v }
            return Line(m.keys.toList(), m.values.toDoubleArray())
        }
    }
}

/**
 * Everything the strategies read, made the same way as the Colab engine's `assemble`:
 * the T-bill [rf] (accrued on the day it applies), [gvz] (decimal), and the US-minus-Japan
 * 3-month rate gap twice over: [gapAccrual] for daily carry and [gapPublished] as you could
 * have known it at each close (daily rates the next business day, monthly ones 15 days after
 * the month ends).
 */
class DeskData(
    val prices: Map<String, Bars>,
    val rf: Line,
    val gvz: Line,
    val gapAccrual: Line,
    val gapPublished: Line,
    val japan: Line,
    /** Every other series by id, as it happened (for carry), and as it could be known at each close (for signals). */
    val raw: Map<String, Line> = emptyMap(),
    val known: Map<String, Line> = emptyMap()
) {
    val lastDate: LocalDate get() = prices.values.maxOf { it.last }

    private val none = Line(emptyList(), DoubleArray(0))

    /** A series as you could have known it at each close (rates lagged to when they were published). */
    fun knownLine(id: String): Line = known[id] ?: none

    /** A series for daily carry: one by id, or "gap:A:B" for A minus B. */
    fun accrual(id: String): Line {
        if (id.startsWith("gap:")) {
            val p = id.split(":")
            if (p.size == 3) return gapOf(raw[p[1]] ?: none, raw[p[2]] ?: none)
        }
        return raw[id] ?: none
    }

    companion object {
        fun assemble(prices: Map<String, Bars>, us: Line, japan: Line, gvz: Line, more: Map<String, Pair<Line, Line>> = emptyMap()): DeskData =
            DeskData(
                prices = prices,
                rf = us,
                gvz = gvz,
                gapAccrual = gapOf(us, japan),
                gapPublished = gapOf(publishedDaily(us), publishedMonthly(japan)),
                japan = japan,
                raw = more.mapValues { it.value.first } + mapOf("DTB3" to us, "JP3M" to japan, "GVZ" to gvz),
                known = more.mapValues { it.value.second } + mapOf("DTB3" to publishedDaily(us), "JP3M" to publishedMonthly(japan), "GVZ" to gvz)
            )

        /** Daily H.15 rates appear the next business day. */
        fun publishedDaily(s: Line): Line = s.shifted(DeskMath::nextBusinessDay)

        /** Weekly series are dated by their week but published about a week later. */
        fun publishedWeekly(s: Line): Line = s.shifted { d -> DeskMath.nextBusinessDay(d.plusDays(6)) }

        /** Monthly averages are dated the 1st but known only 15 days after the month ends. */
        fun publishedMonthly(s: Line, daysAfter: Long = 15): Line =
            s.shifted { d -> d.withDayOfMonth(d.lengthOfMonth()).plusDays(daysAfter) }

        /** a − b on every date either has, each carried forward; dates where either is unknown dropped. */
        fun gapOf(a: Line, b: Line): Line {
            val all = (a.dates + b.dates).distinct().sorted()
            val av = a.asOf(all)
            val bv = b.asOf(all)
            val keep = all.indices.filter { !av[it].isNaN() && !bv[it].isNaN() }
            return Line(keep.map { all[it] }, DoubleArray(keep.size) { av[keep[it]] - bv[keep[it]] })
        }
    }
}
