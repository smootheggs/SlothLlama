package app.localmind.assistant.data.meaning

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.localmind.assistant.data.inference.GpuSettings
import app.localmind.llama.LlamaNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MeaningModelState {
    data object Missing : MeaningModelState
    data class Importing(val fraction: Float) : MeaningModelState
    /** [id] identifies the file, so vectors made by another model are recognised and redone. */
    data class Ready(val name: String, val sizeBytes: Long, val id: String) : MeaningModelState
    data class Failed(val message: String) : MeaningModelState
}

/**
 * The model behind search by meaning: a small embedding model such as EmbeddingGemma (GGUF),
 * imported once and kept in the app's private storage, apart from the chat models. It turns a
 * text into a vector; texts that mean similar things get similar vectors, whatever their words.
 * It runs on the CPU and is let go after a minute without use, to give its memory back.
 */
@Singleton
class MeaningModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gpu: GpuSettings
) {
    private val dir = File(context.filesDir, "meaning")
    private val file = File(dir, "model.gguf")
    private val prefs = context.getSharedPreferences("meaning", Context.MODE_PRIVATE)
    private val thread = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<MeaningModelState> = _state.asStateFlow()

    /** The ready model's id, or null. */
    val readyId: String? get() = (_state.value as? MeaningModelState.Ready)?.id

    @Volatile private var loaded = false
    private var idleJob: Job? = null

    /** Copies a .gguf embedding model in and checks that it really is one. */
    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: "model.gguf"
            require(name.endsWith(".gguf", ignoreCase = true)) { "Pick the .gguf file (for example embeddinggemma-300M-Q8_0.gguf)." }
            val size = fileSize(uri)
            require(size <= 0 || size < MAX_BYTES) { "That file is too big for an embedding model. Pick EmbeddingGemma (about 330 MB)." }
            withContext(thread) { unloadNow() }
            dir.mkdirs()
            val tmp = File(dir, "model.gguf.part")
            _state.value = MeaningModelState.Importing(0f)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open that file.")
                input.use { inp ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var copied = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied += n
                            if (size > 0) _state.value = MeaningModelState.Importing((copied.toFloat() / size).coerceIn(0f, 1f))
                        }
                    }
                }
                check(tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }.contentEquals(GGUF)) { "$name isn't a GGUF model file." }
                // Try it before replacing anything: it must load and give a vector.
                val code = withContext(thread) {
                    initNative()
                    val rc = LlamaNative.embedLoad(tmp.absolutePath)
                    val ok = rc == 0 && LlamaNative.embed("test")?.isNotEmpty() == true
                    LlamaNative.embedUnload()
                    if (rc == 0 && !ok) 2 else rc
                }
                when (code) {
                    0 -> Unit
                    2 -> error("$name isn't an embedding model. Use EmbeddingGemma, not a chat model.")
                    3 -> error("Not enough free memory to try $name. Close other apps and try again.")
                    else -> error("$name couldn't be loaded. Download it again.")
                }
                file.delete()
                check(tmp.renameTo(file)) { "Couldn't save the model." }
                prefs.edit().putString(KEY_NAME, name).putLong(KEY_IMPORTED, System.currentTimeMillis()).apply()
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            } finally {
                _state.value = readState()
            }
        }
    }

    suspend fun remove() = withContext(thread) {
        unloadNow()
        file.delete()
        prefs.edit().clear().apply()
        _state.value = readState()
    }

    /** A question's vector, or null if there's no model (or it failed). */
    suspend fun embedQuery(text: String): FloatArray? =
        embed(QUERY_PREFIX + text.trim().take(MAX_CHARS))

    /** A passage's vector, or null if there's no model (or it failed). */
    suspend fun embedDocument(title: String, text: String): FloatArray? =
        embed("title: ${title.trim().ifEmpty { "none" }} | text: " + text.trim().take(MAX_CHARS))

    private suspend fun embed(input: String): FloatArray? {
        if (_state.value !is MeaningModelState.Ready) return null
        return withContext(thread) {
            try {
                if (!loaded) {
                    initNative()
                    val rc = LlamaNative.embedLoad(file.absolutePath)
                    if (rc != 0) {
                        Log.w(TAG, "Embedding model didn't load: $rc")
                        return@withContext null
                    }
                    loaded = true
                }
                LlamaNative.embed(input)
            } catch (e: Throwable) {
                Log.w(TAG, "Embedding failed", e)
                null
            } finally {
                scheduleIdleUnload()
            }
        }
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            withContext(thread) { unloadNow() }
        }
    }

    private fun unloadNow() {
        if (loaded) runCatching { LlamaNative.embedUnload() }
        loaded = false
    }

    private fun initNative() {
        LlamaNative.ensureInitialized(context.applicationInfo.nativeLibraryDir, gpu.kernelCacheDir)
    }

    private fun readState(): MeaningModelState =
        if (file.isFile && file.length() > 0) {
            val name = prefs.getString(KEY_NAME, null) ?: file.name
            MeaningModelState.Ready(name, file.length(), "$name:${file.length()}:${prefs.getLong(KEY_IMPORTED, 0)}")
        } else {
            MeaningModelState.Missing
        }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun fileSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private companion object {
        const val TAG = "MeaningModel"
        const val KEY_NAME = "name"
        const val KEY_IMPORTED = "imported"
        const val IDLE_MS = 60_000L
        const val MAX_BYTES = 2_000_000_000L
        /** Long enough for a passage (about 480 tokens); the model reads at most 2,048 tokens. */
        const val MAX_CHARS = 4_000
        /** EmbeddingGemma's prompt for search queries; documents get "title: … | text: …". */
        const val QUERY_PREFIX = "task: search result | query: "
        val GGUF = "GGUF".toByteArray()
    }
}
