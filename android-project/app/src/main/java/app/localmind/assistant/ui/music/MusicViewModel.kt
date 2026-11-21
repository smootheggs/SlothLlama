package app.localmind.assistant.ui.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.music.Beat
import app.localmind.assistant.data.music.Metronome
import app.localmind.assistant.data.music.PitchListener
import app.localmind.assistant.data.music.SongBook
import app.localmind.assistant.data.music.SoundPlayer
import app.localmind.assistant.domain.music.Chord
import app.localmind.assistant.domain.music.Horn
import app.localmind.assistant.domain.music.Music
import app.localmind.assistant.domain.music.Song
import app.localmind.assistant.domain.music.Songs
import app.localmind.assistant.domain.music.Synth
import app.localmind.assistant.domain.music.Tuning
import app.localmind.assistant.domain.music.Voicing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class MusicTab(val label: String) { TUNER("Tuner"), METRONOME("Metronome"), CHORDS("Chords"), SCALES("Scales"), SONGBOOK("Songbook"), EAR("Ear") }

enum class EarKind(val label: String) { INTERVALS("Intervals"), CHORDS("Chords") }

/** One ear-training question: the notes played and the right answer among the choices. */
data class EarQuestion(val kind: EarKind, val notes: List<Int>, val answer: String, val choices: List<String>, val together: Boolean)

/**
 * Music: the tuner, metronome, chords, scales, songbook and ear training. Sound is made and heard
 * on the tablet; the songbook is kept (in backups), your settings too, and nothing else.
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val book: SongBook
) : ViewModel() {

    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)

    val player = SoundPlayer(viewModelScope)
    val metronome = Metronome(viewModelScope)
    val listener = PitchListener(viewModelScope)

    private val _tab = MutableStateFlow(MusicTab.entries.getOrNull(prefs.getInt("tab", 0)) ?: MusicTab.TUNER)
    val tab: StateFlow<MusicTab> = _tab.asStateFlow()

    private val _a4 = MutableStateFlow(prefs.getInt("a4", 440).coerceIn(415, 466))
    val a4: StateFlow<Int> = _a4.asStateFlow()

    private val _horn = MutableStateFlow(Horn.entries.firstOrNull { it.name == prefs.getString("horn", null) } ?: Horn.CONCERT)
    val horn: StateFlow<Horn> = _horn.asStateFlow()

    private val _tuning = MutableStateFlow(Music.TUNINGS.firstOrNull { it.name == prefs.getString("tuning", null) } ?: Music.STANDARD)
    val tuning: StateFlow<Tuning> = _tuning.asStateFlow()

    /** Guitar strings on the tuner, or chromatic (any note). */
    private val _chromatic = MutableStateFlow(prefs.getBoolean("chromatic", false))
    val chromatic: StateFlow<Boolean> = _chromatic.asStateFlow()

    private val _beat = MutableStateFlow(
        Beat(
            bpm = prefs.getInt("bpm", 92), beats = prefs.getInt("beats", 4), subdivision = prefs.getInt("sub", 1),
            step = prefs.getInt("step", 0), everyBars = prefs.getInt("every", 4), upTo = prefs.getInt("upto", 120)
        )
    )
    val beat: StateFlow<Beat> = _beat.asStateFlow()

    val songs: StateFlow<List<Song>> = book.songs

    private val _openSong = MutableStateFlow<String?>(null)
    val openSong: StateFlow<String?> = _openSong.asStateFlow()

    private val _shapes = MutableStateFlow<List<Voicing>>(emptyList())
    /** Shapes for the chord asked about, worked out off the main thread. */
    val shapes: StateFlow<List<Voicing>> = _shapes.asStateFlow()

    private val _ear = MutableStateFlow<EarQuestion?>(null)
    val ear: StateFlow<EarQuestion?> = _ear.asStateFlow()

    /** This sitting's ear-training tally: (right, asked). Not kept. */
    private val _tally = MutableStateFlow(0 to 0)
    val tally: StateFlow<Pair<Int, Int>> = _tally.asStateFlow()

    private val _answered = MutableStateFlow<String?>(null)
    val answered: StateFlow<String?> = _answered.asStateFlow()

    init {
        viewModelScope.launch { book.load() }
        listener.a4 = _a4.value.toDouble()
        metronome.update(_beat.value)
    }

    fun showTab(t: MusicTab) {
        _tab.value = t
        prefs.edit().putInt("tab", t.ordinal).apply()
        if (t != MusicTab.TUNER) listener.stop()
    }

    fun setA4(hz: Int) {
        val v = hz.coerceIn(415, 466)
        _a4.value = v
        listener.a4 = v.toDouble()
        prefs.edit().putInt("a4", v).apply()
    }

    fun setHorn(h: Horn) {
        _horn.value = h
        prefs.edit().putString("horn", h.name).apply()
    }

    fun setTuning(t: Tuning) {
        _tuning.value = t
        _chromatic.value = false
        prefs.edit().putString("tuning", t.name).putBoolean("chromatic", false).apply()
    }

    fun setChromatic(on: Boolean) {
        _chromatic.value = on
        prefs.edit().putBoolean("chromatic", on).apply()
    }

    // --- sound ---

    fun playNote(midi: Int, seconds: Double = 2.0) = player.play(Synth.pluck(Music.frequency(midi, _a4.value.toDouble()), seconds))

    fun playShape(v: Voicing, tuning: Tuning = _tuning.value) {
        val notes = v.frets.withIndex().filter { it.value >= 0 }.map { tuning.strings[it.index] + it.value }
        player.play(Synth.strum(notes, a4 = _a4.value.toDouble()))
    }

    fun playNotes(midis: List<Int>, gapMs: Int) = player.play(Synth.sequence(midis, gapMs, _a4.value.toDouble()))

    // --- metronome ---

    fun setBeat(b: Beat) {
        _beat.value = b
        metronome.update(b)
        prefs.edit().putInt("bpm", b.bpm).putInt("beats", b.beats).putInt("sub", b.subdivision)
            .putInt("step", b.step).putInt("every", b.everyBars).putInt("upto", b.upTo).apply()
    }

    fun toggleMetronome() {
        if (metronome.running) metronome.stop() else metronome.start(_beat.value)
    }

    // --- chords ---

    fun findShapes(c: Chord?) {
        viewModelScope.launch {
            _shapes.value = if (c == null) emptyList() else withContext(Dispatchers.Default) { Music.voicings(c, _tuning.value) }
        }
    }

    // --- songbook ---

    fun openSong(id: String?) {
        _openSong.value = id
    }

    fun addSong(text: String, title: String) {
        val clean = text.trim().take(MAX_SONG)
        if (clean.isEmpty()) return
        val song = Song(UUID.randomUUID().toString(), title.trim().take(80).ifEmpty { Songs.titleOf(clean) }, clean, System.currentTimeMillis())
        viewModelScope.launch {
            book.save(song)
            _openSong.value = song.id
        }
    }

    fun saveSong(song: Song) = viewModelScope.launch { book.save(song) }

    fun deleteSong(id: String) = viewModelScope.launch {
        if (_openSong.value == id) _openSong.value = null
        book.delete(id)
    }

    // --- ear training ---

    fun nextQuestion(kind: EarKind, all: Boolean) {
        _answered.value = null
        val root = Random.nextInt(48, 65)
        val q = when (kind) {
            EarKind.INTERVALS -> {
                val pool = if (all) (1..12).toList() else listOf(3, 4, 5, 7, 12)
                val iv = pool.random()
                EarQuestion(kind, listOf(root, root + iv), Music.intervalName(iv), pool.map { Music.intervalName(it) }, together = false)
            }
            EarKind.CHORDS -> {
                val pool = if (all) listOf("", "m", "7", "maj7", "m7", "dim", "aug", "sus4") else listOf("", "m", "7", "dim")
                val sym = pool.random()
                val quality = Music.QUALITIES.first { it.symbol == sym }
                val names = pool.map { chordKindName(it) }
                EarQuestion(kind, quality.intervals.sorted().map { root + it }, chordKindName(sym), names, together = true)
            }
        }
        _ear.value = q
        play(q)
    }

    fun play(q: EarQuestion) = playNotes(q.notes, if (q.together) 18 else 650)

    fun answer(choice: String) {
        val q = _ear.value ?: return
        if (_answered.value != null) return
        _answered.value = choice
        val (right, asked) = _tally.value
        _tally.value = (right + if (choice == q.answer) 1 else 0) to asked + 1
    }

    override fun onCleared() {
        metronome.stop()
        listener.stop()
        player.stop()
    }

    companion object {
        const val MAX_SONG = 20_000

        fun chordKindName(symbol: String): String = when (symbol) {
            "" -> "Major"
            "m" -> "Minor"
            "7" -> "Dominant 7th"
            "maj7" -> "Major 7th"
            "m7" -> "Minor 7th"
            "dim" -> "Diminished"
            "aug" -> "Augmented"
            "sus4" -> "Suspended 4th"
            else -> symbol
        }
    }
}
