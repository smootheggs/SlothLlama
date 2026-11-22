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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.desk.Gate
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.desk.StrategyReport
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlin.math.abs

/**
 * A test's report as six plain questions (1.48): did it beat simply holding, did it hold up on
 * years it never saw, could luck explain it, does it survive doubled costs, do nearby settings
 * agree, have you followed it on paper. Each answer opens to the numbers behind it. Every number
 * is worked out by the Desk; the Trader can explain them but never makes new ones.
 */
@Composable
fun ReportScreen(r: StrategyReport, ui: DeskUi, vm: DeskViewModel, onBack: () -> Unit) {
    val tries = r.robustInSample?.trials ?: 1
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = r.label,
            subtitle = "SlothDesk · the six questions · " + (if (tries == 1) "1 try" else "$tries tries") +
                (ui.lastDate?.let { " · prices to $it" } ?: ""),
            onBack = onBack
        )
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (maxWidth >= WIDE) {
                Row(
                    Modifier.widthIn(max = 1400.dp).fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(Modifier.weight(0.46f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                        Summary(r, vm)
                        Spacer(Modifier.height(24.dp))
                    }
                    Column(Modifier.weight(0.54f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                        Questions(r)
                        Details(r)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Summary(r, vm)
                    Questions(r)
                    Details(r)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(r: StrategyReport, vm: DeskViewModel) {
    val (headline, line) = r.verdict
    Card(padding = 20.dp) {
        EquityChart(r.chart, height = 190.dp)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(P.Accent, "the rule")
            Spacer(Modifier.width(14.dp))
            Legend(P.Faint, "holding ${r.benchLabel}")
            Spacer(Modifier.weight(1f))
            Text("held back from ${r.splitDate}", color = P.Faint, fontSize = 11.sp)
        }
    }
    Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        GateDots(r.gates)
        Text("  ${r.gatesPassed} of ${r.gates.size}", color = P.Faint, fontSize = 13.sp, style = Numbers)
    }
    Text(headline, color = if (headline == "Research only") P.Text else P.Accent, fontSize = 24.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() })
    Text(line, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    r.guardrail?.let { Text("⚠ $it", color = P.Warning, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
    FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button("Ask the Trader about it") { vm.askAbout(r) }
        if (r.gates.getOrNull(4)?.passed == null) OutlineButton("Check nearby settings") { vm.checkNearby(r.key) }
        if (r.mine) OutlineButton("Edit the rule") {
            vm.closeReport()
            vm.openRule(r.key.removePrefix(DeskViewModel.MINE))
        } else OutlineButton("Make my copy") {
            vm.closeReport()
            vm.copyShelf(r.key)
        }
    }
    Text("Tonight's ticket: ${r.ticket.nextOpen}", color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
    Text("The rule", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
    Text(RuleWords.rule(r.rule).substringAfter("\n"), color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = P.Faint, fontSize = 11.sp)
    }
}

@Composable
private fun Questions(r: StrategyReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
        r.gates.forEach { Question(it) }
    }
}

/** One question: a mark, the question, its answer; a tap shows the numbers behind it. */
@Composable
private fun Question(g: Gate) {
    var unfolded by rememberSaveable(g.name) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val (mark, color, word) = when (g.passed) {
        true -> Triple("✓", P.Success, "Yes")
        false -> Triple("✕", P.Danger, "No")
        null -> Triple("○", P.Faint, "Not yet")
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(P.Card).border(1.dp, P.Border, shape)
            .clickable(role = Role.Button, onClickLabel = if (unfolded) "Hide the numbers" else "Show the numbers") { unfolded = !unfolded }
            .semantics { contentDescription = "$word. ${g.name} ${g.detail}" }
            .padding(16.dp)
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).border(1.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, color = color, fontSize = 13.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(g.detail, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
            if (unfolded && g.more.isNotBlank()) {
                Text(g.more, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, style = Numbers, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Details(r: StrategyReport) {
    var unfolded by rememberSaveable(r.key) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
            .clickable(role = Role.Button) { unfolded = !unfolded },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("All the numbers", color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(if (unfolded) "Hide" else "Show", color = P.Accent, fontSize = 14.sp)
    }
    if (!unfolded) return
    Card {
        SubLabel("HELD-BACK YEARS, FROM ${r.splitDate} · GROWTH A YEAR · SHARPE · WORST FALL")
        MetricRow("The rule", r.heldOut, P.Text)
        MetricRow("Holding ${r.benchLabel}", r.benchHeldOut, P.Muted)
        MetricRow("Same risk", r.volMatchedHeldOut, P.Muted)
        MetricRow("Costs ×2", r.costsDoubledHeldOut, P.Muted)
        SubLabel("THE YEARS IT WAS BUILT ON")
        MetricRow("The rule", r.inSample, P.Text)
        SubLabel("WHOLE PERIOD")
        MetricRow("The rule", r.full, P.Text)
        MetricRow("Holding ${r.benchLabel}", r.benchFull, P.Muted)
        Text("${r.entries} entries · ${r.orders} orders · in the market ${DeskText.plainPct(r.exposure, 0)} of days · " +
            "costs ${DeskText.num(r.costsPaid, 0)} on 100,000", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        r.bootstrap?.let { b ->
            SubLabel("RESHUFFLED HISTORY, ${b.sims} RUNS")
            Text("Sharpe ${DeskText.num(b.sharpe.first)} to ${DeskText.num(b.sharpe.third)}, below zero in ${DeskText.num(b.sharpeBelowZeroPct, 0)}% of runs; " +
                "worst fall ${DeskText.plainPct(abs(b.maxDrawdown.third))} to ${DeskText.plainPct(abs(b.maxDrawdown.first))}",
                color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (r.exitReasons.isNotEmpty()) {
            SubLabel("WHY POSITIONS CLOSED (THE YEARS IT WAS BUILT ON)")
            r.exitReasons.take(6).forEach { e ->
                Text("${e.code}: ${e.count} · average ${DeskText.num(e.avgReturnPct)}% · ${DeskText.num(e.wonPct, 0)}% made money",
                    color = P.Muted, fontSize = 12.sp)
            }
        }
        if (r.recent.isNotEmpty()) {
            SubLabel("RECENT POSITIONS")
            r.recent.take(6).forEach { e ->
                Text("${e.entry} → ${e.exit ?: "open"} · ${DeskText.num(e.returnPct)}% · ${e.exitReason.substringBefore(":")}",
                    color = P.Muted, fontSize = 12.sp)
            }
        }
    }
    Text(
        "Simulated: daily data, fills at the next open, costs included; options modelled, not quoted. " +
            "Past results don't promise future ones. A practice desk, never connected to a broker.",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp)
    )
}
