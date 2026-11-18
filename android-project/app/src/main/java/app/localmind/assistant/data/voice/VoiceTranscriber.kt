package app.localmind.assistant.data.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
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
 * Offline speech-to-text through sherpa-onnx, with two models the learner imports once:
 *  - SenseVoice (model.int8.onnx + tokens.txt): Mandarin, Cantonese, English, Japanese, Korean.
 *    Fast, and the better choice for those languages.
 *  - Whisper Small (small-encoder.int8.onnx + small-decoder.int8.onnx + small-tokens.txt):
 *    about 100 languages, used for French, German, Spanish and Italian (and as a fallback).
 * Audio never leaves the device: the app has no network access at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VoiceTranscriber @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir = File(context.filesDir, "voice")
    private val modelFile = File(dir, MODEL_FILE)
    private val tokensFile = File(dir, TOKENS_FILE)

    private val whisperDir = File(dir, "whisper")
    private val whisperEncoder = File(whisperDir, "encoder.int8.onnx")
    private val whisperDecoder = File(whisperDir, "decoder.int8.onnx")
    private val whisperTokens = File(whisperDir, "tokens.txt")

    private val _state = MutableStateFlow(currentState())
    /** SenseVoice. */
    val state: StateFlow<VoiceModelState> = _state.asStateFlow()

    private val _whisperState = MutableStateFlow(currentWhisperState())
    /** Whisper (for French, German, Spanish, Italian and anything SenseVoice doesn't cover). */
    val whisperState: StateFlow<VoiceModelState> = _whisperState.asStateFlow()

    /** The recognizer isn't thread-safe; one thread owns it. */
    private val voiceThread = Dispatchers.IO.limitedParallelism(1)
    private var recognizer: OfflineRecognizer? = null
    private var recognizerLanguage: String? = null

    val isReady: Boolean get() = _state.value is VoiceModelState.Ready
    private val whisperReady: Boolean get() = _whisperState.value is VoiceModelState.Ready

    /** Whether speech in [language] ("zh", "yue", "ja", "fr", "auto"…) can be transcribed now. */
    fun isReadyFor(language: String): Boolean = when {
        language in SENSE_VOICE_LANGUAGES && isReady -> true
        language == "yue" -> false // Whisper's Cantonese is poor; SenseVoice is needed
        else -> whisperReady
    }

    /** Which model to import for [language], for messages: "the voice model" or "Whisper". */
    fun modelNeededFor(language: String): String =
        if (language in SENSE_VOICE_LANGUAGES) "the SenseVoice voice model" else "the Whisper voice model"

    /**
     * Imports Whisper from the files the user picked (any order): the encoder and decoder .onnx
     * (names contain "encoder" / "decoder") and the tokens .txt.
     */
    suspend fun importWhisper(uris: List<Uri>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            var encoder: Uri? = null
            var decoder: Uri? = null
            var tokens: Uri? = null
            for (uri in uris) {
                val name = displayName(uri)?.lowercase() ?: continue
                when {
                    name.endsWith(".onnx") && "encoder" in name -> encoder = uri
                    name.endsWith(".onnx") && "decoder" in name -> decoder = uri
                    name.endsWith(".txt") -> tokens = uri
                }
            }
            val enc = requireNotNull(encoder) { "Select all three Whisper files: …-encoder.int8.onnx, …-decoder.int8.onnx and …-tokens.txt." }
            val dec = requireNotNull(decoder) { "The decoder file is missing: pick …-decoder.int8.onnx too." }
            val tok = requireNotNull(tokens) { "The tokens file is missing: pick …-tokens.txt too." }

            withContext(voiceThread) { releaseRecognizer() }
            whisperDir.mkdirs()
            _whisperState.value = VoiceModelState.Importing(0f)
            val parts = listOf(enc to whisperEncoder, dec to whisperDecoder, tok to whisperTokens)
            val total = parts.sumOf { fileSize(it.first).coerceAtLeast(0) }
            var done = 0L
            for ((uri, target) in parts) {
                val tmp = File(whisperDir, "${target.name}.part")
                val before = done
                copy(uri, tmp) { copied ->
                    if (total > 0) _whisperState.value = VoiceModelState.Importing(((before + copied).toFloat() / total).coerceIn(0f, 1f))
                }
                done += tmp.length()
            }
            val encTmp = File(whisperDir, "${whisperEncoder.name}.part")
            val decTmp = File(whisperDir, "${whisperDecoder.name}.part")
            val tokTmp = File(whisperDir, "${whisperTokens.name}.part")
            for (f in listOf(encTmp, decTmp)) {
                check(f.length() > MIN_WHISPER_PART_BYTES) { "A Whisper .onnx file looks too small; download it again." }
                check(f.inputStream().use { it.read() } == 0x08) { "${f.name.removeSuffix(".part")} isn't an ONNX model." }
            }
            val tokenLines = tokTmp.useLines { lines -> lines.count { it.isNotBlank() } }
            check(tokenLines > 40_000) { "That tokens file doesn't look like Whisper's ($tokenLines entries)." }
            for ((tmp, target) in listOf(encTmp to whisperEncoder, decTmp to whisperDecoder, tokTmp to whisperTokens)) {
                target.delete()
                check(tmp.renameTo(target)) { "Couldn't save the Whisper model." }
            }
            Unit
        }.also {
            whisperDir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
            _whisperState.value = currentWhisperState()
        }
    }

    suspend fun removeWhisper() {
        withContext(voiceThread) { releaseRecognizer() }
        withContext(Dispatchers.IO) { whisperDir.deleteRecursively() }
        _whisperState.value = currentWhisperState()
    }

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
     * Transcribes 16 kHz mono [samples]. [language]: "zh", "yue", "en", "ja", "ko", "fr", "de",
     * "es", "it" or "auto". A language hint helps learners: an accent is less likely to be heard
     * as another language. SenseVoice is used when it covers the language, otherwise Whisper.
     */
    suspend fun transcribe(samples: FloatArray, language: String): String = withContext(voiceThread) {
        check(isReadyFor(language)) { "Import ${modelNeededFor(language)} on the Models screen first." }
        val useSenseVoice = language in SENSE_VOICE_LANGUAGES && isReady
        val rec = recognizerFor(if (useSenseVoice) "sv:$language" else "wh:$language")
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

    /** [key] is "sv:<language>" for SenseVoice or "wh:<language>" for Whisper. */
    private fun recognizerFor(key: String): OfflineRecognizer {
        recognizer?.let { existing ->
            if (recognizerLanguage == key) return existing
            releaseRecognizer()
        }
        val language = key.substringAfter(':')
        val modelConfig = if (key.startsWith("sv:")) {
            OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelFile.absolutePath,
                    language = language,
                    useInverseTextNormalization = true
                ),
                tokens = tokensFile.absolutePath,
                numThreads = 4,
                provider = "cpu"
            )
        } else {
            OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = whisperEncoder.absolutePath,
                    decoder = whisperDecoder.absolutePath,
                    // "" lets Whisper detect the language; Cantonese is closest to "zh" for it.
                    language = when (language) {
                        "auto" -> ""
                        "yue" -> "zh"
                        else -> language
                    },
                    task = "transcribe"
                ),
                tokens = whisperTokens.absolutePath,
                numThreads = 4,
                provider = "cpu"
            )
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig
        )
        return OfflineRecognizer(assetManager = null, config = config).also {
            recognizer = it
            recognizerLanguage = key
        }
    }

    private fun releaseRecognizer() {
        recognizer?.release()
        recognizer = null
        recognizerLanguage = null
    }

    private fun currentWhisperState(): VoiceModelState =
        if (whisperEncoder.isFile && whisperDecoder.isFile && whisperTokens.isFile) {
            VoiceModelState.Ready(whisperEncoder.length() + whisperDecoder.length() + whisperTokens.length())
        } else VoiceModelState.Missing

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
        private const val MIN_WHISPER_PART_BYTES = 10L * 1024 * 1024
        /** Languages SenseVoice handles (and handles better than Whisper Small). */
        val SENSE_VOICE_LANGUAGES = setOf("zh", "yue", "en", "ja", "ko", "auto")
    }
}
