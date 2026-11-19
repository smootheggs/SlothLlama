package app.localmind.assistant

import app.localmind.assistant.data.desk.DeskState
import app.localmind.assistant.data.desk.JournalEntry
import app.localmind.assistant.data.home.HomeSettings
import app.localmind.assistant.data.lobster.LobsterCatch
import app.localmind.assistant.data.lobster.LobsterSource
import app.localmind.assistant.domain.home.DailyWords
import app.localmind.assistant.domain.home.GreetingFacts
import app.localmind.assistant.domain.home.GreetingLanguage
import app.localmind.assistant.domain.home.GreetingOptions
import app.localmind.assistant.domain.home.HomeGreeting
import app.localmind.assistant.domain.home.LunarDay
import app.localmind.assistant.domain.home.LunarSource
import app.localmind.assistant.domain.home.SkyCalendar
import app.localmind.assistant.domain.home.SlothMood
import app.localmind.assistant.domain.home.WordLanguage
import app.localmind.assistant.ui.home.almanacLine
import app.localmind.assistant.ui.home.catchHeadline
import app.localmind.assistant.ui.home.deskGlance
import app.localmind.assistant.ui.home.practiceWeek
import app.localmind.assistant.ui.home.practisedAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The Home page's calendar, greetings, daily words and small tiles. */
class HomeExtrasUnitTests {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    /** A lunar calendar that knows only the dates a test gives it; every other day is an ordinary one. */
    private class FakeLunar(private val known: Map<LocalDate, LunarDay>, private val other: LunarDay = LunarDay(10, 11, false)) : LunarSource {
        override fun lunar(date: LocalDate): LunarDay = known[date] ?: other
    }

    // --- solar terms, signs, hours ---

    @Test
    fun `solar terms fall on the almanac's days in 2026`() {
        assertEquals("立春", SkyCalendar.termOn(d(2026, 2, 4))?.zh)
        assertEquals("清明", SkyCalendar.termOn(d(2026, 4, 5))?.zh)
        assertEquals("夏至", SkyCalendar.termOn(d(2026, 6, 21))?.zh)
        assertEquals("立冬", SkyCalendar.termOn(d(2026, 11, 7))?.zh)
        assertEquals("小雪", SkyCalendar.termOn(d(2026, 11, 22))?.zh)
        assertEquals("冬至", SkyCalendar.termOn(d(2026, 12, 22))?.zh)
        assertNull(SkyCalendar.termOn(d(2026, 11, 19)))
    }

    @Test
    fun `there are 24 terms a year, one every fortnight or so`() {
        var count = 0
        var day = d(2026, 1, 1)
        while (day.year == 2026) {
            if (SkyCalendar.termOn(day) != null) count++
            day = day.plusDays(1)
        }
        assertEquals(24, count)
        assertEquals(24, SkyCalendar.TERMS.size)
    }

    @Test
    fun `the Sun's sign and the day it changes`() {
        assertEquals("Scorpio", SkyCalendar.sunSign(d(2026, 11, 19)).name)
        assertEquals("Sagittarius", SkyCalendar.sunSign(d(2026, 11, 23)).name)
        assertEquals("Aries", SkyCalendar.sunSign(d(2026, 3, 25)).name)
        assertEquals("Leo", SkyCalendar.sunSign(d(2026, 8, 5)).name)
        assertTrue(SkyCalendar.signChanges(d(2026, 11, 22)))
        assertFalse(SkyCalendar.signChanges(d(2026, 11, 19)))
        // Text-style symbols, never emoji.
        assertTrue(SkyCalendar.SIGNS.all { it.symbol.endsWith("︎") })
    }

    @Test
    fun `double hours start at eleven at night`() {
        assertEquals(0, SkyCalendar.doubleHour(23))
        assertEquals(0, SkyCalendar.doubleHour(0))
        assertEquals(1, SkyCalendar.doubleHour(1))
        assertEquals(7, SkyCalendar.doubleHour(13))
        assertEquals(11, SkyCalendar.doubleHour(22))
        assertEquals("未时 · Goat hour", SkyCalendar.doubleHourLabel(14, traditional = false))
        assertEquals("午時 · Horse hour", SkyCalendar.doubleHourLabel(12, traditional = true))
    }

    @Test
    fun `years have their animal and stem-branch`() {
        assertEquals("Horse", SkyCalendar.ANIMAL_NAMES[SkyCalendar.animalOfYear(2026)])
        assertEquals("Goat", SkyCalendar.ANIMAL_NAMES[SkyCalendar.animalOfYear(2027)])
        assertEquals("丙午", SkyCalendar.stemBranch(2026))
        assertEquals("丁未", SkyCalendar.stemBranch(2027))
        assertEquals('羊', SkyCalendar.animalChar(7, traditional = false))
    }

    // --- festivals and the date line ---

    @Test
    fun `festivals by lunar date, and New Year's Eve from tomorrow`() {
        val lunar = FakeLunar(
            mapOf(
                d(2026, 9, 25) to LunarDay(8, 15, false),
                d(2027, 2, 5) to LunarDay(12, 29, false),
                d(2027, 2, 6) to LunarDay(1, 1, false),
                d(2026, 6, 19) to LunarDay(5, 5, true)
            )
        )
        assertEquals("中秋节", SkyCalendar.festivalOn(d(2026, 9, 25), lunar)?.zh)
        assertEquals("除夕", SkyCalendar.festivalOn(d(2027, 2, 5), lunar)?.zh)
        assertEquals("春节", SkyCalendar.festivalOn(d(2027, 2, 6), lunar)?.zh)
        // A leap month's fifth day is not the Dragon Boat Festival.
        assertNull(SkyCalendar.festivalOn(d(2026, 6, 19), lunar))
    }

    @Test
    fun `the date line shows the next term when no festival is near`() {
        val note = SkyCalendar.next(d(2026, 11, 19), FakeLunar(emptyMap()))
        assertEquals("小雪", note?.zh)
        assertEquals(3, note?.daysAway)
        assertEquals("小雪 Minor Snow in 3 days", SkyCalendar.describe(note!!, traditional = false))
        assertEquals("Today: 冬至 Winter Solstice", SkyCalendar.describe(SkyCalendar.next(d(2026, 12, 22), null)!!, traditional = false))
    }

    @Test
    fun `a festival within three weeks wins over the next term`() {
        val lunar = FakeLunar(mapOf(d(2026, 11, 30) to LunarDay(9, 9, false)))
        val note = SkyCalendar.next(d(2026, 11, 19), lunar)
        assertEquals("重阳节", note?.zh)
        assertEquals(11, note?.daysAway)
    }

    @Test
    fun `the New Year is counted down from further off, without its eve`() {
        val lunar = FakeLunar(mapOf(d(2027, 1, 13) to LunarDay(12, 30, false), d(2027, 1, 14) to LunarDay(1, 1, false)))
        val note = SkyCalendar.next(d(2026, 11, 23), lunar)
        // 23 Nov → 14 Jan is 52 days: past three weeks, but the Spring Festival counts down from two months.
        assertEquals("春节", note?.zh)
        assertEquals(52, note?.daysAway)
    }

    @Test
    fun `days to the New Year from a real lunar date`() {
        val lunar = FakeLunar(mapOf(d(2027, 2, 6) to LunarDay(1, 1, false)), other = LunarDay(10, 11, false))
        assertEquals(79, SkyCalendar.daysToNewYear(d(2026, 11, 19), lunar))
    }

    @Test
    fun `the date line, all together and with things turned off`() {
        val all = HomeSettings()
        val lunar = FakeLunar(mapOf(d(2027, 2, 6) to LunarDay(1, 1, false)))
        val line = almanacLine(d(2026, 11, 19), all, lunar, traditional = false)
        assertEquals("小雪 Minor Snow in 3 days · ♏︎ Scorpio", line?.text)
        assertFalse(line!!.special)
        val turning = almanacLine(d(2026, 11, 22), all, lunar, traditional = false)
        assertEquals("Today: 小雪 Minor Snow · Sun enters ♐︎ Sagittarius", turning?.text)
        assertTrue(turning!!.special)
        val none = all.copy(festivals = false, chineseZodiac = false, westernZodiac = false)
        assertNull(almanacLine(d(2026, 11, 19), none, lunar, traditional = false))
    }

    // --- the hello ---

    private val base = "Good morning." to "Slow and steady today? 🦥"

    @Test
    fun `names fit into any hello`() {
        assertEquals("Good morning, Sam.", HomeGreeting.withName("Good morning.", "Sam"))
        assertEquals("Still up, Sam?", HomeGreeting.withName("Still up?", "Sam"))
        assertEquals("Morning", HomeGreeting.withName("Morning", ""))
    }

    @Test
    fun `the hello notices the day, in order`() {
        val o = GreetingOptions()
        val plain = HomeGreeting.compose(GreetingFacts(hour = 9, epochDay = 0, base = base), o)
        assertEquals(base.second, plain.sub)
        assertEquals(SlothMood.COFFEE, plain.mood)
        // Cards and the Lobster's catch are in Today, so the hello doesn't repeat them.
        val busy = HomeGreeting.compose(GreetingFacts(hour = 9, epochDay = 0, base = base, due = 3, caught = 1), o)
        assertEquals(base.second, busy.sub)
        assertEquals(SlothMood.CATCH, busy.mood)
        val birthday = HomeGreeting.compose(GreetingFacts(hour = 9, epochDay = 0, base = base, birthday = true, caught = 2), o)
        assertTrue(birthday.sub.startsWith("Happy birthday"))
        assertEquals("生日快乐", birthday.chinese?.text)
        assertEquals(SlothMood.BIRTHDAY, birthday.mood)
        val off = HomeGreeting.compose(GreetingFacts(hour = 9, epochDay = 0, base = base, due = 3), o.copy(context = false, moods = false))
        assertEquals(base.second, off.sub)
        assertEquals(SlothMood.NONE, off.mood)
    }

    @Test
    fun `festival days get their own hello and a lantern`() {
        val midAutumn = SkyCalendar.festivalOn(d(2026, 9, 25), FakeLunar(mapOf(d(2026, 9, 25) to LunarDay(8, 15, false))))
        val g = HomeGreeting.compose(
            GreetingFacts(hour = 19, epochDay = 1, base = base, today = midAutumn),
            GreetingOptions(language = GreetingLanguage.CANTONESE)
        )
        assertEquals("中秋節快樂", g.chinese?.text)
        assertTrue(g.chinese!!.cantonese)
        assertEquals(SlothMood.LANTERN, g.mood)
        assertTrue(g.sub.contains("mooncakes"))
    }

    @Test
    fun `Chinese hellos take turns by the day`() {
        val o = GreetingOptions(language = GreetingLanguage.ALTERNATE)
        val even = HomeGreeting.compose(GreetingFacts(hour = 8, epochDay = 20000, base = base), o)
        val odd = HomeGreeting.compose(GreetingFacts(hour = 8, epochDay = 20001, base = base), o)
        assertEquals("早上好", even.chinese?.text)
        assertEquals("早晨", odd.chinese?.text)
        assertEquals("zou2 san4", odd.chinese?.reading)
        assertNull(HomeGreeting.compose(GreetingFacts(hour = 8, epochDay = 1, base = base), o.copy(language = GreetingLanguage.OFF)).chinese)
        assertEquals("早唞", HomeGreeting.hello(cantonese = true, hour = 23, occasion = null, traditional = false).text)
    }

    // --- words and idioms ---

    @Test
    fun `the word lists are well formed`() {
        assertTrue(DailyWords.MANDARIN.size >= 70)
        assertTrue(DailyWords.CANTONESE.size >= 50)
        assertTrue(DailyWords.IDIOMS.size >= 55)
        assertEquals(DailyWords.MANDARIN.size, DailyWords.MANDARIN.map { it.simplified }.toSet().size)
        assertEquals(DailyWords.CANTONESE.size, DailyWords.CANTONESE.map { it.traditional }.toSet().size)
        assertEquals(DailyWords.IDIOMS.size, DailyWords.IDIOMS.map { it.simplified }.toSet().size)
        // Pinyin carries tone marks, Jyutping tone numbers.
        assertTrue(DailyWords.MANDARIN.none { w -> w.reading.any { it.isDigit() } })
        assertTrue(DailyWords.CANTONESE.all { w -> w.reading.split(' ').all { it.last().isDigit() } })
        assertTrue(DailyWords.IDIOMS.all { it.simplified.length == it.traditional.length && it.meaning.isNotBlank() && it.literal.isNotBlank() })
        assertTrue(DailyWords.MANDARIN.all { it.simplified.length == it.traditional.length && it.meaning.isNotBlank() })
    }

    @Test
    fun `a word a day, every word in turn`() {
        val first = DailyWords.wordFor(20000, WordLanguage.ALTERNATE)
        val next = DailyWords.wordFor(20001, WordLanguage.ALTERNATE)
        assertTrue(first.cantonese != next.cantonese)
        assertEquals(first, DailyWords.wordFor(20000, WordLanguage.ALTERNATE))
        val seen = (0L until DailyWords.MANDARIN.size).map { DailyWords.wordFor(it, WordLanguage.MANDARIN) }.toSet()
        assertEquals(DailyWords.MANDARIN.size, seen.size)
        val idioms = (0L until DailyWords.IDIOMS.size).map { DailyWords.idiomFor(it) }.toSet()
        assertEquals(DailyWords.IDIOMS.size, idioms.size)
        // Days before 1970 work too.
        assertNotNull(DailyWords.wordFor(-3, WordLanguage.ALTERNATE))
        assertTrue(DailyWords.CANTONESE.first().written(traditionalScript = false) == DailyWords.CANTONESE.first().traditional)
    }

    // --- practice, the Desk, the Lobster, birthdays ---

    @Test
    fun `practice dots count tutor chats and reviews by day`() {
        val today = d(2026, 11, 19)
        fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val week = practiceWeek(
            written = listOf("tutor" to at(today, 9), "tutor" to at(today.minusDays(2), 20), "assistant" to at(today.minusDays(1), 9), "tutor" to at(today.minusDays(9), 9)),
            tutorIds = setOf("tutor"),
            reviews = listOf(at(today.minusDays(6), 7)),
            today = today, zone = ZoneOffset.UTC
        )
        assertEquals(listOf(true, false, false, false, true, false, true), week.days)
        assertEquals(3, week.count)
        assertEquals(7, week.labels.size)
        assertEquals("yesterday", practisedAgo(at(today.minusDays(1), 23), today, ZoneOffset.UTC))
        assertEquals("3 weeks ago", practisedAgo(at(today.minusDays(21), 9), today, ZoneOffset.UTC))
        assertNull(practisedAgo(null, today, ZoneOffset.UTC))
    }

    @Test
    fun `the Desk at a glance reads the newest day of the journal`() {
        assertNull(deskGlance(DeskState()))
        val s = DeskState(
            journal = listOf(
                JournalEntry("2026-11-18", "trend", "2026-11-19", trade = true),
                JournalEntry("2026-11-18", "carry", "2026-11-19", trade = false),
                JournalEntry("2026-11-18", "vol", "2026-11-19", trade = true, followed = "yes"),
                JournalEntry("2026-11-17", "trend", "2026-11-18", trade = true)
            )
        )
        val g = deskGlance(s)!!
        assertEquals(d(2026, 11, 18), g.dataDate)
        assertEquals(2, g.trades)
        assertEquals(1, g.unmarked)
        assertEquals(3, g.strategies)
    }

    @Test
    fun `a brief shows its top headline, anything else its title`() {
        val brief = LobsterCatch(id = "1", title = "Morning brief · 19 Nov", sources = listOf(LobsterSource("Tides turn early", "https://example.org/a")))
        assertEquals("Tides turn early", catchHeadline(brief))
        val answer = LobsterCatch(id = "2", orderId = "o", title = "Research · tidal power")
        assertEquals("Research · tidal power", catchHeadline(answer))
    }

    @Test
    fun `birthdays are day and month only`() {
        assertEquals(4 to 21, HomeSettings.parseBirthday("04-21"))
        assertNull(HomeSettings.parseBirthday("02-30"))
        assertNull(HomeSettings.parseBirthday("1990-04-21"))
        val s = HomeSettings(birthday = "04-21")
        assertTrue(s.isBirthday(4, 21, leapYear = false))
        assertFalse(s.isBirthday(4, 22, leapYear = false))
        val leap = HomeSettings(birthday = "02-29")
        assertTrue(leap.isBirthday(2, 28, leapYear = false))
        assertTrue(leap.isBirthday(2, 29, leapYear = true))
        assertFalse(leap.isBirthday(2, 28, leapYear = true))
    }
}
