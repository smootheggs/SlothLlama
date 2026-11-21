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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * Results and fixtures for SlothLlama's Sports page, as a "slothsports" file.
 *
 *   NBA, NFL and football leagues   ESPN's public scoreboard (the JSON its own site and apps
 *                                   read; no key, no account). The day's line, where ESPN shows
 *                                   one, comes along as ESPN shows it.
 *   F1                              Jolpica-F1, the open successor to Ergast (no key; it asks
 *                                   for no more than four requests a second).
 *
 * Only those two are called, with nothing about you. Fetched a day or a month at a time, gently.
 */

@Serializable
data class SportsBundle(
    val format: String = "slothsports",
    val v: Int = 1,
    @SerialName("made_at") val madeAt: String,
    @SerialName("made_by") val madeBy: String,
    val leagues: Map<String, LeagueData>
)

@Serializable
data class LeagueData(
    val name: String,
    val sport: String,
    val games: List<GameRow> = emptyList(),
    val races: List<RaceRow> = emptyList()
)

@Serializable
data class GameRow(
    val id: String,
    /** When it starts, as ESPN gives it (ISO, UTC). */
    val date: String,
    val home: String,
    val away: String,
    @SerialName("home_abbr") val homeAbbr: String = "",
    @SerialName("away_abbr") val awayAbbr: String = "",
    @SerialName("home_score") val homeScore: Int? = null,
    @SerialName("away_score") val awayScore: Int? = null,
    /** "pre", "in" or "post". */
    val state: String = "pre",
    val line: Line? = null
)

@Serializable
data class Line(
    val provider: String = "",
    /** As shown: "ATL -6.5". */
    val details: String = "",
    @SerialName("over_under") val overUnder: Double? = null,
    @SerialName("home_money") val homeMoney: String? = null,
    @SerialName("away_money") val awayMoney: String? = null
)

@Serializable
data class RaceRow(
    val round: Int,
    val name: String,
    val date: String,
    val results: List<Finish> = emptyList()
)

@Serializable
data class Finish(val pos: Int? = null, val driver: String, val team: String = "", val status: String = "", val grid: Int? = null)

object Sports {

    /** The leagues an order can name: (ESPN sport, ESPN league, name). F1 comes from Jolpica. */
    val LEAGUES = linkedMapOf(
        "nba" to Triple("basketball", "nba", "NBA"),
        "nfl" to Triple("football", "nfl", "NFL"),
        "epl" to Triple("soccer", "eng.1", "Premier League"),
        "laliga" to Triple("soccer", "esp.1", "LaLiga"),
        "bundesliga" to Triple("soccer", "ger.1", "Bundesliga"),
        "seriea" to Triple("soccer", "ita.1", "Serie A"),
        "ligue1" to Triple("soccer", "fra.1", "Ligue 1"),
        "mls" to Triple("soccer", "usa.1", "MLS"),
        "ucl" to Triple("soccer", "uefa.champions", "Champions League"),
        "f1" to Triple("racing", "f1", "Formula 1")
    )

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val SINCE = Regex("""\bfrom\s+(\d{4}-\d{2}-\d{2})\b""", RegexOption.IGNORE_CASE)
    private val DAY = DateTimeFormatter.BASIC_ISO_DATE
    private const val AHEAD_DAYS = 14L
    private const val MAX_DAYS = 200L
    private const val SHORT_DAYS = 31L
    private val MONTH = DateTimeFormatter.ofPattern("yyyyMM")

    /** What an order asks for: the leagues (by the ids above) and the first day. */
    fun request(text: String, today: LocalDate = LocalDate.now()): Pair<List<String>, LocalDate> {
        val words = Regex("""[a-z0-9]+""").findAll(text.lowercase()).map { it.value }.toSet()
        val leagues = LEAGUES.keys.filter { it in words }
        val since = SINCE.find(text)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: today.minusDays(7)
        return leagues to maxOf(since, today.minusDays(MAX_DAYS))
    }

    class Gathered(val bundle: SportsBundle, val notes: List<String>)

    suspend fun gather(leagues: List<String>, since: LocalDate, version: String, today: LocalDate = LocalDate.now(), log: (String, String) -> Unit): Gathered {
        val out = LinkedHashMap<String, LeagueData>()
        val notes = ArrayList<String>()
        for (id in leagues) {
            val (sport, league, name) = LEAGUES[id] ?: continue
            try {
                val data = if (id == "f1") f1(name) else espn(sport, league, name, since, today.plusDays(AHEAD_DAYS))
                out[id] = data
                log(name, if (id == "f1") "${data.races.size} races" else "${data.games.size} games")
            } catch (e: Exception) {
                val why = (e.message ?: e.javaClass.simpleName).take(160)
                notes += "$name: $why"
                log(name, "skipped: $why")
            }
        }
        if (out.isEmpty()) throw WebException("No results came back. " + notes.joinToString("; "))
        return Gathered(SportsBundle(madeAt = java.time.Instant.now().toString(), madeBy = "SlothLobster $version", leagues = out), notes)
    }

    fun encode(b: SportsBundle): String = json.encodeToString(SportsBundle.serializer(), b)

    fun summary(g: Gathered): String = buildString {
        append("Results and fixtures for Sports. Open Sports in SlothLlama and it takes them in.\n\n")
        g.bundle.leagues.forEach { (_, d) ->
            if (d.races.isNotEmpty()) {
                val done = d.races.count { it.results.isNotEmpty() }
                append("- **${d.name}**: $done of ${d.races.size} races with results\n")
            } else {
                val played = d.games.count { it.state == "post" }
                append("- **${d.name}**: $played results, ${d.games.size - played} to come\n")
            }
        }
        if (g.notes.isNotEmpty()) {
            append("\nNot fetched:\n")
            g.notes.forEach { append("- ").append(it).append('\n') }
        }
    }.trim()

    // --- ESPN ---

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    private fun JsonElement?.num(): Double? = (this as? kotlinx.serialization.json.JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    /**
     * The scoreboard takes one day (20261115) or one month (202611), not a range: a range answers
     * 400. A short window goes day by day; a long one (the season so far) month by month, kept to
     * the window. One day or month that fails is noted and skipped; the league fails only if all do.
     */
    internal fun pages(from: LocalDate, to: LocalDate): List<String> =
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) < SHORT_DAYS) {
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it.format(DAY) }.toList()
        } else {
            generateSequence(java.time.YearMonth.from(from)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(java.time.YearMonth.from(to)) }.map { it.format(MONTH) }.toList()
        }

    private suspend fun espn(sport: String, league: String, name: String, from: LocalDate, to: LocalDate): LeagueData {
        val games = LinkedHashMap<String, GameRow>()
        val wanted = from.toString()..to.plusDays(1).toString()
        var failed = 0
        var lastError = ""
        val pages = pages(from, to)
        for (d in pages) {
            try {
                val page = Web.get("https://site.api.espn.com/apis/site/v2/sports/$sport/$league/scoreboard?limit=500&dates=$d", asBrowser = false)
                scoreboard(page.text).filter { it.date.take(10) in wanted }.forEach { games[it.id] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                lastError = e.message ?: e.javaClass.simpleName
            }
            delay(400)
        }
        if (failed == pages.size) throw WebException(lastError.ifBlank { "ESPN didn't answer" })
        return LeagueData(name, sport, games = games.values.sortedBy { it.date })
    }

    /** The games on one ESPN scoreboard page. */
    internal fun scoreboard(text: String): List<GameRow> {
        val root = json.parseToJsonElement(text).obj() ?: throw WebException("ESPN sent something unexpected")
        return root["events"].arr()?.mapNotNull { parseGame(it.obj()) } ?: emptyList()
    }

    private fun parseGame(e: JsonObject?): GameRow? {
        e ?: return null
        val id = e["id"].str() ?: return null
        val date = e["date"].str() ?: return null
        val comp = e["competitions"].arr()?.firstOrNull().obj() ?: return null
        val sides = comp["competitors"].arr()?.mapNotNull { it.obj() } ?: return null
        val home = sides.firstOrNull { it["homeAway"].str() == "home" } ?: return null
        val away = sides.firstOrNull { it["homeAway"].str() == "away" } ?: return null
        val state = e["status"].obj()?.get("type").obj()?.get("state").str() ?: "pre"
        fun team(s: JsonObject) = s["team"].obj()
        fun score(s: JsonObject) = if (state == "pre") null else s["score"].num()?.toInt()
        val odds = comp["odds"].arr()?.firstOrNull().obj()
        val line = odds?.let { o ->
            val ml = o["moneyline"].obj()
            fun money(side: String) = ml?.get(side).obj()?.let { m -> m["close"].obj()?.get("odds").str() ?: m["open"].obj()?.get("odds").str() }
            Line(
                provider = o["provider"].obj()?.get("name").str().orEmpty(),
                details = o["details"].str().orEmpty(),
                overUnder = o["overUnder"].num(),
                homeMoney = money("home"),
                awayMoney = money("away")
            )
        }
        return GameRow(
            id = id,
            date = date,
            home = team(home)?.get("displayName").str() ?: return null,
            away = team(away)?.get("displayName").str() ?: return null,
            homeAbbr = team(home)?.get("abbreviation").str().orEmpty(),
            awayAbbr = team(away)?.get("abbreviation").str().orEmpty(),
            homeScore = score(home),
            awayScore = score(away),
            state = state,
            line = line?.takeIf { it.details.isNotBlank() || it.homeMoney != null || it.overUnder != null }
        )
    }

    // --- Jolpica (F1) ---

    private suspend fun f1(name: String): LeagueData {
        val base = "https://api.jolpi.ca/ergast/f1/current"
        val schedule = json.parseToJsonElement(Web.get("$base.json", asBrowser = false).text).obj()
            ?.get("MRData").obj()?.get("RaceTable").obj()?.get("Races").arr()
            ?: throw WebException("Jolpica sent no calendar")
        val races = LinkedHashMap<Int, RaceRow>()
        schedule.forEach { r ->
            val o = r.obj() ?: return@forEach
            val round = o["round"].str()?.toIntOrNull() ?: return@forEach
            races[round] = RaceRow(round, o["raceName"].str().orEmpty(), o["date"].str().orEmpty())
        }
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total && offset < 2_000) {
            delay(700)
            val data = json.parseToJsonElement(Web.get("$base/results.json?limit=100&offset=$offset", asBrowser = false).text).obj()?.get("MRData").obj()
                ?: throw WebException("Jolpica sent no results")
            total = data["total"].str()?.toIntOrNull() ?: 0
            val got = data["RaceTable"].obj()?.get("Races").arr().orEmpty()
            var rows = 0
            got.forEach { r ->
                val o = r.obj() ?: return@forEach
                val round = o["round"].str()?.toIntOrNull() ?: return@forEach
                val finishes = o["Results"].arr().orEmpty().mapNotNull { x ->
                    val f = x.obj() ?: return@mapNotNull null
                    rows++
                    val d = f["Driver"].obj()
                    Finish(
                        pos = f["position"].str()?.toIntOrNull(),
                        driver = listOfNotNull(d?.get("givenName").str(), d?.get("familyName").str()).joinToString(" ").ifBlank { return@mapNotNull null },
                        team = f["Constructor"].obj()?.get("name").str().orEmpty(),
                        status = f["status"].str().orEmpty(),
                        grid = f["grid"].str()?.toIntOrNull()
                    )
                }
                val known = races[round] ?: RaceRow(round, o["raceName"].str().orEmpty(), o["date"].str().orEmpty())
                races[round] = known.copy(results = known.results + finishes)
            }
            if (rows == 0) break
            offset += rows
        }
        return LeagueData(name, "racing", races = races.values.sortedBy { it.round })
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
