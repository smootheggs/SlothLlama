package app.localmind.assistant.ui.home

import android.icu.util.ChineseCalendar
import android.icu.util.Calendar as IcuCalendar
import app.localmind.assistant.domain.home.LunarDay
import app.localmind.assistant.domain.home.LunarSource
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Lunar dates from Android's own Chinese calendar (ICU), remembered for the day's lookups. */
class IcuLunar : LunarSource {
    private val cache = HashMap<LocalDate, LunarDay>()

    override fun lunar(date: LocalDate): LunarDay? {
        cache[date]?.let { return it }
        val day = runCatching {
            // Noon, so a date is never read across a midnight.
            val cal = ChineseCalendar(Date.from(date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()))
            LunarDay(
                month = cal.get(IcuCalendar.MONTH) + 1,
                day = cal.get(IcuCalendar.DAY_OF_MONTH),
                leap = cal.get(IcuCalendar.IS_LEAP_MONTH) == 1
            )
        }.getOrNull() ?: return null
        cache[date] = day
        return day
    }
}
