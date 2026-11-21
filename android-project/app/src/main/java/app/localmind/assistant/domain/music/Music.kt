package app.localmind.assistant.domain.music

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** A guitar tuning: its strings low to high, as MIDI note numbers (E2 = 40). */
data class Tuning(val name: String, val strings: List<Int>)

/** How a horn's written notes sit above concert pitch, in semitones (an alto sax reads a sixth up). */
enum class Horn(val label: String, val semitones: Int) {
    CONCERT("Concert pitch", 0), ALTO("Alto (E♭)", 9), TENOR("Tenor (B♭)", 14), SOPRANO("Soprano (B♭)", 2)
}

/** What a chord is: its symbol after the root, the intervals from the root, and which of them must sound. */
data class Quality(val symbol: String, val intervals: List<Int>, val required: Set<Int>, val aliases: List<String> = emptyList())

data class Chord(val root: Int, val quality: Quality, val bass: Int? = null) {
    val pitchClasses: Set<Int> get() = (quality.intervals.map { (root + it) % 12 } + listOfNotNull(bass)).toSet()
    val requiredClasses: Set<Int> get() = quality.required.map { (root + it) % 12 }.toSet() + setOfNotNull(bass)
    val lowest: Int get() = bass ?: root
}

/** A chord shape: one fret per string, low to high; −1 is a muted string, 0 an open one. */
data class Voicing(val frets: List<Int>) {
    val played: Int get() = frets.count { it >= 0 }
    val lowestFret: Int get() = frets.filter { it > 0 }.minOrNull() ?: 0
    val highestFret: Int get() = frets.filter { it > 0 }.maxOrNull() ?: 0
    /** "x32010" (frets over 9 in brackets: "x(10)…"). */
    val text: String get() = frets.joinToString("") { f -> when { f < 0 -> "x"; f > 9 -> "($f)"; else -> "$f" } }
}

data class Scale(val name: String, val intervals: List<Int>, val degrees: List<String>)

/** A note heard: its name, octave, how far off in cents (−50…+50), and the frequency. */
data class Heard(val midi: Int, val cents: Double, val hz: Double)

/**
 * Music worked out on the tablet: notes and pitch, tunings, chords from their names (and names
 * from frets), shapes for any chord, scales, and transposing. Nothing here is guessed by a model.
 */
object Music {
    val SHARPS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val FLATS = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
    /** The everyday spelling of each pitch class, as chord roots are usually written. */
    val COMMON = listOf("C", "C#", "D", "E♭", "E", "F", "F#", "G", "A♭", "A", "B♭", "B")

    val TUNINGS = listOf(
        Tuning("Standard", listOf(40, 45, 50, 55, 59, 64)),
        Tuning("Drop D", listOf(38, 45, 50, 55, 59, 64)),
        Tuning("DADGAD", listOf(38, 45, 50, 55, 57, 62)),
        Tuning("Open G", listOf(38, 43, 50, 55, 59, 62)),
        Tuning("Open D", listOf(38, 45, 50, 54, 57, 62)),
        Tuning("Half step down", listOf(39, 44, 49, 54, 58, 63))
    )
    val STANDARD = TUNINGS.first()

    // --- notes and pitch ---

    fun name(pc: Int, flats: Boolean = false): String = (if (flats) FLATS else SHARPS)[((pc % 12) + 12) % 12]

    /** "A2", "C#4". */
    fun noteName(midi: Int, flats: Boolean = false): String = name(midi, flats) + (midi / 12 - 1)

    fun frequency(midi: Int, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    fun heard(hz: Double, a4: Double = 440.0): Heard {
        val m = 69 + 12 * ln(hz / a4) / ln(2.0)
        val near = m.roundToInt()
        return Heard(near, (m - near) * 100, hz)
    }

    /** The note a horn player reads for a concert [midi] note. */
    fun written(midi: Int, horn: Horn): Int = midi + horn.semitones

    /**
     * The pitch of a sound, by YIN (de Cheveigné and Kawahara): the lag at which the signal best
     * repeats. Null for silence or noise without a clear pitch. Works from 60 Hz (below a guitar's
     * low E) to about 1,200 Hz.
     */
    fun pitch(x: FloatArray, rate: Int, minHz: Double = 60.0, maxHz: Double = 1200.0, threshold: Double = 0.15): Double? {
        val n = x.size
        var energy = 0.0
        for (v in x) energy += v * v
        if (energy / n < 1e-5) return null
        val tauMin = (rate / maxHz).toInt().coerceAtLeast(2)
        val tauMax = (rate / minHz).toInt().coerceAtMost(n / 2)
        if (tauMax <= tauMin + 2) return null
        val w = n - tauMax - 1
        val d = DoubleArray(tauMax + 2)
        for (tau in 1..tauMax + 1) {
            if (tau + w > n) break
            var s = 0.0
            for (j in 0 until w) {
                val diff = x[j] - x[j + tau]
                s += diff * diff
            }
            d[tau] = s
        }
        val cm = DoubleArray(tauMax + 2)
        cm[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax + 1) {
            running += d[tau]
            cm[tau] = if (running == 0.0) 1.0 else d[tau] * tau / running
        }
        var tau = tauMin
        while (tau <= tauMax) {
            if (cm[tau] < threshold) {
                while (tau + 1 <= tauMax && cm[tau + 1] < cm[tau]) tau++
                break
            }
            tau++
        }
        if (tau > tauMax) return null
        // A parabola through the three points around the dip, for a lag between samples.
        val a = cm[tau - 1]
        val b = cm[tau]
        val c = cm[tau + 1]
        val denom = a - 2 * b + c
        val shift = if (abs(denom) < 1e-12) 0.0 else 0.5 * (a - c) / denom
        return rate / (tau + shift.coerceIn(-1.0, 1.0))
    }

    // --- chords ---

    val QUALITIES = listOf(
        Quality("", listOf(0, 4, 7), setOf(0, 4), listOf("maj", "M")),
        Quality("m", listOf(0, 3, 7), setOf(0, 3), listOf("min", "-")),
        Quality("7", listOf(0, 4, 7, 10), setOf(0, 4, 10), listOf("dom7")),
        Quality("maj7", listOf(0, 4, 7, 11), setOf(0, 4, 11), listOf("M7", "Δ7", "Δ", "ma7")),
        Quality("m7", listOf(0, 3, 7, 10), setOf(0, 3, 10), listOf("min7", "-7")),
        Quality("5", listOf(0, 7), setOf(0, 7)),
        Quality("sus4", listOf(0, 5, 7), setOf(0, 5), listOf("sus")),
        Quality("sus2", listOf(0, 2, 7), setOf(0, 2)),
        Quality("6", listOf(0, 4, 7, 9), setOf(0, 4, 9)),
        Quality("m6", listOf(0, 3, 7, 9), setOf(0, 3, 9)),
        Quality("add9", listOf(0, 4, 7, 2), setOf(0, 4, 2), listOf("add2")),
        Quality("madd9", listOf(0, 3, 7, 2), setOf(0, 3, 2)),
        Quality("dim", listOf(0, 3, 6), setOf(0, 3, 6), listOf("°", "o")),
        Quality("dim7", listOf(0, 3, 6, 9), setOf(0, 3, 6, 9), listOf("°7", "o7")),
        Quality("m7♭5", listOf(0, 3, 6, 10), setOf(0, 3, 6, 10), listOf("m7b5", "ø", "ø7")),
        Quality("aug", listOf(0, 4, 8), setOf(0, 4, 8), listOf("+")),
        Quality("7sus4", listOf(0, 5, 7, 10), setOf(0, 5, 10), listOf("7sus")),
        Quality("mMaj7", listOf(0, 3, 7, 11), setOf(0, 3, 11), listOf("m(maj7)", "mM7", "-Δ7")),
        Quality("9", listOf(0, 4, 7, 10, 2), setOf(0, 4, 10, 2)),
        Quality("maj9", listOf(0, 4, 7, 11, 2), setOf(0, 4, 11, 2), listOf("M9")),
        Quality("m9", listOf(0, 3, 7, 10, 2), setOf(0, 3, 10, 2), listOf("min9", "-9")),
        Quality("7♭9", listOf(0, 4, 7, 10, 1), setOf(0, 4, 10, 1), listOf("7b9")),
        Quality("7#9", listOf(0, 4, 7, 10, 3), setOf(0, 4, 10, 3)),
        Quality("6/9", listOf(0, 4, 7, 9, 2), setOf(0, 4, 9, 2), listOf("69")),
        Quality("11", listOf(0, 7, 10, 2, 5), setOf(0, 10, 5)),
        Quality("13", listOf(0, 4, 7, 10, 2, 9), setOf(0, 4, 10, 9))
    )

    private val ROOT = Regex("""^([A-Ga-g])([#♯b♭]?)""")

    /** A pitch class from a note name: "C#" → 1, "Bb" → 10, "B♭" → 10. Null if it isn't one. */
    fun pitchClass(note: String): Int? {
        val m = ROOT.find(note.trim()) ?: return null
        if (m.value.length != note.trim().length) return null
        return rootOf(m)
    }

    private fun rootOf(m: MatchResult): Int {
        val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11).getValue(m.groupValues[1].uppercase()[0])
        return when (m.groupValues[2]) { "#", "♯" -> base + 1; "b", "♭" -> base + 11; else -> base } % 12
    }

    /** Reads a chord symbol: "Am7", "F#m7b5", "Cadd9/G", "Bbmaj7", "D/F#". Null if it isn't one. */
    fun chord(symbol: String): Chord? {
        val s = symbol.trim()
        if (s.isEmpty() || s.length > 16) return null
        val m = ROOT.find(s) ?: return null
        if (m.groupValues[1][0].isLowerCase()) return null
        val root = rootOf(m)
        var rest = s.substring(m.value.length)
        var bass: Int? = null
        val slash = rest.lastIndexOf('/')
        if (slash >= 0 && rest.substring(slash + 1) != "9") {
            bass = pitchClass(rest.substring(slash + 1)) ?: return null
            rest = rest.substring(0, slash)
        }
        val q = QUALITIES.firstOrNull { it.symbol == rest || rest in it.aliases } ?: return null
        return Chord(root, q, bass?.takeIf { it != root })
    }

    /** Writes a chord back: root, quality, and a slash bass if any, in sharps or flats. */
    fun symbol(c: Chord, flats: Boolean? = null): String {
        fun n(pc: Int) = when (flats) { true -> name(pc, true); false -> name(pc, false); null -> COMMON[pc] }
        return n(c.root) + c.quality.symbol + (c.bass?.let { "/" + n(it) } ?: "")
    }

    /** Keys usually written with flats, by their tonic (and minor keys by theirs). */
    private val FLAT_MAJOR = setOf(5, 10, 3, 8, 1, 6)
    private val FLAT_MINOR = setOf(2, 7, 0, 5, 10, 3)

    /** Whether a key starting on this chord is written with flats (F, B♭, E♭…; Dm, Gm, Cm…). */
    fun flatKey(c: Chord): Boolean = if (c.quality.intervals.contains(3) && !c.quality.intervals.contains(4)) c.root in FLAT_MINOR else c.root in FLAT_MAJOR

    fun transpose(c: Chord, semitones: Int): Chord =
        c.copy(root = ((c.root + semitones) % 12 + 12) % 12, bass = c.bass?.let { ((it + semitones) % 12 + 12) % 12 })

    /** Names what's held: the frets on [tuning] → "C", "Am7", "C/E"; null if it's no chord in the table. */
    fun nameOf(frets: List<Int>, tuning: Tuning = STANDARD): Chord? {
        val notes = frets.withIndex().filter { it.value >= 0 }.map { tuning.strings[it.index] + it.value }
        if (notes.size < 2) return null
        val bass = notes.min() % 12
        val pcs = notes.map { it % 12 }.toSet()
        val candidates = mutableListOf<Pair<Chord, Int>>()
        for (root in pcs) for ((qi, q) in QUALITIES.withIndex()) {
            val chordPcs = q.intervals.map { (root + it) % 12 }.toSet()
            val required = q.required.map { (root + it) % 12 }.toSet()
            if (!chordPcs.containsAll(pcs) || !pcs.containsAll(required)) continue
            // Exact matches, then the root in the bass, then the simpler name.
            val cost = (if (chordPcs == pcs) 0 else 20) + (if (root == bass) 0 else 10) + qi
            candidates += Chord(root, q, if (root == bass) null else bass) to cost
        }
        return candidates.minByOrNull { it.second }?.first
    }

    /**
     * Shapes for [c] on [tuning], easiest first: at least four strings (two for a power chord),
     * every note in the chord, every required note there, the lowest note the root (or the slash
     * bass), within four frets and four fingers (a barre counts as one), at most one muted string
     * above the lowest played one.
     */
    fun voicings(c: Chord, tuning: Tuning = STANDARD, limit: Int = 6, maxFret: Int = 15): List<Voicing> {
        val pcs = c.pitchClasses
        val required = c.requiredClasses
        val minPlayed = if (c.quality.intervals.size <= 2) 2 else 4
        val found = LinkedHashMap<List<Int>, Int>()
        val strings = tuning.strings
        for (base in 0..(maxFret - 3)) {
            val options = strings.map { openNote ->
                val fs = mutableListOf(-1)
                // Open strings only in the open position: mixed with a shape up the neck they read oddly.
                if (base == 0 && (openNote % 12) in pcs) fs += 0
                for (f in maxOf(1, base)..(base + 3)) if (((openNote + f) % 12) in pcs) fs += f
                fs
            }
            val pick = IntArray(strings.size)
            fun walk(i: Int) {
                if (i == strings.size) {
                    score(pick, strings, c, required, minPlayed)?.let { s ->
                        val key = pick.toList()
                        if (found[key]?.let { it > s } != false) found[key] = s
                    }
                    return
                }
                for (f in options[i]) {
                    pick[i] = f
                    walk(i + 1)
                }
            }
            walk(0)
        }
        // Easiest first; then keep shapes up and down the neck rather than five near copies.
        val sorted = found.entries.sortedWith(compareBy({ it.value }, { it.key.filter { f -> f > 0 }.minOrNull() ?: 0 })).map { Voicing(it.key) }
        val out = mutableListOf<Voicing>()
        for (v in sorted) {
            if (out.size >= limit) break
            if (out.count { abs(it.lowestFret - v.lowestFret) <= 1 } >= 2) continue
            out += v
        }
        return out
    }

    private fun score(pick: IntArray, strings: List<Int>, c: Chord, required: Set<Int>, minPlayed: Int): Int? {
        val played = pick.indices.filter { pick[it] >= 0 }
        if (played.size < minPlayed) return null
        val first = played.first()
        val innerMutes = (first until pick.size).count { pick[it] < 0 }
        if (innerMutes > 1) return null
        val notes = played.map { strings[it] + pick[it] }
        if (notes.min() % 12 != c.lowest) return null
        val heard = notes.map { it % 12 }.toSet()
        if (!heard.containsAll(required)) return null
        val fretted = played.map { pick[it] }.filter { it > 0 }
        if (fretted.isNotEmpty() && fretted.max() - fretted.min() > 3) return null
        val low = fretted.minOrNull() ?: 0
        val atLow = fretted.count { it == low }
        val fingers = fretted.size - if (atLow >= 2) atLow - 1 else 0
        if (fingers > 4) return null
        val opens = played.count { pick[it] == 0 }
        val missingFifth = if (c.quality.intervals.contains(7) && (c.root + 7) % 12 !in heard) 1 else 0
        return low + (strings.size - played.size) * 3 + innerMutes * 4 + fingers + missingFifth * 2 +
            (if (fretted.isNotEmpty()) fretted.max() - fretted.min() else 0) - opens
    }

    // --- scales ---

    val SCALES = listOf(
        Scale("Major", listOf(0, 2, 4, 5, 7, 9, 11), listOf("R", "2", "3", "4", "5", "6", "7")),
        Scale("Natural minor", listOf(0, 2, 3, 5, 7, 8, 10), listOf("R", "2", "♭3", "4", "5", "♭6", "♭7")),
        Scale("Minor pentatonic", listOf(0, 3, 5, 7, 10), listOf("R", "♭3", "4", "5", "♭7")),
        Scale("Major pentatonic", listOf(0, 2, 4, 7, 9), listOf("R", "2", "3", "5", "6")),
        Scale("Blues", listOf(0, 3, 5, 6, 7, 10), listOf("R", "♭3", "4", "♭5", "5", "♭7")),
        Scale("Harmonic minor", listOf(0, 2, 3, 5, 7, 8, 11), listOf("R", "2", "♭3", "4", "5", "♭6", "7")),
        Scale("Melodic minor", listOf(0, 2, 3, 5, 7, 9, 11), listOf("R", "2", "♭3", "4", "5", "6", "7")),
        Scale("Dorian", listOf(0, 2, 3, 5, 7, 9, 10), listOf("R", "2", "♭3", "4", "5", "6", "♭7")),
        Scale("Phrygian", listOf(0, 1, 3, 5, 7, 8, 10), listOf("R", "♭2", "♭3", "4", "5", "♭6", "♭7")),
        Scale("Lydian", listOf(0, 2, 4, 6, 7, 9, 11), listOf("R", "2", "3", "#4", "5", "6", "7")),
        Scale("Mixolydian", listOf(0, 2, 4, 5, 7, 9, 10), listOf("R", "2", "3", "4", "5", "6", "♭7")),
        Scale("Locrian", listOf(0, 1, 3, 5, 6, 8, 10), listOf("R", "♭2", "♭3", "4", "♭5", "♭6", "♭7"))
    )

    /** Every place a scale's notes fall on the neck: (string, fret, degree label). */
    fun fretboard(root: Int, scale: Scale, tuning: Tuning = STANDARD, frets: Int = 15): List<Triple<Int, Int, String>> =
        tuning.strings.withIndex().flatMap { (s, openNote) ->
            (0..frets).mapNotNull { f ->
                val i = scale.intervals.indexOf(((openNote + f - root) % 12 + 12) % 12)
                if (i < 0) null else Triple(s, f, scale.degrees[i])
            }
        }

    /** The frets a position covers: from each of the scale's notes on the low string, five frets up. */
    fun positions(root: Int, scale: Scale, tuning: Tuning = STANDARD): List<IntRange> {
        val low = tuning.strings.first()
        val starts = (0..11).filter { f -> ((low + f - root) % 12 + 12) % 12 in scale.intervals }
        val fromRoot = starts.sortedBy { f -> ((f - ((root - low) % 12 + 12) % 12) + 12) % 12 }
        return fromRoot.take(scale.intervals.size.coerceAtMost(7)).map { it..(it + 4) }
    }

    /** The note names of a chord or scale from [root], in sharps or flats. */
    fun spell(root: Int, intervals: List<Int>, flats: Boolean = false): List<String> = intervals.map { name(root + it, flats) }

    // --- intervals, for ear training ---

    val INTERVALS = listOf(
        "Minor 2nd", "Major 2nd", "Minor 3rd", "Major 3rd", "Perfect 4th", "Tritone",
        "Perfect 5th", "Minor 6th", "Major 6th", "Minor 7th", "Major 7th", "Octave"
    )

    fun intervalName(semitones: Int): String = INTERVALS[(abs(semitones) - 1).coerceIn(0, 11)]
}
