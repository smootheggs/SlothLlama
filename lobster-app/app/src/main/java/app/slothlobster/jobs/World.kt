package app.slothlobster.jobs

import app.slothlobster.web.Web
import app.slothlobster.web.WebException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder

/*
 * The crowd's prices for SlothLlama's World page, as a "slothworld" file: the markets you follow on
 * two prediction markets, read through their public market-data APIs (no key, no account, no
 * wallet; nothing is ever traded or sent about you).
 *
 *   polymarket:<event-slug>    Polymarket's Gamma API (gamma-api.polymarket.com)
 *   kalshi:<EVENT-TICKER>      Kalshi's public trade API (api.elections.kalshi.com), an event or
 *                              one market by its ticker
 *
 * Each market comes back with its yes price as a chance, whether it's open, closed or settled,
 * its result once settled, and its rules as the market states them.
 */

@Serializable
data class WorldBundle(
    val format: String = "slothworld",
    val v: Int = 1,
    @SerialName("made_at") val madeAt: String,
    @SerialName("made_by") val madeBy: String,
    val markets: List<WorldMarket>
)

@Serializable
data class WorldMarket(
    /** "polymarket" or "kalshi". */
    val platform: String,
    /** The market's own id (Polymarket) or ticker (Kalshi). */
    val id: String,
    /** The ref it was followed by ("polymarket:fed-decision-in-december"). */
    val ref: String,
    @SerialName("event_title") val eventTitle: String = "",
    val question: String,
    /** The yes price as a chance, 0–1; null when it can't be read. */
    val chance: Double? = null,
    /** "open", "closed" (trading over, not yet settled) or "settled". */
    val status: String = "open",
    /** "yes" or "no" once settled; blank otherwise. */
    val result: String = "",
    /** How it settles, as the market states it (shortened). */
    val rules: String = "",
    /** When trading ends (ISO), as given. */
    @SerialName("end_date") val endDate: String = "",
    val url: String = ""
)

object World {

    private const val MAX_REFS = 24
    private const val MAX_MARKETS_PER_EVENT = 12
    private const val GAMMA = "https://gamma-api.polymarket.com"
    private const val KALSHI = "https://api.elections.kalshi.com/trade-api/v2"
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val REF = Regex("""\b(polymarket|kalshi):([A-Za-z0-9._-]{1,120})""")

    /** The refs an order names ("World: polymarket:some-slug kalshi:KXFED-26DEC"), in order, once each. */
    fun request(text: String): List<String> =
        REF.findAll(text).map { m ->
            val platform = m.groupValues[1]
            val id = m.groupValues[2].trimEnd('.', ',')
            "$platform:" + if (platform == "kalshi") id.uppercase() else id.lowercase()
        }.distinct().take(MAX_REFS).toList()

    class Gathered(val bundle: WorldBundle, val notes: List<String>)

    suspend fun gather(refs: List<String>, version: String, log: (String, String) -> Unit): Gathered {
        val out = ArrayList<WorldMarket>()
        val notes = ArrayList<String>()
        for (ref in refs) {
            try {
                val markets = if (ref.startsWith("polymarket:")) polymarket(ref) else kalshi(ref)
                if (markets.isEmpty()) throw WebException("no markets found")
                out += markets
                log(ref, "${markets.size} market${if (markets.size == 1) "" else "s"}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val why = (e.message ?: e.javaClass.simpleName).take(160)
                notes += "$ref: $why"
                log(ref, "skipped: $why")
            }
            delay(300)
        }
        if (out.isEmpty()) throw WebException("No markets came back. " + notes.joinToString("; "))
        return Gathered(WorldBundle(madeAt = java.time.Instant.now().toString(), madeBy = "SlothLobster $version", markets = out), notes)
    }

    fun encode(b: WorldBundle): String = json.encodeToString(WorldBundle.serializer(), b)

    fun summary(g: Gathered): String = buildString {
        append("The crowd's prices for World. Open World in SlothLlama and it takes them in.\n\n")
        g.bundle.markets.take(30).forEach { m ->
            val p = m.chance?.let { "${Math.round(it * 100)}%" } ?: "—"
            val state = when (m.status) { "settled" -> " · settled ${m.result}"; "closed" -> " · closed"; else -> "" }
            append("- ${m.question.take(100)}: $p$state (${m.platform})\n")
        }
        if (g.notes.isNotEmpty()) {
            append("\nNot fetched:\n")
            g.notes.forEach { append("- ").append(it).append('\n') }
        }
    }.trim()

    // --- reading JSON ---

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull() }
    private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.contentOrNull?.let { it == "true" }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun short(s: String?, n: Int = 600): String = s.orEmpty().replace(Regex("""\s+"""), " ").trim().take(n)

    // --- Polymarket ---

    /** Polymarket sends outcomes and prices as JSON written inside a string ("[\"Yes\", \"No\"]"). */
    internal fun listIn(e: JsonElement?): List<String> {
        val inner = when (e) {
            is JsonArray -> e
            is JsonPrimitive -> e.contentOrNull?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonArray
            else -> null
        } ?: return emptyList()
        return inner.mapNotNull { it.str() }
    }

    /** One Gamma market, or null if it isn't a yes-or-no market we can read. */
    internal fun polymarketMarket(m: JsonObject, ref: String, eventTitle: String, eventSlug: String): WorldMarket? {
        val id = m["id"].str() ?: return null
        val outcomes = listIn(m["outcomes"])
        val prices = listIn(m["outcomePrices"]).map { it.toDoubleOrNull() }
        val yes = outcomes.indexOfFirst { it.equals("yes", ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        val chance = prices.getOrNull(yes)?.takeIf { it in 0.0..1.0 }
        val closed = m["closed"].bool() == true
        val result = if (!closed || chance == null) "" else when {
            chance >= 0.99 -> "yes"
            chance <= 0.01 -> "no"
            else -> ""
        }
        val label = m["groupItemTitle"].str()?.takeIf { it.isNotBlank() }
        val question = m["question"].str() ?: label ?: return null
        return WorldMarket(
            platform = "polymarket",
            id = id,
            ref = ref,
            eventTitle = eventTitle,
            question = short(question, 240),
            chance = chance,
            status = when { result.isNotEmpty() -> "settled"; closed -> "closed"; else -> "open" },
            result = result,
            rules = short(m["description"].str()),
            endDate = m["endDate"].str() ?: m["endDateIso"].str().orEmpty(),
            url = "https://polymarket.com/event/$eventSlug"
        )
    }

    private suspend fun polymarket(ref: String): List<WorldMarket> {
        val slug = ref.substringAfter(':')
        val events = json.parseToJsonElement(Web.get("$GAMMA/events?slug=${enc(slug)}", asBrowser = false).text).arr()
        val event = events?.firstOrNull().obj()
        if (event != null) {
            val title = event["title"].str().orEmpty()
            val markets = event["markets"].arr().orEmpty().mapNotNull { it.obj() }
                .filter { it["active"].bool() != false || it["closed"].bool() == true }
                .mapNotNull { polymarketMarket(it, ref, title, slug) }
            // An event with many markets (a field of candidates): the likeliest ones.
            return markets.sortedByDescending { it.chance ?: 0.0 }.take(MAX_MARKETS_PER_EVENT)
        }
        // Not an event's slug: perhaps one market's.
        val market = json.parseToJsonElement(Web.get("$GAMMA/markets?slug=${enc(slug)}", asBrowser = false).text).arr()?.firstOrNull().obj()
            ?: throw WebException("Polymarket has no event or market called $slug")
        return listOfNotNull(polymarketMarket(market, ref, "", slug))
    }

    // --- Kalshi ---

    /** One Kalshi market: the yes chance from the bid and ask's middle, else the last trade. */
    internal fun kalshiMarket(m: JsonObject, ref: String, eventTitle: String, eventTicker: String): WorldMarket? {
        val ticker = m["ticker"].str() ?: return null
        val bid = m["yes_bid_dollars"].num()
        val ask = m["yes_ask_dollars"].num()
        val last = m["last_price_dollars"].num()
        val chance = when {
            bid != null && ask != null && bid > 0 && ask > 0 && ask >= bid -> (bid + ask) / 2
            last != null && last > 0 -> last
            else -> null
        }?.coerceIn(0.0, 1.0)
        val status = m["status"].str().orEmpty()
        val result = m["result"].str().orEmpty().lowercase().takeIf { it == "yes" || it == "no" }.orEmpty()
        val settled = result.isNotEmpty() && status in setOf("determined", "finalized", "settled", "amended")
        // The market's own question where it has one ("Will the Federal Reserve cut rates by >25bps…?"),
        // else the event and the option ("Fed decision in Dec 2026 · Cut >25bps").
        val sub = m["yes_sub_title"].str()?.takeIf { it.isNotBlank() }
        val question = m["title"].str()?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(eventTitle.trimEnd('?').takeIf { it.isNotBlank() }, sub).joinToString(" · ").ifBlank { ticker }
        return WorldMarket(
            platform = "kalshi",
            id = ticker,
            ref = ref,
            eventTitle = eventTitle,
            question = short(question, 240),
            chance = chance,
            status = when { settled -> "settled"; status == "active" || status == "initialized" -> "open"; else -> "closed" },
            result = if (settled) result else "",
            rules = short(m["rules_primary"].str()),
            endDate = m["close_time"].str().orEmpty(),
            url = "https://kalshi.com/markets/${eventTicker.lowercase()}"
        )
    }

    private suspend fun kalshi(ref: String): List<WorldMarket> {
        val ticker = ref.substringAfter(':')
        val asEvent = runCatching { Web.get("$KALSHI/events/${enc(ticker)}?with_nested_markets=true", asBrowser = false).text }.getOrNull()
        if (asEvent != null) {
            val root = json.parseToJsonElement(asEvent).obj()
            val event = root?.get("event").obj()
            if (event != null) {
                val title = event["title"].str().orEmpty()
                val markets = (event["markets"].arr() ?: root?.get("markets").arr()).orEmpty().mapNotNull { it.obj() }
                    .mapNotNull { kalshiMarket(it, ref, title, ticker) }
                if (markets.isNotEmpty()) return markets.sortedByDescending { it.chance ?: 0.0 }.take(MAX_MARKETS_PER_EVENT)
            }
        }
        val one = json.parseToJsonElement(Web.get("$KALSHI/markets/${enc(ticker)}", asBrowser = false).text).obj()?.get("market").obj()
            ?: throw WebException("Kalshi has no event or market $ticker")
        return listOfNotNull(kalshiMarket(one, ref, "", one["event_ticker"].str() ?: ticker))
    }
}
