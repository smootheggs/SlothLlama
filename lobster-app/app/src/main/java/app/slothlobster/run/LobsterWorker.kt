package app.slothlobster.run

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.slothlobster.core.Lobster
import java.util.concurrent.TimeUnit

/**
 * The Lobster's check every 15 minutes, which Android keeps running across restarts: takes new
 * orders, runs them, checks watched pages and the brief. The awake service does the same more
 * often; this makes sure nothing waits long when it isn't running.
 */
class LobsterWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val lobster = Lobster.get(applicationContext)
        if (lobster.store.value.settings.stayAwake && !LobsterService.running) LobsterService.start(applicationContext)
        if (!LobsterService.running) lobster.engine.pass()
        return Result.success()
    }

    companion object {
        private const val NAME = "lobster-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LobsterWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
