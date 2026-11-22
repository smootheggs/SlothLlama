package app.localmind.assistant.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.desk.DeskPath
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.trading.Idea
import app.localmind.assistant.domain.trading.IdeaDraft
import app.localmind.assistant.ui.common.ConfirmDialog
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.trading.TradingViewModel
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * An idea on the path (1.49): what you thought, kept as you wrote it before any test; the rule
 * it's tested with (the Trader's draft, or one you write); and, at the end, its review. The new
 * idea form is here too.
 */

@Composable
fun IdeaScreen(ideaId: String, ui: DeskUi, vm: DeskViewModel, onBack: () -> Unit) {
    val idea = ui.ideas.firstOrNull { it.id == ideaId }
    LaunchedEffect(idea == null) { if (idea == null) onBack() }
    idea ?: return
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    val item = ui.path.firstOrNull { it.ideaId == ideaId }
    val stage = item?.stage ?: DeskPath.Stage.IDEA

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = "Idea",
            subtitle = "SlothDesk · ${stage.label}" + (item?.line?.substringAfter(" · ", "")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = P.Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                        DropdownMenuItem(text = { Text("Review it now", color = P.Text) }, onClick = { menu = false; reviewing = true })
                        DropdownMenuItem(text = { Text("Delete this idea", color = P.Danger) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        )
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= WIDE
            val words: @Composable () -> Unit = { YourWords(idea, vm) }
            val rule: @Composable () -> Unit = { TheRule(idea, item, ui, vm, onReview = { reviewing = true }) }
            if (wide) {
                Row(Modifier.widthIn(max = 1300.dp).fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(0.5f).fillMaxSize().verticalScroll(rememberScrollState())) {
                        PathChips(stage)
                        words()
                        Spacer(Modifier.height(24.dp))
                    }
                    Column(Modifier.weight(0.5f).fillMaxSize().verticalScroll(rememberScrollState())) {
                        rule()
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    PathChips(stage)
                    words()
                    rule()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (deleting) {
        ConfirmDialog("Delete this idea?", "What you wrote goes. Its rule stays with your rules.", "Delete",
            onDismiss = { deleting = false }, onConfirm = { vm.deleteIdea(idea.id) })
    }
    if (reviewing) ReviewIdeaDialog(idea, onDismiss = { reviewing = false }) { o, r, w, l -> vm.reviewIdea(idea.id, o, r, w, l) }
}

@Composable
private fun YourWords(idea: Idea, vm: DeskViewModel) {
    var note by remember { mutableStateOf("") }
    Label("THE IDEA, IN YOUR WORDS")
    Card(padding = 20.dp) {
        Text(idea.thesis, color = P.Text, fontSize = 20.sp, lineHeight = 28.sp)
        if (idea.why.isNotBlank()) Fact("Why you think so", idea.why)
        if (idea.wrongIf.isNotBlank()) Fact("What would prove it wrong", idea.wrongIf)
        Row(Modifier.padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("How sure", color = P.Faint, fontSize = 12.sp)
                Text("${idea.conviction}%", color = P.Text, fontSize = 18.sp, style = Numbers)
            }
            Column(Modifier.weight(1f)) {
                Text("Review it on", color = P.Faint, fontSize = 12.sp)
                Text(idea.reviewOn.ifBlank { "no date" }, color = P.Text, fontSize = 18.sp, style = Numbers)
            }
        }
        if (idea.from.isNotBlank()) Fact("From a story", idea.from)
        Text(
            "Written ${day(idea.createdAt)}" + (if (idea.source == "trader") " from the Trader's card" else if (idea.source == "shelf") " from the shelf" else "") +
                ". Kept as you wrote it, before any test: the review compares what happened with what you expected then.",
            color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp)
        )
    }
    if (idea.notes.isNotEmpty()) {
        Label("NOTES")
        idea.notes.forEach { n ->
            Text("${day(n.at)} · ${n.text}", color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { TextInput("Add a note", note, { note = it }) }
        Action("Add") { if (note.isNotBlank()) { vm.noteIdea(idea.id, note); note = "" } }
    }
}

@Composable
private fun Fact(label: String, text: String) {
    Text(label, color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    Text(text, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp)
}

private fun day(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** The rule it's tested with: its sentences and where it is, or the Trader's draft, or the ways to get one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheRule(idea: Idea, item: PathItem?, ui: DeskUi, vm: DeskViewModel, onReview: () -> Unit) {
    val saved = ui.rules.firstOrNull { it.id == idea.ruleId }
    if (saved != null) {
        Label("THE RULE")
        Card(padding = 20.dp) {
            Text(saved.name, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            item?.line?.let { Text(it, color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
            saved.rule?.let { r ->
                Text(RuleWords.rule(r).substringAfter("\n"), color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
            }
            val report = ui.reports.firstOrNull { it.key == saved.key }
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    saved.key in ui.state.paper -> Button("Tonight's ticket") { vm.openPaper(saved.key) }
                    report != null -> Button("See the test") { vm.openReport(saved.key) }
                    else -> Button("Open it to test") { vm.openRule(saved.id) }
                }
                OutlineButton("Edit the rule") { vm.openRule(saved.id) }
                if (item?.stage == DeskPath.Stage.REVIEW) OutlineButton("Review the idea") { onReview() }
            }
        }
        return
    }
    val draft = remember(idea.draftCard, ui.tickers) { vm.draftFor(idea) }
    if (draft != null) {
        Label("THE TRADER'S DRAFT")
        Card(padding = 20.dp) {
            val r = draft.rule
            if (r != null) {
                Text("A first rule for this idea. Check it: you can change every part.", color = P.Muted, fontSize = 13.sp)
                Text(RuleWords.rule(r).substringAfter("\n"), color = P.Text, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
                val missing = remember(r, ui.tickers, ui.otherSeries) { vm.missingFor(r) }
                if (missing.isNotEmpty()) {
                    Text("Needs prices for ${missing.joinToString(", ")}: import a file that has them.", color = P.Warning, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Open in the rule editor") { vm.useDraft(idea.id) }
                    OutlineButton("Not quite…") { vm.askForDraft(idea.id) }
                }
            } else {
                Text("The Trader's draft couldn't be read as a rule:", color = P.Warning, fontSize = 13.sp)
                draft.problems.take(4).forEach { Text("· $it", color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp) }
                Row(Modifier.padding(top = 10.dp)) { OutlineButton("Ask again") { vm.askForDraft(idea.id) } }
            }
        }
    }
    Label(if (draft == null) "A RULE TO TEST IT WITH" else "OR")
    Card(padding = 20.dp) {
        if (draft == null) {
            Text("A rule turns the idea into something the Desk can test: when these hold, then hold this. The Trader can draft " +
                "one from your words, or write it yourself from blocks.", color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        var shelf by remember { mutableStateOf(false) }
        FlowRow(Modifier.padding(top = if (draft == null) 12.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft == null) Button("Ask the Trader for a draft") { vm.askForDraft(idea.id) }
            OutlineButton("Write it myself") { vm.writeRuleFor(idea.id) }
            Box {
                OutlineButton("Start from the shelf") { shelf = true }
                DropdownMenu(expanded = shelf, onDismissRequest = { shelf = false }, modifier = Modifier.background(P.Elevated)) {
                    DeskStrategies.templates.forEach { t ->
                        DropdownMenuItem(text = { Text(t.label, color = P.Text) }, onClick = { shelf = false; vm.writeRuleFor(idea.id, t.key) })
                    }
                }
            }
        }
        Text("A draft only fills in the rule's form. Nothing is tested or traded until you tap Test.",
            color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Closing an idea: how it went, whether for the reason you gave, what happened, the lesson. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewIdeaDialog(idea: Idea, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var outcome by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }
    SmallDialog("Review the idea", onDismiss, canSave = outcome.isNotBlank(), saveLabel = "Save the review",
        onSave = { onSave(outcome, if (outcome == "right") reason else "", what, lesson) }) {
        Text("“${idea.thesis.take(200)}” · ${idea.conviction}% sure", color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Text("How it went", color = P.Faint, fontSize = 12.sp)
        Choices(listOf("right" to "Right", "wrong" to "Wrong", "mixed" to "Mixed", "no call" to "Never got going"), outcome) { outcome = it }
        if (outcome == "right") {
            Text("For the reason you gave?", color = P.Faint, fontSize = 12.sp)
            Choices(listOf("right reason" to "Yes", "partly" to "Partly", "wrong reason" to "No"), reason) { reason = it }
        }
        TextInput("What happened", what, { what = it })
        TextInput("The lesson, in a line", lesson, { lesson = it })
        Text("It goes into your record with how sure you were; its rule comes off paper.", color = P.Faint, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Choices(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (code, words) ->
            val on = code == selected
            val shape = RoundedCornerShape(16.dp)
            Text(words, color = if (on) P.Accent else P.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.heightIn(min = 40.dp).clip(shape).background(if (on) P.AccentSoft else P.Elevated)
                    .border(1.dp, if (on) P.Accent else P.Border, shape)
                    .clickable(role = Role.RadioButton) { onPick(code) }
                    .padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

// --- a new idea ---

/** The new-idea form: in your words, why, what would prove it wrong, how sure, when to review it. */
@Composable
fun NewIdeaScreen(d: IdeaDraft, trading: TradingViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(title = "New idea", subtitle = "SlothDesk · Idea", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PathChips(DeskPath.Stage.IDEA)
                if (d.from.isNotBlank()) {
                    Text("From a story: ${d.from}", color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 16.dp))
                }
                Area("The idea, in your words", d.thesis, "How you think a market behaves, e.g. gold does well when real rates fall",
                    minLines = 3) { trading.editDraft(d.copy(thesis = it)) }
                Area("Why you think so", d.why, "The reason, as you'd say it to a colleague") { trading.editDraft(d.copy(why = it)) }
                Area("What would prove it wrong", d.wrongIf, "Optional, and worth it: the review checks against it") { trading.editDraft(d.copy(wrongIf = it)) }
                Text("How sure", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                Choices((50..90 step 10).map { it.toString() to "$it%" }, d.conviction.toString()) { trading.editDraft(d.copy(conviction = it.toInt())) }
                Text("Review it on", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                val now = LocalDate.now()
                val picks = listOf(1L, 3L, 6L).map { m -> now.plusMonths(m).toString() to "In ${if (m == 1L) "a month" else "$m months"}" }
                Choices(picks, d.reviewOn) { trading.editDraft(d.copy(reviewOn = it)) }
                if (d.reviewOn.isNotBlank() && picks.none { it.first == d.reviewOn }) {
                    Text(d.reviewOn, color = P.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Text("Kept as you wrote it, before any test: the review compares what happened with what you expected then.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (TradingViewModel.canSave(d)) Button("Save the idea") { trading.saveDraft() }
                    else Text("Write the idea to save it", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                    OutlineButton("Cancel") { onBack() }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Area(label: String, value: String, hint: String, minLines: Int = 2, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(2000)) },
        label = { Text(label) },
        placeholder = { Text(hint, color = P.Faint) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border, focusedTextColor = P.Text, unfocusedTextColor = P.Text,
            cursorColor = P.Accent, focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    )
}
