package app.localmind.assistant.domain.world

import app.localmind.assistant.domain.forecasts.Call
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One market as the Lobster read it (SlothLobster's World job writes the same shape). */
@Serializable
data class WorldMarket(
    /** "polymarket" or "kalshi". */
    val platform: String,
    val id: String,
    /** The ref it was followed by ("polymarket:some-event"). */
    val ref: String,
    @SerialName("event_title") val eventTitle: String = "",
    val question: String,
    /** The crowd's chance of yes, 0–1. */
    val chance: Double? = null,
    /** "open", "closed" (trading over, not settled yet) or "settled". */
    val status: String = "open",
    /** "yes" or "no" once settled. */
    val result: String = "",
    val rules: String = "",
    @SerialName("end_date") val endDate: String = "",
    val url: String = ""
)

@Serializable
data class WorldBundle(
    val format: String = WorldData.FORMAT,
    val v: Int = 1,
    @SerialName("made_at") val madeAt: String = "",
    @SerialName("made_by") val madeBy: String = "",
    val markets: List<WorldMarket> = emptyList()
)

/** The crowd's chance on a day, kept to show how it moved. */
@Serializable
data class Point(val on: String, val chance: Double)

/**
 * Yours, for World: the refs you follow, the markets as last read, a day-by-day history of the
 * crowd's chance, and the Lobster news watches tied to your calls (order id → call id).
 */
@Serializable
data class WorldYours(
    val follow: List<String> = emptyList(),
    /** "platform:id" → the market as last read. */
    val markets: Map<String, WorldMarket> = emptyMap(),
    val history: Map<String, List<Point>> = emptyMap(),
    val lastAskedAt: Long = 0,
    val lastTakenAt: Long = 0,
    /** A Lobster watch's order id → the call it watches the news for. */
    val watches: Map<String, String> = emptyMap(),
    /** Alerts already shown as news on a call. */
    val seenNews: List<String> = emptyList()
)

/**
 * World: events you follow on prediction markets, with the crowd's chance as the reference your
 * calls are measured against. The Lobster reads the public prices; the tablet keeps them, works out
 * how they moved, and proposes a settlement when a market settles (you confirm it).
 */
object WorldData {
    const val FORMAT = "slothworld"
    const val KEEP_DAYS = 120L
    const val MAX_FOLLOW = 24
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val REF = Regex("""^(polymarket|kalshi):([A-Za-z0-9._-]{1,120})$""")
    private val POLY = Regex("""polymarket\.com/(?:[a-z]{2}/)?(?:event|market)/([A-Za-z0-9._-]{1,120})""", RegexOption.IGNORE_CASE)
    private val KALSHI = Regex("""kalshi\.com/(?:markets|events)/([^\s?#]+)""", RegexOption.IGNORE_CASE)

    fun key(m: WorldMarket): String = "${m.platform}:${m.id}"

    fun source(m: WorldMarket): String = "world:${key(m)}"

    /** The market key a call is on ("polymarket:123"), or null if it isn't a World call. */
    fun keyOf(c: Call): String? = c.source.takeIf { it.startsWith("world:") }?.removePrefix("world:")

    /**
     * A ref from what you paste: "polymarket:slug" or "kalshi:TICKER" as is, a Polymarket event link,
     * or a Kalshi market link (its last part is the event's ticker). Null if it's neither.
     */
    fun refOf(text: String): String? {
        val t = text.trim()
        REF.matchEntire(t)?.let { m ->
            val platform = m.groupValues[1]
            return "$platform:" + if (platform == "kalshi") m.groupValues[2].uppercase() else m.groupValues[2].lowercase()
        }
        POLY.find(t)?.let { return "polymarket:${it.groupValues[1].lowercase()}" }
        KALSHI.find(t)?.let { m ->
            val last = m.groupValues[1].trimEnd('/').substringAfterLast('/')
            if (last.matches(Regex("""[A-Za-z0-9._-]{1,120}"""))) return "kalshi:${last.uppercase()}"
        }
        return null
    }

    fun parse(text: String): WorldBundle {
        val b = json.decodeFromString(WorldBundle.serializer(), text)
        require(b.format == FORMAT) { "Not a World file" }
        return b
    }

    fun encode(y: WorldYours): String = json.encodeToString(WorldYours.serializer(), y)
    fun decode(text: String): WorldYours = json.decodeFromString(WorldYours.serializer(), text)

    /**
     * Lays a bundle over what's kept: each market as now read, and today's crowd chance added to its
     * history (one point a day; a later read the same day replaces it). History older than
     * [KEEP_DAYS] goes.
     */
    fun take(y: WorldYours, b: WorldBundle, today: LocalDate, now: Long): WorldYours {
        val markets = y.markets.toMutableMap()
        val history = y.history.toMutableMap()
        val day = today.toString()
        val oldest = today.minusDays(KEEP_DAYS).toString()
        for (m in b.markets) {
            val k = key(m)
            markets[k] = m
            val c = m.chance ?: continue
            val kept = history[k].orEmpty().filter { it.on != day && it.on >= oldest }
            history[k] = kept + Point(day, c)
        }
        return y.copy(markets = markets, history = history.mapValues { (_, v) -> v.filter { it.on >= oldest } }, lastTakenAt = now)
    }

    /** The markets of the refs you follow, likeliest first within each ref, in the order you followed them. */
    fun followed(y: WorldYours): List<WorldMarket> =
        y.follow.flatMap { ref -> y.markets.values.filter { it.ref == ref }.sortedByDescending { it.chance ?: 0.0 } }

    /** How the crowd moved over the last week: today's chance less the one a week ago (or the earliest since). */
    fun weekChange(history: List<Point>, today: LocalDate): Double? {
        if (history.size < 2) return null
        val sorted = history.sortedBy { it.on }
        val weekAgo = today.minusDays(7).toString()
        val before = sorted.lastOrNull { it.on <= weekAgo } ?: sorted.first()
        val last = sorted.last()
        if (before === last) return null
        return last.chance - before.chance
    }

    /** What a settled market says happened to a call on it: 0 yes, 1 no; null while it isn't settled. */
    fun settlement(m: WorldMarket?): Int? {
        if (m == null || m.status != "settled") return null
        return when (m.result) {
            "yes" -> 0
            "no" -> 1
            else -> null
        }
    }

    /** The check date for a call on [m]: when trading ends, if that's ahead; else in three months. */
    fun checkOnFor(m: WorldMarket, today: LocalDate): String {
        val end = runCatching { LocalDate.parse(m.endDate.take(10)) }.getOrNull()
        return (end?.takeIf { it.isAfter(today) } ?: today.plusDays(91)).toString()
    }

    /** Whether it's time to ask the Lobster again: never asked, or the last ask is over [hours] old. */
    fun stale(y: WorldYours, now: Long, hours: Long = 6): Boolean =
        y.follow.isNotEmpty() && now - maxOf(y.lastAskedAt, y.lastTakenAt) > hours * 3_600_000L

    /** Days since [on] ("yyyy-MM-dd"), for "news this morning" and the like. */
    fun daysSince(on: String, today: LocalDate): Long? = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(on), today) }.getOrNull()
}
