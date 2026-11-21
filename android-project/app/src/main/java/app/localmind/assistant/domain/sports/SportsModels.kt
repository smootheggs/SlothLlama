package app.localmind.assistant.domain.sports

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Something you know that results don't: points (or goals, or places) toward one side, with why. */
@kotlinx.serialization.Serializable
data class Factor(val label: String, val toHome: Boolean = true, val value: Double, val driver: String = "")

/** A team's season so far, counted from the results. */
data class TeamRecord(
    val team: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val scored: Int,
    val allowed: Int,
    val homeWon: Int,
    val homeLost: Int,
    val awayWon: Int,
    val awayLost: Int,
    /** Newest last: 'W', 'D', 'L'. */
    val form: String
) {
    val points: Int get() = won * 3 + drawn
    val diff: Int get() = scored - allowed
    val pct: Double get() = if (played == 0) 0.0 else (won + drawn * 0.5) / played
}

/** A points game worked out: the expected margin (home minus away), its spread, and the total's. */
data class Expected(val margin: Double, val marginSd: Double, val total: Double, val totalSd: Double, val ratingPoints: Double, val games: Int)

/** 20,000 games played out: the chances, and how often each margin came up. */
data class Played(
    val homeWins: Double,
    val homeCovers: Double?,
    val over: Double?,
    /** (margin from, share of games), in steps of [step]. */
    val bins: List<Pair<Double, Double>>,
    val step: Double,
    val pushes: Double
)

data class Goals(val home: Double, val away: Double, val grid: List<List<Double>>) {
    val homeWin: Double get() = sum { h, a -> h > a }
    val draw: Double get() = sum { h, a -> h == a }
    val awayWin: Double get() = sum { h, a -> h < a }
    fun over(line: Double): Double = sum { h, a -> h + a > line }
    val bothScore: Double get() = sum { h, a -> h > 0 && a > 0 }
    private fun sum(test: (Int, Int) -> Boolean): Double {
        var s = 0.0
        grid.forEachIndexed { h, row -> row.forEachIndexed { a, p -> if (test(h, a)) s += p } }
        return s
    }
}

/** One driver's chances from the simulated races. */
data class DriverOdds(val driver: String, val team: String, val avg: Double?, val dnfRate: Double, val win: Double, val podium: Double, val points: Double)

/**
 * The sports models, all in code: ratings from results (Elo for points sports, attack and defence
 * for goals), games played out many times, and F1 finishing orders. Your factors move the inputs;
 * the market's prices are never touched.
 */
object SportsModels {
    const val RUNS = 20_000

    // --- records and tables ---

    fun records(games: List<GameRow>): List<TeamRecord> {
        val teams = (games.map { it.home } + games.map { it.away }).distinct()
        val played = games.filter { it.played }.sortedBy { it.date }
        return teams.map { t ->
            val mine = played.filter { it.home == t || it.away == t }
            var w = 0; var d = 0; var l = 0; var sf = 0; var sa = 0; var hw = 0; var hl = 0; var aw = 0; var al = 0
            val form = StringBuilder()
            for (g in mine) {
                val home = g.home == t
                val us = if (home) g.homeScore!! else g.awayScore!!
                val them = if (home) g.awayScore!! else g.homeScore!!
                sf += us; sa += them
                when {
                    us > them -> { w++; if (home) hw++ else aw++; form.append('W') }
                    us < them -> { l++; if (home) hl++ else al++; form.append('L') }
                    else -> { d++; form.append('D') }
                }
            }
            TeamRecord(t, mine.size, w, d, l, sf, sa, hw, hl, aw, al, form.takeLast(5).toString())
        }
    }

    /** Points sports by win share; football by points, goal difference, goals. */
    fun table(games: List<GameRow>, kind: Kind): List<TeamRecord> = records(games).sortedWith(
        if (kind == Kind.GOALS) compareByDescending<TeamRecord> { it.points }.thenByDescending { it.diff }.thenByDescending { it.scored }.thenBy { it.team }
        else compareByDescending<TeamRecord> { it.pct }.thenByDescending { it.diff }.thenBy { it.team }
    )

    // --- points sports: Elo ---

    data class EloRules(val k: Double, val home: Double, val perPoint: Double, val marginSd: Double, val totalSd: Double)

    fun rules(league: League): EloRules = when (league) {
        League.NFL -> EloRules(k = 20.0, home = 48.0, perPoint = 25.0, marginSd = 13.5, totalSd = 13.5)
        else -> EloRules(k = 20.0, home = 100.0, perPoint = 28.0, marginSd = 12.5, totalSd = 18.0)
    }

    /** Elo ratings from the season's results, oldest first; the margin of victory counts, damped. */
    fun elo(games: List<GameRow>, r: EloRules): Map<String, Double> {
        val rating = HashMap<String, Double>()
        for (g in games.filter { it.played }.sortedBy { it.date }) {
            val h = rating.getOrPut(g.home) { 1500.0 }
            val a = rating.getOrPut(g.away) { 1500.0 }
            val diff = h + r.home - a
            val expect = 1.0 / (1.0 + Math.pow(10.0, -diff / 400.0))
            val mov = g.homeScore!! - g.awayScore!!
            val result = when { mov > 0 -> 1.0; mov < 0 -> 0.0; else -> 0.5 }
            val winnerDiff = if (mov >= 0) diff else -diff
            val mult = ln(abs(mov) + 1.0) * 2.2 / (winnerDiff * 0.001 + 2.2)
            val change = r.k * mult.coerceAtLeast(0.5) * (result - expect)
            rating[g.home] = h + change
            rating[g.away] = a - change
        }
        return rating
    }

    /** What to expect from [game]: the rating gap in points, your factors, and the season's spread. */
    fun expect(game: GameRow, games: List<GameRow>, league: League, factors: List<Factor>): Expected {
        val r = rules(league)
        val before = games.filter { it.played && it.date < game.date }
        val ratings = elo(before, r)
        val ratingPoints = ((ratings[game.home] ?: 1500.0) + r.home - (ratings[game.away] ?: 1500.0)) / r.perPoint
        val margin = ratingPoints + factors.sumOf { if (it.toHome) it.value else -it.value }
        val recs = records(before).associateBy { it.team }
        fun avg(t: String, scored: Boolean, fallback: Double): Double = recs[t]?.takeIf { it.played > 0 }?.let { (if (scored) it.scored else it.allowed).toDouble() / it.played } ?: fallback
        val usual = before.flatMap { listOf(it.homeScore!!, it.awayScore!!) }.takeIf { it.isNotEmpty() }?.average() ?: if (r.home > 60) 113.0 else 22.0
        val homePts = (avg(game.home, true, usual) + avg(game.away, false, usual)) / 2
        val awayPts = (avg(game.away, true, usual) + avg(game.home, false, usual)) / 2
        // The season's own spread once there are enough games; the usual one before that.
        val residuals = if (before.size >= 40) residualSd(before, r) else null
        return Expected(margin, residuals?.first ?: r.marginSd, homePts + awayPts, residuals?.second ?: r.totalSd, ratingPoints, before.size)
    }

    private fun residualSd(games: List<GameRow>, r: EloRules): Pair<Double, Double> {
        val sorted = games.sortedBy { it.date }
        val ratings = HashMap<String, Double>()
        val misses = ArrayList<Double>()
        val totals = sorted.map { (it.homeScore!! + it.awayScore!!).toDouble() }
        for (g in sorted) {
            val h = ratings[g.home] ?: 1500.0
            val a = ratings[g.away] ?: 1500.0
            if (ratings.containsKey(g.home) && ratings.containsKey(g.away)) misses += (g.homeScore!! - g.awayScore!!) - (h + r.home - a) / r.perPoint
            val diff = h + r.home - a
            val expect = 1.0 / (1.0 + Math.pow(10.0, -diff / 400.0))
            val mov = g.homeScore!! - g.awayScore!!
            val result = when { mov > 0 -> 1.0; mov < 0 -> 0.0; else -> 0.5 }
            val winnerDiff = if (mov >= 0) diff else -diff
            val change = r.k * (ln(abs(mov) + 1.0) * 2.2 / (winnerDiff * 0.001 + 2.2)).coerceAtLeast(0.5) * (result - expect)
            ratings[g.home] = h + change
            ratings[g.away] = a - change
        }
        val m = if (misses.size >= 20) sd(misses) else r.marginSd
        return m.coerceIn(r.marginSd * 0.7, r.marginSd * 1.4) to sd(totals).coerceIn(r.totalSd * 0.6, r.totalSd * 1.5)
    }

    fun sd(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val mean = xs.average()
        return sqrt(xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1))
    }

    /** A standard normal draw (Box–Muller). */
    fun normal(random: Random): Double {
        var u = random.nextDouble()
        if (u < 1e-12) u = 1e-12
        val v = random.nextDouble()
        return sqrt(-2 * ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    /**
     * Plays the game [runs] times: margin and total drawn from their spreads. [homeLine] is the
     * home side's spread as written (−1.5 means they give 1.5); [totalLine] the over/under.
     */
    fun play(e: Expected, homeLine: Double?, totalLine: Double?, runs: Int = RUNS, seed: Int = 1, step: Double = 2.5): Played {
        val random = Random(seed)
        var wins = 0
        var covers = 0
        var overs = 0
        var pushes = 0
        val lo = -30.0
        val counts = IntArray(((60.0) / step).toInt())
        for (i in 0 until runs) {
            val m = e.margin + normal(random) * e.marginSd
            val t = e.total + normal(random) * e.totalSd
            // Whole points: a game can't end 101.37 to 99.
            val margin = Math.round(m).toDouble().let { if (it == 0.0) (if (m >= 0) 1.0 else -1.0) else it }
            if (margin > 0) wins++
            if (homeLine != null) {
                val v = margin + homeLine
                if (v > 0) covers++ else if (v == 0.0) pushes++
            }
            if (totalLine != null && Math.round(t) > totalLine) overs++
            val bin = ((margin - lo) / step).toInt()
            if (bin in counts.indices) counts[bin]++
        }
        return Played(
            homeWins = wins.toDouble() / runs,
            homeCovers = homeLine?.let { covers.toDouble() / runs },
            over = totalLine?.let { overs.toDouble() / runs },
            bins = counts.mapIndexed { i, c -> (lo + i * step) to c.toDouble() / runs },
            step = step,
            pushes = pushes.toDouble() / runs
        )
    }

    // --- football: goals ---

    private fun poisson(k: Int, lambda: Double): Double {
        var p = exp(-lambda)
        for (i in 1..k) p *= lambda / i
        return p
    }

    /**
     * Expected goals for [game] from the season so far: each side's scoring and conceding at home
     * and away against the league's averages, eased toward average while there are few games
     * (three games' worth of average added in). Your factors add or take away goals.
     */
    fun goals(game: GameRow, games: List<GameRow>, factors: List<Factor>, max: Int = 6): Goals {
        val before = games.filter { it.played && it.date < game.date }
        val homeAvg = before.map { it.homeScore!!.toDouble() }.takeIf { it.isNotEmpty() }?.average() ?: 1.5
        val awayAvg = before.map { it.awayScore!!.toDouble() }.takeIf { it.isNotEmpty() }?.average() ?: 1.2
        fun eased(total: Double, n: Int, avg: Double) = (total + 3 * avg) / (n + 3) / avg
        val atHome = before.filter { it.home == game.home }
        val away = before.filter { it.away == game.away }
        val homeAttack = eased(atHome.sumOf { it.homeScore!!.toDouble() }, atHome.size, homeAvg)
        val homeDefence = eased(atHome.sumOf { it.awayScore!!.toDouble() }, atHome.size, awayAvg)
        val awayAttack = eased(away.sumOf { it.awayScore!!.toDouble() }, away.size, awayAvg)
        val awayDefence = eased(away.sumOf { it.homeScore!!.toDouble() }, away.size, homeAvg)
        val lh = (homeAvg * homeAttack * awayDefence + factors.filter { it.toHome }.sumOf { it.value }).coerceIn(0.05, 6.0)
        val la = (awayAvg * awayAttack * homeDefence + factors.filter { !it.toHome }.sumOf { it.value }).coerceIn(0.05, 6.0)
        val grid = (0..max).map { h -> (0..max).map { a -> poisson(h, lh) * poisson(a, la) } }
        val total = grid.sumOf { it.sum() }
        return Goals(lh, la, grid.map { row -> row.map { it / total } })
    }

    // --- F1: finishing order ---

    /**
     * Each driver's chances in the next race, from the last [recent] races: pace from where they
     * finished, a chance of not finishing from how often they didn't (eased toward one in ten),
     * and a spread of about two and a half places. Your factors move a driver up (−) or down (+).
     */
    fun race(races: List<RaceRow>, factors: List<Factor>, recent: Int = 6, runs: Int = RUNS, seed: Int = 1, spread: Double = 2.5): List<DriverOdds> {
        val done = races.filter { it.results.isNotEmpty() }.sortedBy { it.round }.takeLast(recent)
        if (done.isEmpty()) return emptyList()
        val latest = done.last().results
        val drivers = latest.map { it.driver }
        val team = latest.associate { it.driver to it.team }
        val stats = drivers.associateWith { d ->
            val rows = done.mapNotNull { r -> r.results.firstOrNull { it.driver == d } }
            val classified = rows.filter { it.classified }.mapNotNull { it.pos }
            val avg = if (classified.isEmpty()) null else classified.average()
            val dnf = (rows.count { !it.classified } + 0.5) / (rows.size + 5.0)
            avg to dnf
        }
        val field = drivers.size
        val shift = factors.groupBy { it.driver }.mapValues { (_, f) -> f.sumOf { it.value } }
        val random = Random(seed)
        val win = IntArray(field)
        val podium = IntArray(field)
        val points = IntArray(field)
        val pace = DoubleArray(field)
        for (i in 0 until runs) {
            for (j in 0 until field) {
                val (avg, dnf) = stats.getValue(drivers[j])
                pace[j] = if (random.nextDouble() < dnf) Double.MAX_VALUE
                else (avg ?: (field * 0.7)) + (shift[drivers[j]] ?: 0.0) + normal(random) * spread
            }
            val ranked = (0 until field).sortedBy { pace[it] }.filter { pace[it] != Double.MAX_VALUE }
            ranked.forEachIndexed { place, j ->
                if (place == 0) win[j]++
                if (place < 3) podium[j]++
                if (place < 10) points[j]++
            }
        }
        return drivers.indices.map { j ->
            val (avg, dnf) = stats.getValue(drivers[j])
            DriverOdds(drivers[j], team[drivers[j]].orEmpty(), avg, dnf, win[j].toDouble() / runs, podium[j].toDouble() / runs, points[j].toDouble() / runs)
        }.sortedByDescending { it.win }
    }

    /** The drivers' championship from classified finishes: 25, 18, 15, 12, 10, 8, 6, 4, 2, 1 (sprints and bonus points not counted). */
    fun standings(races: List<RaceRow>): List<Pair<String, Int>> {
        val scale = listOf(25, 18, 15, 12, 10, 8, 6, 4, 2, 1)
        val totals = LinkedHashMap<String, Int>()
        races.forEach { r ->
            r.results.forEach { f ->
                val p = f.pos?.takeIf { f.classified }?.let { scale.getOrNull(it - 1) } ?: 0
                totals[f.driver] = (totals[f.driver] ?: 0) + p
            }
        }
        return totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
