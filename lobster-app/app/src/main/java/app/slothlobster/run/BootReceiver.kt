package app.slothlobster.run

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.slothlobster.core.Lobster

/** After a restart, the Lobster wakes up again if it was set to stay awake. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Lobster.get(context).store.value.settings.stayAwake) LobsterService.start(context)
    }
}
