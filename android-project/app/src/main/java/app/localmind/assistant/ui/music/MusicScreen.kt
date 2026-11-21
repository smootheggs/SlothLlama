package app.localmind.assistant.ui.music

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.music.Metronome
import app.localmind.assistant.domain.music.Heard
import app.localmind.assistant.domain.music.Horn
import app.localmind.assistant.domain.music.Music
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Music, under Learn: a tuner (guitar tunings, chromatic, and saxophones' written notes), a
 * metronome with a speed trainer, chords and their shapes, scales on the neck, your songbook,
 * and ear training. Everything is played and heard on the tablet.
 */
@Composable
fun MusicScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val openSong by viewModel.openSong.collectAsStateWithLifecycle()
    BackHandler(enabled = tab == MusicTab.SONGBOOK && openSong != null) { viewModel.openSong(null) }
    // Leaving Music stops the sound and the microphone.
    DisposableEffect(viewModel) {
        val vm = viewModel
        onDispose {
            vm.metronome.stop()
            vm.listener.stop()
            vm.player.stop()
        }
    }
    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        SlothTopBar(title = "Music", subtitle = "Played and heard on the tablet", onBack = onBack)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicTab.entries.forEach { t -> ChoicePill(t.label, tab == t, { viewModel.showTab(t) }) }
        }
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    MusicTab.TUNER -> TunerTab(viewModel)
                    MusicTab.METRONOME -> MetronomeTab(viewModel)
                    MusicTab.CHORDS -> ChordsTab(viewModel)
                    MusicTab.SCALES -> ScalesTab(viewModel)
                    MusicTab.SONGBOOK -> SongbookTab(viewModel, scroll)
                    MusicTab.EAR -> EarTab(viewModel)
                }
            }
        }
    }
}

/** A rounded card for one part of a tab. */
@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

// --- the tuner ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TunerTab(vm: MusicViewModel) {
    val context = LocalContext.current
    val heard by vm.listener.heard.collectAsStateWithLifecycle()
    val failed by vm.listener.failed.collectAsStateWithLifecycle()
    val tuning by vm.tuning.collectAsStateWithLifecycle()
    val chromatic by vm.chromatic.collectAsStateWithLifecycle()
    val horn by vm.horn.collectAsStateWithLifecycle()
    val a4 by vm.a4.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }
    LaunchedEffect(allowed) { if (allowed) vm.listener.start() }
    DisposableEffect(Unit) {
        val listener = vm.listener
        onDispose { listener.stop() }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Music.TUNINGS.forEach { t -> ChoicePill(t.name, !chromatic && horn == Horn.CONCERT && tuning == t, { vm.setHorn(Horn.CONCERT); vm.setTuning(t) }) }
        ChoicePill("Chromatic", chromatic || horn != Horn.CONCERT, { vm.setChromatic(true) })
    }
    Panel {
        if (!allowed) {
            Text("The tuner needs the microphone. It listens only while this page is open, and keeps nothing.", color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp)
            Button(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) { Text("Allow the microphone") }
        } else if (failed) {
            Text("The microphone didn't start. Another app may be using it.", color = P.Warning, fontSize = 15.sp)
            OutlinedButton(onClick = { vm.listener.start() }) { Text("Try again", color = P.Text) }
        } else {
            NoteReadout(heard, horn, if (chromatic || horn != Horn.CONCERT) null else tuning.strings)
        }
        if (!chromatic && horn == Horn.CONCERT) {
            val near = heard?.let { h -> tuning.strings.minByOrNull { abs(it - h.midi) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                tuning.strings.forEach { m ->
                    val on = m == near
                    Column(
                        Modifier.size(width = 64.dp, height = 60.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) P.AccentSoft else Color.Transparent)
                            .border(1.dp, if (on) P.Accent else P.Border, RoundedCornerShape(12.dp))
                            .clickable { vm.playNote(m) }
                            .semantics { contentDescription = "Play ${Music.noteName(m)}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(Music.name(m), color = P.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(String.format(java.util.Locale.ENGLISH, "%.1f", Music.frequency(m, a4.toDouble())), color = P.Faint, fontSize = 10.sp)
                    }
                }
            }
            Text("Tap a string to hear its note.", color = P.Faint, fontSize = 12.sp)
        }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("Saxophone", Modifier.weight(1f))
            Text("shows the note as written for your horn", color = P.Faint, fontSize = 12.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Horn.entries.forEach { h -> ChoicePill(h.label, horn == h, { vm.setHorn(h); if (h != Horn.CONCERT) vm.setChromatic(true) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A4 = $a4 Hz", color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.setA4(a4 - 1) }) { Text("−1", color = P.Accent) }
            TextButton(onClick = { vm.setA4(440) }) { Text("440", color = P.Muted) }
            TextButton(onClick = { vm.setA4(a4 + 1) }) { Text("+1", color = P.Accent) }
        }
    }
}

/** The note heard, large; how far off, on a needle; and which way to turn (or move the mouthpiece). */
@Composable
private fun NoteReadout(heard: Heard?, horn: Horn, strings: List<Int>?) {
    val accent = P.Accent
    val warn = P.Warning
    val tick = P.BorderStrong
    val text = P.Text
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (heard == null) {
            Text("—", color = P.Faint, fontSize = 84.sp, fontFamily = FontFamily.Serif)
            Text("Play a note", color = P.Faint, fontSize = 15.sp)
        } else {
            val shown = Music.written(heard.midi, horn)
            val inTune = abs(heard.cents) <= 5
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Music.name(shown), color = if (inTune) accent else text, fontSize = 84.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                Text("${shown / 12 - 1}", color = P.Faint, fontSize = 22.sp, modifier = Modifier.padding(bottom = 18.dp, start = 4.dp))
            }
            val c = heard.cents.roundToInt()
            val way = when {
                inTune -> "in tune"
                strings != null -> if (c < 0) "$c cents · tighten a little" else "+$c cents · loosen a little"
                horn != Horn.CONCERT -> if (c < 0) "$c cents · push the mouthpiece in" else "+$c cents · pull it out a little"
                else -> if (c < 0) "$c cents flat" else "+$c cents sharp"
            }
            Text(
                String.format(java.util.Locale.ENGLISH, "%.1f Hz · ", heard.hz) + way + if (horn != Horn.CONCERT) " · concert ${Music.name(heard.midi)}" else "",
                color = if (inTune) accent else warn, fontSize = 15.sp
            )
        }
        Canvas(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 16.dp)) {
            val w = size.width
            val mid = w / 2
            for (cents in -50..50 step 5) {
                val x = mid + cents / 50f * (w / 2)
                val big = cents % 25 == 0
                drawLine(if (cents == 0) accent else tick, Offset(x, if (big) 8f else 18f), Offset(x, 40f), if (cents == 0) 4f else 2f)
            }
            heard?.let { h ->
                val x = mid + (h.cents.toFloat().coerceIn(-50f, 50f) / 50f) * (w / 2)
                val color = if (abs(h.cents) <= 5) accent else warn
                drawRoundRect(color, Offset(minOf(x, mid), 48f), Size(abs(x - mid).coerceAtLeast(4f), 10f), CornerRadius(5f, 5f))
                drawLine(text, Offset(x, 0f), Offset(x, 60f), 6f)
            }
        }
    }
}

// --- the metronome ---

private fun tempoName(bpm: Int): String = when {
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetronomeTab(vm: MusicViewModel) {
    val b by vm.beat.collectAsStateWithLifecycle()
    val now by vm.metronome.beat.collectAsStateWithLifecycle()
    val playingBpm by vm.metronome.bpm.collectAsStateWithLifecycle()
    val running = now >= 0
    val taps = remember { mutableStateListOf<Long>() }
    val shownBpm = if (running && b.step > 0) playingBpm else b.bpm

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("$shownBpm", color = P.Text, fontSize = 64.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Column(Modifier.weight(1f)) {
                Text("BPM", color = P.Muted, fontSize = 13.sp)
                Text(tempoName(shownBpm), color = P.Faint, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(b.beats) { i ->
                    val lit = running && now == i
                    Box(
                        Modifier.size(if (b.beats > 6) 22.dp else 30.dp).clip(CircleShape)
                            .background(if (lit) (if (i == 0) P.Accent else P.AccentSoft) else P.Elevated)
                            .border(1.dp, if (lit || i == 0) P.Accent else P.Border, CircleShape)
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = vm::toggleMetronome, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) {
                Text(if (running) "Stop" else "Start")
            }
            OutlinedButton(onClick = {
                val t = System.currentTimeMillis()
                if (taps.isNotEmpty() && t - taps.last() > 2_000) taps.clear()
                taps += t
                while (taps.size > 5) taps.removeAt(0)
                if (taps.size >= 2) {
                    val gaps = taps.zipWithNext { x, y -> y - x }
                    vm.setBeat(b.copy(bpm = (60_000.0 / gaps.average()).roundToInt().coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)))
                }
            }) { Text("Tap", color = P.Text) }
            listOf(-5, -1, 1, 5).forEach { d ->
                OutlinedButton(onClick = { vm.setBeat(b.copy(bpm = (b.bpm + d).coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM))) }) {
                    Text(if (d > 0) "+$d" else "−${-d}", color = P.Text)
                }
            }
        }
    }
    Panel {
        SmallLabel("Beats in a bar")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..7).forEach { n -> ChoicePill("$n", b.beats == n, { vm.setBeat(b.copy(beats = n)) }) }
        }
        SmallLabel("Each beat split in")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Crotchets", 2 to "Quavers", 3 to "Triplets", 4 to "Semiquavers").forEach { (n, name) ->
                ChoicePill(name, b.subdivision == n, { vm.setBeat(b.copy(subdivision = n)) })
            }
        }
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Speed trainer", color = P.Text, fontSize = 16.sp)
                Text(
                    if (b.step > 0) "+${b.step} BPM every ${b.everyBars} bars, up to ${b.upTo}" else "Raises the tempo as you play, a little at a time",
                    color = P.Muted, fontSize = 13.sp
                )
            }
            Switch(
                checked = b.step > 0, onCheckedChange = { on -> vm.setBeat(b.copy(step = if (on) 4 else 0, upTo = maxOf(b.upTo, b.bpm + 4))) },
                colors = SwitchDefaults.colors(checkedTrackColor = P.Accent, checkedThumbColor = P.OnAccent)
            )
        }
        if (b.step > 0) {
            Stepper("Step", "+${b.step} BPM") { d -> vm.setBeat(b.copy(step = (b.step + d).coerceIn(1, 20))) }
            Stepper("Every", "${b.everyBars} bars") { d -> vm.setBeat(b.copy(everyBars = (b.everyBars + d).coerceIn(1, 32))) }
            Stepper("Up to", "${b.upTo} BPM") { d -> vm.setBeat(b.copy(upTo = (b.upTo + d * 5).coerceIn(b.bpm + 1, Metronome.MAX_BPM))) }
        }
    }
}

@Composable
internal fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P.Muted, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        Text(value, color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onStep(-1) }) { Text("−", color = P.Accent, fontSize = 18.sp) }
        TextButton(onClick = { onStep(1) }) { Text("+", color = P.Accent, fontSize = 18.sp) }
    }
}
