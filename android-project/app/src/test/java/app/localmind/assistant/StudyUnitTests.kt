package app.localmind.assistant

import app.localmind.assistant.data.study.CardMemory
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.data.study.Scheduler
import app.localmind.assistant.data.study.StudyLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FSRS-5 flashcard scheduling. */
class StudyUnitTests {

    private val day = 24 * 60 * 60_000L
    private val start = 1_800_000_000_000L

    private fun card(memory: String = "", interval: Int = 0, ease: Float = 2.5f, due: Long = start) = Flashcard(
        id = "c1",
        language = StudyLanguage.values().first(),
        term = "Haus",
        pronunciation = "",
        meaning = "house",
        example = "",
        ease = ease,
        intervalDays = interval,
        dueAt = due,
        memory = memory
    )

    private fun Flashcard.after(next: Scheduler.Next) =
        copy(ease = next.ease, intervalDays = next.intervalDays, dueAt = next.dueAt, memory = next.memory)

    @Test
    fun `a new card follows the FSRS defaults`() {
        val again = Scheduler.next(card(), Grade.AGAIN, start)
        assertEquals(0, again.intervalDays)
        assertEquals(start + 10 * 60_000L, again.dueAt)
        assertEquals(1, Scheduler.next(card(), Grade.HARD, start).intervalDays)
        assertEquals(3, Scheduler.next(card(), Grade.GOOD, start).intervalDays)
        assertEquals(16, Scheduler.next(card(), Grade.EASY, start).intervalDays)
    }

    @Test
    fun `recall at the stability is ninety percent`() {
        assertEquals(0.9, Scheduler.retrievability(7.0, 7.0), 1e-9)
        assertEquals(7.0, Scheduler.interval(7.0), 1e-9)
    }

    @Test
    fun `remembering on time stretches the gap and forgetting shrinks it`() {
        val first = card().after(Scheduler.next(card(), Grade.GOOD, start))
        val second = Scheduler.next(first, Grade.GOOD, first.dueAt)
        assertEquals(11, second.intervalDays)
        val reviewed = first.after(second)
        val hard = Scheduler.next(first, Grade.HARD, first.dueAt).intervalDays
        val easy = Scheduler.next(first, Grade.EASY, first.dueAt).intervalDays
        assertTrue(hard < second.intervalDays && second.intervalDays < easy)

        val lapse = Scheduler.next(reviewed, Grade.AGAIN, reviewed.dueAt)
        val memory = assertNotNullMemory(lapse.memory)
        assertEquals(1, memory.lapses)
        assertEquals(3, memory.reviews)
        assertTrue(memory.stability < 11.0)
        assertEquals(0, lapse.intervalDays)
    }

    @Test
    fun `cards from the old scheduler keep their progress`() {
        val old = card(interval = 10, ease = 2.5f, due = start)
        val next = Scheduler.next(old, Grade.GOOD, start)
        assertTrue("got ${next.intervalDays}", next.intervalDays in 25..40)
    }

    @Test
    fun `memory survives being stored and rejects junk`() {
        val m = CardMemory(stability = 12.5, difficulty = 4.2, lastReview = start, reviews = 3, lapses = 1)
        val back = assertNotNullMemory(m.encode())
        assertEquals(12.5, back.stability, 1e-3)
        assertEquals(4.2, back.difficulty, 1e-3)
        assertEquals(start, back.lastReview)
        assertNull(CardMemory.decode(""))
        assertNull(CardMemory.decode("B1"))
        assertNull(CardMemory.decode("fsrs1|x|4|1|1|0"))
        assertNull(CardMemory.decode("fsrs1|5|42|1|1|0"))
    }

    @Test
    fun `labels read naturally`() {
        assertEquals("10m", Scheduler.label(Scheduler.next(card(), Grade.AGAIN, start), start))
        assertEquals("3d", Scheduler.label(Scheduler.next(card(), Grade.GOOD, start), start))
        assertEquals("1mo", Scheduler.label(Scheduler.Next(2.5f, 40, start + 40 * day), start))
    }

    private fun assertNotNullMemory(text: String): CardMemory {
        val m = CardMemory.decode(text)
        assertNotNull("memory was \"$text\"", m)
        return m!!
    }
}
