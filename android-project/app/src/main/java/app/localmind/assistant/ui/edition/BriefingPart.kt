package app.localmind.assistant.ui.edition

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.briefing.BriefStory
import app.localmind.assistant.domain.briefing.Briefing
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.DateField
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlin.math.roundToInt

/** The briefing's own rating of its sources, as three small dots (not a verdict of ours). */
@Composable
private fun Dots(level: Int) {
    if (level <= 0) return
    val names = mapOf(3 to "high", 2 to "medium", 1 to "low")
    Row(
        Modifier.semantics { contentDescription = "The briefing rates its sources ${names[level]}" },
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < level) P.Accent else P.BorderStrong)) }
    }
}

/**
 * A pasted briefing as a part of the paper: its stories with their places, what's unclear folded
 * away, its own source rating as dots, and its watchlist with what it saw kept apart from its guesses.
 */
@Composable
internal fun BriefingPart(modifier: Modifier, onPlace: (String) -> Unit) {
    val vm: BriefingViewModel = hiltViewModel()
    val all by vm.briefings.collectAsStateWithLifecycle()
    val shownId by vm.shown.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.clearMessage() } }
    var pasting by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf<String?>(null) }
    var news by remember { mutableStateOf<String?>(null) }
    val b = all.firstOrNull { it.id == shownId } ?: all.firstOrNull()

    Column(modifier.padding(top = 18.dp)) {
        SectionHead("The briefing") {
            Text(
                "Paste", color = P.Accent, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { pasting = true }.padding(horizontal = 8.dp, vertical = 10.dp)
            )
        }
        if (b == null) {
            Text(
                "Paste a briefing (Grok's, or any in the same shape: numbered stories with their facts, why they matter, what's " +
                    "unclear and a source rating, then a watchlist). It's read on the tablet into its parts; places are found " +
                    "on the Atlas; nothing is checked or sent.",
                color = P.Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 8.dp)
            )
            OutlinedButton(onClick = { pasting = true }) { Text("Paste a briefing", color = P.Text) }
        } else {
            if (all.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.forEach { x -> ChoicePill(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(x.takenAt)), x.id == b.id, { vm.show(x.id) }) }
                }
            }
            BriefingBody(b, places, onPlace, vm, onWatch = { watching = it }, onNews = { news = it })
        }
    }
    if (pasting) PasteBriefing(onDismiss = { pasting = false }) { text, source -> vm.paste(text, source) }
    watching?.let { q -> WatchDialog(q, onDismiss = { watching = null }) { question, check, chance, why -> vm.watch(question, check, chance, why) } }
    news?.let { t -> NewsDialog(t, onDismiss = { news = null }) { vm.watchNews(it) } }
}

@Composable
private fun BriefingBody(
    b: Briefing,
    places: Map<String, app.localmind.assistant.domain.atlas.PlaceHit>,
    onPlace: (String) -> Unit,
    vm: BriefingViewModel,
    onWatch: (String) -> Unit,
    onNews: (String) -> Unit
) {
    var unfolded by remember(b.id) { mutableStateOf(b.stories.take(2).map { it.rank }.toSet()) }
    var howShown by remember(b.id) { mutableStateOf(false) }
    var restShown by remember(b.id) { mutableStateOf(false) }
    Text(
        "Pasted from ${b.source} · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.takenAt)) +
            ". Its account, not checked on the tablet; the dots are its own rating of its sources.",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 6.dp)
    )
    if (b.preface.isNotBlank()) {
        Text(
            if (howShown) b.preface else "How it was made · show", color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { howShown = !howShown }.padding(vertical = 6.dp)
        )
    }
    b.stories.forEachIndexed { i, s ->
        if (i > 0) HorizontalDivider(color = P.Border)
        StoryBlock(
            s, expanded = s.rank in unfolded, place = places["${b.id}:${s.rank}"],
            onToggle = { unfolded = if (s.rank in unfolded) unfolded - s.rank else unfolded + s.rank },
            onPlace = onPlace, onCheck = { vm.check(s) }, onTrader = { vm.askTrader(s, b.source) }
        )
    }
    if (b.seen.isNotEmpty() || b.guess.isNotBlank()) {
        Text("WATCHLIST", color = P.Faint, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        if (b.seen.isNotEmpty()) Text("What it saw", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        b.seen.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("· $item", color = P.Text, fontSize = 15.sp, lineHeight = 22.sp)
                Row {
                    TextButton(onClick = { onWatch(item) }) { Text("Watch it in Forecasts", color = P.Accent, fontSize = 13.sp) }
                    TextButton(onClick = { onNews(item.split(' ').take(8).joinToString(" ").trimEnd('.', ',', ';')) }) {
                        Text("Lobster: watch the news", color = P.Lobster, fontSize = 13.sp)
                    }
                }
            }
        }
        if (b.guess.isNotBlank()) {
            Text("Its guess, as it labelled it", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            Text(b.guess, color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
    if (b.rest.isNotBlank()) {
        Text(
            if (restShown) b.rest else "The rest, as written · show", color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { restShown = !restShown }.padding(vertical = 6.dp)
        )
    }
    Text(
        "Remove this briefing", color = P.Faint, fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.delete(b.id) }.padding(vertical = 8.dp)
    )
}

@Composable
private fun StoryBlock(
    s: BriefStory,
    expanded: Boolean,
    place: app.localmind.assistant.domain.atlas.PlaceHit?,
    onToggle: () -> Unit,
    onPlace: (String) -> Unit,
    onCheck: () -> Unit,
    onTrader: () -> Unit
) {
    var gaps by remember(s.rank) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${s.rank}", color = P.Faint, fontSize = 22.sp, fontFamily = FontFamily.Serif, modifier = Modifier.width(24.dp))
            Text(s.headline, color = P.Text, fontSize = if (expanded) 18.sp else 16.sp, lineHeight = if (expanded) 25.sp else 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.padding(top = 8.dp)) { Dots(s.confidence) }
        }
        if (s.region.isNotBlank() || place != null) {
            Row(Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (place != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPlace(place.a3) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = P.Accent, modifier = Modifier.size(15.dp))
                        Text(" ${place.named}", color = P.Accent, fontSize = 13.sp)
                    }
                }
                if (s.region.isNotBlank()) Text(s.region, color = P.Faint, fontSize = 12.sp)
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.facts.isNotBlank()) Text(s.facts, color = P.Text, fontSize = 15.sp, lineHeight = 22.sp)
                if (s.implications.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Why it matters", color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(92.dp).padding(top = 2.dp))
                        Text(s.implications, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
                if (s.gaps.isNotBlank()) {
                    Text(
                        if (gaps) "What's unclear: ${s.gaps}" else "What's unclear · show", color = P.Faint, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { gaps = !gaps }.padding(vertical = 4.dp)
                    )
                }
                if (s.confidenceNote.isNotBlank()) Text("Its sources: ${s.confidenceNote}", color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp)
                Row {
                    TextButton(onClick = onCheck) { Text("Check with the Lobster", color = P.Lobster, fontSize = 13.sp) }
                    TextButton(onClick = onTrader) { Text("Ask the Trader", color = P.Muted, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun PasteBriefing(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Grok") }
    EditDialog("Paste a briefing", onDismiss, { onSave(text, source); onDismiss() }, canSave = text.isNotBlank(), saveLabel = "Add to the paper") {
        TextButton(onClick = {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it.take(app.localmind.assistant.domain.briefing.Briefings.MAX_TEXT) }
        }) { Text("Paste from the clipboard", color = P.Accent) }
        Text("In Grok: the copy button under the reply. The clipboard is read only when you tap that.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
        FormField("The briefing", text, { text = it.take(app.localmind.assistant.domain.briefing.Briefings.MAX_TEXT) }, lines = 8)
        FormField("From", source, { source = it.take(40) })
        val found = remember(text) { if (text.isBlank()) null else app.localmind.assistant.domain.briefing.Briefings.parse(text, "preview", 0) }
        found?.let { f ->
            Text(
                if (f.stories.isEmpty()) "No numbered stories found: it would be kept as written." else
                    "Found ${f.stories.size} stories" + (if (f.seen.isNotEmpty()) ", ${f.seen.size} things on the watchlist" else "") +
                        (if (f.guess.isNotBlank()) ", and its guesses, kept apart" else "") + ". Read by the app, not a model.",
                color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun WatchDialog(seen: String, onDismiss: () -> Unit, onSave: (String, String, Double?, String) -> Unit) {
    var question by remember { mutableStateOf(seen) }
    var check by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var withChance by remember { mutableStateOf(false) }
    var chance by remember { mutableFloatStateOf(30f) }
    var why by remember { mutableStateOf("") }
    EditDialog("Watch it in Forecasts", onDismiss, { onSave(question, check, if (withChance) chance / 100.0 else null, why); onDismiss() }, canSave = question.isNotBlank()) {
        Text("Make it a question that will have an answer by the day you check, such as \"Is the strike over by then?\"", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
        FormField("The question", question, { question = it.take(240) }, lines = 3)
        DateField("Check on", check) { if (it.isNotBlank()) check = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Give it a chance now", color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(checked = withChance, onCheckedChange = { withChance = it }, colors = SwitchDefaults.colors(checkedTrackColor = P.Accent, checkedThumbColor = P.OnAccent))
        }
        if (withChance) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your chance of yes", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${chance.roundToInt()}%", color = P.Accent, fontSize = 22.sp, fontFamily = FontFamily.Serif)
            }
            Slider(
                value = chance, onValueChange = { chance = it.roundToInt().toFloat() }, valueRange = 1f..99f, steps = 97,
                colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Elevated)
            )
            FormField("Why (optional)", why, { why = it.take(300) }, lines = 2)
        } else {
            Text("Without a chance it's counted (happened or not) but not scored.", color = P.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NewsDialog(topic: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var t by remember { mutableStateOf(topic) }
    EditDialog("Lobster: watch the news", onDismiss, { onSend(t); onDismiss() }, canSave = t.isNotBlank(), saveLabel = "Send") {
        Text("A few key words work best. The Lobster checks the news and sends an alert when there's more; it never posts or acts.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
        FormField("Topic", t, { t = it.take(120) })
    }
}
