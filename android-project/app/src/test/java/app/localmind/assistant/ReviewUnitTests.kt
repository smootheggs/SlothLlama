package app.localmind.assistant

import app.localmind.assistant.data.study.CardMemory
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.domain.study.Answers
import app.localmind.assistant.domain.study.Ask
import app.localmind.assistant.domain.study.Item
import app.localmind.assistant.domain.study.Match
import app.localmind.assistant.domain.study.ReverseSide
import app.localmind.assistant.domain.study.Session
import app.localmind.assistant.domain.study.Study
import app.localmind.assistant.domain.study.StudySettings
import app.localmind.assistant.domain.study.StudyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.51: reviewing flashcards — today's plan, the session, typed answers. */
class ReviewUnitTests {

    private val day = 24 * 60 * 60_000L
    private val now = 1_800_000_000_000L
    private val today = "2027-01-15"

    private fun card(
        id: String, interval: Int = 3, due: Long = now - day, lapses: Int = 0, new: Boolean = false,
        lang: StudyLanguage = StudyLanguage.MANDARIN, term: String = "学习", meaning: String = "to study; to learn"
    ) = Flashcard(
        id = id, language = lang, term = term, pronunciation = "xuéxí", meaning = meaning, example = "",
        ease = 2.5f, intervalDays = if (new) 0 else interval, dueAt = due,
        memory = if (new) "" else CardMemory(5.0, 5.0, due - interval * day, 3, lapses).encode()
    )

    private fun state(ask: String = "WORD", newPerDay: Int = 20, reverse: Map<String, ReverseSide> = emptyMap(), langs: List<String> = emptyList()) =
        StudyState(settings = StudySettings(askWith = ask, newPerDay = newPerDay, languages = langs), reverse = reverse)

    @Test
    fun `today's plan puts the most overdue first, then new cards up to the day's allowance`() {
        val cards = listOf(card("a", due = now - day), card("b", due = now - 3 * day), card("later", due = now + day)) +
            (1..5).map { card("n$it", new = true, due = now - it) }
        val plan = Study.plan(cards, state(newPerDay = 3), now, today)
        assertEquals(listOf("b", "a"), plan.items.take(2).map { it.id })
        assertEquals(2, plan.reviews)
        assertEquals(3, plan.fresh)
        assertTrue(plan.items.none { it.id == "later" })
        // Two new cards already shown today leave one.
        val shown = Study.countNew(Study.countNew(state(newPerDay = 3), today), today)
        assertEquals(1, Study.plan(cards, shown, now, today).fresh)
        // A new day starts the count again.
        assertEquals(3, Study.plan(cards, shown, now, "2027-01-16").fresh)
    }

    @Test
    fun `the meaning side opens once the word is known, keeps its own schedule, and siblings show once`() {
        val known = card("k", interval = 4, due = now + 2 * day)
        val fresh = card("f", new = true)
        val young = card("y", interval = 0, due = now + day)
        // Meaning: only cards recalled across a day get a meaning side, as new cards.
        val m = Study.plan(listOf(known, fresh, young), state("MEANING"), now, today)
        assertEquals(listOf(Item("k", Ask.MEANING, true)), m.items)
        // Its own schedule: due by its own date, not the word's.
        val rev = mapOf("k" to ReverseSide(CardMemory(2.0, 5.0, now - 3 * day, 1, 0).encode(), 2, now - day))
        assertEquals(listOf(Item("k", Ask.MEANING, false)), Study.plan(listOf(known), state("MEANING", reverse = rev), now, today).items)
        // Mixed: a card due both ways shows once, the more overdue side.
        val both = card("b", interval = 4, due = now - 2 * day)
        val revB = mapOf("b" to ReverseSide("x", 2, now - 5 * day))
        val mixed = Study.plan(listOf(both), state("MIXED", reverse = revB), now, today)
        assertEquals(listOf(Item("b", Ask.MEANING, false)), mixed.items)
        assertEquals(Study.reverseCard(both, revB["b"]).dueAt, now - 5 * day)
    }

    @Test
    fun `languages can be chosen`() {
        val cards = listOf(card("zh"), card("yue", lang = StudyLanguage.CANTONESE))
        assertEquals(listOf("yue"), Study.plan(cards, state(langs = listOf("yue")), now, today).items.map { it.id })
    }

    @Test
    fun `a missed card comes back three cards later and the first answer is what counts`() {
        val items = (1..5).map { Item("c$it", Ask.WORD, it == 1) }
        var s = Session.of(items)
        s = s.answer(Grade.AGAIN)
        assertEquals(listOf("c2", "c3", "c4", "c1", "c5"), s.queue.map { it.id })
        assertFalse(s.queue[3].isNew)
        repeat(3) { s = s.answer(Grade.GOOD) }
        assertEquals("c1", s.current?.id)
        s = s.answer(Grade.GOOD)
        s = s.answer(Grade.GOOD)
        assertTrue(s.finished)
        assertEquals(5, s.done)
        assertEquals(4, s.rightFirstTime)
        assertEquals(listOf("c1"), s.missed.map { it.id })
    }

    @Test
    fun `missing a card again the same day isn't another lapse`() {
        val c = card("r", interval = 5, due = now)
        val first = app.localmind.assistant.data.study.Scheduler.next(c, Grade.AGAIN, now)
        assertEquals(1, CardMemory.decode(first.memory)!!.lapses)
        val again = c.copy(memory = first.memory, intervalDays = first.intervalDays, dueAt = first.dueAt)
        val second = app.localmind.assistant.data.study.Scheduler.next(again, Grade.AGAIN, now + 15 * 60_000L)
        assertEquals(1, CardMemory.decode(second.memory)!!.lapses)
    }

    @Test
    fun `leeches are cards missed eight times or more`() {
        assertTrue(Study.isLeech(card("l", lapses = 8)))
        assertFalse(Study.isLeech(card("ok", lapses = 7)))
        assertFalse(Study.isLeech(card("n", new = true)))
    }

    @Test
    fun `typed Chinese words are exact, one character off, or wrong`() {
        assertEquals(Match.EXACT, Answers.word("机会", " 机会。", cjk = true).match)
        val near = Answers.word("机会", "机汇", cjk = true)
        assertEquals(Match.CLOSE, near.match)
        assertEquals(setOf(1), near.wrongAt)
        assertEquals(Grade.HARD, near.suggested)
        assertEquals(Match.WRONG, Answers.word("会", "汇", cjk = true).match)
        assertEquals(Match.WRONG, Answers.word("机会", "", cjk = true).match)
        assertEquals(Match.WRONG, Answers.word("机会", "时机", cjk = true).match)
    }

    @Test
    fun `typed European words point out accents and articles, and short words get no leeway`() {
        assertEquals(Match.EXACT, Answers.word("l'école", "L'école", cjk = false).match)
        assertEquals("Check the accents", Answers.word("l'école", "l'ecole", cjk = false).note)
        val article = Answers.word("das Haus", "Haus", cjk = false)
        assertEquals(Match.CLOSE, article.match)
        assertTrue(article.note.contains("das Haus"))
        assertEquals(Match.CLOSE, Answers.word("Wohnung", "Wohnnug", cjk = false).match)
        assertEquals(Match.WRONG, Answers.word("rot", "rat", cjk = false).match)
        assertEquals(1, Answers.distance("wohnung", "wohnnug"))
        assertEquals(3, Answers.distance("kitten", "sitting"))
    }

    @Test
    fun `a typed meaning can be any of them, with or without to`() {
        assertEquals(Match.EXACT, Answers.meaning("to study; to learn", "learn").match)
        assertEquals(Match.EXACT, Answers.meaning("to study; to learn", "To Study").match)
        assertEquals(Match.EXACT, Answers.meaning("opportunity (a chance)", "opportunity").match)
        assertEquals(Match.CLOSE, Answers.meaning("opportunity", "oportunity").match)
        assertEquals(Match.WRONG, Answers.meaning("north", "south").match)
        assertEquals(Grade.AGAIN, Answers.meaning("north", "").suggested)
    }

    @Test
    fun `the study file round-trips and is kept within bounds`() {
        val s = state("MIXED", newPerDay = 999, reverse = mapOf("k" to ReverseSide("m", 2, now)), langs = listOf("zh", "xx"))
        val back = Study.decode(Study.encode(s))
        assertEquals(Study.MAX_NEW_PER_DAY, back.settings.newPerDay)
        assertEquals(listOf("zh"), back.settings.languages)
        assertEquals(s.reverse, back.reverse)
        assertEquals("WORD", Study.sound(s.copy(settings = s.settings.copy(askWith = "junk"))).settings.askWith)
        assertTrue(runCatching { Study.decode("{\"format\":\"other\"}") }.isFailure)
    }

    @Test
    fun `about twenty seconds a review and forty a new card`() {
        val plan = Study.plan((1..9).map { card("r$it") } + (1..3).map { card("n$it", new = true) }, state(), now, today)
        assertEquals(5, plan.minutes)
        assertEquals(0, Study.plan(emptyList(), state(), now, today).minutes)
    }
}
