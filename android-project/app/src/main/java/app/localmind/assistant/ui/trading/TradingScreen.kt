package app.localmind.assistant.ui.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.trading.Idea
import app.localmind.assistant.domain.trading.IdeaDraft
import app.localmind.assistant.domain.trading.IdeaStats
import app.localmind.assistant.domain.trading.MetalsMath
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** SlothDesk's three tabs: the strategies, the idea book, the desk's sums. */
enum class DeskTab(val label: String) { STRATEGIES("Strategies"), IDEAS("Ideas"), SUMS("Calculators") }

/** Plain text tabs with a moss underline: navigation, so quieter than any button. */
@Composable
fun DeskTabs(tab: DeskTab, onTab: (DeskTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        DeskTab.entries.forEach { t ->
            val selected = t == tab
            Column(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab, onClick = { onTab(t) })
                    .semantics { this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    t.label, fontSize = 15.sp,
                    color = if (selected) P.Text else P.Muted,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(Modifier.width(28.dp).height(2.dp).clip(CircleShape).background(if (selected) P.Accent else Color.Transparent))
            }
        }
    }
}

/**
 * The Ideas or Calculators tab of SlothDesk: an idea book that keeps what you thought before
 * you knew how it went, and the desk's sums. Paper only; nothing here reaches a broker.
 */
@Composable
fun TradingPane(viewModel: TradingViewModel, tab: DeskTab, wide: Boolean, snackbar: SnackbarHostState) {
    val ideas by viewModel.ideas.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val facts by viewModel.deskFacts.collectAsStateWithLifecycle()
    var shownId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (tab == DeskTab.SUMS && wide) 1200.dp else 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            when (tab) {
                DeskTab.SUMS -> Calculators(viewModel.inputs, facts, wide, onAsk = viewModel::askAboutNumbers)
                else -> IdeasTab(ideas, stats, onNew = viewModel::newIdea, onOpen = { shownId = it.id })
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    draft?.let { d ->
        DraftSheet(d, onChange = viewModel::editDraft, onSave = viewModel::saveDraft, onDismiss = viewModel::cancelDraft)
    }
    ideas.firstOrNull { it.id == shownId }?.let { idea ->
        IdeaSheet(
            idea,
            onNote = { viewModel.addNote(idea, it) },
            onReviewOn = { viewModel.setReviewOn(idea, it) },
            onClose = { outcome, reason, exit, what, lesson -> viewModel.close(idea, outcome, reason, exit, what, lesson) },
            onAsk = { viewModel.askTrader(idea) },
            onDelete = {
                viewModel.delete(idea)
                shownId = null
            },
            onDismiss = { shownId = null }
        )
    }
}

// --- ideas ---

@Composable
private fun IdeasTab(ideas: List<Idea>, stats: IdeaStats, onNew: () -> Unit, onOpen: (Idea) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(summary(stats), color = P.Muted, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = "tnum"))
        }
        FilledButton("New idea", onClick = onNew)
    }
    if (stats.closed >= 3) Calibration(stats)
    if (ideas.isEmpty()) {
        Text(
            "Write down an idea before it plays out: what, why, the levels, and what would prove you wrong. " +
                "Ask the Trader for one in a chat and save its card from under the reply, or start your own.",
            color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 24.dp)
        )
        return
    }
    SectionLabel("Open")
    val live = ideas.filter { it.isOpen }.sortedWith(compareByDescending<Idea> { it.isDue(today) }.thenByDescending { it.createdAt })
    if (live.isEmpty()) Text("Nothing open.", color = P.Faint, fontSize = 14.sp)
    live.forEach { IdeaRow(it, today) { onOpen(it) } }
    val closed = ideas.filterNot { it.isOpen }
    if (closed.isNotEmpty()) {
        SectionLabel("Reviewed")
        closed.sortedByDescending { it.closedAt }.forEach { IdeaRow(it, today) { onOpen(it) } }
    }
}

private fun summary(s: IdeaStats): String {
    val parts = mutableListOf("${s.open} open")
    if (s.due > 0) parts += "${s.due} to review"
    if (s.closed > 0) {
        parts += "${s.closed} reviewed: ${s.right} right, ${s.wrong} wrong" + (if (s.mixed > 0) ", ${s.mixed} mixed" else "")
        s.averageR?.let { parts += "average %+.1f R".format(Locale.US, it) }
    }
    return parts.joinToString(" · ")
}

/** How often each level of conviction turned out right, as small bars: are your 80% ideas right 80% of the time? */
@Composable
private fun Calibration(s: IdeaStats) {
    SectionLabel("How sure you were, and how often you were right")
    s.calibration.forEach { (band, pair) ->
        val (n, right) = pair
        val share = if (n == 0) 0f else right.toFloat() / n
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$band%+", color = P.Muted, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.width(48.dp))
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(P.Border)) {
                Box(Modifier.fillMaxWidth(share).height(4.dp).clip(CircleShape).background(P.Accent))
            }
            Text(
                "$right of $n", color = P.Faint, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.width(64.dp).padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun IdeaRow(idea: Idea, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arrow(idea.direction), color = P.Muted, fontSize = 15.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(idea.instrument, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(idea.thesis, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutcomeMark(idea, today)
    }
}

private fun arrow(direction: String) = when (direction) {
    "long" -> "↗"
    "short" -> "↘"
    "spread" -> "⇄"
    else -> "⛨"
}

/** Due for review: an amber dot (it needs you). Reviewed: ✓, ✕ or ~ in grey. */
@Composable
private fun OutcomeMark(idea: Idea, today: LocalDate) {
    val due = idea.isDue(today)
    val said = when {
        due -> "Due for review"
        !idea.isOpen -> idea.outcome
        else -> "Open"
    }
    Box(Modifier.size(32.dp).semantics { contentDescription = said }, contentAlignment = Alignment.Center) {
        when {
            due -> Box(Modifier.size(8.dp).clip(CircleShape).background(P.Warning))
            !idea.isOpen -> Text(
                when (idea.outcome) { "right" -> "✓"; "wrong" -> "✕"; "mixed" -> "~"; else -> "·" },
                color = P.Faint, fontSize = 14.sp
            )
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

private fun dayOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftSheet(d: IdeaDraft, onChange: (IdeaDraft) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = P.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(if (d.source == "trader") "The Trader's idea" else "New idea", color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(
                "Make it yours before saving: once saved, the thesis, levels and conviction are fixed.",
                color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
            Field("Instrument", d.instrument, "Gold spot, XAU/EUR 3m forward, silver vs gold…") { onChange(d.copy(instrument = it)) }
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("long", "short", "spread", "hedge").forEach { dir ->
                    Pill(dir.replaceFirstChar { it.uppercase() }, selected = d.direction == dir, quiet = true) { onChange(d.copy(direction = dir)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("Entry", d.entry, "", number = true) { onChange(d.copy(entry = it)) } }
                Box(Modifier.weight(1f)) { Field("Stop", d.stop, "", number = true) { onChange(d.copy(stop = it)) } }
                Box(Modifier.weight(1f)) { Field("Target", d.target, "", number = true) { onChange(d.copy(target = it)) } }
            }
            val e = d.entry.num()
            val s = d.stop.num()
            val t = d.target.num()
            if (e != null && s != null && t != null && e != s) {
                Text(
                    "Reward to risk %.1f".format(Locale.US, kotlin.math.abs(t - e) / kotlin.math.abs(e - s)),
                    color = P.Muted, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 6.dp)
                )
            }
            Field("Horizon", d.horizon, "2–4 weeks, into the Fed, by year end…") { onChange(d.copy(horizon = it)) }
            Text("Review it in", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            ReviewChips(d.reviewOn) { onChange(d.copy(reviewOn = it)) }
            Field("Size", d.size, "Optional: 1,000 oz, 2 lots, half a usual clip…") { onChange(d.copy(size = it)) }
            Field("Thesis", d.thesis, "Why: the channel, the catalyst, what's not priced in", lines = 3) { onChange(d.copy(thesis = it)) }
            Field("Wrong if", d.wrongIf, "What would prove it wrong, specifically", lines = 2) { onChange(d.copy(wrongIf = it)) }
            Field("Client angle", d.clientAngle, "Optional: who'd care and what you'd tell them", lines = 2) { onChange(d.copy(clientAngle = it)) }
            Text("How sure: ${d.conviction}%", color = P.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = d.conviction.toFloat(),
                onValueChange = { onChange(d.copy(conviction = (it / 5).toInt() * 5)) },
                valueRange = 50f..95f,
                steps = 8,
                colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Border)
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledButton("Save idea", enabled = TradingViewModel.canSave(d), onClick = onSave)
                Spacer(Modifier.width(12.dp))
                QuietButton("Cancel", onClick = onDismiss)
            }
            if (!TradingViewModel.canSave(d)) {
                Text("It needs an instrument, a thesis and what would prove it wrong.", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ReviewChips(current: String, onPick: (String) -> Unit) {
    val today = LocalDate.now()
    val options = listOf(
        "1 week" to today.plusWeeks(1), "1 month" to today.plusMonths(1),
        "3 months" to today.plusMonths(3), "6 months" to today.plusMonths(6)
    )
    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, date) -> Pill(label, selected = current == date.toString(), quiet = true, small = true) { onPick(date.toString()) } }
        Pill("No date", selected = current.isBlank(), quiet = true, small = true) { onPick("") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaSheet(
    idea: Idea,
    onNote: (String) -> Unit,
    onReviewOn: (String) -> Unit,
    onClose: (String, String, String, String, String) -> Unit,
    onAsk: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by rememberSaveable(idea.id) { mutableStateOf("") }
    var reviewing by rememberSaveable(idea.id) { mutableStateOf(false) }
    var deleting by remember(idea.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = P.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(arrow(idea.direction) + "  ", color = P.Muted, fontSize = 18.sp)
                Text(idea.instrument, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            Text(
                "${idea.direction.replaceFirstChar { it.uppercase() }} · ${idea.conviction}% sure · ${dayOf(idea.createdAt)}" +
                    (if (idea.source == "trader") " · from the Trader" else ""),
                color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
            )
            val levels = listOfNotNull(
                idea.entry?.let { "Entry ${Idea.fmt(it)}" }, idea.stop?.let { "Stop ${Idea.fmt(it)}" },
                idea.target?.let { "Target ${Idea.fmt(it)}" }, idea.rewardToRisk?.let { "R:R %.1f".format(Locale.US, it) }
            )
            if (levels.isNotEmpty()) {
                Text(levels.joinToString("   "), color = P.Text, fontSize = 15.sp, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 12.dp))
            }
            Detail("Horizon", idea.horizon)
            Detail("Size", idea.size)
            Detail("Thesis", idea.thesis)
            Detail("Wrong if", idea.wrongIf)
            Detail("Client angle", idea.clientAngle)

            if (idea.notes.isNotEmpty()) {
                SectionLabel("Notes")
                idea.notes.forEach { n ->
                    Text(dayOf(n.at), color = P.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(n.text, color = P.Text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (idea.isOpen) {
                Field("Add a note", note, "What changed, what you saw", lines = 2) { note = it }
                if (note.isNotBlank()) {
                    QuietButton("Add note") {
                        onNote(note)
                        note = ""
                    }
                }
                Text("Review on", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                ReviewChips(idea.reviewOn, onReviewOn)
            } else {
                SectionLabel("Review")
                val result = listOfNotNull(
                    idea.outcome.replaceFirstChar { it.uppercase() },
                    idea.reason.takeIf { it.isNotBlank() },
                    idea.exit?.let { "exit ${Idea.fmt(it)}" },
                    idea.r?.let { "%+.2f R".format(Locale.US, it) }
                ).joinToString(" · ")
                Text(result, color = P.Text, fontSize = 15.sp)
                Detail("What happened", idea.whatHappened)
                Detail("Lesson", idea.lesson)
            }

            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (idea.isOpen && !reviewing) FilledButton("Review it") { reviewing = true }
                QuietButton("Ask the Trader", onClick = onAsk)
                Spacer(Modifier.weight(1f))
                QuietButton(if (deleting) "Tap again to delete" else "Delete", muted = true) {
                    if (deleting) onDelete() else deleting = true
                }
            }
            if (idea.isOpen && reviewing) ReviewForm(idea) { o, r, x, w, l ->
                onClose(o, r, x, w, l)
                reviewing = false
            }
        }
    }
}

@Composable
private fun ReviewForm(idea: Idea, onSave: (String, String, String, String, String) -> Unit) {
    var outcome by rememberSaveable(idea.id) { mutableStateOf("") }
    var reason by rememberSaveable(idea.id) { mutableStateOf("") }
    var exit by rememberSaveable(idea.id) { mutableStateOf("") }
    var what by rememberSaveable(idea.id) { mutableStateOf("") }
    var lesson by rememberSaveable(idea.id) { mutableStateOf("") }
    SectionLabel("Review")
    Text("How did it go?", color = P.Faint, fontSize = 12.sp)
    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("right", "wrong", "mixed", "no call").forEach { o ->
            Pill(o.replaceFirstChar { it.uppercase() }, selected = outcome == o, quiet = true, small = true) { outcome = o }
        }
    }
    if (outcome == "right" || outcome == "mixed") {
        Text("For the reason you gave?", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("right reason", "partly", "wrong reason").forEach { r ->
                Pill(r.replaceFirstChar { it.uppercase() }, selected = reason == r, quiet = true, small = true) { reason = r }
            }
        }
    }
    if (idea.direction == "long" || idea.direction == "short") Field("Exit or price now", exit, "", number = true) { exit = it }
    val r = idea.entry?.let { e -> idea.stop?.let { s -> exit.num()?.let { x -> MetalsMath.rMultiple(idea.isLong, e, s, x) } } }
    r?.let { Text("%+.2f R".format(Locale.US, it), color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
    Field("What happened", what, "What the market did, and why", lines = 3) { what = it }
    Field("Lesson", lesson, "The one thing to keep or change", lines = 2) { lesson = it }
    Row(Modifier.padding(top = 14.dp)) {
        FilledButton("Save review", enabled = outcome.isNotBlank()) { onSave(outcome, reason, exit, what, lesson) }
    }
}

@Composable
private fun Detail(label: String, text: String) {
    if (text.isBlank()) return
    Text(label, color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    Text(text, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp)
}

// --- shared bits ---

internal fun String.num(): Double? = trim().replace(",", "").toDoubleOrNull()

@Composable
internal fun SectionLabel(label: String) {
    Text(
        label.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 32.dp, bottom = 14.dp)
    )
}

@Composable
internal fun Field(label: String, value: String, hint: String, number: Boolean = false, lines: Int = 1, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, color = P.Faint, fontSize = 12.sp)
        // One line: a fixed height, so fields side by side line up whatever their hint.
        val box = if (lines == 1) Modifier.height(48.dp) else Modifier.heightIn(min = 48.dp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = lines == 1,
            minLines = lines,
            textStyle = TextStyle(color = P.Text, fontSize = 16.sp, fontFeatureSettings = if (number) "tnum" else null),
            cursorBrush = SolidColor(P.Accent),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .then(box)
                .clip(RoundedCornerShape(10.dp))
                .background(P.Elevated)
                .padding(horizontal = 12.dp, vertical = if (lines == 1) 0.dp else 10.dp),
            decorationBox = { inner ->
                Box(
                    if (lines == 1) Modifier.fillMaxHeight() else Modifier,
                    contentAlignment = if (lines == 1) Alignment.CenterStart else Alignment.TopStart
                ) {
                    if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = P.Faint, fontSize = 14.sp, maxLines = lines)
                    inner()
                }
            }
        )
    }
}

@Composable
internal fun Pill(label: String, selected: Boolean, quiet: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bg: Modifier = when {
        selected && quiet -> Modifier.background(P.AccentSoft)
        selected -> Modifier.background(P.Accent)
        else -> Modifier.border(1.dp, P.Border, shape)
    }
    Box(
        Modifier.heightIn(min = 40.dp).clip(shape).then(bg).clickable(onClick = onClick).padding(horizontal = if (small) 12.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = if (small) 12.sp else 14.sp, maxLines = 1,
            color = when {
                selected && !quiet -> P.OnAccent
                selected -> P.Text
                else -> P.Muted
            },
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
internal fun FilledButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) P.Accent else P.Border)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) P.OnAccent else P.Faint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun QuietButton(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (muted) P.Faint else P.Accent, fontSize = 14.sp)
    }
}
