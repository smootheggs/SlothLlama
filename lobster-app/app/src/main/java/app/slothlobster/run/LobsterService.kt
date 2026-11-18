package app.slothlobster.run

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.slothlobster.core.Lobster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the Lobster awake: checks the mailbox every half minute (at once when poked) and writes
 * its heartbeat every minute, so SlothLlama sees it as awake. Shows a quiet notification while it
 * runs. Without it, the 15-minute check ([LobsterWorker]) still picks up orders.
 */
class LobsterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = Notifier.awake(this, "Checking the mailbox")
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, Notifier.AWAKE_ID, notification, type)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't stay awake", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            val lobster = Lobster.get(this)
            lobster.setLive { it.copy(awake = true) }
            scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    lobster.engine.heartbeat()
                }
            }
            scope.launch {
                while (isActive) {
                    runCatching { lobster.engine.pass() }.onFailure { Log.w(TAG, "Pass failed", it) }
                    lobster.engine.nap(CHECK_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        val lobster = Lobster.get(this)
        lobster.setLive { it.copy(awake = false) }
        // Tell SlothLlama the Lobster is going to sleep, rather than letting the heartbeat go stale.
        runBlocking { withTimeoutOrNull(3_000) { lobster.engine.heartbeat("asleep") } }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LobsterService"
        private const val ACTION_STOP = "app.slothlobster.STOP_AWAKE"
        private const val CHECK_MS = 30_000L
        private const val HEARTBEAT_MS = 60_000L

        @Volatile var running = false
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LobsterService::class.java))
            } catch (e: Exception) {
                // Android doesn't let an app start this from the background; the 15-minute check carries on.
                Log.w(TAG, "Couldn't start the awake service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LobsterService::class.java))
        }
    }
}
