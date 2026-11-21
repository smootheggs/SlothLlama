package app.localmind.assistant.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.music.Music
import app.localmind.assistant.domain.music.Tuning
import app.localmind.assistant.domain.music.Voicing
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.theme.SlothPalette as P

// --- chords ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChordsTab(vm: MusicViewModel) {
    val tuning by vm.tuning.collectAsStateWithLifecycle()
    val shapes by vm.shapes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("Am7") }
    val chord = remember(query) { Music.chord(query) }
    LaunchedEffect(chord, tuning) { vm.findShapes(chord) }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(16) }, label = { Text("Chord") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border, focusedTextColor = P.Text, unfocusedTextColor = P.Text,
                    cursorColor = P.Accent, focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted
                ),
                modifier = Modifier.width(170.dp)
            )
            Column(Modifier.weight(1f)) {
                if (chord == null) {
                    Text(if (query.isBlank()) "Type a chord: Am7, F#m7b5, Cadd9/G" else "Not a chord name I know", color = P.Faint, fontSize = 13.sp)
                } else {
                    Text(Music.spell(chord.root, chord.quality.intervals.sorted(), flats = Music.flatKey(chord)).joinToString("  "), color = P.Text, fontSize = 16.sp)
                    Text(
                        "Shapes are worked out from the notes" + if (tuning != Music.STANDARD) ", in ${tuning.name}" else "",
                        color = P.Faint, fontSize = 12.sp
                    )
                }
            }
        }
        if (chord != null) {
            if (shapes.isEmpty()) Text("No comfortable shape within four frets.", color = P.Faint, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shapes.forEach { v ->
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.playShape(v, tuning) }.semantics { contentDescription = "${query} as ${v.text}; tap to hear it" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ChordDiagram(v)
                        Text(v.text, color = P.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Text("Tap a shape to hear it.", color = P.Faint, fontSize = 12.sp)
        }
    }
    NameThisChord(vm, tuning)
}

/** A chord box: strings up, frets across; dots where to press, ○ open, × muted; the fret number beside it up the neck. */
@Composable
internal fun ChordDiagram(v: Voicing, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val line = P.BorderStrong
    val dot = P.Accent
    val faint = P.Faint
    val text = P.Text
    val base = if (v.highestFret <= 4) 1 else v.lowestFret
    Canvas(modifier.size(width = 120.dp, height = 150.dp)) {
        val left = 26.dp.toPx()
        val top = 26.dp.toPx()
        val gap = (size.width - left - 8.dp.toPx()) / 5
        val fh = (size.height - top - 8.dp.toPx()) / 5
        if (base == 1) drawRect(text, Offset(left - 1f, top - 5f), Size(gap * 5 + 2f, 5f))
        else drawText(measurer, "${base}fr", Offset(2f, top + fh * 0.2f), TextStyle(color = faint, fontSize = 11.sp))
        for (i in 0..5) drawLine(line, Offset(left + i * gap, top), Offset(left + i * gap, top + fh * 5), 2f)
        for (j in 0..5) drawLine(line, Offset(left, top + j * fh), Offset(left + gap * 5, top + j * fh), 2f)
        v.frets.forEachIndexed { s, f ->
            val x = left + s * gap
            when {
                f < 0 -> drawText(measurer, "×", Offset(x - 5.dp.toPx(), 2f), TextStyle(color = faint, fontSize = 13.sp))
                f == 0 -> drawCircle(faint, 6.dp.toPx() / 1.6f, Offset(x, top - 13.dp.toPx()), style = Stroke(2f))
                else -> drawCircle(dot, 8.dp.toPx(), Offset(x, top + (f - base + 0.5f) * fh))
            }
        }
    }
}

/** Tap frets on an empty grid; the tablet names what you're holding. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameThisChord(vm: MusicViewModel, tuning: Tuning) {
    var frets by remember { mutableStateOf(listOf(-1, 3, 2, 0, 1, 0)) }
    var base by remember { mutableIntStateOf(1) }
    val named = remember(frets, tuning) { Music.nameOf(frets, tuning) }
    val measurer = rememberTextMeasurer()
    val line = P.BorderStrong
    val dot = P.Accent
    val faint = P.Faint
    val text = P.Text
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("Name this chord", Modifier.weight(1f))
            TextButton(onClick = { frets = List(6) { -1 } }) { Text("Clear", color = P.Muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier.size(width = 200.dp, height = 220.dp).pointerInput(base, frets) {
                    detectTapGestures { p ->
                        val left = 30.dp.toPx()
                        val top = 34.dp.toPx()
                        val gap = (size.width - left - 12.dp.toPx()) / 5
                        val fh = (size.height - top - 8.dp.toPx()) / 5
                        val s = ((p.x - left + gap / 2) / gap).toInt().coerceIn(0, 5)
                        val next = frets.toMutableList()
                        if (p.y < top) {
                            // Above the nut: open, then muted.
                            next[s] = if (next[s] == 0) -1 else 0
                        } else {
                            val f = base + ((p.y - top) / fh).toInt().coerceIn(0, 4)
                            next[s] = if (next[s] == f) 0 else f
                        }
                        frets = next
                    }
                }
            ) {
                val left = 30.dp.toPx()
                val top = 34.dp.toPx()
                val gap = (size.width - left - 12.dp.toPx()) / 5
                val fh = (size.height - top - 8.dp.toPx()) / 5
                if (base == 1) drawRect(text, Offset(left - 1f, top - 5f), Size(gap * 5 + 2f, 5f))
                else drawText(measurer, "${base}fr", Offset(0f, top + fh * 0.2f), TextStyle(color = faint, fontSize = 12.sp))
                for (i in 0..5) drawLine(line, Offset(left + i * gap, top), Offset(left + i * gap, top + fh * 5), 2f)
                for (j in 0..5) drawLine(line, Offset(left, top + j * fh), Offset(left + gap * 5, top + j * fh), 2f)
                frets.forEachIndexed { s, f ->
                    val x = left + s * gap
                    when {
                        f < 0 -> drawText(measurer, "×", Offset(x - 6.dp.toPx(), 4f), TextStyle(color = faint, fontSize = 15.sp))
                        f == 0 -> drawCircle(faint, 6.dp.toPx(), Offset(x, top - 15.dp.toPx()), style = Stroke(2.5f))
                        f in base..(base + 4) -> drawCircle(dot, 11.dp.toPx(), Offset(x, top + (f - base + 0.5f) * fh))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(named?.let { Music.symbol(it) } ?: "—", color = if (named != null) P.Accent else P.Faint, fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                Text(Voicing(frets).text, color = P.Faint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                val notes = frets.withIndex().filter { it.value >= 0 }.map { Music.name(tuning.strings[it.index] + it.value) }
                Text(notes.joinToString(" "), color = P.Muted, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Frets from", color = P.Muted, fontSize = 13.sp)
                    TextButton(onClick = { base = (base - 1).coerceAtLeast(1) }) { Text("−", color = P.Accent, fontSize = 18.sp) }
                    Text("$base", color = P.Text, fontSize = 15.sp)
                    TextButton(onClick = { base = (base + 1).coerceAtMost(15) }) { Text("+", color = P.Accent, fontSize = 18.sp) }
                }
                OutlinedButton(onClick = { vm.playShape(Voicing(frets), tuning) }, enabled = frets.any { it >= 0 }) { Text("Hear it", color = P.Text) }
            }
        }
        Text("Tap a fret to press it, tap again to lift it; tap above the nut for open or muted.", color = P.Faint, fontSize = 12.sp)
    }
}

// --- scales ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScalesTab(vm: MusicViewModel) {
    val tuning by vm.tuning.collectAsStateWithLifecycle()
    var root by remember { mutableIntStateOf(9) }
    var scaleIndex by remember { mutableIntStateOf(2) }
    var position by remember { mutableIntStateOf(0) }
    val scale = Music.SCALES[scaleIndex]
    val positions = remember(root, scaleIndex, tuning) { Music.positions(root, scale, tuning) }
    val window = positions.getOrNull(position - 1)
    val flats = root in setOf(5, 10, 3, 8, 1, 6) && scale.intervals.contains(4) || root in setOf(2, 7, 0, 5, 10, 3) && scale.intervals.contains(3)

    Panel {
        SmallLabel("Key")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..11).forEach { pc -> ChoicePill(Music.COMMON[pc], root == pc, { root = pc; position = 0 }) }
        }
        SmallLabel("Scale")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Music.SCALES.forEachIndexed { i, s -> ChoicePill(s.name, scaleIndex == i, { scaleIndex = i; position = 0 }) }
        }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Music.COMMON[root]} ${scale.name.lowercase()}", color = P.Text, fontSize = 20.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val low = tuning.strings.first() + 12
                val start = low + ((root - low) % 12 + 12) % 12
                vm.playNotes(scale.intervals.map { start + it } + (start + 12), 330)
            }) { Text("Hear it", color = P.Text) }
        }
        Text(Music.spell(root, scale.intervals, flats).joinToString("  ") + "   ·   " + scale.degrees.joinToString(" "), color = P.Muted, fontSize = 14.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Whole neck", position == 0, { position = 0 })
            positions.forEachIndexed { i, r -> ChoicePill("From fret ${r.first}", position == i + 1, { position = i + 1 }) }
        }
        Fretboard(root, scaleIndex, tuning, window)
        Text("R is the root. Pick a position to see five frets of it; the rest go faint.", color = P.Faint, fontSize = 12.sp)
    }
}

/** The neck, frets 0–15, with the scale's notes as dots labelled by degree. */
@Composable
private fun Fretboard(root: Int, scaleIndex: Int, tuning: Tuning, window: IntRange?) {
    val scale = Music.SCALES[scaleIndex]
    val notes = remember(root, scaleIndex, tuning) { Music.fretboard(root, scale, tuning) }
    val measurer = rememberTextMeasurer()
    val line = P.BorderStrong
    val nut = P.Text
    val faint = P.Faint
    val accent = P.Accent
    val soft = P.AccentSoft
    val elevated = P.Elevated
    val onAccent = P.OnAccent
    val text = P.Text
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val left = 22.dp.toPx()
        val right = 8.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = 24.dp.toPx()
        val frets = 15
        val fw = (size.width - left - right) / (frets + 0.6f)
        val sh = (size.height - top - bottom) / 5
        fun xOf(f: Int) = if (f == 0) left - fw * 0.35f else left + (f - 0.5f) * fw
        fun yOf(s: Int) = top + (5 - s) * sh
        for (s in 0..5) drawLine(line, Offset(left, yOf(s)), Offset(size.width - right, yOf(s)), 1.5f)
        for (f in 0..frets) drawLine(if (f == 0) nut else line, Offset(left + f * fw, top), Offset(left + f * fw, top + 5 * sh), if (f == 0) 5f else 1.5f)
        listOf(3, 5, 7, 9, 12, 15).forEach { f ->
            drawText(measurer, "$f", Offset(xOf(f) - 5.dp.toPx(), top + 5 * sh + 4.dp.toPx()), TextStyle(color = faint, fontSize = 10.sp))
        }
        val r = minOf(fw, sh) * 0.36f
        notes.forEach { (s, f, degree) ->
            val inside = window == null || f in window
            val isRoot = degree == "R"
            val fill = when {
                !inside -> elevated
                isRoot -> accent
                else -> soft
            }
            drawCircle(fill, r, Offset(xOf(f), yOf(s)))
            if (inside && !isRoot) drawCircle(accent, r, Offset(xOf(f), yOf(s)), style = Stroke(1.5f))
            val label = measurer.measure(degree, TextStyle(color = if (isRoot && inside) onAccent else if (inside) text else faint, fontSize = 9.sp, fontWeight = FontWeight.Medium))
            drawText(label, topLeft = Offset(xOf(f) - label.size.width / 2f, yOf(s) - label.size.height / 2f))
        }
    }
}
