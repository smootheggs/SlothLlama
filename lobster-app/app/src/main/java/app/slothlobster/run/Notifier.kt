package app.slothlobster.run

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.slothlobster.MainActivity
import app.slothlobster.R

/** The Lobster's two kinds of notice: a quiet "awake" one, and "brought back" when something lands. */
object Notifier {
    const val AWAKE_CHANNEL = "awake"
    const val CATCH_CHANNEL = "catch"
    const val AWAKE_ID = 7101
    private var nextId = 7200

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(AWAKE_CHANNEL, "Awake", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while the Lobster stays awake to check the mailbox"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CATCH_CHANNEL, "Brought back", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a job is done, a brief is ready or a watched page changes"
            }
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun awake(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, AWAKE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lobster)
            .setContentTitle("Sloth Lobster is awake")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(context))
            .build()

    fun brought(context: Context, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(context, CATCH_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lobster)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextId++, n) }
    }
}
