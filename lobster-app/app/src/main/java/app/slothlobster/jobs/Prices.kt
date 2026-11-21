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
 *   GLD, SLV, SPY … (any US ticker)  Tiingo end-of-day, adjusted open and close (needs a free key)
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
    val values: List<Double> = emptyList()
)

object Prices {

    private const val MAX_SERIES = 12
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val TICKER = Regex("""\b[A-Z][A-Z0-9.]{0,9}\b""")
    private val SINCE = Regex("""\bfrom\s+(\d{4}-\d{2}-\d{2})\b""", RegexOption.IGNORE_CASE)
    private val NOT_TICKERS = setOf("PRICES", "FOR", "SLOTHDESK", "FROM", "AND", "THE", "DAILY", "EVERY", "AT", "ON")
    private val US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy")

    /** What an order asks for: the series names and the first date. */
    fun request(text: String): Pair<List<String>, LocalDate> {
        val since = SINCE.find(text)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(2008, 1, 1)
        val words = TICKER.findAll(text).map { it.value }.filter { it !in NOT_TICKERS }.distinct().take(MAX_SERIES).toList()
        return words to since
    }

    class Gathered(val bundle: PriceBundle, val notes: List<String>)

    /** Fetches each series; one that fails is noted and skipped. Throws if none came back. */
    suspend fun gather(names: List<String>, since: LocalDate, tiingoKey: String?, version: String, log: (String, String) -> Unit): Gathered {
        val out = LinkedHashMap<String, PriceSeries>()
        val notes = ArrayList<String>()
        for (name in names) {
            try {
                val s = when (name) {
                    "DTB3" -> fredRate("DTB3", "daily", since)
                    "JP3M" -> fredRate("IR3TIB01JPM156N", "monthly", since)
                    "GVZ" -> cboe("GVZ", since)
                    "USDJPY" -> if (tiingoKey != null) tiingoFx("usdjpy", since, tiingoKey) else fredFx("DEXJPUS", since)
                    // (1.10) FRED's noon rate on its own: SlothLlama's second source for USD/JPY price calls.
                    "DEXJPUS" -> fredFx("DEXJPUS", since)
                    else -> {
                        if (tiingoKey == null) throw WebException("needs a Tiingo key (SlothLobster settings, Market data)")
                        tiingoDaily(name, since, tiingoKey)
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
            append("- **$name** $shown on ${s.dates.last()} · ${s.dates.size} days · ${s.source}\n")
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
        val d = ArrayList<String>(); val o = ArrayList<Double>(); val c = ArrayList<Double>()
        for (r in rows) {
            val date = r["date"]?.jsonPrimitive?.contentOrNull?.take(10) ?: continue
            val ao = r["adjOpen"]?.jsonPrimitive?.doubleOrNull ?: continue
            val ac = r["adjClose"]?.jsonPrimitive?.doubleOrNull ?: continue
            d += date; o += ao; c += ac
        }
        return PriceSeries("bars", "tiingo", adjusted = true, dates = d, opens = o, closes = c)
    }

    private suspend fun tiingoFx(pair: String, since: LocalDate, key: String): PriceSeries {
        val url = "https://api.tiingo.com/tiingo/fx/${enc(pair)}/prices?startDate=$since&resampleFreq=1day"
        val rows = tiingoRows(url, key)
        val d = ArrayList<String>(); val o = ArrayList<Double>(); val c = ArrayList<Double>()
        for (r in rows) {
            val date = r["date"]?.jsonPrimitive?.contentOrNull?.take(10) ?: continue
            val op = r["open"]?.jsonPrimitive?.doubleOrNull ?: continue
            val cl = r["close"]?.jsonPrimitive?.doubleOrNull ?: continue
            // FX trades through the weekend's edges; the Desk works on weekdays.
            if (LocalDate.parse(date).dayOfWeek.value >= 6) continue
            d += date; o += op; c += cl
        }
        return PriceSeries("bars", "tiingo fx", dates = d, opens = o, closes = c)
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
