package app.localmind.assistant.data.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Reads (never writes) the tablet's calendars, for "what's on my calendar tomorrow?". */
@Singleton
class CalendarReader @Inject constructor(@ApplicationContext private val context: Context) {

    class Event(val title: String, val start: Long, val end: Long, val allDay: Boolean, val location: String)

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events from the start of [from] for [days] days, in order (at most [limit]). */
    suspend fun events(from: LocalDate, days: Int, limit: Int = 25): List<Event> = withContext(Dispatchers.IO) {
        if (!hasPermission) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val out = mutableListOf<Event>()
        context.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                out += Event(
                    title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(no title)",
                    start = c.getLong(1),
                    end = c.getLong(2),
                    allDay = c.getInt(3) != 0,
                    location = c.getString(4).orEmpty()
                )
            }
        }
        out
    }

    /** "Tue 18 Nov 09:30–10:00 Team sync (Room 4)"; all-day events by date only. */
    fun describe(e: Event): String {
        val zone = ZoneId.systemDefault()
        val where = if (e.location.isNotBlank()) " (${e.location})" else ""
        if (e.allDay) {
            // All-day events are stored as UTC midnights.
            val day = Instant.ofEpochMilli(e.start).atZone(ZoneOffset.UTC).toLocalDate()
            return "${DAY.format(day)}, all day: ${e.title}$where"
        }
        val s = Instant.ofEpochMilli(e.start).atZone(zone)
        val t = Instant.ofEpochMilli(e.end).atZone(zone)
        val endText = if (t.toLocalDate() == s.toLocalDate()) TIME.format(t) else "${DAY.format(t)} ${TIME.format(t)}"
        return "${DAY.format(s)} ${TIME.format(s)}–$endText: ${e.title}$where"
    }

    private companion object {
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    }
}
