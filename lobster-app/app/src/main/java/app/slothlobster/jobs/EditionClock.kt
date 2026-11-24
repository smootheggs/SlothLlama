package app.slothlobster.jobs

import kotlinx.serialization.Serializable
import java.util.Calendar

/**
 * (1.14) Today's edition that hasn't come out yet: how many times it was tried, when the next try
 * is, the time after which it isn't tried again, and why the last try failed. Kept with the
 * settings, apart from the editions made, so a failed try never counts as made.
 */
@Serializable
data class EditionMiss(
    /** The edition's key, "2026-11-24|Morning". */
    val key: String,
    val name: String,
    val tries: Int,
    /** When to try again; 0 once it's given up. */
    val nextAt: Long,
    val giveUpAt: Long,
    /** Why the last try failed, in plain words ("None of the edition's sources answered"). */
    val why: String,
    val gaveUp: Boolean = false
)

/**
 * When the edition is made and tried again. Plain arithmetic on the plan and the clock, so it can
 * be checked without a phone: the Lobster wakes for [nextWake], makes the edition [Editions.due],
 * and after a failed try waits [retryDelayMs] until [giveUpAt].
 */
object EditionClock {

    /** Retries keep going this long after the edition's time (07:00 → 11:00)… */
    const val RETRY_WINDOW_MS = 4L * 60 * 60 * 1000
    /** …and at least this long after the first try, for a tablet that only woke late. */
    const val RETRY_AFTER_FIRST_MS = 60L * 60 * 1000
    private const val MINUTE_MS = 60L * 1000

    fun key(now: Calendar, t: EditionTime): String = "${Repeats.dayKey(now)}|${t.name}"

    /** The day part of a key ("2026-11-24"). */
    fun day(key: String): String = key.substringBefore('|')

    /** [t] today, on [now]'s day, to the minute. */
    fun todayAt(now: Calendar, t: EditionTime): Long = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, t.h)
        set(Calendar.MINUTE, t.m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun editionDay(plan: EditionPlan, c: Calendar): Boolean {
        if (plan.weekends) return true
        val d = c.get(Calendar.DAY_OF_WEEK)
        return d != Calendar.SATURDAY && d != Calendar.SUNDAY
    }

    /** The plan's next time after [now] (today or a later day), or null when the edition is off. */
    fun nextAt(plan: EditionPlan, now: Calendar): Long? {
        if (!plan.on || plan.times.isEmpty()) return null
        val nowMs = now.timeInMillis
        val day = (now.clone() as Calendar)
        // A week and a day covers weekdays-only plans from any day.
        repeat(8) {
            if (editionDay(plan, day)) {
                plan.times.map { todayAt(day, it) }.filter { it > nowMs }.minOrNull()?.let { return it }
            }
            day.add(Calendar.DAY_OF_MONTH, 1)
            day.set(Calendar.HOUR_OF_DAY, 0)
            day.set(Calendar.MINUTE, 0)
        }
        return null
    }

    /** The wait before try [tries] + 1: 15, 15, 30, 30, then 60 minutes. */
    fun retryDelayMs(tries: Int): Long = MINUTE_MS * when {
        tries <= 2 -> 15
        tries <= 4 -> 30
        else -> 60
    }

    /** When a failed edition stops being tried: four hours after its time, or an hour after the first try. */
    fun giveUpAt(now: Calendar, t: EditionTime): Long =
        maxOf(todayAt(now, t) + RETRY_WINDOW_MS, now.timeInMillis + RETRY_AFTER_FIRST_MS)

    /** What a failed try leaves: the next try, or given up when the next would come too late. */
    fun failed(prev: EditionMiss?, key: String, name: String, now: Calendar, t: EditionTime, why: String): EditionMiss {
        val same = prev?.takeIf { it.key == key && !it.gaveUp }
        val tries = (same?.tries ?: 0) + 1
        val giveUp = same?.giveUpAt ?: giveUpAt(now, t)
        val next = now.timeInMillis + retryDelayMs(tries)
        return if (next > giveUp) EditionMiss(key, name, tries, 0, giveUp, why.take(200), gaveUp = true)
        else EditionMiss(key, name, tries, next, giveUp, why.take(200))
    }

    /** True while a retry for [key] is waiting for its time. */
    fun waiting(miss: EditionMiss?, key: String, nowMs: Long): Boolean =
        miss != null && miss.key == key && !miss.gaveUp && nowMs < miss.nextAt

    /**
     * When the Lobster should next wake for the edition: the next retry, or the plan's next time,
     * whichever comes first. Null when there's nothing to wake for.
     */
    fun nextWake(plan: EditionPlan?, now: Calendar, miss: EditionMiss?): Long? {
        if (plan == null || !plan.on) return null
        val nowMs = now.timeInMillis
        val retry = miss?.takeIf { !it.gaveUp && it.nextAt > nowMs && day(it.key) == Repeats.dayKey(now) }?.nextAt
        return listOfNotNull(retry, nextAt(plan, now)).minOrNull()
    }

    /** A line for SlothLlama and the Log about an edition that hasn't come out today, or null. */
    fun statusLine(miss: EditionMiss?, now: Calendar, clock: (Long) -> String): String? {
        if (miss == null || day(miss.key) != Repeats.dayKey(now)) return null
        val label = Editions.label(miss.name)
        return if (miss.gaveUp) "$label didn't come out: ${miss.why}"
        else "$label is late: ${miss.why}. Trying again at ${clock(miss.nextAt)}"
    }
}
