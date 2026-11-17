package app.localmind.assistant.data.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.localmind.assistant.data.inference.ModelFileLocator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelFormat(val label: String, val runtime: String) {
    GGUF("GGUF", "llama.cpp"),
    LITERT("LiteRT", "MediaPipe");

    companion object {
        fun of(file: File): ModelFormat? = when (file.extension.lowercase()) {
            "gguf" -> GGUF
            "task", "litertlm" -> LITERT
            else -> null
        }
    }
}

data class LocalModel(
    val file: File,
    val displayName: String,
    val format: ModelFormat,
    val sizeBytes: Long,
    /** e.g. "Q4_K_M", parsed from the file name; null if unknown. */
    val quantization: String?
) {
    val path: String get() = file.absolutePath
}

/**
 * The models on this device. The folders are the source of truth, so files copied over USB
 * show up just like imported ones. The chosen ("active") model is remembered across launches.
 */
@Singleton
class ModelLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locator: ModelFileLocator
) {
    private val prefs = context.getSharedPreferences("model_library", Context.MODE_PRIVATE)

    private val _models = MutableStateFlow<List<LocalModel>>(emptyList())
    val models: StateFlow<List<LocalModel>> = _models.asStateFlow()

    private val _activePath = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activePath: StateFlow<String?> = _activePath.asStateFlow()

    val importFolder: File get() = locator.sideloadDir

    suspend fun refresh(): List<LocalModel> = withContext(Dispatchers.IO) {
        val found = locator.searchDirs()
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && it.length() > 0 && isListable(it) }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                val format = ModelFormat.of(file) ?: return@mapNotNull null
                LocalModel(
                    file = file,
                    displayName = file.nameWithoutExtension,
                    format = format,
                    sizeBytes = file.length(),
                    quantization = QUANT.find(file.nameWithoutExtension)?.value?.uppercase()
                )
            }
        _models.value = found
        found
    }

    /** The chosen model, or the most recently added one if nothing was chosen (or it was deleted). */
    suspend fun activeFile(): File? {
        if (_models.value.isEmpty()) refresh()
        _activePath.value?.let { path ->
            val file = File(path)
            if (file.isFile) return file
        }
        return _models.value.firstOrNull()?.file
    }

    fun setActive(model: LocalModel) {
        prefs.edit().putString(KEY_ACTIVE, model.path).apply()
        _activePath.value = model.path
    }

    suspend fun delete(model: LocalModel) = withContext(Dispatchers.IO) {
        if (_activePath.value == model.path) {
            prefs.edit().remove(KEY_ACTIVE).apply()
            _activePath.value = null
        }
        model.file.delete()
        refresh()
    }

    /** Copies a model picked with the system file picker (Downloads, an SSD, …) into the app's folder. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): LocalModel = withContext(Dispatchers.IO) {
        val (name, size) = describe(uri)
        val format = ModelFormat.of(File(name))
            ?: error("$name isn't a model file. SlothLlama runs .gguf (llama.cpp) and .task / .litertlm (MediaPipe) files.")
        val dir = importFolder
        if (size > 0 && dir.usableSpace < size + FREE_SPACE_MARGIN) {
            error("Not enough free space: ${formatBytes(size)} needed, ${formatBytes(dir.usableSpace)} free.")
        }

        val target = File(dir, name)
        val partial = File(dir, "$name.part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the selected file")
            input.use { inp ->
                partial.outputStream().use { out ->
                    val buf = ByteArray(4 shl 20)
                    var copied = 0L
                    var lastPct = -1
                    while (true) {
                        ensureActive()
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (size > 0) {
                            val pct = (copied * 100 / size).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                        }
                    }
                }
            }
            if (format == ModelFormat.GGUF) check(hasGgufMagic(partial)) { "$name is not a valid GGUF file." }
            target.delete()
            check(partial.renameTo(target)) { "Couldn't save $name" }
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
        refresh().first { it.path == target.absolutePath }
    }

    private fun isListable(file: File): Boolean {
        val name = file.name.lowercase()
        if (name.endsWith(".part")) return false
        if ("mmproj" in name) return false           // vision projector, not a chat model
        if (SPLIT_TAIL.containsMatchIn(name)) return false // only show part 1 of split GGUFs
        return true
    }

    private fun hasGgufMagic(file: File): Boolean = runCatching {
        file.inputStream().use { s ->
            val magic = ByteArray(4)
            s.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF"
        }
    }.getOrDefault(false)

    private fun describe(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val safe = (name ?: "model-${System.currentTimeMillis()}").replace(Regex("[^A-Za-z0-9._-]"), "_")
        return safe to size
    }

    companion object {
        private const val KEY_ACTIVE = "active_model_path"
        private const val FREE_SPACE_MARGIN = 200L * 1024 * 1024
        private val QUANT = Regex("(?i)(IQ[1-4]_[A-Z0-9_]+|Q[2-8]_K(_[SML])?|Q[2-8]_[01]|BF16|F16|F32|int4|int8|q4|q8)")
        private val SPLIT_TAIL = Regex("-0*(?:[2-9]|[1-9][0-9]+)-of-[0-9]+\\.gguf$")

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%d MB".format(bytes / (1L shl 20))
            else -> "%d KB".format(bytes / 1024)
        }
    }
}
