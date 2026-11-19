package app.localmind.assistant.ui.home

import app.localmind.assistant.data.desk.DeskState
import app.localmind.assistant.data.home.HomeSettings
import app.localmind.assistant.data.lobster.LobsterCatch
import app.localmind.assistant.domain.home.LunarSource
import app.localmind.assistant.domain.home.SkyCalendar
import app.localmind.assistant.domain.home.SkyNote
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The Home page's small extras, worked out from what's already on the tablet. No Android here,
// so all of it can be tested.

/** Seven days, oldest first and today last: was there any practice that day? */
data class PracticeWeek(val days: List<Boolean>, val labels: List<String>) {
    val count get() = days.count { it }
}

/**
 * A day counts as practice if you wrote to a tutor or reviewed a flashcard. [written] is
 * (persona, time) pairs; [reviews] are flashcard review times.
 */
fun practiceWeek(
    written: List<Pair<String, Long>>,
    tutorIds: Set<String>,
    reviews: List<Long>,
    today: LocalDate,
    zone: ZoneId
): PracticeWeek {
    val first = today.minusDays(6)
    val days = BooleanArray(7)
    fun mark(time: Long) {
        val d = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val i = ChronoUnit.DAYS.between(first, d).toInt()
        if (i in 0..6) days[i] = true
    }
    written.filter { it.first in tutorIds }.forEach { mark(it.second) }
    reviews.forEach(::mark)
    val labels = (0..6).map { first.plusDays(it.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) }
    return PracticeWeek(days.toList(), labels)
}

/** "today", "yesterday", "3 days ago", "2 weeks ago"; null if never. */
fun practisedAgo(time: Long?, today: LocalDate, zone: ZoneId): String? {
    if (time == null || time <= 0) return null
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(time).atZone(zone).toLocalDate(), today).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        else -> "a while ago"
    }
}

/** What a catch is about in one line: a brief's top headline, otherwise its title. */
fun catchHeadline(item: LobsterCatch): String {
    val brief = item.title.contains("brief", ignoreCase = true) && item.orderId == null
    val top = item.sources.firstOrNull()?.title?.trim().orEmpty()
    return if (brief && top.isNotEmpty()) top else item.title.trim()
}

/** The Desk at a glance, from its paper journal: the newest tickets and whether they're marked. */
data class DeskGlance(val dataDate: LocalDate, val trades: Int, val unmarked: Int, val nextOpen: String, val strategies: Int)

fun deskGlance(state: DeskState): DeskGlance? {
    val latest = state.journal.mapNotNull { runCatching { LocalDate.parse(it.dataDate) }.getOrNull() }.maxOrNull() ?: return null
    val rows = state.journal.filter { it.dataDate == latest.toString() }
    val trades = rows.filter { it.trade }
    return DeskGlance(
        dataDate = latest,
        trades = trades.size,
        unmarked = trades.count { it.followed.isBlank() },
        nextOpen = rows.firstOrNull()?.nextOpen.orEmpty(),
        strategies = rows.size
    )
}

/** "20 Nov" for an ISO date, else the text as it came. */
fun shortDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }.getOrDefault(iso)

/** The faint third line under the date, and whether today is a festival or a solar term. */
data class AlmanacLine(val text: String, val special: Boolean)

/**
 * The line under the date: today's festival or solar term, or the next one; the countdown to the
 * New Year in its last two months; and the sign the Sun is in. Null when there's nothing to show.
 */
fun almanacLine(today: LocalDate, s: HomeSettings, lunar: LunarSource?, traditional: Boolean): AlmanacLine? {
    val parts = ArrayList<String>()
    var special = false
    var note: SkyNote? = null
    if (s.festivals) {
        note = SkyCalendar.next(today, lunar)
        note?.let {
            parts += SkyCalendar.describe(it, traditional)
            special = it.daysAway == 0
        }
    }
    if (s.chineseZodiac && lunar != null) {
        val days = SkyCalendar.daysToNewYear(today, lunar)
        val year = today.plusDays((days ?: 0).toLong()).year
        val animal = SkyCalendar.ANIMAL_NAMES[SkyCalendar.animalOfYear(year)]
        val showingNewYear = note?.zh == "春节"
        when {
            days == null -> {}
            showingNewYear -> parts[parts.lastIndex] = parts.last() + " · Year of the $animal"
            days == 0 -> parts += "Year of the $animal begins"
            days <= 60 -> parts += "Year of the $animal in $days days"
        }
    }
    if (s.westernZodiac) {
        val sign = SkyCalendar.sunSign(today)
        parts += if (SkyCalendar.signChanges(today)) "Sun enters ${sign.symbol} ${sign.name}" else "${sign.symbol} ${sign.name}"
    }
    return if (parts.isEmpty()) null else AlmanacLine(parts.joinToString(" · "), special)
}

/** One row of the almanac that opens from the date: a label and what it says. */
data class AlmanacRow(val label: String, val text: String)

/** The almanac: the hour, the next term and festival, the New Year, the Sun's sign. */
fun almanac(today: LocalDate, hour: Int, s: HomeSettings, lunar: LunarSource?, traditional: Boolean): List<AlmanacRow> = buildList {
    val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    if (s.chineseZodiac) {
        val i = SkyCalendar.doubleHour(hour)
        val start = (i * 2 + 23) % 24
        add(AlmanacRow("Hour", "${SkyCalendar.doubleHourLabel(hour, traditional)} · %02d:00–%02d:00".format(start, (start + 2) % 24)))
    }
    if (s.festivals) {
        SkyCalendar.nextTerm(today)?.let {
            val name = if (traditional) it.zhTraditional else it.zh
            add(AlmanacRow("Solar term", "$name ${it.english} · ${if (it.daysAway == 0) "today" else today.plusDays(it.daysAway.toLong()).format(day)}"))
        }
        if (lunar != null) {
            SkyCalendar.nextFestival(today, lunar, skip = setOf("除夕"))?.let {
                val name = if (traditional) it.zhTraditional else it.zh
                add(AlmanacRow("Festival", "$name ${it.english} · ${if (it.daysAway == 0) "today" else today.plusDays(it.daysAway.toLong()).format(day)}"))
            }
        }
    }
    if (s.chineseZodiac && lunar != null) {
        SkyCalendar.daysToNewYear(today, lunar)?.let { d ->
            val date = today.plusDays(d.toLong())
            val i = SkyCalendar.animalOfYear(date.year)
            add(
                AlmanacRow(
                    "New Year",
                    "${SkyCalendar.stemBranch(date.year)}${SkyCalendar.animalChar(i, traditional)}年 · Year of the ${SkyCalendar.ANIMAL_NAMES[i]} · " +
                        if (d == 0) "today" else date.format(day)
                )
            )
        }
    }
    if (s.westernZodiac) {
        val sign = SkyCalendar.sunSign(today)
        var next = today.plusDays(1)
        var guard = 0
        while (SkyCalendar.sunSign(next) == sign && guard++ < 40) next = next.plusDays(1)
        val after = SkyCalendar.sunSign(next)
        add(AlmanacRow("Sun", "${sign.symbol} ${sign.name}, ${sign.meaning} · ${after.symbol} ${after.name} from ${next.format(day)}"))
    }
}
