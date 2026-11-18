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
import java.io.RandomAccessFile
import java.security.MessageDigest
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

    /** The model file called [fileName] (e.g. a persona's own model), if it's on the device. */
    suspend fun fileNamed(fileName: String): File? {
        val match = { _models.value.firstOrNull { it.file.name == fileName }?.file }
        return (match() ?: run { refresh(); match() })?.takeIf { it.isFile }
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
        projectorFor(model.file)?.delete()
        refresh()
    }

    /** The vision projector (mmproj) added for [modelFile], if any. Lets that model see photos. */
    fun projectorFor(modelFile: File): File? = projectorFile(modelFile).takeIf { it.isFile }

    private fun projectorFile(modelFile: File) =
        File(modelFile.parentFile ?: importFolder, "mmproj-for-${modelFile.nameWithoutExtension}.gguf")

    /**
     * Adds a vision projector (an "mmproj" .gguf from the same Hugging Face page as the model) to
     * [model], so it can look at photos. Stored next to the model and hidden from the model list.
     */
    suspend fun importProjector(model: LocalModel, uri: Uri, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val (name, size) = describe(uri)
        if (!name.endsWith(".gguf", ignoreCase = true)) error("$name isn't a .gguf file. Pick the mmproj .gguf for ${model.displayName}.")
        val target = projectorFile(model.file)
        copyIn(uri, target, size, onProgress) { partial ->
            ggufProblem(partial)?.let { error("$name: $it") }
            if (ggufArchitecture(partial) != "clip") {
                error("$name isn't a vision projector. On the model's Hugging Face page, look for a file with \"mmproj\" in its name.")
            }
        }
        target
    }

    suspend fun removeProjector(model: LocalModel) = withContext(Dispatchers.IO) {
        projectorFor(model.file)?.delete()
        Unit
    }

    /** Copies [uri] to [target] via a .part file; [validate] can reject it before it's kept. */
    private suspend fun copyIn(uri: Uri, target: File, size: Long, onProgress: (Float) -> Unit, validate: (File) -> Unit) {
        val dir = target.parentFile ?: importFolder
        if (size > 0 && dir.usableSpace < size + FREE_SPACE_MARGIN) {
            error("Not enough free space: ${formatBytes(size)} needed, ${formatBytes(dir.usableSpace)} free.")
        }
        val partial = File(dir, "${target.name}.part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the selected file")
            input.use { inp ->
                partial.outputStream().use { out ->
                    val buf = ByteArray(4 shl 20)
                    var copied = 0L
                    var lastPct = -1
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
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
            validate(partial)
            target.delete()
            check(partial.renameTo(target)) { "Couldn't save ${target.name}" }
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
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
        val digest = MessageDigest.getInstance("SHA-256")
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
                        digest.update(buf, 0, n)
                        copied += n
                        if (size > 0) {
                            val pct = (copied * 100 / size).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                        }
                    }
                }
            }
            if (format == ModelFormat.GGUF) {
                ggufProblem(partial)?.let { error("$name: $it") }
                if (ggufArchitecture(partial) == "clip") {
                    error("$name is a vision file (mmproj), not a chat model. Add it with \"Photos\" on its model's card.")
                }
            }
            target.delete()
            check(partial.renameTo(target)) { "Couldn't save $name" }
            rememberSha256(target, digest.digest().toHex())
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

    /** The SHA-256 recorded at import (or by an earlier [sha256] call), if the file hasn't changed since. */
    fun knownSha256(model: LocalModel): String? =
        prefs.getString(shaKey(model.file), null)

    /**
     * SHA-256 of the model file, to compare with the hash Hugging Face lists for it
     * (file page → "SHA256"). Takes a few seconds per GB; the result is cached.
     */
    suspend fun sha256(model: LocalModel, onProgress: (Float) -> Unit = {}): String = withContext(Dispatchers.IO) {
        knownSha256(model)?.let { return@withContext it }
        val digest = MessageDigest.getInstance("SHA-256")
        val total = model.file.length().coerceAtLeast(1)
        model.file.inputStream().use { inp ->
            val buf = ByteArray(4 shl 20)
            var read = 0L
            var lastPct = -1
            while (true) {
                ensureActive()
                val n = inp.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                read += n
                val pct = (read * 100 / total).toInt()
                if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
            }
        }
        digest.digest().toHex().also { rememberSha256(model.file, it) }
    }

    private fun rememberSha256(file: File, hex: String) {
        prefs.edit().putString(shaKey(file), hex).apply()
    }

    /** Keyed by path, size and timestamp, so a replaced file never shows a stale hash. */
    private fun shaKey(file: File) = "sha256:${file.absolutePath}:${file.length()}:${file.lastModified()}"

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
        /**
         * Cheap sanity check before a file reaches llama.cpp's native parser: the GGUF magic,
         * a known format version, and plausible tensor / metadata counts. Returns null if the
         * header looks fine, otherwise a short reason. It can't prove a file is safe — only
         * download models from sources you trust — but it stops junk and obviously crafted files.
         */
        fun ggufProblem(file: File): String? = runCatching {
            RandomAccessFile(file, "r").use { f ->
                if (f.length() < 24) return "file is too small to be a GGUF model"
                val header = ByteArray(24)
                f.readFully(header)
                if (String(header, 0, 4, Charsets.US_ASCII) != "GGUF") return "not a GGUF file"
                val version = le32(header, 4)
                if (version !in 2..3) return "unsupported GGUF version $version"
                val tensors = le64(header, 8)
                val metadata = le64(header, 16)
                if (tensors <= 0 || tensors > MAX_TENSORS) return "implausible tensor count ($tensors)"
                if (metadata < 0 || metadata > MAX_METADATA) return "implausible metadata count ($metadata)"
                null
            }
        }.getOrElse { "couldn't read the file (${it.message})" }

        /**
         * The GGUF's general.architecture ("qwen35", "llama", "clip" for vision projectors…),
         * read from the metadata at the start of the file, or null if it isn't there.
         */
        fun ggufArchitecture(file: File): String? = runCatching {
            java.io.DataInputStream(java.io.BufferedInputStream(file.inputStream(), 1 shl 16)).use { input ->
                val header = ByteArray(24)
                input.readFully(header)
                if (String(header, 0, 4, Charsets.US_ASCII) != "GGUF") return null
                val count = le64(header, 16).coerceAtMost(256)
                val word = ByteArray(8)
                // (InputStream.skipNBytes needs Android 14; this works on 13 too.)
                fun skipN(n: Long) {
                    var left = n
                    while (left > 0) {
                        val skipped = input.skip(left)
                        if (skipped > 0) left -= skipped
                        else if (input.read() < 0) error("unexpected end") else left--
                    }
                }
                fun u32(): Long { input.readFully(word, 0, 4); return le32(word, 0).toLong() and 0xFFFFFFFFL }
                fun u64(): Long { input.readFully(word, 0, 8); return le64(word, 0) }
                fun str(limit: Int = 4096): String? {
                    val len = u64()
                    if (len < 0 || len > 1_000_000) error("bad string")
                    if (len > limit) { skipN(len); return null }
                    val b = ByteArray(len.toInt())
                    input.readFully(b)
                    return String(b, Charsets.UTF_8)
                }
                fun skip(type: Long) {
                    when (type) {
                        0L, 1L, 7L -> skipN(1)
                        2L, 3L -> skipN(2)
                        4L, 5L, 6L -> skipN(4)
                        10L, 11L, 12L -> skipN(8)
                        8L -> str(0)
                        9L -> {
                            val inner = u32()
                            val n = u64()
                            if (n < 0 || n > 10_000_000) error("bad array")
                            if (inner == 8L) repeat(n.toInt()) { str(0) } else {
                                val width = when (inner) { 0L, 1L, 7L -> 1L; 2L, 3L -> 2L; 4L, 5L, 6L -> 4L; 10L, 11L, 12L -> 8L; else -> error("bad type") }
                                skipN(width * n)
                            }
                        }
                        else -> error("bad type $type")
                    }
                }
                repeat(count.toInt()) {
                    val key = str() ?: ""
                    val type = u32()
                    if (key == "general.architecture" && type == 8L) return str()
                    skip(type)
                }
                null
            }
        }.getOrNull()

        private fun le32(b: ByteArray, at: Int): Int =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

        private fun le64(b: ByteArray, at: Int): Long =
            (le32(b, at).toLong() and 0xFFFFFFFFL) or (le32(b, at + 4).toLong() shl 32)

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private const val MAX_TENSORS = 1_000_000L
        private const val MAX_METADATA = 100_000L
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
