package app.slothlobster.run

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.slothlobster.core.Lobster
import app.slothlobster.jobs.EditionClock
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * (1.14) The edition's alarm: wakes the tablet at the edition's time (and at each retry), so the
 * paper doesn't wait for the 15-minute check, which Android holds back while the tablet sleeps.
 *
 * Exact when "Alarms & reminders" is allowed for the SlothLobster (Settings → Diagnostics), else
 * Android's inexact alarm, set again after every round so it lands closer as the time nears. The
 * alarm only ever reaches [EditionAlarmReceiver], inside this app (an explicit, immutable intent),
 * and carries nothing: the receiver reads the plan from the Lobster's own storage.
 */
object EditionAlarm {
    private const val TAG = "EditionAlarm"
    const val ACTION = "app.slothlobster.action.EDITION_ALARM"
    private const val REQUEST = 7301

    /** Whether alarms can be exact here (always below Android 12). */
    fun exact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun intent(context: Context, create: Boolean): PendingIntent? {
        val i = Intent(context, EditionAlarmReceiver::class.java).setAction(ACTION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST, i, flags)
    }

    /** When the alarm is set for, as last armed (for Diagnostics); 0 when none. */
    @Volatile var armedFor: Long = 0
        private set

    /** Sets the alarm for the next edition time or retry, or clears it when there's none. */
    fun arm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val s = Lobster.get(context).store.value.settings
        val at = EditionClock.nextWake(s.edition, Calendar.getInstance(), s.editionMiss)
        if (at == null) {
            intent(context, create = false)?.let { am.cancel(it); it.cancel() }
            armedFor = 0
            return
        }
        if (at == armedFor) return
        val pi = intent(context, create = true) ?: return
        try {
            if (exact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            // The exact-alarm switch was turned off between the check and the call.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        armedFor = at
    }

    /** Forget what was armed, so the next [arm] sets it again (after a restart or an update). */
    fun reset() {
        armedFor = 0
    }
}

/**
 * The edition's alarm went off: a round runs now, held awake, once the internet answers (a
 * one-time job with a connection required, so a tablet just woken doesn't spend a try offline).
 */
class EditionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EditionAlarm.ACTION) return
        val app = context.applicationContext
        EditionAlarm.reset()
        LobsterWorker.runNow(app)
        val lobster = Lobster.get(app)
        if (lobster.store.value.settings.stayAwake && !LobsterService.running) LobsterService.start(app)
        val pending = goAsync()
        lobster.scope.launch {
            try {
                EditionAlarm.arm(app)
            } catch (e: Exception) {
                Log.w("EditionAlarm", "Couldn't set the next alarm", e)
            } finally {
                pending.finish()
            }
        }
    }
}
