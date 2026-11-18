package app.localmind.assistant.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * When to stop listening on its own (hands-free mode, repeat-after-me): after [silenceMs] of quiet
 * following speech, or after [noSpeechMs] if nobody speaks at all (then nothing is returned).
 */
data class Endpointing(val silenceMs: Int = 1_200, val noSpeechMs: Int = 8_000)

/**
 * Records 16 kHz mono audio from the microphone until [stop] is called, the time limit, or (with
 * [Endpointing]) the speaker pauses.
 */
@Singleton
class VoiceRecorder @Inject constructor() {

    private val _level = MutableStateFlow(0f)
    /** Loudness 0..1, for the "listening" animation. */
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile private var keepRecording = false

    /**
     * Caller must hold RECORD_AUDIO. Returns the samples as floats in -1..1; empty when
     * [endpointing] heard no speech.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(maxSeconds: Int = MAX_SECONDS, endpointing: Endpointing? = null): FloatArray = withContext(Dispatchers.IO) {
        val rate = VoiceTranscriber.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, rate / 5 * 2) * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("The microphone isn't available.")
        }
        val maxSamples = maxSeconds * rate
        val samples = FloatArray(maxSamples)
        var count = 0
        val chunk = ShortArray(rate / 10)   // 100 ms
        // Voice activity, by loudness against the room's own noise level (measured first).
        val vad = endpointing?.let { Vad(it) }
        keepRecording = true
        try {
            recorder.startRecording()
            while (keepRecording && isActive && count < maxSamples) {
                val n = recorder.read(chunk, 0, minOf(chunk.size, maxSamples - count))
                if (n <= 0) continue
                var sumSquares = 0.0
                for (i in 0 until n) {
                    val v = chunk[i] / 32768f
                    samples[count + i] = v
                    sumSquares += v * v
                }
                count += n
                val rms = sqrt(sumSquares / n).toFloat()
                _level.value = (rms * 6).coerceIn(0f, 1f)
                if (vad != null && vad.shouldStop(rms, n * 1000 / rate)) break
            }
        } finally {
            keepRecording = false
            runCatching { recorder.stop() }
            recorder.release()
            _level.value = 0f
        }
        if (vad != null && !vad.heardSpeech) FloatArray(0) else samples.copyOf(count)
    }

    /** Energy-based endpointing: simple, and plenty for one person talking to a tablet. */
    private class Vad(private val rules: Endpointing) {
        private var elapsedMs = 0
        private var noise = 0f
        private var calibrationChunks = 0
        private var loudMs = 0
        private var quietMs = 0
        var heardSpeech = false
            private set

        fun shouldStop(rms: Float, chunkMs: Int): Boolean {
            elapsedMs += chunkMs
            if (elapsedMs <= CALIBRATION_MS) {
                noise = (noise * calibrationChunks + rms) / (calibrationChunks + 1)
                calibrationChunks++
                return false
            }
            // Capped, in case someone was already talking while the room was being measured.
            val threshold = maxOf(MIN_SPEECH_RMS, minOf(noise, MAX_NOISE_RMS) * 3f)
            if (!heardSpeech) {
                loudMs = if (rms > threshold) loudMs + chunkMs else 0
                if (loudMs >= SPEECH_START_MS) heardSpeech = true
                return elapsedMs >= rules.noSpeechMs
            }
            quietMs = if (rms < threshold * 0.7f) quietMs + chunkMs else 0
            return quietMs >= rules.silenceMs
        }

        private companion object {
            const val CALIBRATION_MS = 300
            const val SPEECH_START_MS = 200
            const val MIN_SPEECH_RMS = 0.015f
            const val MAX_NOISE_RMS = 0.03f
        }
    }

    /** Ends the current recording; [record] then returns what was captured. */
    fun stop() {
        keepRecording = false
    }

    companion object {
        const val MAX_SECONDS = 60
    }
}
