package app.localmind.assistant.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.desk.JournalEntry
import app.localmind.assistant.domain.desk.DeskPath
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.ui.trading.TradingViewModel
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate

/*
 * SlothDesk's front (1.49): your ideas on one path, Idea → Test → Paper → Review; the shelf
 * to start from; tonight's tickets for the rules on paper; and the Sunday review. On a wide
 * screen the ideas are on the left and tonight on the right; upright, tonight comes first.
 */

@Composable
internal fun DeskFront(ui: DeskUi, vm: DeskViewModel, trading: TradingViewModel, wide: Boolean, prices: @Composable () -> Unit) {
    val ideas: @Composable () -> Unit = {
        PathChips(null, counts(ui))
        Label("YOUR IDEAS")
        YourIdeas(ui, vm, trading)
        Label("FROM THE SHELF")
        Shelf(ui, vm)
    }
    val tonight: @Composable () -> Unit = {
        prices()
        if (ui.hasData && ui.tonight.isNotEmpty()) {
            Label("TONIGHT")
            ui.tonight.forEach { t -> TonightCard(t, vm, compact = true) }
        }
        if (ui.ready) {
            Label("SUNDAY'S REVIEW")
            SundayCard(ui, vm)
        }
        ui.waiting.forEach { (name, missing) ->
            Text("$name: waiting for ${missing.joinToString(", ")}", color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
    if (wide) {
        Row(Modifier.widthIn(max = 1400.dp).fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(0.56f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                ideas()
                Footer()
            }
            Column(Modifier.weight(0.44f).fillMaxSize().verticalScroll(rememberScrollState())) {
                tonight()
                Spacer(Modifier.height(32.dp))
            }
        }
    } else {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            tonight()
            Spacer(Modifier.height(8.dp))
            ideas()
            Footer()
        }
    }
}

private fun counts(ui: DeskUi): Map<DeskPath.Stage, String> {
    val by = ui.path.groupingBy { it.stage }.eachCount()
    fun n(s: DeskPath.Stage, one: String, many: String) = (by[s] ?: 0).let { if (it == 1) "1 $one" else "$it $many" }
    return mapOf(
        DeskPath.Stage.IDEA to n(DeskPath.Stage.IDEA, "idea", "ideas"),
        DeskPath.Stage.TEST to n(DeskPath.Stage.TEST, "being tested", "being tested"),
        DeskPath.Stage.PAPER to n(DeskPath.Stage.PAPER, "on paper", "on paper"),
        DeskPath.Stage.REVIEW to when {
            ui.reviewWaiting != null -> "ready"
            ui.daysToSunday == 1 -> "tomorrow"
            else -> "Sunday"
        }
    )
}

/**
 * The four steps of the path. On the front each shows its count; over an idea, the step it's
 * at is lit.
 */
@Composable
internal fun PathChips(current: DeskPath.Stage?, counts: Map<DeskPath.Stage, String>? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DeskPath.Stage.entries.forEachIndexed { i, s ->
            val on = s == current
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.weight(1f).clip(shape).background(if (on) P.AccentSoft else P.Card).border(1.dp, if (on) P.Accent else P.Border, shape)
                    .semantics { contentDescription = "Step ${i + 1}, ${s.label}" + (counts?.get(s)?.let { ": $it" } ?: "") + if (on) ", here" else "" }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("${i + 1} · ${s.label}", color = if (on) P.Accent else P.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                counts?.get(s)?.let { Text(it, color = P.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

// --- your ideas ---

@Composable
private fun YourIdeas(ui: DeskUi, vm: DeskViewModel, trading: TradingViewModel) {
    Card {
        if (ui.path.isEmpty()) {
            Text("Start with an idea in your own words: how you think a market behaves. Then a rule to test it (the " +
                "Trader can draft one), its six questions, 60 days on paper, and a review against what you expected.",
                color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        ui.path.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = P.Border)
            PathRow(item, ui, vm)
        }
        Row(Modifier.padding(top = 6.dp)) {
            Action("New idea") { trading.newPathIdea() }
        }
    }
}

@Composable
private fun PathRow(item: PathItem, ui: DeskUi, vm: DeskViewModel) {
    val report = item.key?.let { k -> ui.reports.firstOrNull { it.key == k } }
    val (label, act) = when (item.stage) {
        DeskPath.Stage.IDEA -> "Draft a rule" to { item.ideaId?.let(vm::openIdea) ?: Unit }
        DeskPath.Stage.TEST -> if (report != null) "See the test" to { vm.openReport(report.key) }
            else "Test it" to { item.ruleId?.let(vm::openRule) ?: Unit }
        DeskPath.Stage.PAPER -> "Tonight's ticket" to { item.key?.let(vm::openPaper) ?: Unit }
        DeskPath.Stage.REVIEW -> "Review it" to { if (item.ideaId != null) vm.openIdea(item.ideaId) else item.key?.let(vm::openPaper) ?: Unit }
    }
    val open: () -> Unit = {
        when {
            item.ideaId != null -> vm.openIdea(item.ideaId)
            item.stage == DeskPath.Stage.PAPER || item.stage == DeskPath.Stage.REVIEW -> item.key?.let(vm::openPaper)
            item.ruleId != null -> vm.openRule(item.ruleId)
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClickLabel = "Open", onClick = open).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, color = P.Text, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                StageDot(item.stage)
                Spacer(Modifier.width(6.dp))
                Text(item.line, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Action(label) { act() }
    }
}

@Composable
internal fun StageDot(s: DeskPath.Stage) {
    val c = when (s) {
        DeskPath.Stage.IDEA -> P.Faint
        DeskPath.Stage.TEST -> P.Warning
        DeskPath.Stage.PAPER -> P.Accent
        DeskPath.Stage.REVIEW -> P.Success
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
}

// --- the shelf ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shelf(ui: DeskUi, vm: DeskViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeskStrategies.templates.forEach { t ->
            val r = ui.reports.firstOrNull { it.key == t.key }
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.widthIn(min = 150.dp).clip(shape).background(P.Card).border(1.dp, P.Border, shape)
                    .clickable(role = Role.Button, onClickLabel = "See its test") { if (r != null) vm.openReport(t.key) else vm.copyShelf(t.key) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(t.label.substringBefore(" with ").substringBefore(" ("), color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (r != null) Row(Modifier.padding(top = 4.dp)) { GateDots(r.gates) }
            }
        }
    }
    Text("Ready-made rules to start from: copy one and it becomes your idea, with its tries so far.",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
}

// --- tonight ---

/**
 * Tonight's ticket for a rule on paper: what to do at the next open, why (each condition as it
 * stood at the close), and whether you followed it.
 */
@Composable
internal fun TonightCard(t: Tonight, vm: DeskViewModel, compact: Boolean) {
    var slipping by remember { mutableStateOf(false) }
    val r = t.report
    Card(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.name, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (compact) Action("${t.progress.days} of ${DeskPath.PAPER_DAYS} days", muted = true) { vm.openPaper(t.key) }
        }
        Text("At the next open: ${r.ticket.nextOpen}", color = P.Text, fontSize = if (compact) 16.sp else 19.sp, lineHeight = 24.sp,
            modifier = Modifier.padding(top = 6.dp))
        Text((if (r.ticket.trade) "A trade. " else "No trade. ") + "Data from ${dayName(r.ticket.dataDate)}'s close.",
            color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        val why = if (compact) t.why.take(2) else t.why
        why.forEach { w ->
            Row(Modifier.padding(top = 4.dp)) {
                Text(when (w.met) { true -> "✓"; false -> "✕"; null -> "○" }, fontSize = 12.sp,
                    color = when (w.met) { true -> P.Success; false -> P.Muted; null -> P.Faint }, modifier = Modifier.width(18.dp))
                Text(w.text, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Marking(t.entry, onFollowed = { e -> vm.mark(t.key, e.dataDate, true) }, onDidnt = { slipping = true })
    }
    if (slipping) {
        t.entry?.let { e -> SlipDialog(onDismiss = { slipping = false }) { code -> vm.mark(t.key, e.dataDate, false, code) } }
    }
}

/** Followed it / Didn't…, or how it was marked (with a way to change it). */
@Composable
internal fun Marking(entry: JournalEntry?, onFollowed: (JournalEntry) -> Unit, onDidnt: () -> Unit) {
    if (entry == null) {
        Text("Tonight's ticket is being written.", color = P.Faint, fontSize = 12.sp)
        return
    }
    when (entry.followed) {
        "yes" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ Followed it", color = P.Success, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Action("Change", muted = true) { onDidnt() }
        }
        "no" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Didn't" + DeskPath.slip(entry.mistake).let { if (it.isBlank()) "" else " · $it" }, color = P.Muted, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Action("Followed it after all", muted = true) { onFollowed(entry) }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Followed it") { onFollowed(entry) }
            OutlineButton("Didn't…") { onDidnt() }
        }
    }
}

/** What happened instead: the tags show, over time, which slips cost you most. */
@Composable
internal fun SlipDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("If you didn't, what happened instead?", color = P.Text) },
        text = {
            Column {
                Text("Tags show, over time, which slips cost you most.", color = P.Muted, fontSize = 13.sp)
                DeskPath.SLIPS.forEach { (code, words) ->
                    Text(words, color = P.Accent, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onPick(code); onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) } }
    )
}

internal fun dayName(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)

// --- the Sunday review ---

@Composable
private fun SundayCard(ui: DeskUi, vm: DeskViewModel) {
    val waiting = ui.reviewWaiting
    val week = remember(ui.state.journal, ui.state.paper) {
        val start = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).toString()
        val mine = ui.state.journal.filter { it.strategy in ui.state.paper.keys && it.dataDate >= start }
        Triple(mine.size, mine.count { it.followed == "yes" }, mine.count { it.followed == "no" })
    }
    Card {
        if (waiting != null) {
            val today = waiting == LocalDate.now()
            Text(if (today) "Sunday's review is ready" else "Last Sunday's review is waiting", color = P.Text, fontSize = 16.sp,
                fontWeight = FontWeight.Medium)
            Text("About five minutes: the week's tickets, how your ideas fit it, and your call for next week.",
                color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp)) { Button("Start the review") { vm.openReview() } }
        } else {
            Text(when (ui.daysToSunday) { 0 -> "This week's review is done"; 1 -> "Sunday's review is tomorrow"; else -> "Sunday's review in ${ui.daysToSunday} days" },
                color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val (n, yes, no) = week
            if (n > 0) {
                Text("$n ticket${if (n == 1) "" else "s"} this week · followed $yes" + (if (no > 0) " · $no slip${if (no == 1) "" else "s"}" else ""),
                    color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (ui.lastLine.isNotBlank()) {
            Text("Your line for this week: “${ui.lastLine}”", color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
