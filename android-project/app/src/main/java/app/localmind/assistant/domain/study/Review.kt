package app.localmind.assistant.domain.study

import app.localmind.assistant.data.study.CardMemory
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.data.study.StudyLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.min

/**
 * Reviewing flashcards (1.51): what to review today, one session at a time, and how close a typed
 * answer came. Pure Kotlin, so every rule is tested on the JVM. The ideas are the ones that work
 * in Anki and WaniKani: each card's two directions are siblings with their own schedules, only one
 * of which shows per session; new cards are rationed per day; a card missed in a session comes
 * back a few cards later; a card missed again and again is a "leech" worth rewriting; and a typed
 * near miss is pointed out but never graded for you.
 */

/** Which side a card shows first: the word (recall its meaning) or the meaning (recall the word). */
enum class Ask { WORD, MEANING }

/** What a review asks with. */
enum class AskWith(val label: String) { WORD("Word"), MEANING("Meaning"), MIXED("Mixed") }

@Serializable
data class StudySettings(
    val askWith: String = "WORD",
    /** Show the pinyin or other reading on the front too (off: it gives the word away). */
    val showReading: Boolean = false,
    /** Type the answer before the flip. */
    val typed: Boolean = false,
    /** New cards a day, both directions together (Anki's default is 20). */
    val newPerDay: Int = 20,
    /** Language codes to review; empty: all of them. */
    val languages: List<String> = emptyList()
) {
    val ask: AskWith get() = AskWith.entries.firstOrNull { it.name == askWith } ?: AskWith.WORD
}

/** The meaning→word side's own schedule: the same FSRS memory the word side keeps in the database. */
@Serializable
data class ReverseSide(val memory: String = "", val intervalDays: Int = 0, val dueAt: Long = 0)

/** Everything the review keeps beside the cards (study/study.json). */
@Serializable
data class StudyState(
    val format: String = Study.FORMAT,
    val v: Int = 1,
    val settings: StudySettings = StudySettings(),
    val reverse: Map<String, ReverseSide> = emptyMap(),
    /** The day ("2026-11-22") [newCount] new cards were first shown on. */
    val newDay: String = "",
    val newCount: Int = 0
)

/** One card on one side, in a session. */
data class Item(val id: String, val ask: Ask, val isNew: Boolean) {
    val key: String get() = "$id:${ask.name}"
}

/** Today's review: the items in order, and how many are reviews and how many new. */
data class Plan(val items: List<Item>, val reviews: Int, val fresh: Int) {
    /** About 20 seconds a review and 40 a new card. */
    val minutes: Int get() = if (items.isEmpty()) 0 else ceil((reviews * 20 + fresh * 40) / 60.0).toInt().coerceAtLeast(1)
}

object Study {
    const val FORMAT = "slothllama-study"
    /** Misses before a card counts as a leech (Anki's default). */
    const val LEECH = 8
    const val MAX_NEW_PER_DAY = 200
    private const val MAX_REVERSE = 20_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(s: StudyState): String = json.encodeToString(StudyState.serializer(), s)

    /** The state from its file, kept within bounds; throws if it isn't one. */
    fun decode(text: String): StudyState = sound(json.decodeFromString(StudyState.serializer(), text))

    fun sound(s: StudyState): StudyState {
        require(s.format == FORMAT) { "Not a study file" }
        require(s.v == 1) { "A newer study file than this SlothLlama reads" }
        val codes = StudyLanguage.entries.map { it.code }.toSet()
        return s.copy(
            settings = s.settings.copy(
                askWith = s.settings.ask.name,
                newPerDay = s.settings.newPerDay.coerceIn(0, MAX_NEW_PER_DAY),
                languages = s.settings.languages.filter { it in codes }.distinct()
            ),
            reverse = s.reverse.entries.asSequence()
                .filter { (id, r) -> id.length in 1..64 && r.memory.length <= 120 && r.intervalDays in 0..36_500 }
                .take(MAX_REVERSE).associate { it.key to it.value },
            newDay = s.newDay.take(10),
            newCount = s.newCount.coerceIn(0, 10_000)
        )
    }

    /** Never reviewed. */
    fun isNew(c: Flashcard): Boolean = c.memory.isBlank() && c.intervalDays == 0

    fun lapses(c: Flashcard): Int = CardMemory.decode(c.memory)?.lapses ?: 0

    /** Missed [LEECH] times or more: worth rewriting, splitting or giving an example. */
    fun isLeech(c: Flashcard): Boolean = lapses(c) >= LEECH

    /** The meaning→word side opens once the word has been recalled across a day: recognise first, then produce. */
    fun canReverse(c: Flashcard): Boolean = c.meaning.isNotBlank() && !isNew(c) && c.intervalDays >= 1

    /** The card as its meaning→word side, for the scheduler: that side's memory, due date and interval. */
    fun reverseCard(c: Flashcard, side: ReverseSide?): Flashcard =
        c.copy(ease = 2.5f, intervalDays = side?.intervalDays ?: 0, dueAt = side?.dueAt ?: 0L, memory = side?.memory.orEmpty())

    /**
     * What to review [today]: reviews due by [now], most overdue first, then new cards up to the
     * day's allowance. One side per card per session (Anki's sibling burying): in Mixed, a card
     * due both ways shows the more overdue side today.
     */
    fun plan(cards: List<Flashcard>, state: StudyState, now: Long, today: String): Plan {
        val s = state.settings
        val pool = cards.filter { s.languages.isEmpty() || it.language.code in s.languages }
        val reviews = ArrayList<Pair<Item, Long>>()
        val fresh = ArrayList<Pair<Item, Long>>()
        if (s.ask != AskWith.MEANING) for (c in pool) {
            if (isNew(c)) fresh += Item(c.id, Ask.WORD, true) to c.dueAt
            else if (c.dueAt <= now) reviews += Item(c.id, Ask.WORD, false) to c.dueAt
        }
        if (s.ask != AskWith.WORD) for (c in pool) {
            val r = state.reverse[c.id]
            if (r == null) { if (canReverse(c)) fresh += Item(c.id, Ask.MEANING, true) to c.dueAt }
            else if (r.dueAt <= now) reviews += Item(c.id, Ask.MEANING, false) to r.dueAt
        }
        val left = (s.newPerDay - if (state.newDay == today) state.newCount else 0).coerceAtLeast(0)
        val due = reviews.sortedBy { it.second }.map { it.first }.distinctBy { it.id }
        val dueIds = due.map { it.id }.toSet()
        val new = fresh.sortedBy { it.second }.map { it.first }.filter { it.id !in dueIds }.distinctBy { it.id }.take(left)
        return Plan(due + new, due.size, new.size)
    }

    /** The day's new-card count after [shown] more new cards. */
    fun countNew(state: StudyState, today: String, shown: Int = 1): StudyState =
        if (state.newDay == today) state.copy(newCount = state.newCount + shown) else state.copy(newDay = today, newCount = shown)
}

/**
 * One review session. A card missed ("Again") comes back [REQUEUE] cards later, as Anki's
 * relearning step does, until it's answered; its first answer is the one the summary counts.
 * A practice session (going over the ones you missed) changes no schedule.
 */
data class Session(
    val queue: List<Item>,
    val all: List<Item>,
    val first: Map<String, Grade> = emptyMap(),
    val practice: Boolean = false
) {
    val current: Item? get() = queue.firstOrNull()
    val total: Int get() = all.size
    /** Items answered at least once. */
    val done: Int get() = first.size
    val finished: Boolean get() = queue.isEmpty()
    val missed: List<Item> get() = all.filter { first[it.key] == Grade.AGAIN }
    val rightFirstTime: Int get() = first.values.count { it != Grade.AGAIN }

    fun answer(grade: Grade): Session {
        val item = queue.firstOrNull() ?: return this
        val rest = queue.drop(1)
        val next = if (grade == Grade.AGAIN) rest.take(REQUEUE) + item.copy(isNew = false) + rest.drop(REQUEUE) else rest
        return copy(queue = next, first = if (item.key in first) first else first + (item.key to grade))
    }

    companion object {
        const val REQUEUE = 3
        fun of(items: List<Item>, practice: Boolean = false) = Session(items, items, practice = practice)
    }
}

enum class Match { EXACT, CLOSE, WRONG }

/** A typed answer, checked: how close, what the answer was, which of its characters you got wrong, and a word on why. */
data class Checked(val match: Match, val expected: String, val wrongAt: Set<Int> = emptySet(), val note: String = "") {
    /** The grade to suggest; the buttons stay yours to press. */
    val suggested: Grade get() = when (match) { Match.EXACT -> Grade.GOOD; Match.CLOSE -> Grade.HARD; Match.WRONG -> Grade.AGAIN }
}

/**
 * Typed answers. A meaning matches any of the meanings given ("to study; to learn"), with or
 * without "to" or an article. A near miss is CLOSE only where a typo is likely: one character
 * off in a word of 4 to 7 letters, two in a longer one, never in a shorter one (as WaniKani), and
 * for Chinese and Japanese one character off in a word of two or more. Missing accents and a
 * missing article are CLOSE, with a note.
 */
object Answers {
    private val CJK = setOf("zh", "yue", "ja")
    private val ARTICLES = setOf(
        "to", "a", "an", "the", "le", "la", "les", "l'", "un", "une", "der", "die", "das", "ein", "eine",
        "el", "los", "las", "uno", "il", "lo", "gli", "i"
    )

    fun check(card: Flashcard, ask: Ask, typed: String): Checked =
        if (ask == Ask.MEANING) word(card.term, typed, card.language.code in CJK) else meaning(card.meaning, typed)

    /** The word, from its meaning. */
    fun word(expected: String, typed: String, cjk: Boolean): Checked {
        if (cjk) {
            val want = chars(expected)
            val got = chars(typed)
            if (got.isEmpty()) return Checked(Match.WRONG, expected, want.indices.toSet())
            if (want == got) return Checked(Match.EXACT, expected)
            val off = if (want.size == got.size) want.indices.filter { want[it] != got[it] }.toSet() else want.indices.toSet()
            val close = want.size >= 2 && want.size == got.size && off.size == 1
            return Checked(if (close) Match.CLOSE else Match.WRONG, expected, off,
                if (close) "One character off" else "")
        }
        val want = latin(expected, keepAccents = true)
        val got = latin(typed, keepAccents = true)
        if (got.isEmpty()) return Checked(Match.WRONG, expected)
        if (want == got) return Checked(Match.EXACT, expected)
        if (latin(expected, false) == latin(typed, false)) return Checked(Match.CLOSE, expected, note = "Check the accents")
        if (bare(want) == got) return Checked(Match.CLOSE, expected, note = "With its article: $expected")
        return if (typo(want, got)) Checked(Match.CLOSE, expected, note = "Nearly: a typo away") else Checked(Match.WRONG, expected)
    }

    /** The meaning, from the word: any of its meanings will do. */
    fun meaning(expected: String, typed: String): Checked {
        val options = expected.split(';', ',', '/').map { bare(latin(it.replace(Regex("\\(.*?\\)"), " "), false)) }.filter { it.isNotEmpty() }
        val got = bare(latin(typed, false))
        if (got.isEmpty() || options.isEmpty()) return Checked(Match.WRONG, expected)
        if (got in options) return Checked(Match.EXACT, expected)
        return if (options.any { typo(it, got) }) Checked(Match.CLOSE, expected, note = "Nearly: a typo away") else Checked(Match.WRONG, expected)
    }

    private fun chars(s: String): List<Int> =
        Normalizer.normalize(s, Normalizer.Form.NFKC).codePoints().toArray().filter { Character.isLetterOrDigit(it) }

    private fun latin(s: String, keepAccents: Boolean): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKC).replace('’', '\'')
        if (!keepAccents) t = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return t.replace(Regex("[^\\p{L}\\p{N}' ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** Without a leading "to", "the" or article. */
    private fun bare(s: String): String {
        val first = s.substringBefore(' ')
        return when {
            s.startsWith("l'") -> s.removePrefix("l'").trim()
            first in ARTICLES && s.contains(' ') -> s.substringAfter(' ').trim()
            else -> s
        }
    }

    private fun typo(want: String, got: String): Boolean {
        val n = want.length
        val allowed = when { n < 4 -> 0; n < 8 -> 1; else -> 2 }
        return allowed > 0 && distance(want, got, allowed) <= allowed
    }

    /**
     * Edit distance counting a swap of two neighbours as one edit, the commonest typo (optimal
     * string alignment), stopping early past [cap].
     */
    fun distance(a: String, b: String, cap: Int = Int.MAX_VALUE): Int {
        if (kotlin.math.abs(a.length - b.length) > cap) return cap + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var best = cur[0]
            for (j in 1..b.length) {
                var d = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d = min(d, prev2[j - 2] + 1)
                cur[j] = d
                best = min(best, d)
            }
            if (best > cap) return cap + 1
            prev2 = prev
            prev = cur
        }
        return prev[b.length]
    }
}
