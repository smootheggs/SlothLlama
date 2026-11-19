package app.localmind.assistant.domain.home

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.sin

/** A lunar date: month 1–12, day 1–30, and whether the month is a leap month. */
data class LunarDay(val month: Int, val day: Int, val leap: Boolean)

/** Turns a date into its Chinese lunar date (on the tablet: ICU's Chinese calendar). */
fun interface LunarSource {
    fun lunar(date: LocalDate): LunarDay?
}

/** A festival or a solar term, [daysAway] from today (0 = today). */
data class SkyNote(
    val zh: String,
    val zhTraditional: String,
    val english: String,
    val daysAway: Int,
    val isFestival: Boolean,
    /** A line about the day itself, shown only on the day. */
    val custom: String = ""
)

/**
 * The sky and the calendar, worked out on the tablet with no data files: the 24 solar terms
 * (from the Sun's apparent longitude, Meeus' low-precision formulas, good to about a quarter of
 * an hour, dated in China's time as the almanac does), the Western sign the Sun is in, the twelve
 * double hours (时辰) and the traditional festivals.
 */
object SkyCalendar {

    /** The Sun's apparent ecliptic longitude in degrees, at a Julian day (UT). */
    fun sunLongitude(julianDay: Double): Double {
        val t = (julianDay - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) + 0.000289 * sin(3 * m)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val lon = l0 + c - 0.00569 - 0.00478 * sin(omega)
        return ((lon % 360.0) + 360.0) % 360.0
    }

    fun julianDay(utc: LocalDateTime): Double =
        utc.toEpochSecond(ZoneOffset.UTC) / 86400.0 + 2440587.5

    // --- the 24 solar terms (节气), from 0° (Spring Equinox) in 15° steps ---

    data class Term(val zh: String, val zhTraditional: String, val english: String, val custom: String = "")

    val TERMS: List<Term> = listOf(
        Term("春分", "春分", "Spring Equinox"),
        Term("清明", "清明", "Clear and Bright", "Qingming: families sweep their ancestors' graves and go walking in the spring air."),
        Term("谷雨", "穀雨", "Grain Rain"),
        Term("立夏", "立夏", "Start of Summer"),
        Term("小满", "小滿", "Grain Buds"),
        Term("芒种", "芒種", "Grain in Ear"),
        Term("夏至", "夏至", "Summer Solstice", "The longest day of the year."),
        Term("小暑", "小暑", "Minor Heat"),
        Term("大暑", "大暑", "Major Heat"),
        Term("立秋", "立秋", "Start of Autumn"),
        Term("处暑", "處暑", "End of Heat"),
        Term("白露", "白露", "White Dew"),
        Term("秋分", "秋分", "Autumn Equinox"),
        Term("寒露", "寒露", "Cold Dew"),
        Term("霜降", "霜降", "Frost's Descent"),
        Term("立冬", "立冬", "Start of Winter"),
        Term("小雪", "小雪", "Minor Snow"),
        Term("大雪", "大雪", "Major Snow"),
        Term("冬至", "冬至", "Winter Solstice", "冬至大過年: in Hong Kong, the solstice dinner matters more than New Year's. Tangyuan for dessert."),
        Term("小寒", "小寒", "Minor Cold"),
        Term("大寒", "大寒", "Major Cold"),
        Term("立春", "立春", "Start of Spring"),
        Term("雨水", "雨水", "Rain Water"),
        Term("惊蛰", "驚蟄", "Awakening of Insects")
    )

    /** The solar term that begins on [date] (China's time), or null. */
    fun termOn(date: LocalDate): Term? {
        val start = julianDay(date.atStartOfDay()) - 8.0 / 24.0
        val a = sunLongitude(start)
        val b = sunLongitude(start + 1.0)
        val ia = floor(a / 15.0).toInt()
        val ib = floor(b / 15.0).toInt()
        return if (ia != ib) TERMS[((ib % 24) + 24) % 24] else null
    }

    // --- the Western zodiac: the sign the Sun is in ---

    data class Sign(val symbol: String, val name: String, val meaning: String)

    /** Symbols carry the text-style selector (U+FE0E) so they never turn into emoji. */
    val SIGNS: List<Sign> = listOf(
        Sign("♈︎", "Aries", "the Ram"), Sign("♉︎", "Taurus", "the Bull"),
        Sign("♊︎", "Gemini", "the Twins"), Sign("♋︎", "Cancer", "the Crab"),
        Sign("♌︎", "Leo", "the Lion"), Sign("♍︎", "Virgo", "the Maiden"),
        Sign("♎︎", "Libra", "the Scales"), Sign("♏︎", "Scorpio", "the Scorpion"),
        Sign("♐︎", "Sagittarius", "the Archer"), Sign("♑︎", "Capricorn", "the Sea-Goat"),
        Sign("♒︎", "Aquarius", "the Water-Bearer"), Sign("♓︎", "Pisces", "the Fish")
    )

    /** The sign the Sun is in at noon (UT) on [date]. */
    fun sunSign(date: LocalDate): Sign {
        val lon = sunLongitude(julianDay(date.atTime(12, 0)))
        return SIGNS[(floor(lon / 30.0).toInt()) % 12]
    }

    /** True on the day the Sun enters a new sign. */
    fun signChanges(date: LocalDate): Boolean = sunSign(date) != sunSign(date.minusDays(1))

    // --- the Chinese zodiac: years and double hours ---

    const val STEMS = "甲乙丙丁戊己庚辛壬癸"
    const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"
    private const val ANIMALS = "鼠牛虎兔龙蛇马羊猴鸡狗猪"
    private const val ANIMALS_TRAD = "鼠牛虎兔龍蛇馬羊猴雞狗豬"
    val ANIMAL_NAMES = listOf("Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake", "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig")

    /** The zodiac animal (0 = Rat) of the lunar year that starts in Gregorian [year]. */
    fun animalOfYear(year: Int): Int = ((year - 4) % 12 + 12) % 12

    /** "丁未" for the lunar year that starts in Gregorian [year]. */
    fun stemBranch(year: Int): String {
        val i = ((year - 4) % 60 + 60) % 60
        return "${STEMS[i % 10]}${BRANCHES[i % 12]}"
    }

    fun animalChar(index: Int, traditional: Boolean): Char = (if (traditional) ANIMALS_TRAD else ANIMALS)[index]

    /** The double hour (时辰) at [hour] o'clock: 子 is 23:00–01:00, 丑 01:00–03:00 … */
    fun doubleHour(hour: Int): Int = ((hour + 1) / 2) % 12

    /** "午时 · Horse hour". */
    fun doubleHourLabel(hour: Int, traditional: Boolean): String {
        val i = doubleHour(hour)
        return "${BRANCHES[i]}${if (traditional) "時" else "时"} · ${ANIMAL_NAMES[i]} hour"
    }

    // --- festivals ---

    private data class Festival(val month: Int, val day: Int, val zh: String, val trad: String, val english: String, val custom: String)

    private val FESTIVALS = listOf(
        Festival(1, 1, "春节", "春節", "Spring Festival", "新年快乐 · 恭喜發財: red envelopes, dumplings in the north, turnip cake in the south."),
        Festival(1, 15, "元宵节", "元宵節", "Lantern Festival", "Lanterns, riddles and sweet tangyuan close the New Year."),
        Festival(5, 5, "端午节", "端午節", "Dragon Boat Festival", "Dragon boats race and zongzi are unwrapped, in memory of Qu Yuan."),
        Festival(7, 7, "七夕", "七夕", "Qixi", "The Cowherd and the Weaver Girl meet on a bridge of magpies."),
        Festival(8, 15, "中秋节", "中秋節", "Mid-Autumn Festival", "The fullest moon of the year: mooncakes and lanterns."),
        Festival(9, 9, "重阳节", "重陽節", "Double Ninth Festival", "A day to climb high and honour the elderly."),
        Festival(12, 8, "腊八节", "臘八節", "Laba Festival", "Laba porridge of grains, beans and dried fruit.")
    )

    /** The festival on [date], given its lunar date and tomorrow's (for New Year's Eve). */
    fun festivalOn(date: LocalDate, source: LunarSource): SkyNote? {
        val today = source.lunar(date) ?: return null
        if (!today.leap) {
            FESTIVALS.firstOrNull { it.month == today.month && it.day == today.day }?.let {
                return SkyNote(it.zh, it.trad, it.english, 0, true, it.custom)
            }
        }
        val tomorrow = source.lunar(date.plusDays(1))
        if (tomorrow != null && tomorrow.month == 1 && tomorrow.day == 1 && !tomorrow.leap) {
            return SkyNote("除夕", "除夕", "New Year's Eve", 0, true,
                "The reunion dinner, and fish on the table: 年年有餘, plenty every year.")
        }
        return null
    }

    /**
     * What to show on the date line: today's festival or solar term; else the nearest festival
     * within three weeks (the Spring Festival within two months); else the next solar term,
     * which is never more than about 16 days off.
     */
    fun next(today: LocalDate, source: LunarSource?): SkyNote? {
        source?.let { s -> festivalOn(today, s) }?.let { return it }
        termOn(today)?.let { return SkyNote(it.zh, it.zhTraditional, it.english, 0, false, it.custom) }
        if (source != null) {
            for (d in 1..60) {
                val f = festivalOn(today.plusDays(d.toLong()), source) ?: continue
                // New Year's Eve counts only up close; from further off the countdown is to the New Year.
                if (d <= 21 || f.zh == "春节") return f.copy(daysAway = d)
            }
        }
        for (d in 1..20) {
            termOn(today.plusDays(d.toLong()))?.let { return SkyNote(it.zh, it.zhTraditional, it.english, d, false, it.custom) }
        }
        return null
    }

    /** The next solar term from [from] (today included), within about 16 days. */
    fun nextTerm(from: LocalDate): SkyNote? {
        for (d in 0..20) {
            termOn(from.plusDays(d.toLong()))?.let { return SkyNote(it.zh, it.zhTraditional, it.english, d, false, it.custom) }
        }
        return null
    }

    /** The next festival from [from] (today included), up to [within] days ahead. */
    fun nextFestival(from: LocalDate, source: LunarSource, within: Int = 120, skip: Set<String> = emptySet()): SkyNote? {
        for (d in 0..within) {
            val f = festivalOn(from.plusDays(d.toLong()), source) ?: continue
            if (f.zh in skip) continue
            return f.copy(daysAway = d)
        }
        return null
    }

    /** Days until the next Spring Festival (0 = today). Starts looking a little before the likely day. */
    fun daysToNewYear(from: LocalDate, source: LunarSource): Int? {
        val now = source.lunar(from) ?: return null
        if (now.month == 1 && now.day == 1 && !now.leap) return 0
        val guess = ((12 - now.month) * 29.53 + (30 - now.day)).toInt()
        for (d in maxOf(1, guess - 25)..guess + 60) {
            val l = source.lunar(from.plusDays(d.toLong())) ?: return null
            if (l.month == 1 && l.day == 1 && !l.leap) return d
        }
        return null
    }

    /** "冬至 Winter Solstice in 3 days", "Today: 立冬 Start of Winter". */
    fun describe(note: SkyNote, traditional: Boolean): String {
        val name = "${if (traditional) note.zhTraditional else note.zh} ${note.english}"
        return when (note.daysAway) {
            0 -> "Today: $name"
            1 -> "$name tomorrow"
            else -> "$name in ${note.daysAway} days"
        }
    }
}
