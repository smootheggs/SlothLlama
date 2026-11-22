package app.slothlobster.jobs

import app.slothlobster.web.Web
import app.slothlobster.web.WebException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * Daily prices for SlothDesk, as a "slothdesk-prices" file (the same format Colab's
 * export_prices writes; android-project/docs/LOBSTER_MAILBOX.md describes it).
 *
 *   GLD, SLV, SPY … (any US ticker)  Tiingo end-of-day, adjusted open, high, low and close (needs a free key)
 *   crypto:BTCUSD (1.13)             Tiingo crypto, daily bars every day of the week (needs the key)
 *   fx:EURUSD (1.13)                 Tiingo FX daily bars, weekdays (needs the key)
 *   fred:DFII10 (1.13)               any FRED series, as a "series" divided by 100 (no key)
 *   USDJPY                           Tiingo FX daily bars; without a key, FRED's DEXJPUS noon rate
 *   DEXJPUS                          FRED's USD/JPY noon rate on its own (no key): a second source
 *   DTB3                             FRED, 3-month T-bill, daily (no key)
 *   JP3M                             FRED IR3TIB01JPM156N, Japan 3-month rate, monthly (no key)
 *   GVZ                              Cboe gold volatility index, daily (no key)
 *
 * Only these public data services are called, with nothing about you but the key, sent in a
 * header (never in a link, so it can't end up in the Log).
 */

@Serializable
data class PriceBundle(
    val format: String = "slothdesk-prices",
    val v: Int = 1,
    @SerialName("made_at") val madeAt: String,
    @SerialName("made_by") val madeBy: String,
    val series: Map<String, PriceSeries>
)

@Serializable
data class PriceSeries(
    val kind: String,
    val source: String,
    val adjusted: Boolean = false,
    val freq: String = "",
    val dates: List<String>,
    @SerialName("open") val opens: List<Double> = emptyList(),
    @SerialName("close") val closes: List<Double> = emptyList(),
    val values: List<Double> = emptyList(),
    /** (1.13) Daily highs and lows, where the source has them; SlothLlama checks they fit the open and close. */
    @SerialName("high") val highs: List<Double> = emptyList(),
    @SerialName("low") val lows: List<Double> = emptyList()
)

object Prices {

    private const val MAX_SERIES = 24
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val TICKER = Regex("""\b[A-Z][A-Z0-9.]{0,9}\b""")
    /** (1.13) "crypto:BTCUSD", "fx:EURUSD", "fred:DFII10": a name and where to fetch it. */
    private val TYPED = Regex("""\b(crypto|fx|fred):([A-Za-z0-9_.^-]{1,24})""", RegexOption.IGNORE_CASE)
    private val PAIR = Regex("""^[A-Z]{6,8}$""")
    private val FRED_ID = Regex("""^[A-Z0-9_]{2,24}$""")
    private val SINCE = Regex("""\bfrom\s+(\d{4}-\d{2}-\d{2})\b""", RegexOption.IGNORE_CASE)
    private val NOT_TICKERS = setOf("PRICES", "FOR", "SLOTHDESK", "FROM", "AND", "THE", "DAILY", "EVERY", "AT", "ON")
    private val US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy")

    /** Where a name is fetched from. */
    enum class Source { NAMED, CRYPTO, FX, FRED }

    /** One series an order asks for: its name in the file, and where it comes from. */
    data class Ask(val name: String, val source: Source)

    /** What an order asks for: the series names and the first date (names only, as before 1.13). */
    fun request(text: String): Pair<List<String>, LocalDate> = asks(text).let { (a, since) -> a.map { it.name } to since }

    /**
     * What an order asks for, each with its source, and the first date. Typed names come first
     * as written ("crypto:BTCUSD"); every other capitalised word is a ticker or a named series.
     * Names that aren't plain capitals and digits are left out, as is anything past [MAX_SERIES].
     */
    fun asks(text: String): Pair<List<Ask>, LocalDate> {
        val since = SINCE.find(text)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(2008, 1, 1)
        val found = ArrayList<Pair<Int, Ask>>()
        for (m in TYPED.findAll(text)) {
            val name = m.groupValues[2].uppercase()
            val ask = when (m.groupValues[1].lowercase()) {
                "crypto" -> if (PAIR.matches(name)) Ask(name, Source.CRYPTO) else null
                "fx" -> if (PAIR.matches(name)) Ask(name, Source.FX) else null
                else -> if (FRED_ID.matches(name)) Ask(name, Source.FRED) else null
            }
            if (ask != null) found += m.range.first to ask
        }
        // The rest of the order, typed names blanked out (same length, so positions hold).
        val rest = TYPED.replace(text) { " ".repeat(it.value.length) }
        for (m in TICKER.findAll(rest)) if (m.value !in NOT_TICKERS) found += m.range.first to Ask(m.value, Source.NAMED)
        val out = LinkedHashMap<String, Ask>()
        for ((_, ask) in found.sortedBy { it.first }) if (ask.name !in out) out[ask.name] = ask
        return out.values.take(MAX_SERIES) to since
    }

    class Gathered(val bundle: PriceBundle, val notes: List<String>)

    /** Fetches each series; one that fails is noted and skipped. Throws if none came back. */
    suspend fun gather(names: List<String>, since: LocalDate, tiingoKey: String?, version: String, log: (String, String) -> Unit): Gathered =
        gatherAsks(names.map { Ask(it, Source.NAMED) }, since, tiingoKey, version, log)

    suspend fun gatherAsks(asks: List<Ask>, since: LocalDate, tiingoKey: String?, version: String, log: (String, String) -> Unit): Gathered {
        val out = LinkedHashMap<String, PriceSeries>()
        val notes = ArrayList<String>()
        fun key(): String = tiingoKey ?: throw WebException("needs a Tiingo key (SlothLobster settings, Market data)")
        for (ask in asks) {
            val name = ask.name
            try {
                val s = when (ask.source) {
                    Source.CRYPTO -> tiingoCrypto(name.lowercase(), since, key())
                    Source.FX -> tiingoFx(name.lowercase(), since, key())
                    Source.FRED -> fredSeries(name, since)
                    Source.NAMED -> when (name) {
                        "DTB3" -> fredRate("DTB3", "daily", since)
                        "JP3M" -> fredRate("IR3TIB01JPM156N", "monthly", since)
                        "GVZ" -> cboe("GVZ", since)
                        "USDJPY" -> if (tiingoKey != null) tiingoFx("usdjpy", since, tiingoKey) else fredFx("DEXJPUS", since)
                        // (1.10) FRED's noon rate on its own: SlothLlama's second source for USD/JPY price calls.
                        "DEXJPUS" -> fredFx("DEXJPUS", since)
                        else -> tiingoDaily(name, since, key())
                    }
                }
                require(s.dates.size >= 20) { "too few days came back" }
                out[name] = s
                log(name, "${s.dates.size} days to ${s.dates.last()} (${s.source})")
            } catch (e: Exception) {
                val why = (e.message ?: e.javaClass.simpleName).take(160)
                notes += "$name: $why"
                log(name, "skipped: $why")
            }
        }
        if (out.isEmpty()) throw WebException("No prices came back. " + notes.joinToString("; "))
        return Gathered(PriceBundle(madeAt = java.time.Instant.now().toString(), madeBy = "SlothLobster $version", series = out), notes)
    }

    fun encode(b: PriceBundle): String = json.encodeToString(PriceBundle.serializer(), b)

    /** A short Markdown summary for the catch: each series' last value and date. */
    fun summary(g: Gathered): String = buildString {
        append("Prices for SlothDesk. Open the SlothDesk page in SlothLlama and it takes them in.\n\n")
        g.bundle.series.forEach { (name, s) ->
            val last = if (s.kind == "bars") s.closes.last() else s.values.last()
            val shown = if (s.kind == "bars") "%.2f".format(last) else "%.2f%%".format(last * 100)
            val range = if (s.highs.isNotEmpty()) " · highs and lows" else ""
            append("- **$name** $shown on ${s.dates.last()} · ${s.dates.size} days · ${s.source}$range\n")
        }
        if (g.notes.isNotEmpty()) {
            append("\nNot fetched:\n")
            g.notes.forEach { append("- ").append(it).append('\n') }
        }
    }.trim()

    // --- sources ---

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun tiingoDaily(ticker: String, since: LocalDate, key: String): PriceSeries {
        val url = "https://api.tiingo.com/tiingo/daily/${enc(ticker.lowercase())}/prices?startDate=$since&format=json"
        val rows = tiingoRows(url, key)
        val b = BarsBuilder()
        for (r in rows) {
            val date = r["date"]?.jsonPrimitive?.contentOrNull?.take(10) ?: continue
            b.add(date, num(r, "adjOpen") ?: continue, num(r, "adjHigh"), num(r, "adjLow"), num(r, "adjClose") ?: continue)
        }
        return b.series("tiingo", adjusted = true)
    }

    private suspend fun tiingoFx(pair: String, since: LocalDate, key: String): PriceSeries {
        val url = "https://api.tiingo.com/tiingo/fx/${enc(pair)}/prices?startDate=$since&resampleFreq=1day"
        val rows = tiingoRows(url, key)
        val b = BarsBuilder()
        for (r in rows) {
            val date = r["date"]?.jsonPrimitive?.contentOrNull?.take(10) ?: continue
            // FX trades through the weekend's edges; the Desk works on weekdays.
            if (LocalDate.parse(date).dayOfWeek.value >= 6) continue
            b.add(date, num(r, "open") ?: continue, num(r, "high"), num(r, "low"), num(r, "close") ?: continue)
        }
        return b.series("tiingo fx")
    }

    /**
     * (1.13) Tiingo's crypto prices: daily bars, every day of the week (crypto never closes),
     * as one ticker's "priceData". Days are UTC.
     */
    private suspend fun tiingoCrypto(pair: String, since: LocalDate, key: String): PriceSeries {
        val url = "https://api.tiingo.com/tiingo/crypto/prices?tickers=${enc(pair)}&startDate=$since&resampleFreq=1day"
        val top = tiingoRows(url, key)
        val rows = top.firstOrNull()?.get("priceData") as? JsonArray ?: throw WebException("Tiingo has no crypto prices for $pair")
        val b = BarsBuilder()
        for (r in rows.mapNotNull { it as? JsonObject }) {
            val date = r["date"]?.jsonPrimitive?.contentOrNull?.take(10) ?: continue
            b.add(date, num(r, "open") ?: continue, num(r, "high"), num(r, "low"), num(r, "close") ?: continue)
        }
        return b.series("tiingo crypto")
    }

    private fun num(r: JsonObject, field: String): Double? = r[field]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }

    /** Daily bars as they come, with highs and lows only if every day has them. */
    private class BarsBuilder {
        val d = ArrayList<String>(); val o = ArrayList<Double>(); val h = ArrayList<Double>(); val l = ArrayList<Double>(); val c = ArrayList<Double>()
        var ranged = true
        fun add(date: String, open: Double, high: Double?, low: Double?, close: Double) {
            // Two rows for one day (a feed's overlap) keep the later.
            if (d.isNotEmpty() && d.last() == date) { d.removeAt(d.size - 1); o.removeAt(o.size - 1); c.removeAt(c.size - 1); if (h.size > d.size) { h.removeAt(h.size - 1); l.removeAt(l.size - 1) } }
            d += date; o += open; c += close
            if (high == null || low == null) ranged = false else { h += high; l += low }
        }
        fun series(source: String, adjusted: Boolean = false) = PriceSeries(
            "bars", source, adjusted = adjusted, dates = d, opens = o, closes = c,
            highs = if (ranged && h.size == d.size) h else emptyList(), lows = if (ranged && l.size == d.size) l else emptyList()
        )
    }

    private suspend fun tiingoRows(url: String, key: String): List<JsonObject> {
        val page = Web.get(url, asBrowser = false, headers = mapOf("Authorization" to "Token $key", "Content-Type" to "application/json"))
        val el = runCatching { json.parseToJsonElement(page.text) }.getOrNull()
            ?: throw WebException("Tiingo sent something that isn't data")
        if (el is JsonObject) throw WebException("Tiingo: " + (el["detail"]?.jsonPrimitive?.contentOrNull ?: "refused").take(120))
        return (el as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    /** FRED's public CSV ("." marks a day without a value). Percent → decimal. */
    private suspend fun fredCsv(id: String, since: LocalDate): List<Pair<String, Double>> {
        val page = Web.get("https://fred.stlouisfed.org/graph/fredgraph.csv?id=${enc(id)}&cosd=$since")
        return page.text.lineSequence().drop(1).mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size < 2) return@mapNotNull null
            val date = parts[0].trim()
            val v = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (runCatching { LocalDate.parse(date) }.getOrNull()?.let { it >= since } != true) return@mapNotNull null
            date to v
        }.toList()
    }

    private suspend fun fredRate(id: String, freq: String, since: LocalDate): PriceSeries {
        val rows = fredCsv(id, since.minusMonths(2))
        return PriceSeries("rate", "fred:$id", freq = freq, dates = rows.map { it.first }, values = rows.map { it.second / 100.0 })
    }

    /**
     * (1.13) Any FRED series, read like a rate (divided by 100: percents become decimals, an
     * index in points becomes hundreds), with how often it comes: daily, weekly or monthly,
     * judged from the gaps between its dates. SlothLlama uses it only from when it was published.
     */
    private suspend fun fredSeries(id: String, since: LocalDate): PriceSeries {
        val rows = fredCsv(id, since.minusMonths(2))
        if (rows.isEmpty()) throw WebException("FRED has no series $id")
        val dates = rows.map { LocalDate.parse(it.first) }
        val gaps = dates.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b) }.sorted()
        val gap = gaps.getOrNull(gaps.size / 2) ?: 1
        val freq = when { gap <= 4 -> "daily"; gap <= 10 -> "weekly"; else -> "monthly" }
        return PriceSeries("series", "fred:$id", freq = freq, dates = rows.map { it.first }, values = rows.map { it.second / 100.0 })
    }

    private suspend fun fredFx(id: String, since: LocalDate): PriceSeries {
        val rows = fredCsv(id, since)
        return PriceSeries("bars", "fred:$id (noon rate; open = close)", dates = rows.map { it.first },
            opens = rows.map { it.second }, closes = rows.map { it.second })
    }

    /** A Cboe index history (DATE, OPEN, HIGH, LOW, CLOSE; dates as 01/31/2024). Points → decimal. */
    private suspend fun cboe(index: String, since: LocalDate): PriceSeries {
        val page = Web.get("https://cdn.cboe.com/api/global/us_indices/daily_prices/${enc(index)}_History.csv")
        val lines = page.text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.uppercase()?.split(',')?.map { it.trim() } ?: throw WebException("Cboe sent nothing")
        val di = header.indexOf("DATE").takeIf { it >= 0 } ?: 0
        val ci = header.indexOf("CLOSE").takeIf { it >= 0 } ?: (header.size - 1)
        val rows = java.util.TreeMap<LocalDate, Double>()
        for (line in lines.drop(1)) {
            val p = line.split(',')
            if (p.size <= maxOf(di, ci)) continue
            val raw = p[di].trim()
            val d = runCatching { LocalDate.parse(raw) }.getOrNull() ?: runCatching { LocalDate.parse(raw, US_DATE) }.getOrNull() ?: continue
            val v = p[ci].trim().toDoubleOrNull() ?: continue
            if (d >= since) rows[d] = v / 100.0
        }
        return PriceSeries("vol", "cboe:$index", freq = "daily", dates = rows.keys.map { it.toString() }, values = rows.values.toList())
    }
}
