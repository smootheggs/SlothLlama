package app.slothlobster

import app.slothlobster.jobs.EditionClock
import app.slothlobster.jobs.EditionMiss
import app.slothlobster.jobs.EditionPlan
import app.slothlobster.jobs.EditionTime
import app.slothlobster.jobs.Editions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 1.14: the edition wakes at its time, a failed one is tried again, and only one that came out counts. */
class EditionClockTests {

    private val morning = EditionTime("Morning", 7, 0)
    private val plan = EditionPlan(times = listOf(morning, EditionTime("Evening", 18, 30)))

    /** Tuesday 24 November 2026 at [h]:[m]. */
    private fun at(h: Int, m: Int, day: Int = 24): Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, h, m, 0)
    }

    private fun ms(h: Int, m: Int, day: Int = 24) = at(h, m, day).timeInMillis

    @Test
    fun `the alarm is set for the next time, tomorrow once today's are past`() {
        assertEquals(ms(7, 0), EditionClock.nextAt(plan, at(6, 10)))
        assertEquals(ms(18, 30), EditionClock.nextAt(plan, at(7, 0)))
        assertEquals(ms(7, 0, 25), EditionClock.nextAt(plan, at(19, 0)))
        assertNull(EditionClock.nextAt(plan.copy(on = false), at(6, 0)))
    }

    @Test
    fun `a weekdays-only plan skips to Monday`() {
        val weekdays = plan.copy(weekends = false, times = listOf(morning))
        // Friday 27 November, after the morning: next is Monday 30th.
        assertEquals(ms(7, 0, 30), EditionClock.nextAt(weekdays, at(9, 0, 27)))
    }

    @Test
    fun `a failed try is tried again after 15, 15, 30, 30 then 60 minutes`() {
        assertEquals(listOf(15L, 15, 30, 30, 60, 60), (1..6).map { EditionClock.retryDelayMs(it) / 60_000 })
    }

    @Test
    fun `failed tries go on until four hours after the time, then give up`() {
        val key = EditionClock.key(at(7, 0), morning)
        var miss = EditionClock.failed(null, key, "Morning", at(7, 1), morning, "None of the edition's sources answered")
        assertEquals(1, miss.tries)
        assertEquals(ms(7, 16), miss.nextAt)
        assertEquals(ms(11, 0), miss.giveUpAt)
        assertFalse(miss.gaveUp)
        assertTrue(EditionClock.waiting(miss, key, ms(7, 10)))
        assertFalse(EditionClock.waiting(miss, key, ms(7, 16)))
        miss = EditionClock.failed(miss, key, "Morning", at(7, 16), morning, "x")
        assertEquals(2, miss.tries)
        assertEquals(ms(7, 31), miss.nextAt)
        // Late in the window, the next try would come after 11:00: given up.
        miss = EditionClock.failed(miss.copy(tries = 5), key, "Morning", at(10, 30), morning, "x")
        assertTrue(miss.gaveUp)
        assertEquals(0L, miss.nextAt)
        assertFalse(EditionClock.waiting(miss, key, ms(10, 31)))
    }

    @Test
    fun `a tablet that only woke late still gets an hour of tries`() {
        val key = EditionClock.key(at(7, 0), morning)
        val miss = EditionClock.failed(null, key, "Morning", at(10, 50), morning, "x")
        assertEquals(ms(11, 50), miss.giveUpAt)
        assertFalse(miss.gaveUp)
    }

    @Test
    fun `yesterday's miss doesn't count today`() {
        val yesterday = EditionMiss("2026-11-23|Morning", "Morning", 3, ms(7, 45, 23), ms(11, 0, 23), "x", gaveUp = true)
        val fresh = EditionClock.failed(yesterday, EditionClock.key(at(7, 0), morning), "Morning", at(7, 0), morning, "y")
        assertEquals(1, fresh.tries)
        assertNull(EditionClock.statusLine(yesterday, at(8, 0), { "" }))
    }

    @Test
    fun `the Lobster wakes for a retry before the next time`() {
        val miss = EditionMiss("2026-11-24|Morning", "Morning", 1, ms(7, 16), ms(11, 0), "x")
        assertEquals(ms(7, 16), EditionClock.nextWake(plan, at(7, 2), miss))
        assertEquals(ms(18, 30), EditionClock.nextWake(plan, at(7, 2), miss.copy(gaveUp = true, nextAt = 0)))
        assertNull(EditionClock.nextWake(null, at(7, 2), miss))
    }

    @Test
    fun `a failed edition isn't counted as made, so it's still due`() {
        // Nothing marked: the morning is still due at 07:20 after a failed 07:05 try.
        assertEquals("Morning", Editions.due(plan, at(7, 20), emptyList())?.name)
        val made = Editions.madeKeys(plan, at(7, 20), morning)
        assertNull(Editions.due(plan, at(7, 20), made))
    }

    @Test
    fun `the status line says late with the next try, or why it didn't come out`() {
        val miss = EditionMiss("2026-11-24|Morning", "Morning", 1, ms(7, 16), ms(11, 0), "None of the edition's sources answered")
        val late = EditionClock.statusLine(miss, at(7, 5)) { "07:16" }
        assertNotNull(late)
        assertEquals("Morning edition is late: None of the edition's sources answered. Trying again at 07:16", late)
        assertEquals(
            "Morning edition didn't come out: None of the edition's sources answered",
            EditionClock.statusLine(miss.copy(gaveUp = true, nextAt = 0), at(11, 5)) { "" }
        )
    }
}
