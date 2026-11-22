package app.localmind.assistant.domain.desk

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The path every idea takes on SlothDesk (1.49): Idea → Test → Paper → Review. Where an idea is
 * on it, how its paper days are going, and the week the Sunday review looks back on. Worked out
 * here, from what's kept; nothing on the path trades or reaches a broker.
 */
object DeskPath {

    enum class Stage(val label: String) { IDEA("Idea"), TEST("Test"), PAPER("Paper"), REVIEW("Review") }

    /** Paper days a rule is followed for before its review. */
    const val PAPER_DAYS = 60

    /** The share of marked tickets to follow over the paper days. */
    const val FOLLOWED = 0.80

    /** What happened instead, when a ticket wasn't followed: code → words. */
    val SLIPS: List<Pair<String, String>> = listOf(
        "late" to "Late",
        "skipped" to "Skipped it",
        "early" to "Traded early",
        "size" to "Changed the size",
        "distrust" to "Didn't trust it"
    )

    /** A slip's words, including the tags from before 1.49 (entry, exit, sizing…). */
    fun slip(code: String): String = SLIPS.firstOrNull { it.first == code }?.second ?: when (code) {
        "entry" -> "The entry"
        "exit" -> "The exit"
        "management" -> "Managing it"
        "sizing" -> "Changed the size"
        "skipped a ticket" -> "Skipped it"
        "", "none" -> ""
        else -> code
    }

    /** Where an idea is: written, its rule being tested, on paper, or ready for its review. */
    fun stage(hasRule: Boolean, onPaper: Boolean, paperDays: Int): Stage = when {
        !hasRule -> Stage.IDEA
        !onPaper -> Stage.TEST
        paperDays < PAPER_DAYS -> Stage.PAPER
        else -> Stage.REVIEW
    }

    // --- paper days ---

    /** One ticket in the paper journal, as far as the path needs it. */
    data class Mark(val dataDate: String, val followed: String, val slip: String)

    enum class Day { FOLLOWED, NOT_FOLLOWED, UNMARKED, TO_COME }

    /**
     * How the paper days are going: [days] journalled since [since], how many marked, followed
     * and slipped (by tag), the share followed of those marked, and the 60 days as cells.
     */
    data class Progress(
        val since: String,
        val days: Int,
        val marked: Int,
        val followed: Int,
        val slips: Map<String, Int>,
        val cells: List<Day>
    ) {
        val share: Double? get() = if (marked == 0) null else followed.toDouble() / marked
        val done: Boolean get() = days >= PAPER_DAYS
        val passing: Boolean get() = share?.let { it >= FOLLOWED } ?: false
    }

    /** The paper days of one rule: its tickets on or after [since] (a data date, yyyy-MM-dd). */
    fun progress(marks: List<Mark>, since: String): Progress {
        val mine = marks.filter { it.dataDate >= since }.sortedBy { it.dataDate }.distinctBy { it.dataDate }
        val marked = mine.filter { it.followed == "yes" || it.followed == "no" }
        val slips = mine.filter { it.followed == "no" }.groupingBy { it.slip.ifBlank { "unsaid" } }.eachCount()
        val cells = (0 until maxOf(PAPER_DAYS, mine.size)).map { i ->
            val m = mine.getOrNull(i)
            when {
                m == null -> Day.TO_COME
                m.followed == "yes" -> Day.FOLLOWED
                m.followed == "no" -> Day.NOT_FOLLOWED
                else -> Day.UNMARKED
            }
        }
        return Progress(since, mine.size, marked.size, marked.count { it.followed == "yes" }, slips, cells)
    }

    /**
     * Which strategies were being paper-traded before 1.49, when every strategy wrote a ticket
     * each night: those with a ticket you marked, from their first day in the journal.
     */
    fun followedBefore(journal: Map<String, List<Mark>>): Map<String, String> =
        journal.filterValues { l -> l.any { it.followed == "yes" || it.followed == "no" } }
            .mapValues { (_, l) -> l.minOf { it.dataDate } }

    // --- the Sunday review ---

    /** The Sunday on or after [day]: the end of its review week. */
    fun sundayOf(day: LocalDate): LocalDate = day.plusDays(((7 - day.dayOfWeek.value) % 7).toLong())

    /** Days until the next review: 0 on a Sunday. */
    fun daysToSunday(today: LocalDate): Int = (7 - today.dayOfWeek.value) % 7

    /** The review week ending [sunday]: Monday to Sunday. */
    fun week(sunday: LocalDate): ClosedRange<LocalDate> = sunday.minusDays(6)..sunday

    /**
     * The review waiting for you, as its Sunday: today's on a Sunday, else last Sunday's, unless
     * it's done. Only the latest is ever waiting: a missed week isn't stacked up.
     */
    fun waiting(today: LocalDate, done: Set<String>): LocalDate? {
        val last = if (today.dayOfWeek == DayOfWeek.SUNDAY) today else today.minusDays(today.dayOfWeek.value.toLong())
        return if (last.toString() in done) null else last
    }

    /**
     * The change in a series over a span: the last value on or before [to] against the last one
     * before [from]. Null when either is missing.
     */
    fun change(dates: List<LocalDate>, values: DoubleArray, from: LocalDate, to: LocalDate): Double? {
        val end = dates.indexOfLast { it <= to }
        val start = dates.indexOfLast { it < from }
        if (end < 0 || start < 0 || end <= start) return null
        val a = values[start]
        val b = values[end]
        if (a.isNaN() || b.isNaN() || a == 0.0) return null
        return b / a - 1
    }
}
