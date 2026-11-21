package app.localmind.assistant.domain.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A song you pasted: its chord sheet as written, and how you play it (shifted, with a capo). */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: Long,
    /** Semitones up (or down) from the sheet as written. */
    val shift: Int = 0,
    /** The capo's fret; the shapes shown are the sounding chords moved down by it. */
    val capo: Int = 0,
    /** Seconds per screen when it scrolls by itself (0: it doesn't). */
    val scroll: Int = 0
)

@Serializable
data class SongFile(val format: Int = 1, val songs: List<Song> = emptyList())

/** A piece of a line: the chord over it (as written), and the words under it. */
data class Piece(val chord: String?, val words: String)

sealed interface SheetLine {
    /** Words with chords over them (or chords alone, when [pieces] have no words). */
    data class Sung(val pieces: List<Piece>) : SheetLine
    /** A label: "Chorus", "Verse 2", a {comment}. */
    data class Label(val text: String) : SheetLine
    data object Blank : SheetLine
}

/**
 * Chord sheets, read on the tablet: chords over words (the usual paste), or ChordPro ("[G]Words
 * [Em]more"). Chords are moved by transposing on the fly; the words are never changed.
 */
object Songs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val BRACKET = Regex("""\[([^\]\s]{1,16})]""")
    private val DIRECTIVE = Regex("""^\{\s*([a-z_]+)\s*(?::\s*(.*?))?\s*}$""", RegexOption.IGNORE_CASE)
    private val SECTION = Regex("""^\[?\s*(intro|verse|chorus|pre-chorus|bridge|outro|solo|interlude|refrain|coda|hook)(\s*\d*)\s*]?:?$""", RegexOption.IGNORE_CASE)
    private val EXTRAS = setOf("|", "||", "-", "/", "x2", "x3", "x4", "(x2)", "(x3)", "(x4)", "%", "N.C.", "NC")

    fun isChord(token: String): Boolean = Music.chord(token.trim('(', ')', ',')) != null

    /** A line of chords only: every token a chord (or a bar line, a repeat mark). */
    fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return tokens.isNotEmpty() && tokens.any { isChord(it) } && tokens.all { isChord(it) || it in EXTRAS }
    }

    fun parse(text: String): List<SheetLine> {
        val lines = text.replace("\r\n", "\n").replace('\t', ' ').lines()
        val out = mutableListOf<SheetLine>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].trimEnd()
            val t = raw.trim()
            when {
                t.isEmpty() -> out += SheetLine.Blank
                DIRECTIVE.matches(t) -> {
                    val m = DIRECTIVE.matchEntire(t)!!
                    val key = m.groupValues[1].lowercase()
                    val value = m.groupValues[2]
                    when (key) {
                        "c", "comment", "ci", "comment_italic" -> if (value.isNotBlank()) out += SheetLine.Label(value)
                        "soc", "start_of_chorus" -> out += SheetLine.Label("Chorus")
                        "sov", "start_of_verse" -> out += SheetLine.Label(value.ifBlank { "Verse" })
                        "sob", "start_of_bridge" -> out += SheetLine.Label("Bridge")
                        else -> Unit // title, artist, key, eoc… are kept in the song, not drawn
                    }
                }
                SECTION.matches(t) -> out += SheetLine.Label(t.trim('[', ']', ':', ' ').replaceFirstChar { it.uppercase() })
                BRACKET.containsMatchIn(raw) && BRACKET.findAll(raw).any { isChord(it.groupValues[1]) } -> out += SheetLine.Sung(chordPro(raw))
                isChordLine(raw) -> {
                    val next = lines.getOrNull(i + 1)?.trimEnd()
                    if (next != null && next.isNotBlank() && !isChordLine(next) && !DIRECTIVE.matches(next.trim()) && !SECTION.matches(next.trim())) {
                        out += SheetLine.Sung(overWords(raw, next))
                        i++
                    } else {
                        out += SheetLine.Sung(overWords(raw, ""))
                    }
                }
                else -> out += SheetLine.Sung(listOf(Piece(null, raw.trim())))
            }
            i++
        }
        // No more than one blank line in a row.
        return out.filterIndexed { j, l -> !(l is SheetLine.Blank && out.getOrNull(j - 1) is SheetLine.Blank) }
    }

    /** "[G]Words [Em]more" → (null, ""), (G, "Words "), (Em, "more"). */
    fun chordPro(line: String): List<Piece> {
        val pieces = mutableListOf<Piece>()
        var last = 0
        var chord: String? = null
        for (m in BRACKET.findAll(line)) {
            if (!isChord(m.groupValues[1])) continue
            val words = line.substring(last, m.range.first)
            if (chord != null || words.isNotEmpty()) pieces += Piece(chord, words)
            chord = m.groupValues[1]
            last = m.range.last + 1
        }
        pieces += Piece(chord, line.substring(last))
        return pieces.filter { it.chord != null || it.words.isNotBlank() }
    }

    /** Chords over words: each chord takes the words from its column to the next chord's. */
    fun overWords(chords: String, words: String): List<Piece> {
        val marks = Regex("""\S+""").findAll(chords).filter { isChord(it.value) }.map { it.range.first to it.value.trim('(', ')', ',') }.toList()
        if (marks.isEmpty()) return listOf(Piece(null, words))
        val pieces = mutableListOf<Piece>()
        val firstCol = marks.first().first
        if (firstCol > 0 && words.length > 0) {
            val lead = words.substring(0, minOf(firstCol, words.length))
            if (lead.isNotBlank()) pieces += Piece(null, lead)
        }
        marks.forEachIndexed { k, (col, chord) ->
            val end = marks.getOrNull(k + 1)?.first ?: Int.MAX_VALUE
            val text = if (col >= words.length) "" else words.substring(col, minOf(end, words.length))
            pieces += Piece(chord, text)
        }
        return pieces
    }

    /** The sheet's chord as you'll play it: moved by [semitones], spelled for the key it lands in. */
    fun moved(chord: String, semitones: Int, flats: Boolean): String {
        if (semitones % 12 == 0) return chord
        val c = Music.chord(chord) ?: return chord
        return Music.symbol(Music.transpose(c, semitones), flats)
    }

    /** The first chord of the sheet, which usually names its key. */
    fun firstChord(lines: List<SheetLine>): Chord? = lines.asSequence()
        .filterIsInstance<SheetLine.Sung>().flatMap { it.pieces.asSequence() }
        .mapNotNull { p -> p.chord?.let { Music.chord(it) } }.firstOrNull()

    /** Whether the chords, moved by [semitones], are best written with flats. */
    fun flatsAfter(lines: List<SheetLine>, semitones: Int): Boolean =
        firstChord(lines)?.let { Music.flatKey(Music.transpose(it, semitones)) } ?: false

    /** The song's title: a {title:} line, else its first line of words. */
    fun titleOf(text: String): String {
        val lines = text.lines().map { it.trim() }
        lines.firstNotNullOfOrNull { l -> DIRECTIVE.matchEntire(l)?.takeIf { it.groupValues[1].lowercase() in setOf("title", "t") }?.groupValues?.get(2) }
            ?.takeIf { it.isNotBlank() }?.let { return it.take(80) }
        return lines.firstOrNull { it.isNotEmpty() && !isChordLine(it) && !DIRECTIVE.matches(it) && !SECTION.matches(it) }
            ?.replace(BRACKET, "")?.trim()?.take(80)?.ifBlank { null } ?: "Untitled"
    }

    /** All the different chords the sheet uses, in order of first use, moved by [semitones]. */
    fun chordsUsed(lines: List<SheetLine>, semitones: Int): List<String> {
        val flats = flatsAfter(lines, semitones)
        return lines.filterIsInstance<SheetLine.Sung>().flatMap { it.pieces }.mapNotNull { it.chord }
            .map { moved(it, semitones, flats) }.distinct()
    }

    fun encode(songs: List<Song>): String = json.encodeToString(SongFile.serializer(), SongFile(songs = songs))

    fun decode(text: String): List<Song> = json.decodeFromString(SongFile.serializer(), text).songs

    fun merge(mine: List<Song>, theirs: List<Song>): List<Song> {
        val have = mine.map { it.id }.toSet()
        return mine + theirs.filter { it.id !in have }
    }
}
