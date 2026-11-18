package app.localmind.assistant.domain.tools

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Clock and calendar sums from the tablet's own clock: the time in other cities, converting a
 * time between cities, days until a date, the weekday of a date, a date N days away.
 */
object TimeTools {

    class Result(val label: String, val text: String)

    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun find(message: String, now: ZonedDateTime): Result? {
        val text = message.lowercase(Locale.ROOT).replace('’', '\'')
        return convertTime(text, now) ?: timeDifference(text, now) ?: timeIn(text, now) ?: dates(text, now)
    }

    // --- time zones ---

    private val CLOCK = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?(?=\\W|$)")
    private val TIME_QUESTION = Regex(
        "\\b(what time is it|what's the time|what is the time|current time|time now|time is it|local time|the time in|what time)\\b"
    )
    private val DIFFERENCE = Regex("\\btime difference\\b|\\bhours? (?:ahead|behind)\\b|\\bhow far (?:ahead|behind)\\b")

    /** "3pm in New York in Tokyo", "what time is 9:30 Hong Kong time in San Francisco". */
    private fun convertTime(text: String, now: ZonedDateTime): Result? {
        if (!Regex("\\btime\\b|\\bconvert\\b|\\bwhen it'?s\\b|\\bwhen it is\\b").containsMatchIn(text)) return null
        val places = places(text)
        if (places.isEmpty()) return null
        val clock = CLOCK.findAll(text).firstOrNull { m ->
            val h = m.groupValues[1].toInt()
            val hasMinutes = m.groupValues[2].isNotEmpty()
            val hasMeridiem = m.groupValues[3].isNotEmpty()
            (hasMinutes || hasMeridiem) && h in 0..23 && !(hasMeridiem && h !in 1..12)
        } ?: return null
        // Which clock is the time on? The place written right next to it, or "my time" / "here".
        val near = (clock.range.first - 24)..(clock.range.last + 24)
        val after = text.substring(clock.range.last + 1, minOf(text.length, clock.range.last + 25))
        val local = Regex("\\b(my time|here|local time|for me|my local)\\b")
        val placeNear = places.firstOrNull { it.at in near }
        val (fromZone, fromName, toZone, toName) = when {
            places.size >= 2 -> {
                val from = placeNear ?: places[0]
                val to = places.first { it !== from }
                Quad(from.zone, from.name, to.zone, to.name)
            }
            local.containsMatchIn(after) -> Quad(now.zone, "your time", places[0].zone, places[0].name)
            placeNear != null -> Quad(placeNear.zone, placeNear.name, now.zone, "your time")
            else -> Quad(now.zone, "your time", places[0].zone, places[0].name)
        }
        if (fromZone == toZone) return null
        var hour = clock.groupValues[1].toInt()
        val minute = clock.groupValues[2].toIntOrNull() ?: 0
        val meridiem = clock.groupValues[3]
        if (meridiem.startsWith("p") && hour < 12) hour += 12
        if (meridiem.startsWith("a") && hour == 12) hour = 0
        val date = if (Regex("\\btomorrow\\b").containsMatchIn(text)) now.withZoneSameInstant(fromZone).toLocalDate().plusDays(1)
        else now.withZoneSameInstant(fromZone).toLocalDate()
        val from = ZonedDateTime.of(date, LocalTime.of(hour, minute), fromZone)
        val to = from.withZoneSameInstant(toZone)
        val dayNote = when (ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate())) {
            1L -> " (the next day)"
            -1L -> " (the day before)"
            else -> ""
        }
        val label = "${TIME.format(from)} $fromName = ${TIME.format(to)} $toName$dayNote"
        val text2 = "$label. ${fromName.cap()}: ${zoneLabel(from)}; ${toName}: ${zoneLabel(to)}. " +
            "Date at the destination: ${DAY.format(to)}."
        return Result(label, text2)
    }

    private fun timeDifference(text: String, now: ZonedDateTime): Result? {
        if (!DIFFERENCE.containsMatchIn(text)) return null
        val places = places(text)
        val (a, b) = when {
            places.size >= 2 -> places[0] to places[1]
            places.size == 1 -> Place("your time", now.zone) to places[0]
            else -> return null
        }
        val offA = now.withZoneSameInstant(a.zone).offset.totalSeconds
        val offB = now.withZoneSameInstant(b.zone).offset.totalSeconds
        val diff = (offB - offA) / 3600.0
        val hours = Calculator.format(kotlin.math.abs(diff))
        val label = when {
            diff == 0.0 -> "${b.name.cap()} and ${a.name} are on the same time"
            diff > 0 -> "${b.name.cap()} is $hours h ahead of ${a.name}"
            else -> "${b.name.cap()} is $hours h behind ${a.name}"
        }
        return Result(label, "$label right now. ${a.name.cap()}: ${zoneLabel(now.withZoneSameInstant(a.zone))}; " +
            "${b.name}: ${zoneLabel(now.withZoneSameInstant(b.zone))}.")
    }

    private fun timeIn(text: String, now: ZonedDateTime): Result? {
        val asksTime = TIME_QUESTION.containsMatchIn(text)
        val asksDate = Regex("\\b(what(?:'s| is) (?:the )?date|what day is it|today's date|what's today|what is today|date today)\\b").containsMatchIn(text)
        if (!asksTime && !asksDate) return null
        // "What's the date in 100 days?" is a date sum, not today's date.
        if (Regex("\\b\\d{1,5}\\s+(day|week|month|year)s?\\b").containsMatchIn(text)) return null
        val place = places(text).firstOrNull()
        val zone = place?.zone ?: now.zone
        val there = now.withZoneSameInstant(zone)
        val name = place?.name ?: "here"
        val label = if (asksTime) "${TIME.format(there)} in ${name.removePrefix("here").ifEmpty { "your time zone" }}"
        else DAY.format(there)
        return Result(
            label,
            "It is ${TIME.format(there)} on ${DAY.format(there)} ${if (place != null) "in ${place.name}" else "where you are"} (${zoneLabel(there)})."
        )
    }

    private fun zoneLabel(t: ZonedDateTime): String {
        val abbreviation = t.zone.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val offset = t.offset.id.let { if (it == "Z") "UTC" else "UTC$it" }
        return if (abbreviation.startsWith("GMT") || abbreviation.contains('/')) offset else "$abbreviation, $offset"
    }

    private data class Quad(val a: ZoneId, val b: String, val c: ZoneId, val d: String)
    private class Place(val name: String, val zone: ZoneId, val at: Int = -1)

    /** Places named in [text], in the order they appear. */
    private fun places(text: String): List<Place> {
        val found = mutableListOf<Pair<Int, Place>>()
        val taken = BooleanArray(text.length)
        for ((name, zoneId) in PLACE_NAMES) {
            var from = 0
            while (true) {
                val i = text.indexOf(name, from)
                if (i < 0) break
                from = i + name.length
                val end = i + name.length
                val ascii = name.first().code < 128
                val wordStart = i == 0 || !text[i - 1].isLetter() || !ascii
                val wordEnd = end >= text.length || !text[end].isLetter() || !ascii
                if (!wordStart || !wordEnd || (i until end).any { taken[it] }) continue
                for (k in i until end) taken[k] = true
                found += i to Place(display(name), ZoneId.of(zoneId), i)
            }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun display(name: String): String =
        DISPLAY[name] ?: name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }

    private fun String.cap() = replaceFirstChar { it.titlecase(Locale.ENGLISH) }

    private val DISPLAY = mapOf(
        "sf" to "San Francisco", "nyc" to "New York", "hk" to "Hong Kong", "bay area" to "the Bay Area",
        "uk" to "the UK", "utc" to "UTC", "gmt" to "GMT", "washington dc" to "Washington DC", "washington, dc" to "Washington DC",
        "pacific time" to "Pacific time", "eastern time" to "Eastern time"
    )

    /** Longest first, so "new york" is found before "york" and "hong kong" before "kong". */
    private val PLACE_NAMES: List<Pair<String, String>> = listOf(
        "san francisco" to "America/Los_Angeles", "sf" to "America/Los_Angeles", "bay area" to "America/Los_Angeles",
        "silicon valley" to "America/Los_Angeles", "palo alto" to "America/Los_Angeles", "san jose" to "America/Los_Angeles",
        "los angeles" to "America/Los_Angeles", "seattle" to "America/Los_Angeles",
        "vancouver" to "America/Vancouver", "california" to "America/Los_Angeles", "pacific time" to "America/Los_Angeles",
        "las vegas" to "America/Los_Angeles", "phoenix" to "America/Phoenix", "denver" to "America/Denver",
        "chicago" to "America/Chicago", "houston" to "America/Chicago", "dallas" to "America/Chicago",
        "austin" to "America/Chicago", "texas" to "America/Chicago", "mexico city" to "America/Mexico_City",
        "new york" to "America/New_York", "nyc" to "America/New_York", "boston" to "America/New_York",
        "washington dc" to "America/New_York", "washington, dc" to "America/New_York", "miami" to "America/New_York",
        "atlanta" to "America/New_York", "toronto" to "America/Toronto", "montreal" to "America/Toronto",
        "eastern time" to "America/New_York", "honolulu" to "Pacific/Honolulu", "hawaii" to "Pacific/Honolulu",
        "anchorage" to "America/Anchorage", "caracas" to "America/Caracas", "venezuela" to "America/Caracas",
        "bogota" to "America/Bogota", "bogotá" to "America/Bogota", "lima" to "America/Lima",
        "santiago" to "America/Santiago", "buenos aires" to "America/Argentina/Buenos_Aires",
        "sao paulo" to "America/Sao_Paulo", "são paulo" to "America/Sao_Paulo", "rio de janeiro" to "America/Sao_Paulo",
        "london" to "Europe/London", "uk" to "Europe/London", "england" to "Europe/London", "dublin" to "Europe/Dublin",
        "lisbon" to "Europe/Lisbon", "madrid" to "Europe/Madrid", "barcelona" to "Europe/Madrid", "spain" to "Europe/Madrid",
        "paris" to "Europe/Paris", "france" to "Europe/Paris", "brussels" to "Europe/Brussels",
        "amsterdam" to "Europe/Amsterdam", "berlin" to "Europe/Berlin", "frankfurt" to "Europe/Berlin",
        "hanau" to "Europe/Berlin", "munich" to "Europe/Berlin", "münchen" to "Europe/Berlin", "hamburg" to "Europe/Berlin",
        "germany" to "Europe/Berlin", "zurich" to "Europe/Zurich", "zürich" to "Europe/Zurich", "geneva" to "Europe/Zurich",
        "vienna" to "Europe/Vienna", "rome" to "Europe/Rome", "milan" to "Europe/Rome", "italy" to "Europe/Rome",
        "stockholm" to "Europe/Stockholm", "oslo" to "Europe/Oslo", "copenhagen" to "Europe/Copenhagen",
        "helsinki" to "Europe/Helsinki", "warsaw" to "Europe/Warsaw", "prague" to "Europe/Prague",
        "budapest" to "Europe/Budapest", "athens" to "Europe/Athens", "istanbul" to "Europe/Istanbul",
        "moscow" to "Europe/Moscow", "cairo" to "Africa/Cairo", "lagos" to "Africa/Lagos", "nairobi" to "Africa/Nairobi",
        "johannesburg" to "Africa/Johannesburg", "dubai" to "Asia/Dubai", "abu dhabi" to "Asia/Dubai",
        "riyadh" to "Asia/Riyadh", "doha" to "Asia/Qatar", "tel aviv" to "Asia/Jerusalem", "tehran" to "Asia/Tehran",
        "karachi" to "Asia/Karachi", "mumbai" to "Asia/Kolkata", "delhi" to "Asia/Kolkata", "new delhi" to "Asia/Kolkata",
        "bangalore" to "Asia/Kolkata", "bengaluru" to "Asia/Kolkata", "india" to "Asia/Kolkata",
        "kathmandu" to "Asia/Kathmandu", "dhaka" to "Asia/Dhaka", "bangkok" to "Asia/Bangkok", "hanoi" to "Asia/Bangkok",
        "ho chi minh city" to "Asia/Ho_Chi_Minh", "saigon" to "Asia/Ho_Chi_Minh", "jakarta" to "Asia/Jakarta",
        "kuala lumpur" to "Asia/Kuala_Lumpur", "singapore" to "Asia/Singapore", "manila" to "Asia/Manila",
        "hong kong" to "Asia/Hong_Kong", "hk" to "Asia/Hong_Kong", "香港" to "Asia/Hong_Kong", "macau" to "Asia/Macau",
        "macao" to "Asia/Macau", "shenzhen" to "Asia/Shanghai", "guangzhou" to "Asia/Shanghai",
        "shanghai" to "Asia/Shanghai", "上海" to "Asia/Shanghai", "beijing" to "Asia/Shanghai", "北京" to "Asia/Shanghai",
        "china" to "Asia/Shanghai", "taipei" to "Asia/Taipei", "台北" to "Asia/Taipei", "taiwan" to "Asia/Taipei",
        "seoul" to "Asia/Seoul", "korea" to "Asia/Seoul", "tokyo" to "Asia/Tokyo", "東京" to "Asia/Tokyo",
        "osaka" to "Asia/Tokyo", "kyoto" to "Asia/Tokyo", "japan" to "Asia/Tokyo", "sydney" to "Australia/Sydney",
        "melbourne" to "Australia/Melbourne", "brisbane" to "Australia/Brisbane", "perth" to "Australia/Perth",
        "auckland" to "Pacific/Auckland", "new zealand" to "Pacific/Auckland", "utc" to "UTC", "gmt" to "UTC"
    ).sortedByDescending { it.first.length }

    // --- dates ---

    private val ISO_DATE = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    private const val MONTH = "(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)"
    private val MONTH_FIRST = Regex("\\b$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b")
    private val DAY_FIRST = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTH\\.?(?:,?\\s+(\\d{4}))?\\b")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    private fun dates(text: String, now: ZonedDateTime): Result? {
        val today = now.toLocalDate()
        // "in 90 days", "100 days from now", "3 weeks ago", "date in 2 months"
        Regex("\\b(\\d{1,5})\\s+(day|week|month|year)s?\\s+(from (?:now|today)|ago|later|after today)\\b").find(text)
            ?.let { return offsetDate(today, it.groupValues[1].toLong(), it.groupValues[2], it.groupValues[3] == "ago") }
        Regex("\\b(?:date|day|what day|which day) (?:will it be )?in\\s+(\\d{1,5})\\s+(day|week|month|year)s?\\b").find(text)
            ?.let { return offsetDate(today, it.groupValues[1].toLong(), it.groupValues[2], false) }

        val found = datesIn(text, today)
        val until = Regex("\\b(how (?:many|long)|days?|weeks?) (?:\\w+ )?(?:until|till|til|to go until|before|to)\\b").containsMatchIn(text) ||
            Regex("\\bhow long until\\b|\\bcountdown\\b").containsMatchIn(text)
        val between = Regex("\\bbetween\\b|\\bfrom\\b.+\\bto\\b").containsMatchIn(text)
        val weekday = Regex("\\bwhat day (?:of the week )?(?:is|was|will|does|did)\\b|\\bwhich day of the week\\b|\\bweekday\\b").containsMatchIn(text)
        return when {
            found.size >= 2 && (between || until || Regex("\\bhow many days\\b").containsMatchIn(text)) -> span(found[0], found[1])
            found.isNotEmpty() && until -> span(today, found[0], fromToday = true)
            found.isNotEmpty() && weekday -> {
                val d = found[0]
                val label = "${d.format(DateTimeFormatter.ISO_LOCAL_DATE)} is a ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
                Result(label, "${DAY.format(d)} is a ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}.")
            }
            else -> null
        }
    }

    private fun offsetDate(today: LocalDate, n: Long, unit: String, past: Boolean): Result {
        val sign = if (past) -1 else 1
        val date = when (unit) {
            "week" -> today.plusWeeks(sign * n)
            "month" -> today.plusMonths(sign * n)
            "year" -> today.plusYears(sign * n)
            else -> today.plusDays(sign * n)
        }
        val what = "$n $unit${if (n == 1L) "" else "s"} ${if (past) "ago" else "from today"}"
        return Result("$what: ${DAY.format(date)}", "$what (today is ${DAY.format(today)}) is ${DAY.format(date)}.")
    }

    private fun span(a: LocalDate, b: LocalDate, fromToday: Boolean = false): Result {
        val days = ChronoUnit.DAYS.between(a, b)
        val abs = kotlin.math.abs(days)
        val weeks = "${abs / 7} weeks and ${abs % 7} days"
        val label = if (fromToday) {
            if (days >= 0) "$days days until ${DAY.format(b)}" else "${DAY.format(b)} was $abs days ago"
        } else "$abs days between ${a} and ${b}"
        val text = if (fromToday) "Today is ${DAY.format(a)}. ${label.cap()} ($weeks)."
        else "From ${DAY.format(a)} to ${DAY.format(b)} is $abs days ($weeks)."
        return Result(label, text)
    }

    /** Dates in [text], in order: ISO dates, "25 July 2027", "July 25", weekdays, holidays. */
    fun datesIn(text: String, today: LocalDate): List<LocalDate> {
        val out = mutableListOf<Pair<Int, LocalDate>>()
        ISO_DATE.findAll(text).forEach { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .getOrNull()?.let { out += m.range.first to it }
        }
        MONTH_FIRST.findAll(text).forEach { m ->
            monthDay(m.groupValues[1], m.groupValues[2], m.groupValues[3], today)?.let { out += m.range.first to it }
        }
        DAY_FIRST.findAll(text).forEach { m ->
            monthDay(m.groupValues[2], m.groupValues[1], m.groupValues[3], today)?.let { out += m.range.first to it }
        }
        Regex("\\b(?:next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").findAll(text).forEach { m ->
            val dow = DayOfWeek.of(WEEKDAYS.indexOf(m.groupValues[1]) + 1)
            out += m.range.first to today.with(TemporalAdjusters.next(dow))
        }
        HOLIDAYS.forEach { (pattern, day) ->
            pattern.find(text)?.let { m -> out += m.range.first to nextOccurrence(day, today) }
        }
        Regex("\\btomorrow\\b").find(text)?.let { out += it.range.first to today.plusDays(1) }
        Regex("\\btoday\\b|\\bnow\\b").find(text)?.let { out += it.range.first to today }
        Regex("\\b(?:new year's eve|nye)\\b").find(text)?.let { out += it.range.first to nextOccurrence(MonthDay.of(12, 31), today) }
        Regex("\\b(?:end of (?:the )?year)\\b").find(text)?.let { out += it.range.first to LocalDate.of(today.year, 12, 31) }
        // "my birthday" etc. aren't known here; the model can ask.
        return out.distinctBy { it.second to it.first }.sortedBy { it.first }.map { it.second }.distinct()
    }

    private val HOLIDAYS = listOf(
        Regex("\\bchristmas(?! eve)(?: day)?\\b|\\bxmas\\b") to MonthDay.of(12, 25),
        Regex("\\bchristmas eve\\b") to MonthDay.of(12, 24),
        Regex("\\bnew year(?!(?:'s)? eve)(?:'s)?(?: day)?\\b") to MonthDay.of(1, 1),
        Regex("\\bvalentine'?s(?: day)?\\b") to MonthDay.of(2, 14),
        Regex("\\bhalloween\\b") to MonthDay.of(10, 31),
        Regex("\\bindependence day\\b|\\b(?:the )?4th of july\\b|\\bfourth of july\\b") to MonthDay.of(7, 4)
    )

    private fun nextOccurrence(day: MonthDay, today: LocalDate): LocalDate {
        val thisYear = day.atYear(today.year)
        return if (thisYear.isBefore(today)) day.atYear(today.year + 1) else thisYear
    }

    private fun monthDay(month: String, day: String, year: String, today: LocalDate): LocalDate? {
        val m = MONTHS.indexOf(month.take(3)) + 1
        if (m == 0) return null
        val d = day.toIntOrNull() ?: return null
        return runCatching {
            if (year.isNotEmpty()) LocalDate.of(year.toInt(), m, d)
            else nextOccurrence(MonthDay.of(m, d), today)
        }.getOrNull()
    }
}
