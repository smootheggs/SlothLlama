package app.localmind.assistant

import app.localmind.assistant.domain.sports.Factor
import app.localmind.assistant.domain.sports.Finish
import app.localmind.assistant.domain.sports.GameRow
import app.localmind.assistant.domain.sports.Kind
import app.localmind.assistant.domain.sports.League
import app.localmind.assistant.domain.sports.LeagueData
import app.localmind.assistant.domain.sports.Line
import app.localmind.assistant.domain.sports.Market
import app.localmind.assistant.domain.sports.RaceRow
import app.localmind.assistant.domain.sports.SportsBundle
import app.localmind.assistant.domain.sports.SportsFile
import app.localmind.assistant.domain.sports.SportsLines
import app.localmind.assistant.domain.sports.SportsModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39: Sports, worked out on the tablet from what the Lobster brings. Teams here are made up. */
class SportsUnitTests {

    private fun game(id: String, day: Int, home: String, away: String, hs: Int?, aw: Int?, line: Line? = null) = GameRow(
        id = id, date = "2026-11-%02dT01:00Z".format(day), home = home, away = away,
        homeAbbr = home.take(3).uppercase(), awayAbbr = away.take(3).uppercase(),
        homeScore = hs, awayScore = aw, state = if (hs == null) "pre" else "post", line = line
    )

    // Strong beats everyone; Weak loses to everyone.
    private val season = listOf(
        game("1", 1, "Strong", "Weak", 120, 95), game("2", 2, "Middle", "Strong", 100, 112),
        game("3", 3, "Weak", "Middle", 98, 105), game("4", 4, "Strong", "Middle", 115, 104),
        game("5", 5, "Weak", "Strong", 90, 118), game("6", 6, "Middle", "Weak", 110, 101)
    )

    @Test
    fun `records and tables are counted from results`() {
        val t = SportsModels.table(season, Kind.POINTS)
        assertEquals(listOf("Strong", "Middle", "Weak"), t.map { it.team })
        val strong = t.first()
        assertEquals(4, strong.won)
        assertEquals(0, strong.lost)
        assertEquals("WWWW", strong.form)
        assertEquals(2, strong.homeWon)
    }

    @Test
    fun `ratings favour the better side, and the games played out agree`() {
        val ratings = SportsModels.elo(season, SportsModels.rules(League.NBA))
        assertTrue(ratings.getValue("Strong") > ratings.getValue("Middle"))
        assertTrue(ratings.getValue("Middle") > ratings.getValue("Weak"))
        val next = game("7", 8, "Weak", "Strong", null, null)
        val e = SportsModels.expect(next, season, League.NBA, emptyList())
        assertTrue(e.margin < 0) // Strong is the better side, even away
        val p = SportsModels.play(e, homeLine = 5.5, totalLine = 210.5, seed = 3)
        val strongHome = SportsModels.play(SportsModels.expect(game("8", 8, "Strong", "Weak", null, null), season, League.NBA, emptyList()), null, null, seed = 3)
        assertTrue(strongHome.homeWins > 0.6)
        assertTrue(strongHome.homeWins > p.homeWins)
        assertEquals(1.0, p.bins.sumOf { it.second }, 0.02)
        // A factor for the home side moves the margin by exactly its points.
        val helped = SportsModels.expect(next, season, League.NBA, listOf(Factor("their star is back", toHome = true, value = 4.0)))
        assertEquals(e.margin + 4.0, helped.margin, 1e-9)
        // Same seed, same games.
        assertEquals(p.homeWins, SportsModels.play(e, 5.5, 210.5, seed = 3).homeWins, 1e-12)
    }

    @Test
    fun `goals: every score adds to one, and the stronger side is likelier`() {
        val league = listOf(
            game("a", 1, "Reds", "Blues", 3, 0), game("b", 2, "Blues", "Reds", 0, 2),
            game("c", 3, "Greens", "Blues", 1, 1), game("d", 4, "Reds", "Greens", 2, 1)
        )
        val x = SportsModels.goals(game("e", 6, "Reds", "Blues", null, null), league, emptyList())
        assertEquals(1.0, x.grid.sumOf { it.sum() }, 1e-9)
        assertEquals(1.0, x.homeWin + x.draw + x.awayWin, 1e-9)
        assertTrue(x.homeWin > x.awayWin)
        assertTrue(x.over(2.5) in 0.0..1.0)
        val worse = SportsModels.goals(game("e", 6, "Reds", "Blues", null, null), league, listOf(Factor("keeper out", toHome = false, value = 0.5)))
        assertEquals(x.away + 0.5, worse.away, 1e-9)
    }

    @Test
    fun `races: the quickest is likeliest, and the championship counts places`() {
        val races = (1..3).map { r ->
            RaceRow(r, "Race $r", "2026-0$r-01", listOf(
                Finish(1, "A Driver", "Team One", "Finished"),
                Finish(2, "B Driver", "Team Two", "Finished"),
                Finish(3, "C Driver", "Team One", if (r == 2) "Engine" else "Finished")
            ))
        }
        val odds = SportsModels.race(races, emptyList(), seed = 5)
        assertEquals("A Driver", odds.first().driver)
        assertTrue(odds.first().win > odds.last().win)
        assertTrue(odds.first { it.driver == "C Driver" }.dnfRate > odds.first { it.driver == "A Driver" }.dnfRate)
        val table = SportsModels.standings(races)
        assertEquals("A Driver" to 75, table.first())
        assertEquals("C Driver" to 30, table.last()) // no points for the race not finished
    }

    @Test
    fun `the Lobster's line becomes a book, the spread on the home side`() {
        val g = game("1", 9, "Hawks", "Hornets", null, null, Line("A book", "HAW -6.5", 218.5, "-237", "+195"))
        val b = SportsLines.fromLine(g)!!
        assertEquals(-6.5, b.homeLine!!, 1e-9)
        assertEquals(218.5, b.total!!, 1e-9)
        val fair = b.fair(false)!!
        assertEquals(1.0, fair.sum(), 1e-9)
        assertTrue(fair[0] > 0.6)
        val away = SportsLines.fromLine(g.copy(line = Line(details = "HOR -2.5")))!!
        assertEquals(2.5, away.homeLine!!, 1e-9)
        assertEquals(0.0, SportsLines.fromLine(g.copy(line = Line(details = "EVEN")))!!.homeLine!!, 1e-9)
    }

    @Test
    fun `a pasted line is read`() {
        val g = game("1", 9, "Golden State Warriors", "Los Angeles Lakers", null, null).copy(homeAbbr = "GSW", awayAbbr = "LAL")
        val b = SportsLines.paste("Golden State Warriors −115 · Los Angeles Lakers −105 · Spread GSW −1.5 (−110) · O/U 228.5", g, withDraw = false)
        assertEquals("-115", b.homeMoney)
        assertEquals("-105", b.awayMoney)
        assertEquals(-1.5, b.homeLine!!, 1e-9)
        assertEquals("-110", b.linePrice)
        assertEquals(228.5, b.total!!, 1e-9)
        val f = game("2", 9, "Reds", "Blues", null, null)
        val s = SportsLines.paste("Reds 2.05 · Draw 3.50 · Blues 3.70", f, withDraw = true)
        assertEquals("2.05", s.homeMoney)
        assertEquals("3.50", s.draw)
        assertEquals("3.70", s.awayMoney)
    }

    @Test
    fun `picks settle from the score`() {
        val src = SportsLines.source(League.NBA, "9", Market.HOME_LINE, "-1.5")
        val pick = SportsLines.pickOf(src)!!
        assertEquals(Market.HOME_LINE, pick.market)
        val won = game("9", 9, "Hawks", "Hornets", 110, 105)
        assertEquals(true, SportsLines.outcome(pick, won, null))
        assertEquals(false, SportsLines.outcome(pick, won.copy(awayScore = 109), null))
        assertNull(SportsLines.outcome(SportsLines.pickOf(SportsLines.source(League.NBA, "9", Market.OVER, "215"))!!, won, null)) // exactly 215: a push
        assertNull(SportsLines.outcome(pick, won.copy(state = "pre", homeScore = null, awayScore = null), null))
        val race = RaceRow(4, "R", "2026-04-01", listOf(Finish(1, "A Driver", status = "Finished"), Finish(2, "B Driver", status = "Finished")))
        assertEquals(true, SportsLines.outcome(SportsLines.pickOf(SportsLines.source(League.F1, "4", Market.WIN, "A Driver"))!!, null, race))
        assertEquals(true, SportsLines.outcome(SportsLines.pickOf(SportsLines.source(League.F1, "4", Market.PODIUM, "B Driver"))!!, null, race))
        assertNull(SportsLines.pickOf("idea:1"))
    }

    @Test
    fun `a new file is laid over the old, keeping the line seen before the start`() {
        val before = SportsBundle(leagues = mapOf("nba" to LeagueData("NBA", "basketball", games = listOf(game("1", 9, "A", "B", null, null, Line(details = "AAA -3"))))))
        val after = SportsBundle(leagues = mapOf("nba" to LeagueData("NBA", "basketball", games = listOf(game("1", 9, "A", "B", 100, 90)))))
        val merged = SportsFile.merge(before, after)
        val g = merged.leagues.getValue("nba").games.single()
        assertEquals(100, g.homeScore)
        assertEquals("AAA -3", g.line?.details)
        assertEquals(merged, SportsFile.parse(SportsFile.encode(merged)))
        assertFalse(runCatching { SportsFile.parse("""{"format":"other"}""") }.isSuccess)
    }
}
