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

/** Records 16 kHz mono audio from the microphone until [stop] is called (or the time limit). */
@Singleton
class VoiceRecorder @Inject constructor() {

    private val _level = MutableStateFlow(0f)
    /** Loudness 0..1, for the "listening" animation. */
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile private var keepRecording = false

    /** Caller must hold RECORD_AUDIO. Returns the samples as floats in -1..1. */
    @SuppressLint("MissingPermission")
    suspend fun record(maxSeconds: Int = MAX_SECONDS): FloatArray = withContext(Dispatchers.IO) {
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
                _level.value = (sqrt(sumSquares / n) * 6).toFloat().coerceIn(0f, 1f)
            }
        } finally {
            keepRecording = false
            runCatching { recorder.stop() }
            recorder.release()
            _level.value = 0f
        }
        samples.copyOf(count)
    }

    /** Ends the current recording; [record] then returns what was captured. */
    fun stop() {
        keepRecording = false
    }

    companion object {
        const val MAX_SECONDS = 60
    }
}
