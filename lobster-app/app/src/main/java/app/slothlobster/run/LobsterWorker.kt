package app.slothlobster.run

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.slothlobster.core.Lobster
import java.util.concurrent.TimeUnit

/**
 * The Lobster's check every 15 minutes, which Android keeps running across restarts: takes new
 * orders, runs them, checks watched pages, the brief and the edition. The awake service does the
 * same more often while the tablet is on.
 *
 * (1.14) The check runs its round itself, held awake, even while the awake service is on: when
 * the tablet sleeps, the service's half-minute timer stops with it, and this job (or the edition's
 * alarm) is what Android wakes it for. Before, the check left the round to a sleeping service,
 * which is how a 07:00 edition could wait until the tablet was picked up.
 */
class LobsterWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val lobster = Lobster.get(applicationContext)
        if (lobster.store.value.settings.stayAwake && !LobsterService.running) LobsterService.start(applicationContext)
        lobster.engine.passAwake()
        return Result.success()
    }

    /** Below Android 12 a rushed job runs as a foreground service, with the quiet "awake" notice. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        // Never asked for from Android 12 on, where a rushed job is an ordinary job that goes first.
        return ForegroundInfo(Notifier.RUSH_ID, Notifier.awake(applicationContext, "Making the edition"))
    }

    companion object {
        private const val NAME = "lobster-check"
        private const val NOW_NAME = "lobster-edition-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LobsterWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * A round as soon as the internet answers (the edition's alarm): rushed when Android allows,
         * an ordinary job when it doesn't. One at a time; a second alarm while it waits changes nothing.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LobsterWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
