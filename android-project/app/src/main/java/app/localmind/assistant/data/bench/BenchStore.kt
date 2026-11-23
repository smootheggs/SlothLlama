package app.localmind.assistant.data.bench

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import app.localmind.assistant.domain.bench.Bench
import app.localmind.assistant.domain.bench.BenchRun
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** This device, as the benchmark names it. */
data class BenchDevice(
    /** The name the owner gave it ("Galaxy S25+"), else its maker and model. */
    val name: String,
    /** "Qualcomm SM8750", "Mediatek MT6991", or "" when Android doesn't say. */
    val chip: String,
    val ramGb: Int,
    val cores: Int
)

/**
 * The benchmark's results (1.52). Kept in the no-backup folder: they describe this device, so a
 * backup restored on another device mustn't carry them over. Newest first, [Bench.MAX_KEPT] at most.
 */
@Singleton
class BenchStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val file = File(context.noBackupFilesDir, "bench.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private val _runs = MutableStateFlow(read())
    val runs: StateFlow<List<BenchRun>> = _runs.asStateFlow()

    private fun read(): List<BenchRun> = runCatching {
        if (!file.isFile || file.length() > MAX_FILE_BYTES) emptyList()
        else json.decodeFromString(ListSerializer(BenchRun.serializer()), file.readText())
    }.getOrDefault(emptyList())

    suspend fun add(run: BenchRun) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = (listOf(run) + _runs.value).take(Bench.MAX_KEPT)
            val tmp = File(file.parentFile, "bench.json.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(BenchRun.serializer()), updated))
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
            _runs.value = updated
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            file.delete()
            _runs.value = emptyList()
        }
    }

    fun device(): BenchDevice {
        val given = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        val name = given?.trim()?.takeIf { it.isNotEmpty() }
            ?: listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).joinToString(" ")
        val chip = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { !it.isNullOrBlank() && it != Build.UNKNOWN }
            .joinToString(" ")
        val mem = ActivityManager.MemoryInfo()
        runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem) }
        // Total memory as Android reports it is a little under the box's figure: round up to whole GB.
        val ramGb = Math.ceil(mem.totalMem / (1024.0 * 1024 * 1024)).toInt()
        return BenchDevice(name.take(60), chip.take(60), ramGb, Runtime.getRuntime().availableProcessors())
    }

    private companion object {
        const val MAX_FILE_BYTES = 512L * 1024
    }
}
