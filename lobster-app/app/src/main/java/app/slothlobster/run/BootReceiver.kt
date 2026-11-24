package app.slothlobster.run

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.slothlobster.core.Lobster
import kotlinx.coroutines.launch

/**
 * After a restart or an update, the Lobster wakes up again if it was set to stay awake. (1.14)
 * And the edition's alarm is set again whenever Android drops it or its time moves: a restart or
 * an update clears alarms, a new time zone moves 07:00, and allowing "Alarms & reminders" makes
 * it exact. Only Android sends these; the receiver isn't open to other apps.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        val app = context.applicationContext
        val lobster = Lobster.get(app)
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (lobster.store.value.settings.stayAwake) LobsterService.start(app)
        }
        EditionAlarm.reset()
        val pending = goAsync()
        lobster.scope.launch {
            try {
                EditionAlarm.arm(app)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Couldn't set the edition's alarm", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
