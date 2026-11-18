package app.localmind.assistant.domain.tools

import app.localmind.assistant.domain.attachments.ToolResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Something a message asks the app to work out or do before the model answers. */
sealed interface ToolRequest {
    /** Already worked out (calculator, units, clock, dates). */
    data class Computed(val result: ToolResult) : ToolRequest
    data class Timer(val seconds: Int, val message: String?) : ToolRequest
    data class Alarm(val hour: Int, val minute: Int, val message: String?) : ToolRequest
    data class AddNote(val text: String) : ToolRequest
    data object ListNotes : ToolRequest
    data class SearchNotes(val query: String) : ToolRequest
    data class Calendar(val from: LocalDate, val days: Int, val label: String) : ToolRequest
    data class Define(val words: List<String>) : ToolRequest
    data class Library(val query: String) : ToolRequest
    /** An order for the SlothLobster ("ask the lobster to …"). */
    data class Lobster(val text: String) : ToolRequest
}

/** Tool names, as stored in messages and shown on their chips. */
object ToolNames {
    const val CALCULATOR = "calculator"
    const val UNITS = "units"
    const val CLOCK = "clock"
    const val DATES = "dates"
    const val TIMER = "timer"
    const val ALARM = "alarm"
    const val NOTES = "notes"
    const val CALENDAR = "calendar"
    const val DICTIONARY = "dictionary"
    const val LOBSTER = "lobster"
}

/**
 * Reads what a message asks for: a sum, a conversion, the time somewhere, a date, a timer or
 * alarm, a note, the calendar, a Chinese word, or a library look-up. Plain pattern matching, so
 * it works the same with any model, and nothing runs unless the message clearly asks for it.
 */
object ToolRouter {

    /** What a request will do, in a few words, for the line above the message box: (tool, text). */
    fun describe(request: ToolRequest): Pair<String, String> = when (request) {
        is ToolRequest.Computed -> request.result.tool to request.result.label
        is ToolRequest.Timer -> ToolNames.TIMER to "Will set a ${duration(request.seconds)} timer" +
            (request.message?.let { " for “$it”" } ?: "")
        is ToolRequest.Alarm -> ToolNames.ALARM to "Will set an alarm at %02d:%02d".format(request.hour, request.minute)
        is ToolRequest.AddNote -> ToolNames.NOTES to "Will save this as a note"
        ToolRequest.ListNotes -> ToolNames.NOTES to "Will look at your notes"
        is ToolRequest.SearchNotes -> ToolNames.NOTES to "Will search your notes for “${request.query}”"
        is ToolRequest.Calendar -> ToolNames.CALENDAR to "Will read your calendar for ${request.label}"
        is ToolRequest.Define -> ToolNames.DICTIONARY to "Will look up ${request.words.joinToString(", ")}"
        is ToolRequest.Library -> "library" to "Will search your library for “${request.query}”"
        is ToolRequest.Lobster -> ToolNames.LOBSTER to "Will send this to the Lobster"
    }

    private fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(if (h > 0) "$h h" else null, if (m > 0) "$m min" else null, if (s > 0) "$s s" else null)
            .joinToString(" ")
    }

    fun route(message: String, now: ZonedDateTime): List<ToolRequest> {
        val text = message.trim()
        if (text.isEmpty()) return emptyList()
        val lower = text.lowercase(Locale.ROOT).replace('’', '\'')
        val out = mutableListOf<ToolRequest>()

        // Actions first: they're commands, and their numbers aren't sums.
        val action = lobster(lower, text) ?: timer(lower, text) ?: alarm(lower, text) ?: note(lower, text)
        if (action != null) return listOf(action)

        library(lower, text)?.let { out += it }
        calendar(lower, now.toLocalDate())?.let { out += it }
        define(lower, text)?.let { out += it }

        Units.find(text)?.let {
            out += ToolRequest.Computed(ToolResult(ToolNames.UNITS, it.label, "${it.label} (exact conversion${if (it.from.kind == Units.Kind.VOLUME) ", US measures" else ""})."))
        }
        if (out.none { it is ToolRequest.Computed }) {
            Calculator.find(text)?.let {
                val label = "${it.expression} = ${it.formatted}"
                out += ToolRequest.Computed(ToolResult(ToolNames.CALCULATOR, label, "$label (computed exactly${if (TRIG.containsMatchIn(it.expression)) "; angles in degrees" else ""})."))
            }
        }
        TimeTools.find(text, now)?.let {
            val tool = if (Regex("\\d{1,2}:\\d{2}|\\btime\\b|\\bahead\\b|\\bbehind\\b").containsMatchIn(it.label + " " + lower)) ToolNames.CLOCK else ToolNames.DATES
            out += ToolRequest.Computed(ToolResult(tool, it.label, it.text))
        }
        return out.take(3)
    }

    private val TRIG = Regex("sin|cos|tan")

    // --- timers and alarms ---

    private val DURATION = Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b")
    private val TIMER = Regex(
        "\\b(?:(?:set|start|put on|make|create)\\s+(?:me\\s+)?(?:an?\\s+)?)?(?:timer|countdown)\\b|" +
            "\\b\\d+(?:\\.\\d+)?\\s*(?:-\\s*)?(?:hours?|hrs?|minutes?|mins?|seconds?|secs?)\\s+timer\\b|" +
            "\\bremind me in\\b"
    )

    private fun timer(lower: String, original: String): ToolRequest? {
        val m = TIMER.find(lower) ?: return null
        // Questions about timers ("how do timers work") aren't requests.
        if (Regex("^(how|why|what is a|what's a)\\b").containsMatchIn(lower)) return null
        var seconds = 0.0
        DURATION.findAll(lower).forEach { d ->
            val n = d.groupValues[1].toDouble()
            seconds += n * when (d.groupValues[2].first()) {
                'h' -> 3600
                'm' -> 60
                else -> 1
            }
        }
        if (seconds < 1 || seconds > 24 * 3600) return null
        return ToolRequest.Timer(seconds.toInt(), labelAfter(lower, original, m.range.first))
    }

    private val ALARM = Regex(
        "\\b(?:(?:set|make|create)\\s+(?:me\\s+)?(?:an?\\s+)?)?alarm\\s+(?:for|at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?|" +
            "\\bwake me(?: up)?\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?"
    )

    private fun alarm(lower: String, original: String): ToolRequest? {
        val m = ALARM.find(lower) ?: return null
        val g = m.groupValues
        val hourText = g[1].ifEmpty { g[4] }
        var hour = hourText.toIntOrNull() ?: return null
        val minute = g[2].ifEmpty { g[5] }.toIntOrNull() ?: 0
        val meridiem = g[3].ifEmpty { g[6] }
        if (meridiem.startsWith("p") && hour < 12) hour += 12
        if (meridiem.startsWith("a") && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return ToolRequest.Alarm(hour, minute, labelAfter(lower, original, m.range.last + 1))
    }

    private val LABEL = Regex("\\b(?:to|for|labell?ed|called|saying)\\s+(?!\\d)(.{2,60}?)[.!?]*$")

    /** "… to check the oven": the timer's or alarm's label, in the words as typed. */
    private fun labelAfter(lower: String, original: String, from: Int): String? {
        if (from >= lower.length) return null
        val m = LABEL.find(lower, from) ?: return null
        val g = m.groups[1] ?: return null
        if (DURATION.containsMatchIn(g.value)) return null
        val source = if (original.length == lower.length) original else lower
        return source.substring(g.range.first, g.range.last + 1).trim().takeIf { it.length >= 2 }
    }

    // --- the SlothLobster ---

    private val LOBSTER = Regex(
        "^(?:🦞\\s*)?(?:please\\s+)?(?:(?:ask|tell|have|get|send)\\s+(?:the\\s+)?(?:sloth\\s*)?lobster\\s+(?:to\\s+|for\\s+)?|(?:sloth\\s*)?lobster[:,]\\s*|🦞\\s+)(.+)$",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun lobster(lower: String, original: String): ToolRequest? {
        val m = LOBSTER.find(lower) ?: return null
        val g = m.groups[1] ?: return null
        val source = if (original.length == lower.length) original else lower
        val text = source.substring(g.range.first, g.range.last + 1).trim()
        return if (text.length >= 3) ToolRequest.Lobster(text) else null
    }

    // --- notes ---

    private val ADD_NOTE = listOf(
        Regex("^(?:please\\s+)?(?:can you\\s+|could you\\s+)?(?:take|make|add|save|write(?: down)?|jot(?: down)?|create)\\s+(?:a\\s+|this\\s+|the\\s+)?note(?:\\s+that)?\\s*[:,-]?\\s+(.+)$", RegexOption.DOT_MATCHES_ALL),
        Regex("^note(?: to self)?\\s*:\\s*(.+)$", RegexOption.DOT_MATCHES_ALL),
        Regex("^(?:please\\s+)?(?:add|put|save)\\s+(.+?)\\s+(?:to|in|on)\\s+my notes\\.?$", RegexOption.DOT_MATCHES_ALL)
    )
    private val LIST_NOTES = Regex("^(?:my notes|notes)\\??$|\\b(?:show|list|read|open|see|what are|what's in|whats in)\\b.{0,20}\\bmy notes\\b")
    private val SEARCH_NOTES = listOf(
        Regex("\\b(?:search|find|look (?:up|for)|check)\\s+(?:in\\s+|through\\s+)?my notes\\s+(?:for|about)\\s+(.+?)[?.!]*$"),
        Regex("\\bwhat did i (?:note|write down|save|jot down) about\\s+(.+?)[?.!]*$"),
        Regex("\\b(?:in|from) my notes,?\\s+(?:what|where|when|who|which)\\b(.+?)[?.!]*$")
    )

    private fun note(lower: String, original: String): ToolRequest? {
        for (r in ADD_NOTE) {
            val m = r.find(lower) ?: continue
            val start = m.groups[1]?.range?.first ?: continue
            val end = m.groups[1]?.range?.last ?: continue
            val body = original.substring(start, end + 1).trim()
            if (body.length >= 2) return ToolRequest.AddNote(body)
        }
        for (r in SEARCH_NOTES) {
            r.find(lower)?.let { return ToolRequest.SearchNotes(it.groupValues[1].trim()) }
        }
        if (LIST_NOTES.containsMatchIn(lower)) return ToolRequest.ListNotes
        return null
    }

    // --- calendar ---

    private val CALENDAR = Regex(
        "\\b(?:on\\s+)?my\\s+(?:calendar|agenda|schedule|diary|meetings?|appointments?|events?)\\b|" +
            "\\bwhat(?:'s| is| does)\\s+my\\s+(?:day|week)\\b|\\bmy plans (?:for )?(?:today|tomorrow|this week)\\b|" +
            "\\bam i (?:free|busy|available|booked)\\b|\\bwhat do i have\\b|\\bdo i have (?:any )?(?:meetings?|appointments?|events?|plans)\\b|" +
            "\\bwhat's on (?:today|tomorrow|this week|next week)\\b|\\bmy next (?:meeting|appointment|event)\\b"
    )

    private fun calendar(lower: String, today: LocalDate): ToolRequest? {
        if (!CALENDAR.containsMatchIn(lower)) return null
        return when {
            Regex("\\bnext week\\b").containsMatchIn(lower) -> {
                val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                ToolRequest.Calendar(monday, 7, "next week")
            }
            Regex("\\b(?:this week|my week|the week|rest of the week)\\b").containsMatchIn(lower) -> ToolRequest.Calendar(today, 7, "the next 7 days")
            Regex("\\bmy next (?:meeting|appointment|event)\\b").containsMatchIn(lower) -> ToolRequest.Calendar(today, 14, "the next 14 days")
            Regex("\\btomorrow\\b").containsMatchIn(lower) -> ToolRequest.Calendar(today.plusDays(1), 1, "tomorrow")
            else -> {
                val date = TimeTools.datesIn(lower, today).firstOrNull { it != today }
                if (date != null) ToolRequest.Calendar(date, 1, date.toString()) else ToolRequest.Calendar(today, 1, "today")
            }
        }
    }

    // --- dictionary ---

    private val DEFINE_CUE = Regex("\\b(mean|means|meaning|define|definition|translate|translation|what is|what's|how do you (?:say|read|pronounce)|pronounce|pronunciation|pinyin|jyutping|reading)\\b|意思|什么意思|點解|是什么")
    private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]{1,8}")

    private fun define(lower: String, original: String): ToolRequest? {
        if (!DEFINE_CUE.containsMatchIn(lower)) return null
        val stop = setOf("意思", "什么意思", "什么", "是什么", "點解", "什麼", "什麼意思")
        val words = HAN.findAll(original).map { it.value }.filter { it !in stop }
            .map { it.removeSuffix("什么意思").removeSuffix("是什么").removeSuffix("的意思").removeSuffix("什麼意思") }
            .filter { it.isNotEmpty() }.distinct().take(3).toList()
        return if (words.isEmpty()) null else ToolRequest.Define(words)
    }

    // --- library ---

    private val LIBRARY = listOf(
        Regex("\\b(?:look up|search(?: for)?|find|check)\\s+(.+?)\\s+(?:in|on)\\s+(?:my library|the library|wikipedia|my books|my documents)\\b"),
        Regex("\\b(?:search|check)\\s+(?:my library|the library|wikipedia|my books)\\s+(?:for|about)\\s+(.+?)[?.!]*$"),
        Regex("\\b(?:according to|from)\\s+(?:my library|wikipedia|my books)\\b,?\\s*(.+?)[?.!]*$"),
        Regex("^wiki(?:pedia)?\\s*[:,]\\s*(.+?)[?.!]*$")
    )

    private fun library(lower: String, original: String): ToolRequest? {
        for (r in LIBRARY) {
            val m = r.find(lower) ?: continue
            val g = m.groups[1] ?: continue
            val q = original.substring(g.range.first, g.range.last + 1).trim()
            if (q.length >= 2) return ToolRequest.Library(q)
        }
        return null
    }
}
