package app.slothlobster

import app.slothlobster.jobs.Sports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 1.9: results and fixtures for SlothLlama's Sports page. */
class SportsTests {

    @Test
    fun `an order names its leagues and a first day`() {
        val today = LocalDate.of(2026, 11, 21)
        val (leagues, since) = Sports.request("Sports: NBA, epl and F1 from 2026-10-01", today)
        assertEquals(listOf("nba", "epl", "f1"), leagues)
        assertEquals(LocalDate.of(2026, 10, 1), since)
        // No date: the last week. Never more than 200 days back.
        assertEquals(today.minusDays(7), Sports.request("nfl", today).second)
        assertEquals(today.minusDays(200), Sports.request("nba from 2020-01-01", today).second)
    }

    @Test
    fun `a scoreboard page becomes games`() {
        val page = """
            {"events":[
              {"id":"1","date":"2026-11-21T00:30Z","status":{"type":{"state":"post","completed":true}},
               "competitions":[{"competitors":[
                 {"homeAway":"home","score":"112","team":{"displayName":"Home Town","abbreviation":"HOM"}},
                 {"homeAway":"away","score":"104","team":{"displayName":"Away City","abbreviation":"AWY"}}],
                 "odds":[{"provider":{"name":"A book"},"details":"HOM -6.5","overUnder":218.5,
                   "moneyline":{"home":{"close":{"odds":"-237"}},"away":{"open":{"odds":"+195"}}}}]}]},
              {"id":"2","date":"2026-11-22T01:00Z","status":{"type":{"state":"pre"}},
               "competitions":[{"competitors":[
                 {"homeAway":"home","score":"0","team":{"displayName":"Away City","abbreviation":"AWY"}},
                 {"homeAway":"away","score":"0","team":{"displayName":"Home Town","abbreviation":"HOM"}}]}]},
              {"id":"3","date":"2026-11-22T01:00Z","competitions":[]}
            ]}
        """.trimIndent()
        val games = Sports.scoreboard(page)
        assertEquals(2, games.size)
        val g = games[0]
        assertEquals("Home Town", g.home)
        assertEquals(112, g.homeScore)
        assertEquals(104, g.awayScore)
        assertEquals("post", g.state)
        assertEquals("HOM -6.5", g.line?.details)
        assertEquals(218.5, g.line?.overUnder!!, 1e-9)
        assertEquals("-237", g.line?.homeMoney)
        assertEquals("+195", g.line?.awayMoney)
        assertNull(games[1].homeScore) // not started: no score
        assertNull(games[1].line)
    }
}
