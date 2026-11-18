package app.localmind.assistant.data.lobster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.localmind.assistant.MainActivity
import app.localmind.assistant.R
import app.localmind.assistant.data.ask.AppShortcuts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes while the Lobster is switched on: looks in the mailbox folder (no network)
 * and, if the Lobster brought something new, a quiet notification that opens its page.
 */
@HiltWorker
class LobsterCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mailbox: LobsterMailbox
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!mailbox.isReady) return Result.success()
        val (total, fresh) = mailbox.newCatch()
        if (fresh > 0) LobsterCheck.notify(applicationContext, total)
        return Result.success()
    }
}

object LobsterCheck {
    private const val WORK_NAME = "lobster-mailbox-check"
    private const val CHANNEL_ID = "lobster"
    private const val NOTIFICATION_ID = 4102

    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        work.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LobsterCheckWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun notify(context: Context, total: Int) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SlothLobster", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When the Lobster brings something back"
            }
        )
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHORTCUT)
                .putExtra(MainActivity.EXTRA_TARGET, AppShortcuts.TARGET_LOBSTER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lobster)
            .setContentTitle(if (total == 1) "🦞 The Lobster brought something back" else "🦞 The Lobster brought back $total things")
            .setContentText("Open to file it in your Library, Notes or a chat.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
