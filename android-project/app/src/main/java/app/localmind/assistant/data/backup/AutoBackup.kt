package app.localmind.assistant.data.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.localmind.assistant.MainActivity
import app.localmind.assistant.R
import app.localmind.assistant.data.lobster.KeyVault
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AutoBackupState(
    val enabled: Boolean = false,
    /** The chosen folder's name, or null if none is chosen. */
    val folder: String? = null,
    val hasPassword: Boolean = false,
    val lastAt: Long = 0,
    val lastOk: Boolean = true,
    /** "6 chats, 120 flashcards, 2 custom personas" or why the last try failed. */
    val lastResult: String? = null
) {
    val ready: Boolean get() = folder != null && hasPassword
}

/**
 * Weekly backups into a folder the user picks (an SD card or USB stick is best). Each file is
 * the same encrypted backup as Export, always with a password; the password is kept wrapped by a
 * key in the Android Keystore, never as plain text. The newest [KEEP] backups are kept.
 */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupManager
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val vault = KeyVault(prefs, alias = "slothllama-auto-backup")
    private val lock = Mutex()

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AutoBackupState> = _state.asStateFlow()

    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)?.takeIf { it != uri }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        refresh()
    }

    fun setPassword(password: String) {
        require(password.length >= MIN_PASSWORD) { "Use at least $MIN_PASSWORD characters." }
        vault.save(password.toByteArray(Charsets.UTF_8))
        prefs.edit().putBoolean(KEY_HAS_PASSWORD, true).apply()
        refresh()
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on && _state.value.ready).apply()
        refresh()
        schedule(context, _state.value.enabled)
    }

    /** Backs up now, whatever the date. Returns what was saved; throws with a plain reason. */
    suspend fun runNow(): String = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val summary = write()
                val text = "${summary.chats} chats, ${summary.flashcards} flashcards, ${summary.personas} custom personas"
                record(ok = true, text)
                text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val reason = e.message ?: "Something went wrong."
                record(ok = false, reason)
                throw IllegalStateException(reason, e)
            }
        }
    }

    /** From the daily check: backs up if it's switched on and the last good backup is a week old. */
    suspend fun runIfDue() {
        val s = _state.value
        if (!s.enabled) return
        val lastGood = prefs.getLong(KEY_LAST_GOOD, 0)
        if (System.currentTimeMillis() - lastGood < EVERY_DAYS * DAY) return
        runCatching { runNow() }.onFailure { notifyFailure(it.message) }
    }

    private suspend fun write(): BackupSummary {
        val folderUri = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
            ?: error("Choose a folder for automatic backups first.")
        val password = vault.load()?.toString(Charsets.UTF_8)
            ?: error("Set the automatic backup password again.")
        val tree = DocumentFile.fromTreeUri(context, folderUri)?.takeIf { it.exists() && it.canWrite() }
            ?: error("SlothLlama can't reach the backup folder. Is the card or stick in? If so, choose the folder again.")

        val name = "SlothLlama backup ${LocalDate.now()}.slothllama"
        // Write under a temporary name, so a failure never costs the backup already there.
        val partial = tree.createFile("application/octet-stream", "$name.partial")
            ?: error("Couldn't create a file in the backup folder.")
        val summary = try {
            backups.export(partial.uri, password)
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
        tree.findFile(name)?.delete()
        if (!partial.renameTo(name)) {
            partial.delete()
            error("Couldn't finish the backup file.")
        }
        prune(tree)
        return summary
    }

    private fun prune(tree: DocumentFile) {
        val files = tree.listFiles()
        files.filter { it.name.orEmpty().endsWith(".slothllama.partial") }.forEach { it.delete() }
        files.filter { NAME.matches(it.name.orEmpty()) }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    private fun record(ok: Boolean, result: String) {
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            putLong(KEY_LAST_AT, now)
            putBoolean(KEY_LAST_OK, ok)
            putString(KEY_LAST_RESULT, result)
            if (ok) putLong(KEY_LAST_GOOD, now)
        }.apply()
        refresh()
    }

    private fun refresh() {
        _state.value = read()
    }

    private fun read(): AutoBackupState {
        val folder = prefs.getString(KEY_FOLDER, null)?.let { text ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(text))?.name }.getOrNull() ?: "Chosen folder"
        }
        return AutoBackupState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            folder = folder,
            hasPassword = prefs.getBoolean(KEY_HAS_PASSWORD, false),
            lastAt = prefs.getLong(KEY_LAST_AT, 0),
            lastOk = prefs.getBoolean(KEY_LAST_OK, true),
            lastResult = prefs.getString(KEY_LAST_RESULT, null)
        )
    }

    private fun notifyFailure(reason: String?) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When an automatic backup couldn't be saved"
            }
        )
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cards)
            .setContentTitle("The weekly backup didn't save")
            .setContentText(reason ?: "Open Settings → Backup to see why.")
            .setStyle(NotificationCompat.BigTextStyle().bigText((reason ?: "") + "\nIt will try again tomorrow."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val KEEP = 4
        const val MIN_PASSWORD = 8
        private const val EVERY_DAYS = 7
        private const val DAY = 24 * 60 * 60_000L
        private const val PREFS = "auto_backup"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FOLDER = "folder"
        private const val KEY_HAS_PASSWORD = "has_password"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_LAST_OK = "last_ok"
        private const val KEY_LAST_GOOD = "last_good"
        private const val KEY_LAST_RESULT = "last_result"
        private const val WORK_NAME = "auto-backup"
        private const val CHANNEL_ID = "backups"
        private const val NOTIFICATION_ID = 4103
        private val NAME = Regex("""SlothLlama backup \d{4}-\d{2}-\d{2}\.slothllama""")

        /** A daily look (on charge-friendly terms); the backup itself runs once a week. */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            work.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
            )
        }
    }
}

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val autoBackup: AutoBackup
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        autoBackup.runIfDue()
        return Result.success()
    }
}
