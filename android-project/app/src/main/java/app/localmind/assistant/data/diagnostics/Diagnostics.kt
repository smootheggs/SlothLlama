package app.localmind.assistant.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.localmind.assistant.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash notes and a plain report for bug-hunting. When the app crashes, the error is written to a
 * small file in the app's private storage (nothing is sent anywhere); Settings → Diagnostics shows
 * the report so it can be copied into a bug report by hand.
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

    /** The report: app, device, memory and storage, then each recent crash in full. */
    fun report(context: Context, extra: List<Pair<String, String>> = emptyList()): String {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val crashes = crashes(context)
        return buildString {
            appendLine("SlothLlama ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Processor: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Memory: ${gb(memory.availMem)} free of ${gb(memory.totalMem)}")
            appendLine("Storage: ${gb(context.filesDir.usableSpace)} free")
            extra.forEach { (k, v) -> appendLine("$k: $v") }
            appendLine("Report made: ${stamp(System.currentTimeMillis())}")
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
