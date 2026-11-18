package app.slothlobster.jobs

import java.util.Calendar

/** When an order repeats: every day, or every week on one day, at a time. */
data class RepeatRule(val everyDays: Int, val weekday: Int, val hour: Int, val minute: Int) {
    fun describe(): String {
        val time = "%02d:%02d".format(hour, minute)
        return if (everyDays == 7) "Every ${Repeats.dayName(weekday)} at $time" else "Every day at $time"
    }
}

/** Reading "every Monday at 7" and friends out of an order, and knowing when a repeat is due. */
object Repeats {

    private val DAYS = listOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    fun dayName(weekday: Int): String =
        DAYS.firstOrNull { it.second == weekday }?.first?.replaceFirstChar { it.uppercase() } ?: "Monday"

    private val WEEKDAY = Regex("""\b(?:every|each|on)\s+(sunday|monday|tuesday|wednesday|thursday|friday|saturday)s?\b""", RegexOption.IGNORE_CASE)
    private val DAILY = Regex("""\b(?:every\s+day|each\s+day|daily|every\s+(?:morning|evening|night))\b""", RegexOption.IGNORE_CASE)
    private val WEEKLY = Regex("""\b(?:weekly|every\s+week|each\s+week)\b""", RegexOption.IGNORE_CASE)
    private val AT_TIME = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?(?=\s|$|[,.!?])""", RegexOption.IGNORE_CASE)

    /** The repeat an order asks for, or null if it's a one-off. */
    fun parse(text: String): RepeatRule? {
        val day = WEEKDAY.find(text)
        val weekly = day != null || WEEKLY.containsMatchIn(text)
        val daily = DAILY.containsMatchIn(text)
        if (!weekly && !daily) return null
        val weekday = day?.groupValues?.get(1)?.lowercase()?.let { d -> DAYS.first { it.first == d }.second } ?: Calendar.MONDAY
        val (hour, minute) = timeOf(text)
        return RepeatRule(if (weekly) 7 else 1, weekday, hour, minute)
    }

    private fun timeOf(text: String): Pair<Int, Int> {
        AT_TIME.find(text)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            val ampm = m.groupValues[3].lowercase().replace(".", "")
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            if (h in 0..23 && min in 0..59) return h to min
        }
        val lower = text.lowercase()
        return when {
            "evening" in lower -> 18 to 0
            "night" in lower -> 21 to 0
            else -> 7 to 0
        }
    }

    /** The order without its repeat words: "every Monday at 7, research X" → "research X". */
    fun strip(text: String): String =
        text.replace(WEEKDAY, " ").replace(DAILY, " ").replace(WEEKLY, " ").replace(AT_TIME, " ")
            .replace(Regex("""\s+"""), " ")
            .trim().trim(',', ';', ':', '-', ' ')
            .ifBlank { text.trim() }

    /** Due now: the right day, past its time, and not yet run today. */
    fun due(everyDays: Int, weekday: Int, hour: Int, minute: Int, lastRunDay: String?, now: Calendar): Boolean {
        if (lastRunDay == dayKey(now)) return false
        if (everyDays == 7 && now.get(Calendar.DAY_OF_WEEK) != weekday) return false
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) >= hour * 60 + minute
    }

    /**
     * When it next runs: [now] itself if it's due, else the next matching day at its time.
     * Also used for the daily brief (every day, its own time).
     */
    fun next(everyDays: Int, weekday: Int, hour: Int, minute: Int, lastRunDay: String?, now: Calendar): Calendar {
        for (i in 0..14) {
            val day = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (everyDays == 7 && day.get(Calendar.DAY_OF_WEEK) != weekday) continue
            if (lastRunDay == dayKey(day)) continue
            return if (i == 0 && day.before(now)) now.clone() as Calendar else day
        }
        return now.clone() as Calendar
    }

    fun dayKey(c: Calendar): String =
        "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}
