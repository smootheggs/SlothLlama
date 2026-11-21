package app.localmind.assistant.ui.music

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.music.Music
import app.localmind.assistant.domain.music.SheetLine
import app.localmind.assistant.domain.music.Song
import app.localmind.assistant.domain.music.Songs
import app.localmind.assistant.domain.music.Voicing
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.ConfirmDialog
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.EmptyLine
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.common.NameDialog
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// --- the songbook ---

@Composable
internal fun SongbookTab(vm: MusicViewModel, scroll: ScrollState) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val openId by vm.openSong.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.id == openId }
    if (song != null) SongView(song, vm, scroll) else SongList(songs, vm)
}

@Composable
private fun SongList(songs: List<Song>, vm: MusicViewModel) {
    var pasting by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("Your songbook", Modifier.weight(1f))
            Button(onClick = { pasting = true }, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) { Text("Paste a song") }
        }
        Text(
            "Paste a chord sheet: chords over the words, or ChordPro. Move it to another key, put a capo on, and let it scroll " +
                "while you play. Kept on the tablet, in backups.",
            color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
        )
        if (songs.isEmpty()) EmptyLine("No songs yet.")
        songs.forEach { s ->
            val chords = remember(s.text) { Songs.chordsUsed(Songs.parse(s.text), 0) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.openSong(s.id) }.padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = P.Text, fontSize = 16.sp, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chords.take(8).joinToString("  "), color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.capo > 0) Text("capo ${s.capo}", color = P.Muted, fontSize = 12.sp)
            }
        }
    }
    if (pasting) PasteSong(onDismiss = { pasting = false }) { text, title -> vm.addSong(text, title) }
}

@Composable
private fun PasteSong(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    EditDialog("Paste a song", onDismiss, { onSave(text, title); onDismiss() }, canSave = text.isNotBlank(), saveLabel = "Add") {
        TextButton(onClick = {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it.take(MusicViewModel.MAX_SONG) }
        }) { Text("Paste from the clipboard", color = P.Accent) }
        FormField("The sheet", text, { text = it.take(MusicViewModel.MAX_SONG) }, lines = 8)
        FormField("Title (optional)", title, { title = it.take(80) }, hint = if (text.isBlank()) null else Songs.titleOf(text))
        val found = remember(text) { if (text.isBlank()) emptyList() else Songs.chordsUsed(Songs.parse(text), 0) }
        if (text.isNotBlank()) {
            Text(
                if (found.isEmpty()) "No chords found: it will keep the words as they are." else "Chords found: " + found.take(12).joinToString(" "),
                color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
    }
}

private val SCROLL_SPEEDS = listOf(0 to "Off", 120 to "Slow", 75 to "Medium", 45 to "Fast")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongView(song: Song, vm: MusicViewModel, scroll: ScrollState) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val lines = remember(song.text) { Songs.parse(song.text) }
    val shapesShift = song.shift - song.capo
    val flats = remember(lines, shapesShift) { Songs.flatsAfter(lines, shapesShift) }
    val first = remember(lines) { Songs.firstChord(lines) }
    val used = remember(lines, shapesShift) { Songs.chordsUsed(lines, shapesShift) }
    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    // Scrolls a screen in the time you picked, until you stop it or it reaches the end.
    LaunchedEffect(rolling, song.scroll) {
        if (!rolling || song.scroll <= 0) return@LaunchedEffect
        val perTick = screenPx / (song.scroll * 20f)
        while (rolling && scroll.value < scroll.maxValue) {
            delay(50)
            scroll.scrollBy(perTick)
        }
        rolling = false
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.openSong(null) }) { Text("‹ All songs", color = P.Muted) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { renaming = true }) { Text("Rename", color = P.Muted) }
        TextButton(onClick = { deleting = true }) { Text("Delete", color = P.Muted) }
    }
    Text(song.title, color = P.Text, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
    Panel {
        val sounding = first?.let { Music.symbol(Music.transpose(it, song.shift), Songs.flatsAfter(lines, song.shift)) }
        val shapes = first?.let { Music.symbol(Music.transpose(it, shapesShift), flats) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Key" + (sounding?.let { ": starts on $it" } ?: ""), color = P.Text, fontSize = 15.sp)
                Text(if (song.shift == 0) "as written" else if (song.shift > 0) "up ${song.shift}" else "down ${-song.shift}", color = P.Faint, fontSize = 12.sp)
            }
            TextButton(onClick = { vm.saveSong(song.copy(shift = (song.shift - 1).coerceIn(-11, 11))) }) { Text("−1", color = P.Accent) }
            TextButton(onClick = { vm.saveSong(song.copy(shift = 0)) }, enabled = song.shift != 0) { Text("Reset", color = P.Muted) }
            TextButton(onClick = { vm.saveSong(song.copy(shift = (song.shift + 1).coerceIn(-11, 11))) }) { Text("+1", color = P.Accent) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (song.capo == 0) "No capo" else "Capo ${song.capo}", color = P.Text, fontSize = 15.sp)
                if (song.capo > 0 && shapes != null) Text("chords shown as the shapes you play ($shapes shapes)", color = P.Faint, fontSize = 12.sp)
            }
            TextButton(onClick = { vm.saveSong(song.copy(capo = (song.capo - 1).coerceIn(0, 9))) }, enabled = song.capo > 0) { Text("−", color = P.Accent, fontSize = 18.sp) }
            TextButton(onClick = { vm.saveSong(song.copy(capo = (song.capo + 1).coerceIn(0, 9))) }) { Text("+", color = P.Accent, fontSize = 18.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scroll", color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            SCROLL_SPEEDS.forEach { (secs, name) -> ChoicePill(name, song.scroll == secs, { vm.saveSong(song.copy(scroll = secs)); if (secs == 0) rolling = false }) }
        }
        if (song.scroll > 0) {
            OutlinedButton(onClick = { rolling = !rolling }) { Text(if (rolling) "Stop scrolling" else "Start scrolling", color = P.Text) }
        }
    }
    if (used.isNotEmpty()) {
        Panel {
            SmallLabel("Chords in it · tap one for its shape")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                used.forEach { c -> ChoicePill(c, picked == c, { picked = if (picked == c) null else c }) }
            }
            picked?.let { name -> PickedShape(name, vm) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            when (line) {
                is SheetLine.Blank -> Spacer(Modifier.height(10.dp))
                is SheetLine.Label -> Text(line.text.uppercase(), color = P.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
                is SheetLine.Sung -> FlowRow {
                    line.pieces.forEach { p ->
                        Column(Modifier.padding(end = if (p.words.isBlank()) 14.dp else 0.dp)) {
                            Text(
                                p.chord?.let { Songs.moved(it, shapesShift, flats) } ?: " ",
                                color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                modifier = if (p.chord != null) Modifier.clickable { picked = Songs.moved(p.chord, shapesShift, flats) } else Modifier
                            )
                            Text(p.words.ifEmpty { " " }, color = P.Text, fontSize = 18.sp, fontFamily = FontFamily.Serif, lineHeight = 26.sp)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(240.dp))
    if (renaming) NameDialog("Rename the song", song.title, onDismiss = { renaming = false }, onSave = { vm.saveSong(song.copy(title = it.trim().take(80))) }, maxLength = 80)
    if (deleting) ConfirmDialog("Delete this song?", song.title, "Delete", onDismiss = { deleting = false }, onConfirm = { vm.deleteSong(song.id) })
}

@Composable
private fun PickedShape(name: String, vm: MusicViewModel) {
    val tuning by vm.tuning.collectAsStateWithLifecycle()
    val shapes by produceState(emptyList<Voicing>(), name, tuning) {
        value = Music.chord(name)?.let { c -> withContext(Dispatchers.Default) { Music.voicings(c, tuning, limit = 3) } } ?: emptyList()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shapes.isEmpty()) Text("No shape for $name.", color = P.Faint, fontSize = 13.sp)
        shapes.forEach { v ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.playShape(v, tuning) }, horizontalAlignment = Alignment.CenterHorizontally) {
                ChordDiagram(v)
                Text(v.text, color = P.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// --- ear training ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EarTab(vm: MusicViewModel) {
    val q by vm.ear.collectAsStateWithLifecycle()
    val answered by vm.answered.collectAsStateWithLifecycle()
    val tally by vm.tally.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(EarKind.INTERVALS) }
    var all by remember { mutableStateOf(false) }
    Panel {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EarKind.entries.forEach { k -> ChoicePill(k.label, kind == k, { kind = k }) }
            ChoicePill("Starter", !all, { all = false })
            ChoicePill("All", all, { all = true })
        }
        Text(
            if (kind == EarKind.INTERVALS) "Two notes, one after the other. What's the gap?" else "A chord, all at once. What kind is it?",
            color = P.Muted, fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.nextQuestion(kind, all) }, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) {
                Text(if (q == null) "Play one" else "Next")
            }
            q?.let { question -> OutlinedButton(onClick = { vm.play(question) }) { Text("Play again", color = P.Text) } }
        }
    }
    q?.let { question ->
        Panel {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.choices.forEach { c ->
                    val color = when {
                        answered == null -> P.Accent
                        c == question.answer -> P.Success
                        c == answered -> P.Warning
                        else -> P.Muted
                    }
                    ChoicePill(c, answered != null && (c == question.answer || c == answered), { vm.answer(c) }, color = color)
                }
            }
            answered?.let { a ->
                Text(
                    if (a == question.answer) "Yes: ${question.answer}." else "It was ${question.answer}.",
                    color = if (a == question.answer) P.Success else P.Warning, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
    if (tally.second > 0) Text("${tally.first} right of ${tally.second} this time. Not kept.", color = P.Faint, fontSize = 13.sp)
}
