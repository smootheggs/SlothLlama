package app.localmind.assistant.domain.sports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What the Lobster brings for Sports: a "slothsports" file (SlothLobster 1.9). Results and
 * fixtures as ESPN's scoreboard and Jolpica-F1 give them; everything else is worked out here.
 */

@Serializable
data class SportsBundle(
    val format: String = FORMAT,
    val v: Int = 1,
    @SerialName("made_at") val madeAt: String = "",
    @SerialName("made_by") val madeBy: String = "",
    val leagues: Map<String, LeagueData> = emptyMap()
) {
    companion object {
        const val FORMAT = "slothsports"
    }
}

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
    val date: String,
    val home: String,
    val away: String,
    @SerialName("home_abbr") val homeAbbr: String = "",
    @SerialName("away_abbr") val awayAbbr: String = "",
    @SerialName("home_score") val homeScore: Int? = null,
    @SerialName("away_score") val awayScore: Int? = null,
    val state: String = "pre",
    val line: Line? = null
) {
    val played: Boolean get() = state == "post" && homeScore != null && awayScore != null
}

@Serializable
data class Line(
    val provider: String = "",
    val details: String = "",
    @SerialName("over_under") val overUnder: Double? = null,
    @SerialName("home_money") val homeMoney: String? = null,
    @SerialName("away_money") val awayMoney: String? = null
)

@Serializable
data class RaceRow(val round: Int, val name: String, val date: String, val results: List<Finish> = emptyList())

@Serializable
data class Finish(val pos: Int? = null, val driver: String, val team: String = "", val status: String = "", val grid: Int? = null) {
    /** Classified: finished, or a lap or more down. */
    val classified: Boolean get() = pos != null && (status == "Finished" || status.startsWith("+") || status.contains("Lap", ignoreCase = true))
}

/** How a sport is worked out: by points margin (NBA, NFL), goals (football), or finishing order (F1). */
enum class Kind { POINTS, GOALS, RACE }

/** A league you can follow: its id in orders, its name, and the model that fits it. */
enum class League(val id: String, val label: String, val kind: Kind) {
    NBA("nba", "NBA", Kind.POINTS),
    NFL("nfl", "NFL", Kind.POINTS),
    EPL("epl", "Premier League", Kind.GOALS),
    LALIGA("laliga", "LaLiga", Kind.GOALS),
    BUNDESLIGA("bundesliga", "Bundesliga", Kind.GOALS),
    SERIEA("seriea", "Serie A", Kind.GOALS),
    LIGUE1("ligue1", "Ligue 1", Kind.GOALS),
    MLS("mls", "MLS", Kind.GOALS),
    UCL("ucl", "Champions League", Kind.GOALS),
    F1("f1", "Formula 1", Kind.RACE);

    companion object {
        fun of(id: String): League? = entries.firstOrNull { it.id == id }
        val DEFAULT = listOf(NBA, F1, EPL, NFL)
    }
}

object SportsFile {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String): SportsBundle {
        val b = json.decodeFromString(SportsBundle.serializer(), text)
        require(b.format == SportsBundle.FORMAT) { "Not a sports file" }
        return b
    }

    fun encode(b: SportsBundle): String = json.encodeToString(SportsBundle.serializer(), b)

    /**
     * A new bundle laid over what's kept: games by id (the newer wins, but a line seen before the
     * start is kept if the newer has none, so the closing line survives), races by round.
     */
    fun merge(old: SportsBundle?, new: SportsBundle): SportsBundle {
        if (old == null) return new
        val leagues = (old.leagues.keys + new.leagues.keys).associateWith { id ->
            val a = old.leagues[id]
            val b = new.leagues[id]
            when {
                a == null -> b!!
                b == null -> a
                else -> {
                    val games = LinkedHashMap<String, GameRow>()
                    a.games.forEach { games[it.id] = it }
                    b.games.forEach { g -> games[g.id] = if (g.line == null) g.copy(line = games[g.id]?.line) else g }
                    val races = LinkedHashMap<Int, RaceRow>()
                    a.races.forEach { races[it.round] = it }
                    b.races.forEach { r -> races[r.round] = if (r.results.isEmpty()) r.copy(results = races[r.round]?.results.orEmpty()) else r }
                    b.copy(games = games.values.sortedBy { it.date }, races = races.values.sortedBy { it.round })
                }
            }
        }
        return new.copy(leagues = leagues)
    }
}
