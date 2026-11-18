package app.slothlobster.core

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import app.slothlobster.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash notes and a plain report for bug-hunting. A crash is written to a small file in the app's
 * private storage and never sent anywhere; Settings → Diagnostics shows the report to copy by hand.
 */
object Diagnostics {
    private const val DIR = "crashes"
    private const val KEEP = 10
    private const val MAX_CHARS = 24_000

    data class Crash(val at: Long, val title: String, val text: String)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, where: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val now = System.currentTimeMillis()
        val text = buildString {
            appendLine("When: ${stamp(now)}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Thread: $where")
            appendLine()
            append(trace.take(MAX_CHARS))
        }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        File(dir, "crash-$now.txt").writeText(text)
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    fun crashes(context: Context): List<Crash> =
        File(context.filesDir, DIR).listFiles().orEmpty()
            .filter { it.name.startsWith("crash-") }
            .sortedByDescending { it.name }
            .mapNotNull { file ->
                val at = file.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: return@mapNotNull null
                val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                val title = text.lineSequence().dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() }?.take(140) ?: "Crash"
                Crash(at, title, text)
            }

    fun clear(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /** App, device, what the Lobster is up to, recent problems from the Log, then each crash. */
    fun report(context: Context, saved: Saved, live: Live): String {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val power = context.getSystemService(PowerManager::class.java)
        val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        val notifications = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val now = System.currentTimeMillis()
        val problems = saved.log.filter { it.kind == "problem" }.sortedByDescending { it.at }.take(10)
        val crashes = crashes(context)
        return buildString {
            appendLine("SlothLobster ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Memory: ${gb(memory.availMem)} free of ${gb(memory.totalMem)}")
            appendLine("Battery use: ${if (unrestricted) "unrestricted" else "optimised (Android may delay checks)"}")
            appendLine("Notifications: ${if (notifications) "allowed" else "not allowed"}")
            appendLine("Paired: ${if (live.paired) "yes" else "no"}")
            appendLine("Mailbox: ${live.folderName ?: if (live.folderMissing) "folder can't be opened" else "not chosen"}")
            appendLine("Mode: ${if (live.paused) "paused" else if (live.awake) "awake" else "checks every 15 minutes"}")
            appendLine("Last check: ${if (live.lastPass > 0) stamp(live.lastPass) else "never"}")
            live.waitingFor?.let { appendLine("Waiting for: $it") }
            appendLine("Jobs: ${saved.jobs.count { it.open }} open, ${saved.jobs.size} kept")
            appendLine("Watches: ${saved.watches.size} · Repeats: ${saved.repeats.size} · Feeds: ${saved.settings.feeds.size}")
            appendLine(
                "Engines: " + saved.engines.entries.joinToString { (name, h) ->
                    "$name " + if (h.failsInRow > 0) "failing ×${h.failsInRow}" else "ok"
                }.ifEmpty { "not used yet" }
            )
            appendLine("Report made: ${stamp(now)}")
            appendLine()
            if (problems.isEmpty()) {
                appendLine("No problems in the Log.")
            } else {
                appendLine("Recent problems from the Log:")
                problems.forEach { appendLine("${stamp(it.at)}  ${it.what}${if (it.detail.isNotBlank()) " · ${it.detail}" else ""}") }
            }
            appendLine()
            if (crashes.isEmpty()) {
                appendLine("No crashes recorded.")
            } else {
                appendLine("Recent crashes (${crashes.size}):")
                crashes.forEach { crash ->
                    appendLine()
                    appendLine("—— ${stamp(crash.at)} ——")
                    appendLine(crash.text.trim())
                }
            }
        }
    }

    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

    private fun stamp(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
