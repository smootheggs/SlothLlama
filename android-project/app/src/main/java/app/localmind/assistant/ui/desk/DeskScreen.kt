package app.localmind.assistant.ui.desk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.desk.JournalEntry
import app.localmind.assistant.domain.desk.Chart
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Gate
import app.localmind.assistant.domain.desk.Metrics
import app.localmind.assistant.domain.desk.StrategyReport
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ln

/**
 * The SlothDesk page: prices, tonight's tickets, the strategies with their evidence, and the
 * paper journal. Paper trading only; nothing here reaches a broker.
 */
@Composable
fun DeskScreen(viewModel: DeskViewModel, onBack: () -> Unit, onOpenLobster: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val lobster by viewModel.lobster.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }
    var marking by remember { mutableStateOf<JournalEntry?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(30_000)
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val lobsterReady = lobster.enabled && lobster.folderName != null
    val pick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(title = "SlothDesk", subtitle = "Paper trading · nothing goes to a broker", onBack = onBack)
            ui.working?.let { w ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = P.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(w, color = P.Accent, fontSize = 13.sp)
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth >= WIDE
                val left: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        DataCard(ui, lobsterReady, onUpdate = viewModel::updateNow, onDaily = viewModel::daily,
                            onImport = pick, onOpenLobster = onOpenLobster)
                        if (ui.hasData) {
                            Tickets(ui, onMark = { marking = it }, onFollowed = { e -> viewModel.mark(e.strategy, e.dataDate, true) })
                            Journal(ui)
                        }
                    }
                }
                val right: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        if (ui.hasData) {
                            Label("STRATEGIES")
                            ui.reports.forEach { r -> StrategyCard(r, viewModel) }
                            ui.waiting.forEach { (key, missing) ->
                                Text("${DeskStrategies.template(key).label}: waiting for ${missing.joinToString(", ")}",
                                    color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            if (ui.state.hidden.isNotEmpty()) Action("Show hidden strategies (${ui.state.hidden.size})", muted = true) { viewModel.showAll() }
                        }
                        Footer()
                    }
                }
                if (wide) {
                    Row(Modifier.widthIn(max = 1400.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                        left(Modifier.weight(0.42f).fillMaxSize().verticalScroll(rememberScrollState()))
                        VerticalDivider(color = P.Border, modifier = Modifier.padding(16.dp))
                        right(Modifier.weight(0.58f).fillMaxSize().verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        left(Modifier.fillMaxWidth())
                        right(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    marking?.let { e ->
        AlertDialog(
            onDismissRequest = { marking = null },
            containerColor = P.Elevated,
            title = { Text("What happened instead?", color = P.Text) },
            text = {
                Column {
                    Text("Tags show which slips cost you, over time.", color = P.Muted, fontSize = 13.sp)
                    DeskStore.MISTAKES.filter { it != "none" }.forEach { m ->
                        Text(m.replaceFirstChar { it.uppercase() }, color = P.Accent, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.mark(e.strategy, e.dataDate, false, m); marking = null }
                                .padding(horizontal = 8.dp, vertical = 12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { marking = null }) { Text("Cancel", color = P.Muted) } }
        )
    }
}

// --- prices ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataCard(
    ui: DeskUi,
    lobsterReady: Boolean,
    onUpdate: () -> Unit,
    onDaily: (String) -> Unit,
    onImport: () -> Unit,
    onOpenLobster: () -> Unit
) {
    Label("PRICES")
    Card {
        if (!ui.ready) {
            Text("Opening…", color = P.Muted, fontSize = 14.sp)
            return@Card
        }
        if (!ui.hasData) {
            Text("No prices yet", color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "SlothDesk runs four starter strategies on daily prices, here on the tablet, writes tonight's tickets " +
                    "for paper trading and keeps score. Two ways to get prices:\n\n" +
                    "• The Lobster fetches them (it needs a free Tiingo key: in the SlothLobster, Settings → Market data).\n" +
                    "• Or import a prices file made in Colab (export_prices in the SlothDesk notebook).",
                color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp)
            )
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lobsterReady) Button("Ask the Lobster for prices", onUpdate) else Button("Set up the Lobster", onOpenLobster)
                Action("Import a prices file", onClick = onImport)
            }
            return@Card
        }
        val s = ui.state
        Text("Prices to ${ui.lastDate}", color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            buildString {
                append(if (s.pricesFrom.isNotBlank()) "From ${s.pricesFrom}" else "Stored on the tablet")
                append(" · ").append(ui.series.joinToString(" "))
            },
            color = P.Faint, fontSize = 12.sp
        )
        if (ui.ageDays > 4) {
            Text("The latest close is ${ui.ageDays} business days old: update before acting on a ticket.",
                color = P.Warning, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        s.pricesNotes.take(4).forEach { Text("• $it", color = P.Muted, fontSize = 12.sp) }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (lobsterReady) {
                Action("Update now", onClick = onUpdate)
                if (!s.dailyOrderSent) {
                    Action("Every evening (18:30)") { onDaily("18:30") }
                    Action("Every morning (07:00)", muted = true) { onDaily("07:00") }
                }
            } else {
                Action("Set up the Lobster", muted = true, onClick = onOpenLobster)
            }
            Action("Import a file", muted = true, onClick = onImport)
        }
        if (s.dailyOrderSent) Text("Daily prices are on. To stop them, switch the repeat off in the SlothLobster.", color = P.Faint, fontSize = 12.sp)
    }
}

// --- tonight's tickets ---

@Composable
private fun Tickets(ui: DeskUi, onMark: (JournalEntry) -> Unit, onFollowed: (JournalEntry) -> Unit) {
    if (ui.reports.isEmpty()) return
    Label("TONIGHT'S TICKETS · FROM THE ${ui.lastDate} CLOSE")
    ui.reports.forEach { r ->
        Card(Modifier.padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                GateDots(r.gates)
            }
            Text(r.ticket.nextOpen, color = if (r.ticket.trade) P.Accent else P.Muted, fontSize = 14.sp,
                fontWeight = if (r.ticket.trade) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
            Text("Now: ${r.ticket.positionNow}", color = P.Faint, fontSize = 12.sp)
            r.guardrail?.let { Text("⚠ $it", color = P.Warning, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (r.gatesPassed < 4) {
                Text("Research only: ${r.gatesPassed} of ${r.gates.size} gates passed.", color = P.Faint, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            val entry = ui.state.journal.firstOrNull { it.strategy == r.key && it.dataDate == r.ticket.dataDate.toString() }
            if (entry != null) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (entry.followed) {
                        "yes" -> Text("Marked: followed", color = P.Success, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        "no" -> Text("Marked: didn't follow (${entry.mistake})", color = P.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        else -> Text("Paper journal", color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                    Action("Followed") { onFollowed(entry) }
                    Action("Didn't", muted = true) { onMark(entry) }
                }
            }
        }
    }
}

@Composable
private fun Journal(ui: DeskUi) {
    val j = ui.state.journal
    if (j.isEmpty()) return
    Label("PAPER JOURNAL")
    Card {
        ui.reports.forEach { r ->
            val mine = j.filter { it.strategy == r.key }
            val tagged = mine.filter { it.followed == "yes" || it.followed == "no" }
            val share = if (tagged.isEmpty()) null else tagged.count { it.followed == "yes" } * 100 / tagged.size
            Text("${r.label}: ${mine.size} day(s), ${tagged.size} marked" + (share?.let { ", rules followed $it%" } ?: ""),
                color = P.Text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
        val slips = j.filter { it.followed == "no" }.groupingBy { it.mistake }.eachCount()
        if (slips.isNotEmpty()) {
            Text("Slips: " + slips.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" },
                color = P.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Text("Gate 6 wants 60 journalled days with the rules followed on 80% of marked days.",
            color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// --- strategies ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyCard(r: StrategyReport, vm: DeskViewModel) {
    var expanded by rememberSaveable(r.key) { mutableStateOf(false) }
    val t = DeskStrategies.template(r.key)
    Card(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.label, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Held out: " + DeskText.line(r.heldOut), color = P.Muted, fontSize = 12.sp)
            }
            GateDots(r.gates)
            Text(" ${r.gatesPassed}/${r.gates.size}", color = P.Faint, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        EquityChart(r.chart)
        Text("Growth of 1 · strategy (accent) against buy-and-hold (grey); dashes mark where the held-out years start.",
            color = P.Faint, fontSize = 11.sp)
        if (!expanded) {
            Action("Details, settings and gates", muted = true) { expanded = true }
            return@Card
        }
        Text(t.blurb, color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))

        SubLabel("HELD-OUT YEARS, FROM ${r.splitDate}")
        MetricRow("Strategy", r.heldOut, P.Text)
        MetricRow("Buy and hold", r.benchHeldOut, P.Muted)
        MetricRow("Same risk", r.volMatchedHeldOut, P.Muted)
        MetricRow("Costs ×2", r.costsDoubledHeldOut, P.Muted)
        SubLabel("WHOLE PERIOD")
        MetricRow("Strategy", r.full, P.Text)
        MetricRow("Buy and hold", r.benchFull, P.Muted)
        Text("${r.entries} entries · ${r.orders} orders · in the market ${DeskText.plainPct(r.exposure, 0)} of days · " +
            "costs ${DeskText.num(r.costsPaid, 0)} on 100,000", color = P.Faint, fontSize = 12.sp)

        SubLabel("IS IT LUCK?")
        r.robustInSample?.let { rb ->
            Text("Deflated Sharpe ${DeskText.num(rb.dsr)} (needs 0.90) after ${rb.trials} setting(s): chance alone could reach " +
                "an annual Sharpe of ${DeskText.num(rb.luckHurdleAnnual)}" + if (!rb.trialSpreadMeasured) ", spread assumed" else "",
                color = P.Text, fontSize = 13.sp)
            Text("Track record to tell it from zero: " +
                (rb.minTrackRecordYears?.let { "about ${DeskText.num(it, 1)} years" } ?: "never, at this Sharpe"),
                color = P.Muted, fontSize = 12.sp)
        }
        r.bootstrap?.let { b ->
            Text("Reshuffled history, ${b.sims} runs: Sharpe ${DeskText.num(b.sharpe.first)} to ${DeskText.num(b.sharpe.third)}; " +
                "below zero in ${DeskText.num(b.sharpeBelowZeroPct, 0)}%", color = P.Muted, fontSize = 12.sp)
        }

        SubLabel("GATES")
        r.gates.forEach { g -> GateRow(g) }

        SubLabel("SETTINGS" + if (r.isDefault) " · DEFAULT" else " · CHANGED")
        t.knobs.forEach { k ->
            val v = r.params[k.key]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.label, color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Action("−", muted = true) { vm.set(r.key, k.key, v - k.step) }
                Text(DeskText.num(v, if (k.step < 0.01) 4 else if (k.step < 1) 2 else 0) + k.unit, color = P.Text, fontSize = 14.sp)
                Action("+", muted = true) { vm.set(r.key, k.key, v + k.step) }
            }
        }
        Text("Each new setting counts as a trial: the more you try, the higher the luck bar.", color = P.Faint, fontSize = 12.sp)

        if (r.exitReasons.isNotEmpty()) {
            SubLabel("WHY POSITIONS CLOSED (IN-SAMPLE)")
            r.exitReasons.take(5).forEach { e ->
                Text("${e.code}: ${e.count} · average ${DeskText.num(e.avgReturnPct)}% · ${DeskText.num(e.wonPct, 0)}% made money",
                    color = P.Muted, fontSize = 12.sp)
            }
        }
        if (r.recent.isNotEmpty()) {
            SubLabel("RECENT POSITIONS")
            r.recent.take(5).forEach { e ->
                Text("${e.entry} → ${e.exit ?: "open"} · ${DeskText.num(e.returnPct)}% · ${e.exitReason.substringBefore(":")}",
                    color = P.Muted, fontSize = 12.sp)
            }
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Action("Check nearby settings") { vm.checkNearby(r.key) }
            Action("Ask about this") { vm.askAbout(r) }
            if (!r.isDefault) Action("Back to default", muted = true) { vm.reset(r.key) }
            Action("Hide", muted = true) { vm.hide(r.key) }
            Action("Less", muted = true) { expanded = false }
        }
    }
}

@Composable
private fun MetricRow(label: String, m: Metrics?, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.weight(1.1f))
        Text(DeskText.pct(m?.cagr), color = color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(DeskText.num(m?.sharpe), color = color, fontSize = 13.sp, modifier = Modifier.weight(0.8f))
        Text(m?.let { DeskText.plainPct(abs(it.maxDrawdown)) } ?: "–", color = color, fontSize = 13.sp, modifier = Modifier.weight(0.9f))
    }
}

@Composable
private fun GateRow(g: Gate) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(when (g.passed) { true -> "✓"; false -> "✗"; null -> "○" },
            color = when (g.passed) { true -> P.Success; false -> P.Danger; null -> P.Faint },
            fontSize = 14.sp, modifier = Modifier.width(22.dp))
        Column {
            Text(g.name, color = P.Text, fontSize = 13.sp)
            Text(g.detail, color = P.Faint, fontSize = 12.sp)
        }
    }
}

/** Six small marks: filled for passed, ringed for not passed, faint for not checked. */
@Composable
private fun GateDots(gates: List<Gate>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        gates.forEach { g ->
            val c = when (g.passed) { true -> P.Success; false -> P.Danger; null -> P.Border }
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (g.passed == true) c else Color.Transparent)
                    .border(1.dp, c, CircleShape)
            )
        }
    }
}

@Composable
private fun EquityChart(c: Chart) {
    val accent = P.Accent
    val grey = P.Faint
    val grid = P.Border
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val all = (c.strategy.toList() + c.benchmark.toList()).filter { !it.isNaN() && it > 0 }
        if (all.size < 2 || c.dates.size < 2) return@Canvas
        // Log scale: equal heights are equal percentage moves.
        val lo = ln(all.min())
        val hi = ln(all.max()).let { if (it - lo < 1e-9) lo + 1 else it }
        val span = ChronoUnit.DAYS.between(c.dates.first(), c.dates.last()).toFloat().coerceAtLeast(1f)
        fun x(i: Int) = size.width * ChronoUnit.DAYS.between(c.dates.first(), c.dates[i]) / span
        fun y(v: Double) = (size.height * (1 - (ln(v) - lo) / (hi - lo))).toFloat()
        val one = y(1.0)
        drawLine(grid, Offset(0f, one), Offset(size.width, one), strokeWidth = 1f)
        val sx = size.width * ChronoUnit.DAYS.between(c.dates.first(), c.split) / span
        drawLine(grid, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        fun path(v: DoubleArray): Path {
            val p = Path()
            var started = false
            for (i in v.indices) {
                if (v[i].isNaN() || v[i] <= 0) continue
                if (!started) { p.moveTo(x(i), y(v[i])); started = true } else p.lineTo(x(i), y(v[i]))
            }
            return p
        }
        drawPath(path(c.benchmark), grey, style = Stroke(width = 2f))
        drawPath(path(c.strategy), accent, style = Stroke(width = 3f))
    }
}

@Composable
private fun Footer() {
    Text(
        "Simulated results: daily data, fills at the next open, slippage, commission, borrow and financing costs " +
            "included; options are modelled from the volatility index, not real quotes. Past results don't promise " +
            "future ones. This is a practice desk, not advice, and it never connects to a broker.",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 16.dp)
    )
}

// --- small pieces ---

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(P.Card).border(1.dp, P.Border, shape).padding(14.dp)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun SubLabel(text: String) {
    Text(text, color = P.Faint, fontSize = 10.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Action(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (muted) P.Muted else P.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun Button(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = P.OnAccent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(P.Accent).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}
