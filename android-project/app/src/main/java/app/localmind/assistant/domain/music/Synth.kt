package app.localmind.assistant.domain.music

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sounds made on the tablet, as samples: a plucked string (Karplus–Strong: a burst of noise
 * through a short delay that softens as it rings) and a metronome's click. No recordings ship.
 */
object Synth {
    const val RATE = 44_100

    /** A plucked string at [hz], ringing for [seconds]. */
    fun pluck(hz: Double, seconds: Double = 1.6, rate: Int = RATE, seed: Int = 7, decay: Double = 0.996): FloatArray {
        val n = (seconds * rate).toInt()
        val period = (rate / hz).roundToInt().coerceAtLeast(2)
        val random = Random(seed + period)
        val ring = FloatArray(period) { random.nextFloat() * 2f - 1f }
        val out = FloatArray(n)
        var at = 0
        for (i in 0 until n) {
            val a = ring[at]
            val b = ring[(at + 1) % period]
            out[i] = a
            ring[at] = ((a + b) * 0.5 * decay).toFloat()
            at = (at + 1) % period
        }
        // A short fade in and out, so it never clicks.
        val fade = minOf(n / 2, rate / 200)
        for (i in 0 until fade) {
            out[i] *= i / fade.toFloat()
            out[n - 1 - i] *= i / fade.toFloat()
        }
        return out
    }

    /** A short tick: a sine at [hz] that dies away in [ms]. */
    fun click(hz: Double, ms: Int = 28, gain: Float = 0.8f, rate: Int = RATE): FloatArray {
        val n = rate * ms / 1000
        return FloatArray(n) { i ->
            val t = i / rate.toDouble()
            (sin(2 * PI * hz * t) * exp(-t * 1000.0 / (ms / 4.0)) * gain).toFloat()
        }
    }

    /** Sounds laid over each other: (start in samples, samples), scaled so nothing clips. */
    fun mix(parts: List<Pair<Int, FloatArray>>, gain: Float = 0.5f): FloatArray {
        if (parts.isEmpty()) return FloatArray(0)
        val length = parts.maxOf { it.first + it.second.size }
        val out = FloatArray(length)
        for ((start, samples) in parts) for (i in samples.indices) out[start + i] += samples[i]
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val scale = gain / peak
        for (i in out.indices) out[i] *= scale
        return out
    }

    /** Notes one after another (a melody, an interval), each [gapMs] apart. */
    fun sequence(midis: List<Int>, gapMs: Int, a4: Double = 440.0, seconds: Double = 1.4): FloatArray =
        mix(midis.mapIndexed { i, m -> (i * gapMs * RATE / 1000) to pluck(Music.frequency(m, a4), seconds) })

    /** A strum, low string to high, [gapMs] between strings. */
    fun strum(midis: List<Int>, gapMs: Int = 28, a4: Double = 440.0): FloatArray = sequence(midis, gapMs, a4, 2.0)

    /** 16-bit samples for the speaker. */
    fun pcm(samples: FloatArray): ShortArray = ShortArray(samples.size) { i -> (samples[i].coerceIn(-1f, 1f) * 32_000).toInt().toShort() }
}
