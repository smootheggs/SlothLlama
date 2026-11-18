package app.localmind.assistant.data.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceModelState {
    data object Missing : VoiceModelState
    data class Importing(val progress: Float) : VoiceModelState
    data class Ready(val sizeBytes: Long) : VoiceModelState
}

/**
 * Offline speech-to-text with SenseVoice (Mandarin, Cantonese, English, Japanese, Korean) through
 * sherpa-onnx. Audio never leaves the device: the app has no network access at all.
 *
 * The model is imported once from the sherpa-onnx package (model.int8.onnx + tokens.txt) and
 * copied into private storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VoiceTranscriber @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir = File(context.filesDir, "voice")
    private val modelFile = File(dir, MODEL_FILE)
    private val tokensFile = File(dir, TOKENS_FILE)

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<VoiceModelState> = _state.asStateFlow()

    /** The recognizer isn't thread-safe; one thread owns it. */
    private val voiceThread = Dispatchers.IO.limitedParallelism(1)
    private var recognizer: OfflineRecognizer? = null
    private var recognizerLanguage: String? = null

    val isReady: Boolean get() = _state.value is VoiceModelState.Ready

    /** Imports the two files the user picked (in either order). */
    suspend fun import(uris: List<Uri>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            var modelUri: Uri? = null
            var tokensUri: Uri? = null
            for (uri in uris) {
                val name = displayName(uri)?.lowercase() ?: continue
                when {
                    name.endsWith(".onnx") -> modelUri = uri
                    name.endsWith(".txt") -> tokensUri = uri
                }
            }
            val model = requireNotNull(modelUri) { "Select model.int8.onnx together with tokens.txt." }
            val tokens = requireNotNull(tokensUri) { "Select tokens.txt together with model.int8.onnx." }

            withContext(voiceThread) { releaseRecognizer() }
            dir.mkdirs()
            _state.value = VoiceModelState.Importing(0f)

            val tokensTmp = File(dir, "$TOKENS_FILE.part")
            copy(tokens, tokensTmp) {}
            val tokenLines = tokensTmp.useLines { lines -> lines.count { it.isNotBlank() } }
            check(tokenLines > 1000) { "tokens.txt looks wrong (only $tokenLines entries)." }

            val modelTmp = File(dir, "$MODEL_FILE.part")
            val total = fileSize(model)
            copy(model, modelTmp) { copied ->
                if (total > 0) _state.value = VoiceModelState.Importing((copied.toFloat() / total).coerceIn(0f, 1f))
            }
            check(modelTmp.length() > MIN_MODEL_BYTES) { "That .onnx file is too small to be the SenseVoice model." }
            // An ONNX model is a protobuf whose first field is ir_version (tag byte 0x08).
            check(modelTmp.inputStream().use { it.read() } == 0x08) { "That file isn't an ONNX model." }

            modelFile.delete(); tokensFile.delete()
            check(modelTmp.renameTo(modelFile) && tokensTmp.renameTo(tokensFile)) { "Couldn't save the voice model." }
            Unit
        }.also {
            File(dir, "$MODEL_FILE.part").delete()
            File(dir, "$TOKENS_FILE.part").delete()
            _state.value = currentState()
        }
    }

    suspend fun remove() {
        withContext(voiceThread) { releaseRecognizer() }
        withContext(Dispatchers.IO) {
            modelFile.delete()
            tokensFile.delete()
        }
        _state.value = currentState()
    }

    /**
     * Transcribes 16 kHz mono [samples]. [language]: "zh", "yue", "en", "ja", "ko" or "auto".
     * A language hint helps learners: an accent is less likely to be heard as another language.
     */
    suspend fun transcribe(samples: FloatArray, language: String): String = withContext(voiceThread) {
        check(isReady) { "Import the voice model on the Models screen first." }
        val rec = recognizerFor(language)
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            ensureActive()
            rec.decode(stream)
            rec.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun recognizerFor(language: String): OfflineRecognizer {
        recognizer?.let { existing ->
            if (recognizerLanguage == language) return existing
            releaseRecognizer()
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelFile.absolutePath,
                    language = language,
                    useInverseTextNormalization = true
                ),
                tokens = tokensFile.absolutePath,
                numThreads = 4,
                provider = "cpu"
            )
        )
        return OfflineRecognizer(assetManager = null, config = config).also {
            recognizer = it
            recognizerLanguage = language
        }
    }

    private fun releaseRecognizer() {
        recognizer?.release()
        recognizer = null
        recognizerLanguage = null
    }

    private fun currentState(): VoiceModelState =
        if (modelFile.isFile && tokensFile.isFile) VoiceModelState.Ready(modelFile.length() + tokensFile.length())
        else VoiceModelState.Missing

    private fun copy(uri: Uri, target: File, onProgress: (Long) -> Unit) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the selected file.")
        input.use { inp ->
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var copied = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onProgress(copied)
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun fileSize(uri: Uri): Long =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MODEL_FILE = "model.int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"
        private const val MIN_MODEL_BYTES = 20L * 1024 * 1024
    }
}
