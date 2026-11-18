package app.localmind.assistant.data.study

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
import app.localmind.assistant.data.local.LocalMindDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Once a day: if flashcards are due, a gentle notification that opens the review. */
@HiltWorker
class ReviewReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: LocalMindDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val due = dao.countDueVocabulary(System.currentTimeMillis())
        if (due > 0) ReviewReminder.notify(applicationContext, due)
        return Result.success()
    }
}

object ReviewReminder {
    private const val WORK_NAME = "flashcard-review-reminder"
    private const val CHANNEL_ID = "reviews"
    private const val NOTIFICATION_ID = 4101

    /** Schedules (or cancels) the daily check at [hour]:00 local time. */
    fun schedule(context: Context, enabled: Boolean, hour: Int, replace: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntil(hour), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun millisUntil(hour: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    fun notify(context: Context, due: Int) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Flashcard reviews", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily reminder when flashcards are due"
            }
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHORTCUT)
                .putExtra(MainActivity.EXTRA_TARGET, AppShortcuts.TARGET_FLASHCARDS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cards)
            .setContentTitle(if (due == 1) "1 flashcard is due" else "$due flashcards are due")
            .setContentText("A few minutes of review keeps them fresh.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
