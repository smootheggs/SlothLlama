package app.localmind.assistant.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.desk.JournalEntry
import app.localmind.assistant.domain.desk.DeskPath
import app.localmind.assistant.ui.common.ConfirmDialog
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate

/**
 * A rule's paper days (1.49): tonight's ticket and why, the 60 days as marks, how closely you've
 * followed it, the slips, and what the rule itself made over the same days. The ticket's trade is
 * never sent anywhere: you place it yourself, or not, and mark what you did.
 */
@Composable
fun PaperScreen(key: String, ui: DeskUi, vm: DeskViewModel, onBack: () -> Unit) {
    val t = ui.tonight.firstOrNull { it.key == key }
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(t == null, ui.ready, ui.working) { if (t == null && ui.ready && ui.working == null && key !in ui.state.paper) onBack() }
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = t?.name ?: "Paper",
            subtitle = "SlothDesk · Paper" + (t?.let { " · day ${it.progress.days.coerceAtMost(DeskPath.PAPER_DAYS)} of ${DeskPath.PAPER_DAYS}" } ?: ""),
            onBack = onBack
        )
        if (t == null) {
            Text(ui.working ?: "Waiting for its next run: it may need prices.", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= WIDE
            val left: @Composable () -> Unit = {
                PathChips(if (t.progress.done) DeskPath.Stage.REVIEW else DeskPath.Stage.PAPER)
                Label("TONIGHT'S TICKET · DATA FROM ${dayName(t.report.ticket.dataDate).uppercase()}'S CLOSE")
                TonightCard(t, vm, compact = false)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("See the test") { vm.openReport(key) }
                    if (key.startsWith(DeskViewModel.MINE)) OutlineButton("Edit the rule") { vm.openRule(key.removePrefix(DeskViewModel.MINE)) }
                    Action("Stop paper-trading", muted = true) { stopping = true }
                }
                Text("The ticket's trade is never sent anywhere: you place it yourself, or not, and mark what you did.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
            val right: @Composable () -> Unit = {
                Label("YOUR ${DeskPath.PAPER_DAYS} DAYS")
                Days(t)
                Label("THE TICKETS SO FAR")
                Past(key, ui, vm, t.progress.since)
            }
            if (wide) {
                Row(Modifier.widthIn(max = 1300.dp).fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(0.5f).fillMaxSize().verticalScroll(rememberScrollState())) { left(); Spacer(Modifier.height(24.dp)) }
                    Column(Modifier.weight(0.5f).fillMaxSize().verticalScroll(rememberScrollState())) { right(); Spacer(Modifier.height(24.dp)) }
                }
            } else {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    left(); right(); Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (stopping) {
        ConfirmDialog("Stop paper-trading ${t?.name ?: "it"}?", "No more tickets for it. Its days stay in the journal; papering it again starts a new 60.",
            "Stop", onDismiss = { stopping = false }, onConfirm = { vm.stopPaper(key) })
    }
}

/** The 60 days as marks, how closely they've been followed, the slips, and the rule's own result. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Days(t: Tonight) {
    val p = t.progress
    Card(padding = 20.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics { contentDescription = "${p.days} days on paper, ${p.followed} followed of ${p.marked} marked" }) {
            p.cells.take(DeskPath.PAPER_DAYS).forEach { c -> Cell(c) }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(DeskPath.Day.FOLLOWED, "followed")
            Legend(DeskPath.Day.NOT_FOLLOWED, "not followed")
            Legend(DeskPath.Day.UNMARKED, "not marked")
            Legend(DeskPath.Day.TO_COME, "to come")
        }
        val share = p.share
        Text(
            if (share == null) "No tickets marked yet · needs ${(DeskPath.FOLLOWED * 100).toInt()}% followed by day ${DeskPath.PAPER_DAYS}"
            else "${(share * 100).toInt()}% followed so far · needs ${(DeskPath.FOLLOWED * 100).toInt()}% by day ${DeskPath.PAPER_DAYS}",
            color = if (share != null && share < DeskPath.FOLLOWED) P.Warning else P.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp)
        )
        if (p.slips.isNotEmpty()) {
            Text("Slips: " + p.slips.entries.joinToString(" · ") { (k, n) -> "${DeskPath.slip(k).ifBlank { "unsaid" }.lowercase()} $n" },
                color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        val r = t.report
        val start = LocalDate.parse(p.since).plusDays(1)
        val own = DeskPath.change(r.recentDates, r.recentEquity, start, r.ticket.dataDate)
        if (own != null) {
            Text("The rule's own result over these days: ${DeskText.pct(own)}. What you made depends on your own fills: " +
                "the Desk doesn't guess them.", color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Cell(c: DeskPath.Day) {
    val shape = RoundedCornerShape(3.dp)
    val (fill, edge) = cellColors(c)
    Box(Modifier.size(14.dp).clip(shape).background(fill).border(1.dp, edge, shape))
}

@Composable
private fun cellColors(c: DeskPath.Day): Pair<Color, Color> = when (c) {
    DeskPath.Day.FOLLOWED -> P.Accent to P.Accent
    DeskPath.Day.NOT_FOLLOWED -> Color.Transparent to P.Danger
    DeskPath.Day.UNMARKED -> Color.Transparent to P.BorderStrong
    DeskPath.Day.TO_COME -> P.Elevated to P.Border
}

@Composable
private fun Legend(c: DeskPath.Day, words: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cell(c)
        Spacer(Modifier.width(5.dp))
        Text(words, color = P.Faint, fontSize = 11.sp)
    }
}

/** Every ticket since paper began, newest first; any can be marked or changed. */
@Composable
private fun Past(key: String, ui: DeskUi, vm: DeskViewModel, since: String) {
    var slipping by remember { mutableStateOf<JournalEntry?>(null) }
    val entries = ui.state.journal.filter { it.strategy == key && it.dataDate >= since }.sortedByDescending { it.dataDate }.take(DeskPath.PAPER_DAYS + 10)
    Card {
        if (entries.isEmpty()) Text("Tonight's ticket will be the first.", color = P.Muted, fontSize = 13.sp)
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = P.Border)
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("${e.dataDate} · ${e.nextOpen}", color = P.Text, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Marking(e, onFollowed = { vm.mark(key, it.dataDate, true) }, onDidnt = { slipping = e })
            }
        }
    }
    slipping?.let { e -> SlipDialog(onDismiss = { slipping = null }) { code -> vm.mark(key, e.dataDate, false, code) } }
}
