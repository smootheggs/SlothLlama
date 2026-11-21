package app.localmind.assistant.domain.forecasts

import app.localmind.assistant.domain.trading.Idea
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Where a call belongs. The Watchlist holds the risks you keep an eye on (a briefing's, or yours). */
@Serializable
enum class Area(val label: String) {
    MARKETS("Markets"), SPORTS("Sports"), POLITICS("Politics"), WATCHLIST("Watchlist"), OTHER("Other")
}

@Serializable
enum class CallKind(val label: String, val what: String) {
    YES_NO("Yes or no", "one chance"),
    ONE_OF("One of several", "chances add to 100"),
    NUMBER("A number", "above or below a line")
}

/** Your chances on one day (one per outcome, adding to 1), with why. */
@Serializable
data class Chance(val on: String, val at: Long, val chances: List<Double>, val reason: String = "")

/**
 * A call: a question about what will happen, its outcomes, your chances (every change kept, with
 * its reason) and the day to check it. You mark what happened; the scores are worked out here.
 */
@Serializable
data class Call(
    val id: String,
    val question: String,
    val area: Area = Area.OTHER,
    val kind: CallKind = CallKind.YES_NO,
    /** The outcomes of a "one of several" call. */
    val options: List<String> = emptyList(),
    /** A number call's line, as you wrote it ("2,650"). */
    val line: String = "",
    /** yyyy-MM-dd. */
    val checkOn: String,
    val createdAt: Long,
    /** Oldest first. */
    val estimates: List<Chance> = emptyList(),
    /** A market's chances as you entered them, oldest first (reason = where from). */
    val market: List<Chance> = emptyList(),
    /** Which outcome happened (its index), once marked. */
    val happened: Int? = null,
    /** Marked as not counting (called off, or the question stopped making sense). */
    val void: Boolean = false,
    val markedOn: String = "",
    /** "you", "idea:<id>" (read from SlothDesk), "sports:…", "briefing", "price:<series>" or "world:…". */
    val source: String = "you",
    /** How you rated your reasoning once it was marked: "sound", "partly" or "no"; blank if you didn't. */
    val reasoning: String = "",
    /** (1.41) A price call: a level by a date, settled from SlothDesk's daily closes once you confirm. */
    val price: PriceTerms? = null,
    /** What settled it, as shown when you confirmed ("251.30 on 31 Dec 2026 · Tiingo"). */
    val evidence: String = ""
) {
    val outcomes: List<String> get() = Forecasts.outcomes(this)
    val open: Boolean get() = happened == null && !void
    val latest: Chance? get() = estimates.lastOrNull()
    /** Read from somewhere else (a SlothDesk idea): shown here, changed there. */
    val readOnly: Boolean get() = source.startsWith("idea:")
}

@Serializable
data class CallFile(val format: Int = Forecasts.FORMAT, val calls: List<Call> = emptyList())

/** One band of the calibration chart: what you said on average, and how often it happened. */
data class Band(val from: Int, val said: Double, val happened: Double, val count: Int)

data class Summary(
    val checked: Int,
    /** Average Brier score over checked calls (each over the days you held it); null until one. */
    val brier: Double?,
    val bands: List<Band>,
    val toCheck: Int,
    val open: Int,
    /** Checked calls that beat their reference (the market, the price or the crowd when you called), of those with one. */
    val beat: Int = 0,
    val withReference: Int = 0
)

/** Where a call was made: Weigh's three places, and your own. */
enum class Place(val label: String, val reference: String) {
    MARKETS("Markets", "the market"),
    SPORTS("Sports", "the market"),
    WORLD("World", "the crowd"),
    OWN("Your own", "the market")
}

/** One place's record: checked calls, their Brier score, and how many beat their reference. */
data class PlaceStat(val place: Place, val checked: Int, val open: Int, val brier: Double?, val beat: Int, val withReference: Int)

/**
 * Reasoning against result, for the calls you rated when you marked them: sound reasoning that
 * went your way, sound but unlucky, shaky but lucky, shaky and wrong. "Partly" counts as shaky.
 */
data class Luck(val soundRight: Int, val soundWrong: Int, val shakyRight: Int, val shakyWrong: Int) {
    val total: Int get() = soundRight + soundWrong + shakyRight + shakyWrong
}

/**
 * Forecasts: every call with a chance and a date, scored when it's marked. The Brier score is the
 * squared distance between your chances and what happened (0 never wrong; 0.25 is saying 50% on
 * every yes-or-no), averaged over each day you held the call, so being right early counts.
 */
object Forecasts {
    const val FORMAT = 1
    const val MAX_QUESTION = 240
    const val MAX_OPTIONS = 8

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun outcomes(c: Call): List<String> = when (c.kind) {
        CallKind.YES_NO -> listOf("Yes", "No")
        CallKind.NUMBER -> listOf("Above ${c.line}".trim(), "Below ${c.line}".trim())
        CallKind.ONE_OF -> c.options
    }

    fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** Percents as typed (any total) → chances adding to 1; null if there's nothing above 0. */
    fun normalise(percents: List<Double>): List<Double>? {
        val clean = percents.map { it.coerceAtLeast(0.0) }
        val total = clean.sum()
        if (total <= 0.0) return null
        return clean.map { it / total }
    }

    /** One yes-or-no chance → [yes, no], held a hair away from 0 and 1. */
    fun binary(chance: Double): List<Double> {
        val p = chance.coerceIn(0.01, 0.99)
        return listOf(p, 1 - p)
    }

    /** A whole percent for showing: 0.557 → 56. */
    fun percent(p: Double): Int = (p * 100).roundToInt()

    // --- scoring ---

    /** The Brier score of one set of chances: Σ (chance − what happened)², halved, so a yes-or-no scores (p − o)². */
    fun brier(chances: List<Double>, happened: Int): Double =
        chances.withIndex().sumOf { (i, p) -> val o = if (i == happened) 1.0 else 0.0; (p - o) * (p - o) } / 2.0

    /** The chances you held on [day]: the last ones set on or before it (the first ones before that). */
    fun heldOn(c: Call, day: LocalDate): Chance? =
        c.estimates.lastOrNull { e -> date(e.on)?.let { !it.isAfter(day) } == true } ?: c.estimates.firstOrNull()

    /** The day the call ended: when you marked it, else its check day. */
    private fun endOf(c: Call): LocalDate? = date(c.markedOn) ?: date(c.checkOn)

    /** The Brier score averaged over every day from your first chance to the day it was marked. */
    fun score(c: Call): Double? {
        val h = c.happened ?: return null
        if (c.void || c.estimates.isEmpty()) return null
        val start = date(c.estimates.first().on) ?: return brier(c.estimates.last().chances, h)
        val end = endOf(c)?.takeIf { !it.isBefore(start) } ?: start
        val days = ChronoUnit.DAYS.between(start, end).toInt()
        if (days <= 0) return brier(c.estimates.last().chances, h)
        var total = 0.0
        for (d in 0 until days) total += brier(heldOn(c, start.plusDays(d.toLong()))!!.chances, h)
        return total / days
    }

    /**
     * The calibration bands, 50–60% up to 90–100%. Each checked call counts once, by the outcome
     * you gave the biggest chance (a 30% yes is a 70% no), with the last chances you set.
     */
    fun bands(calls: List<Call>): List<Band> {
        val points = calls.filter { it.happened != null && !it.void && it.estimates.isNotEmpty() }.map { c ->
            val ch = c.estimates.last().chances
            val top = ch.indices.maxByOrNull { ch[it] } ?: 0
            ch[top] to (c.happened == top)
        }
        return (50..90 step 10).mapNotNull { from ->
            val these = points.filter { (p, _) ->
                val pct = p * 100
                pct >= from - 1e-9 && (pct < from + 10 - 1e-9 || from == 90)
            }
            if (these.isEmpty()) null else Band(from, these.map { it.first }.average(), these.count { it.second }.toDouble() / these.size, these.size)
        }
    }

    fun due(c: Call, today: LocalDate): Boolean = c.open && date(c.checkOn)?.let { !it.isAfter(today) } == true

    fun summary(calls: List<Call>, today: LocalDate): Summary {
        val scores = calls.mapNotNull { score(it) }
        val beats = calls.mapNotNull { beat(it) }
        return Summary(
            checked = calls.count { it.happened != null && !it.void },
            brier = if (scores.isEmpty()) null else scores.average(),
            bands = bands(calls),
            toCheck = calls.count { due(it, today) },
            open = calls.count { it.open },
            beat = beats.count { it },
            withReference = beats.size
        )
    }

    // --- places, references and reasoning (1.41) ---

    fun place(c: Call): Place = when (c.area) {
        Area.MARKETS -> Place.MARKETS
        Area.SPORTS -> Place.SPORTS
        Area.POLITICS, Area.WATCHLIST -> Place.WORLD
        Area.OTHER -> Place.OWN
    }

    /** The reference a call is measured against: the market's (or crowd's) chances when you made it. */
    fun reference(c: Call): List<Double>? = c.market.firstOrNull()?.chances?.takeIf { it.size == c.outcomes.size && it.isNotEmpty() }

    /**
     * Did your call beat its reference? Your Brier score (over the days you held it) against the
     * reference's, both on what happened; a tie doesn't beat it. Null when there's nothing to compare.
     */
    fun beat(c: Call): Boolean? {
        val h = c.happened ?: return null
        if (c.void) return null
        val ref = reference(c) ?: return null
        val mine = score(c) ?: return null
        return mine < brier(ref, h) - 1e-12
    }

    /** Did it go the way you leaned (the outcome you gave the biggest chance)? */
    fun wentYourWay(c: Call): Boolean? {
        val h = c.happened ?: return null
        if (c.void) return null
        val ch = c.latest?.chances ?: return null
        return ch.indices.maxByOrNull { ch[it] } == h
    }

    fun byPlace(calls: List<Call>): List<PlaceStat> = Place.entries.mapNotNull { p ->
        val here = calls.filter { place(it) == p }
        if (here.isEmpty()) return@mapNotNull null
        val checked = here.filter { it.happened != null && !it.void }
        val scores = checked.mapNotNull { score(it) }
        val beats = checked.mapNotNull { beat(it) }
        PlaceStat(p, checked.size, here.count { it.open }, if (scores.isEmpty()) null else scores.average(), beats.count { it }, beats.size)
    }

    fun luck(calls: List<Call>): Luck {
        var sr = 0; var sw = 0; var kr = 0; var kw = 0
        for (c in calls) {
            if (c.reasoning.isBlank()) continue
            val right = wentYourWay(c) ?: continue
            val sound = c.reasoning == "sound"
            when {
                sound && right -> sr++
                sound -> sw++
                right -> kr++
                else -> kw++
            }
        }
        return Luck(sr, sw, kr, kw)
    }

    /** "today", "tomorrow", "in 6 days", "2 days ago". */
    fun relative(day: LocalDate, today: LocalDate): String {
        val d = ChronoUnit.DAYS.between(today, day)
        return when {
            d == 0L -> "today"
            d == 1L -> "tomorrow"
            d > 1 -> "in $d days"
            d == -1L -> "yesterday"
            else -> "${-d} days ago"
        }
    }

    // --- SlothDesk ideas, read in place ---

    private fun level(v: Double?): String =
        v?.let { if (it % 1.0 == 0.0) String.format(Locale.ENGLISH, "%,d", it.toLong()) else String.format(Locale.ENGLISH, "%,.2f", it) }.orEmpty()

    /**
     * A SlothDesk idea as a call: right or wrong, at the conviction you saved it with, checked on
     * its review day. "Mixed" and "no call" don't count. It stays in SlothDesk; this only reads it.
     */
    fun fromIdea(idea: Idea, zone: ZoneId = ZoneId.systemDefault()): Call? {
        val made = Instant.ofEpochMilli(idea.createdAt).atZone(zone).toLocalDate()
        val check = date(idea.reviewOn) ?: return null
        val levels = listOfNotNull(
            idea.entry?.let { "from ${level(it)}" },
            idea.target?.let { "to ${level(it)}" },
            idea.stop?.let { "stop ${level(it)}" }
        ).joinToString(", ")
        val question = "${idea.instrument} ${idea.direction}" +
            (if (levels.isNotEmpty()) " $levels" else "") +
            (if (idea.thesis.isNotBlank()) ": ${idea.thesis.trim().lineSequence().first().take(120)}" else "")
        val closed = !idea.isOpen
        val happened = when (idea.outcome) { "right" -> 0; "wrong" -> 1; else -> null }
        val markedOn = if (closed) Instant.ofEpochMilli(idea.closedAt).atZone(zone).toLocalDate().toString() else ""
        return Call(
            id = "idea:${idea.id}",
            question = question,
            area = Area.MARKETS,
            kind = CallKind.YES_NO,
            checkOn = check.toString(),
            createdAt = idea.createdAt,
            estimates = listOf(Chance(made.toString(), idea.createdAt, binary(idea.conviction / 100.0), "how sure you were when you saved it")),
            happened = if (closed) happened else null,
            void = closed && happened == null,
            markedOn = markedOn,
            source = "idea:${idea.id}"
        )
    }

    // --- the file ---

    fun encode(calls: List<Call>): String = json.encodeToString(CallFile.serializer(), CallFile(calls = calls))

    fun parse(text: String): List<Call> = json.decodeFromString(CallFile.serializer(), text).calls

    /** A backup's calls join yours; where both have the same call, yours stays. */
    fun merge(mine: List<Call>, theirs: List<Call>): List<Call> {
        val have = mine.map { it.id }.toSet()
        return mine + theirs.filter { it.id !in have }
    }
}
