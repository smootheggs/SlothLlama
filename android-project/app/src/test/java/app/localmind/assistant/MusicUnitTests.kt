package app.localmind.assistant

import app.localmind.assistant.domain.music.Horn
import app.localmind.assistant.domain.music.Music
import app.localmind.assistant.domain.music.Piece
import app.localmind.assistant.domain.music.SheetLine
import app.localmind.assistant.domain.music.Song
import app.localmind.assistant.domain.music.Songs
import app.localmind.assistant.domain.music.Synth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 1.38: Music, all worked out on the tablet. */
class MusicUnitTests {

    private fun tone(hz: Double, n: Int = 4096, rate: Int = 44_100) =
        FloatArray(n) { i -> (0.5 * sin(2 * PI * hz * i / rate) + 0.2 * sin(4 * PI * hz * i / rate)).toFloat() }

    @Test
    fun `the tuner hears the right note`() {
        assertEquals(110.0, Music.pitch(tone(110.0), 44_100)!!, 0.5)
        assertEquals(329.63, Music.pitch(tone(329.63), 44_100)!!, 1.0)
        assertEquals(82.41, Music.pitch(tone(82.41), 44_100)!!, 0.5)
        assertNull(Music.pitch(FloatArray(4096), 44_100))
        val a = Music.heard(110.0)
        assertEquals(45, a.midi)
        assertEquals("A2", Music.noteName(a.midi))
        assertEquals(0.0, a.cents, 0.01)
        assertEquals(-6.3, Music.heard(109.6).cents, 0.2)
        assertEquals(440.0, Music.frequency(69), 1e-9)
    }

    @Test
    fun `a horn reads its own note`() {
        assertEquals("F#", Music.name(Music.written(45, Horn.ALTO)))  // concert A is an alto's F#
        assertEquals("B", Music.name(Music.written(57, Horn.TENOR)))  // and a tenor's B
        assertEquals("A", Music.name(Music.written(57, Horn.CONCERT)))
    }

    @Test
    fun `chord names are read`() {
        assertEquals(9, Music.chord("Am7")!!.root)
        assertEquals("m7", Music.chord("Am7")!!.quality.symbol)
        assertEquals("m7♭5", Music.chord("F#m7b5")!!.quality.symbol)
        assertEquals(7, Music.chord("Cadd9/G")!!.bass)
        assertEquals(6, Music.chord("D/F#")!!.bass)
        assertEquals("6/9", Music.chord("C6/9")!!.quality.symbol)
        assertEquals(10, Music.chord("Bbmaj7")!!.root)
        assertEquals("B♭maj7", Music.symbol(Music.chord("Bbmaj7")!!))
        assertNull(Music.chord("am"))
        assertNull(Music.chord("H"))
        assertNull(Music.chord("Cxyz"))
        assertEquals("A", Music.symbol(Music.transpose(Music.chord("G")!!, 2), flats = false))
        assertTrue(Music.flatKey(Music.chord("F")!!))
        assertTrue(Music.flatKey(Music.chord("Dm")!!))
        assertFalse(Music.flatKey(Music.chord("G")!!))
    }

    @Test
    fun `what's held is named`() {
        fun name(frets: List<Int>) = Music.nameOf(frets)?.let { Music.symbol(it) }
        assertEquals("C", name(listOf(-1, 3, 2, 0, 1, 0)))
        assertEquals("Am7", name(listOf(-1, 0, 2, 0, 1, 0)))
        assertEquals("E", name(listOf(0, 2, 2, 1, 0, 0)))
        assertEquals("G", name(listOf(3, 2, 0, 0, 0, 3)))
        assertEquals("D", name(listOf(-1, -1, 0, 2, 3, 2)))
        assertEquals("C/E", name(listOf(0, 3, 2, 0, 1, 0)))
        assertNull(name(listOf(-1, -1, -1, -1, -1, 0)))
    }

    @Test
    fun `shapes are found for any chord`() {
        fun shapes(s: String) = Music.voicings(Music.chord(s)!!).map { it.text }
        assertTrue(shapes("C").take(2).contains("x32010"))
        assertEquals("x02010", shapes("Am7").first())
        assertEquals("320003", shapes("G").first())
        assertTrue(shapes("D").contains("xx0232"))
        assertTrue(shapes("F").contains("133211"))
        // Every shape's lowest note is the bass the name asks for.
        Music.voicings(Music.chord("D/F#")!!).forEach { v ->
            val low = v.frets.withIndex().filter { it.value >= 0 }.minOf { Music.STANDARD.strings[it.index] + it.value }
            assertEquals(6, low % 12)
        }
    }

    @Test
    fun `scales sit on the neck`() {
        val pent = Music.SCALES.first { it.name == "Minor pentatonic" }
        val notes = Music.fretboard(9, pent)
        assertTrue(Triple(0, 5, "R") in notes)          // A on the low E string, 5th fret
        assertTrue(Triple(1, 0, "R") in notes)          // the open A string
        assertFalse(notes.any { it.first == 0 && it.second == 6 })
        assertEquals(5..9, Music.positions(9, pent).first())
        assertEquals(listOf("C", "D", "E", "F", "G", "A", "B"), Music.spell(0, Music.SCALES.first().intervals))
        assertEquals("Perfect 5th", Music.intervalName(7))
    }

    @Test
    fun `chord sheets are read both ways`() {
        assertEquals(listOf(Piece("G", "Hello "), Piece("Em", "there")), Songs.chordPro("[G]Hello [Em]there"))
        assertEquals(listOf(Piece(null, "Oh "), Piece("C", "yes")), Songs.chordPro("Oh [C]yes"))
        assertEquals(listOf(Piece("G", "Hello "), Piece("Em", "there")), Songs.overWords("G     Em", "Hello there"))
        assertTrue(Songs.isChordLine("G   Em  C  D"))
        assertTrue(Songs.isChordLine("| G | C |"))
        assertFalse(Songs.isChordLine("A day by the sea"))
        val sheet = """
            {title: Practice Song}
            [Verse 1]
            G     Em
            Hello there
            C     D
            More words here


            Chorus:
            [G]Sing [D]along
        """.trimIndent()
        val lines = Songs.parse(sheet)
        assertEquals(SheetLine.Label("Verse 1"), lines[0])
        assertEquals(SheetLine.Sung(listOf(Piece("G", "Hello "), Piece("Em", "there"))), lines[1])
        assertEquals(1, lines.count { it is SheetLine.Blank }) // two blank lines become one
        assertEquals(SheetLine.Label("Chorus"), lines[4])
        assertEquals("Practice Song", Songs.titleOf(sheet))
        assertEquals(listOf("A", "F#m", "D", "E"), Songs.chordsUsed(lines, 2))
        assertEquals(listOf("F", "Dm", "B♭", "C"), Songs.chordsUsed(lines, -2))
        assertEquals("G", Songs.moved("G", 0, false))
        assertEquals("Untitled", Songs.titleOf("G C D"))
    }

    @Test
    fun `songs round-trip, and a backup only adds what's missing`() {
        val s = Song("s1", "One", "[G]la", 5, shift = 2, capo = 1, scroll = 75)
        assertEquals(listOf(s), Songs.decode(Songs.encode(listOf(s))))
        assertEquals(listOf("Mine", "One"), Songs.merge(listOf(s.copy(title = "Mine")), listOf(s, s.copy(id = "s2"))).map { it.title })
    }

    @Test
    fun `sounds are made, and never clip`() {
        assertEquals(44_100, Synth.pluck(110.0, 1.0).size)
        assertEquals(44_100 * 28 / 1000, Synth.click(1000.0).size)
        val chord = Synth.strum(listOf(40, 45, 50, 55, 59, 64))
        assertNotNull(chord)
        assertTrue(chord.maxOf { abs(it) } <= 0.51f)
        assertTrue(Synth.mix(emptyList()).isEmpty())
    }
}
