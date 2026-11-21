package app.localmind.assistant.data.music

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import app.localmind.assistant.domain.music.Heard
import app.localmind.assistant.domain.music.Music
import app.localmind.assistant.domain.music.Synth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun attributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

private fun mono(rate: Int): AudioFormat = AudioFormat.Builder()
    .setSampleRate(rate)
    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
    .build()

/** Plays a few seconds of samples made on the tablet (a strum, an interval). One at a time. */
class SoundPlayer(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun play(samples: FloatArray) {
        job?.cancel()
        if (samples.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            val pcm = Synth.pcm(samples)
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes())
                .setAudioFormat(mono(Synth.RATE))
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                // Wait for it to finish (or to be cancelled by the next sound).
                val ms = pcm.size * 1000L / Synth.RATE + 100
                var waited = 0L
                while (isActive && waited < ms) {
                    Thread.sleep(20)
                    waited += 20
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/** How the metronome should run: a speed trainer adds [step] BPM every [everyBars] bars, up to [upTo]. */
data class Beat(val bpm: Int = 92, val beats: Int = 4, val subdivision: Int = 1, val step: Int = 0, val everyBars: Int = 4, val upTo: Int = 120)

/**
 * A metronome that writes its clicks into a stream sample by sample, so the timing is exact
 * whatever the screen is doing. The first beat of a bar is higher; subdivisions are quieter.
 */
class Metronome(private val scope: CoroutineScope) {
    private val _beat = MutableStateFlow(-1)
    /** The beat just sounded (0 = the bar's first), −1 when stopped. Runs a hair ahead of the sound. */
    val beat: StateFlow<Int> = _beat.asStateFlow()

    private val _bpm = MutableStateFlow(0)
    /** The tempo now (the speed trainer moves it). */
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    @Volatile private var settings = Beat()
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    fun update(next: Beat) {
        settings = next.copy(bpm = next.bpm.coerceIn(MIN_BPM, MAX_BPM), beats = next.beats.coerceIn(1, 12), subdivision = next.subdivision.coerceIn(1, 4))
        if (!running) _bpm.value = settings.bpm
    }

    fun start(with: Beat) {
        update(with)
        if (running) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _beat.value = -1
    }

    private suspend fun run() {
        val rate = Synth.RATE
        val high = Synth.click(1_760.0)
        val low = Synth.click(1_100.0)
        val sub = Synth.click(880.0, gain = 0.35f)
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes())
            .setAudioFormat(mono(rate))
            .setBufferSizeInBytes(maxOf(min, rate / 10 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val chunk = ShortArray(rate / 100) // 10 ms
        val mixing = FloatArray(chunk.size)
        val sounding = mutableListOf<Pair<Long, FloatArray>>()
        var written = 0L
        var nextTick = 0L
        var tick = 0 // within the bar: beats × subdivision ticks
        var bars = 0
        var tempo = settings.bpm
        _bpm.value = tempo
        try {
            track.play()
            while (currentCoroutineContext().isActive) {
                val s = settings
                mixing.fill(0f)
                val end = written + chunk.size
                while (nextTick < end) {
                    val ticksPerBar = s.beats * s.subdivision
                    if (tick >= ticksPerBar) {
                        tick = 0
                        bars++
                        if (s.step > 0 && bars % s.everyBars.coerceAtLeast(1) == 0 && tempo < s.upTo) {
                            tempo = minOf(s.upTo, tempo + s.step)
                            _bpm.value = tempo
                        }
                    }
                    if (s.step == 0 && tempo != s.bpm) {
                        tempo = s.bpm
                        _bpm.value = tempo
                    }
                    val sound = when {
                        tick == 0 -> high
                        tick % s.subdivision == 0 -> low
                        else -> sub
                    }
                    sounding += nextTick to sound
                    if (tick % s.subdivision == 0) _beat.value = tick / s.subdivision
                    tick++
                    nextTick += (rate * 60.0 / (tempo * s.subdivision)).toLong()
                }
                val iterator = sounding.iterator()
                while (iterator.hasNext()) {
                    val (start, samples) = iterator.next()
                    for (i in mixing.indices) {
                        val at = (written + i - start).toInt()
                        if (at >= 0 && at < samples.size) mixing[i] += samples[at]
                    }
                    if (start + samples.size <= end) iterator.remove()
                }
                for (i in chunk.indices) chunk[i] = (mixing[i].coerceIn(-1f, 1f) * 30_000).toInt().toShort()
                track.write(chunk, 0, chunk.size)
                written = end
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            _beat.value = -1
        }
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 260
    }
}

/**
 * Listens to the microphone only while the tuner is open, and says which note it hears. Nothing
 * is recorded or kept: each slice of sound is measured and dropped.
 */
class PitchListener(private val scope: CoroutineScope) {
    private val _heard = MutableStateFlow<Heard?>(null)
    val heard: StateFlow<Heard?> = _heard.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private var job: Job? = null
    @Volatile var a4: Double = 440.0

    /** Caller holds RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (job?.isActive == true) return
        _failed.value = false
        job = scope.launch(Dispatchers.Default) {
            val rate = Synth.RATE
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            // The raw microphone when the tablet offers it (no voice processing to bend the pitch), else the usual one.
            fun openMic(source: Int): AudioRecord? {
                val r = runCatching { AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, WINDOW * 4)) }.getOrNull()
                if (r != null && r.state != AudioRecord.STATE_INITIALIZED) {
                    r.release()
                    return null
                }
                return r
            }
            val recorder = openMic(MediaRecorder.AudioSource.UNPROCESSED) ?: openMic(MediaRecorder.AudioSource.MIC)
            if (recorder == null) {
                _failed.value = true
                return@launch
            }
            val window = FloatArray(WINDOW)
            val hop = ShortArray(WINDOW / 2)
            val recent = ArrayDeque<Double>()
            try {
                recorder.startRecording()
                while (isActive) {
                    var got = 0
                    while (got < hop.size && isActive) {
                        val n = recorder.read(hop, got, hop.size - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got < hop.size) continue
                    System.arraycopy(window, hop.size, window, 0, WINDOW - hop.size)
                    for (i in hop.indices) window[WINDOW - hop.size + i] = hop[i] / 32_768f
                    val hz = Music.pitch(window, rate)
                    if (hz == null) {
                        recent.clear()
                        _heard.value = null
                        continue
                    }
                    // The middle of the last three readings, so one stray reading doesn't jump the needle.
                    recent.addLast(hz)
                    while (recent.size > 3) recent.removeFirst()
                    val steady = recent.sorted()[recent.size / 2]
                    _heard.value = Music.heard(steady, a4)
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                _heard.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val WINDOW = 4_096
    }
}
