package app.localmind.assistant.ui.desk

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.desk.StrategyReport
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.trading.DeskTab
import app.localmind.assistant.ui.trading.DeskTabs
import app.localmind.assistant.ui.trading.TradingPane
import app.localmind.assistant.ui.trading.TradingViewModel
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
fun DeskScreen(
    viewModel: DeskViewModel,
    trading: TradingViewModel,
    onBack: () -> Unit,
    onOpenLobster: () -> Unit,
    /** Your record (1.45: reached from the places calls are made, not Home). */
    onRecord: () -> Unit = {}
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val reportKey by viewModel.report.collectAsStateWithLifecycle()
    val lobster by viewModel.lobster.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }
    var marking by remember { mutableStateOf<JournalEntry?>(null) }
    var tab by rememberSaveable { mutableStateOf(DeskTab.STRATEGIES) }
    // An idea card saved from a Trader chat opens on the Ideas tab, in the editor.
    val draft by trading.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft != null) { if (draft != null) tab = DeskTab.IDEAS }

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
    var menu by remember { mutableStateOf(false) }
    // The strategy shown in full; the others are rows beneath it.
    var featured by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = ui.reports.firstOrNull { it.key == featured } ?: ui.reports.firstOrNull()

    // The report and the rule editor open over the Desk (1.48); Back closes them in turn.
    val opened = reportKey?.let { k -> ui.reports.firstOrNull { it.key == k } }
    val editRule = editing?.let { id -> ui.rules.firstOrNull { it.id == id } }
    if (reportKey != null || editRule != null) {
        Box(Modifier.fillMaxSize().background(P.Canvas)) {
            if (reportKey != null) {
                BackHandler { viewModel.closeReport() }
                if (opened != null) ReportScreen(opened, ui, viewModel, onBack = viewModel::closeReport)
                else Column(Modifier.fillMaxSize()) {
                    SlothTopBar(title = "The six questions", onBack = viewModel::closeReport)
                    Text(ui.working ?: "This rule isn't on the Desk right now: it may be waiting for prices.",
                        color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                }
            } else if (editRule != null) {
                BackHandler { viewModel.closeRule() }
                RuleEditor(editRule, ui, viewModel, onBack = viewModel::closeRule)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        return
    }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(
                title = "SlothDesk",
                subtitle = "Markets · paper trading, nothing goes to a broker",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = P.Muted)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                            if (lobsterReady && !ui.state.dailyOrderSent) {
                                DropdownMenuItem(text = { Text("Prices every evening (18:30)", color = P.Text) }, onClick = { menu = false; viewModel.daily("18:30") })
                                DropdownMenuItem(text = { Text("Prices every morning (07:00)", color = P.Text) }, onClick = { menu = false; viewModel.daily("07:00") })
                            }
                            if (!lobsterReady) {
                                DropdownMenuItem(text = { Text("Set up the Lobster", color = P.Text) }, onClick = { menu = false; onOpenLobster() })
                            }
                            DropdownMenuItem(text = { Text("Your record", color = P.Text) }, onClick = { menu = false; onRecord() })
                            DropdownMenuItem(text = { Text("Import a prices file", color = P.Text) }, onClick = { menu = false; pick() })
                            if (ui.state.hidden.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Show hidden strategies (${ui.state.hidden.size})", color = P.Text) },
                                    onClick = { menu = false; viewModel.showAll() }
                                )
                            }
                        }
                    }
                }
            )
            ui.working?.let { w ->
                Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = P.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(w, color = P.Accent, fontSize = 13.sp)
                }
            }
            DeskTabs(tab, onTab = { tab = it }, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp))
            if (tab == DeskTab.CALLS) {
                val priceCalls: PriceCallsViewModel = hiltViewModel()
                BoxWithConstraints(Modifier.fillMaxSize()) { PriceCallsPane(priceCalls, maxWidth >= WIDE, onAskPrices = { viewModel.updateNow() }) }
            } else if (tab != DeskTab.STRATEGIES) {
                BoxWithConstraints(Modifier.fillMaxSize()) { TradingPane(trading, tab, maxWidth >= WIDE, snackbar) }
            } else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth >= WIDE
                val left: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        PricesLine(ui, lobsterReady, onUpdate = viewModel::updateNow, onImport = pick, onOpenLobster = onOpenLobster)
                        if (ui.hasData) {
                            Tickets(
                                ui,
                                onMark = { marking = it },
                                onFollowed = { e -> viewModel.mark(e.strategy, e.dataDate, true) },
                                onAllFollowed = { entries -> entries.forEach { e -> viewModel.mark(e.strategy, e.dataDate, true) } }
                            )
                            if (!wide) Journal(ui)
                        }
                    }
                }
                val right: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        if (ui.hasData) {
                            shown?.let { FeaturedStrategy(it, viewModel) }
                            val others = ui.reports.filter { it.key != shown?.key }
                            if (others.isNotEmpty()) {
                                Label("OTHER STRATEGIES")
                                others.forEach { r ->
                                    HorizontalDivider(color = P.Border)
                                    StrategyRow(r) { featured = r.key }
                                }
                                HorizontalDivider(color = P.Border)
                            }
                            ui.waiting.forEach { (name, missing) ->
                                Text("$name: waiting for ${missing.joinToString(", ")}",
                                    color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                        if (ui.ready) YourRules(ui, viewModel)
                        if (ui.hasData && wide) Journal(ui)
                        Footer()
                    }
                }
                if (wide) {
                    Row(
                        Modifier.widthIn(max = 1400.dp).fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        left(Modifier.weight(0.46f).fillMaxSize().verticalScroll(rememberScrollState()))
                        right(Modifier.weight(0.54f).fillMaxSize().verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
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

/** One line about the prices, with Update now; the rest is in ⋯. A first visit gets the full story. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PricesLine(
    ui: DeskUi,
    lobsterReady: Boolean,
    onUpdate: () -> Unit,
    onImport: () -> Unit,
    onOpenLobster: () -> Unit
) {
    if (!ui.ready) {
        Text("Opening…", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        return
    }
    if (!ui.hasData) {
        Card(Modifier.padding(top = 12.dp)) {
            Text("No prices yet", color = P.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                "SlothDesk runs four starter strategies on daily prices, here on the tablet, writes tonight's tickets " +
                    "for paper trading and keeps score. Two ways to get prices:\n\n" +
                    "• The Lobster fetches them (it needs a free Tiingo key: in the SlothLobster, Settings → Market data).\n" +
                    "• Or import a prices file made in Colab (export_prices in the SlothDesk notebook).",
                color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp)
            )
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lobsterReady) Button("Ask the Lobster for prices", onUpdate) else Button("Set up the Lobster", onOpenLobster)
                Action("Import a prices file", onClick = onImport)
            }
        }
        return
    }
    val s = ui.state
    val stale = ui.ageDays > 4
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (stale) P.Warning else P.Success))
        Spacer(Modifier.width(12.dp))
        Text(
            listOfNotNull(
                "Prices to ${ui.lastDate}",
                s.pricesFrom.takeIf { it.isNotBlank() }?.let { "from ${it.replaceFirstChar { c -> c.lowercase() }}" },
                if (s.dailyOrderSent) "daily" else null
            ).joinToString(" · "),
            color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (lobsterReady) Action("Update now", onClick = onUpdate)
    }
    if (stale) {
        Text("The latest close is ${ui.ageDays} business days old: update before acting on a ticket.",
            color = P.Warning, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
    }
    s.pricesNotes.take(3).forEach {
        Text("· $it", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
    }
}

// --- tonight's tickets ---

/** Tonight's tickets as a table: what to do at the next open, the gates, and whether you followed it. */
@Composable
private fun Tickets(
    ui: DeskUi,
    onMark: (JournalEntry) -> Unit,
    onFollowed: (JournalEntry) -> Unit,
    onAllFollowed: (List<JournalEntry>) -> Unit
) {
    if (ui.reports.isEmpty()) return
    val entries = ui.reports.associate { r ->
        r.key to ui.state.journal.firstOrNull { it.strategy == r.key && it.dataDate == r.ticket.dataDate.toString() }
    }
    val unmarked = entries.values.filterNotNull().filter { it.followed.isBlank() }
    Card(Modifier.padding(top = 16.dp), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TONIGHT · FROM THE ${ui.lastDate?.let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) } ?: ""} CLOSE",
                color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (unmarked.isNotEmpty()) OutlineButton("Mark all followed") { onAllFollowed(unmarked) }
        }
        Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Strategy", color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("At the next open", color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1.3f))
            Text("Tests", color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(GATES_W))
            Text("Done", color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(DONE_W), textAlign = TextAlign.Center)
        }
        ui.reports.forEach { r ->
            HorizontalDivider(color = P.Border)
            val entry = entries[r.key]
            val researchOnly = r.gatesPassed < 4
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, color = P.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp))
                Column(Modifier.weight(1.3f).padding(end = 8.dp)) {
                    Text(
                        r.ticket.nextOpen,
                        color = when {
                            researchOnly -> P.Faint
                            r.ticket.trade -> P.Accent
                            else -> P.Text
                        },
                        fontSize = 14.sp, fontWeight = if (r.ticket.trade && !researchOnly) FontWeight.Medium else FontWeight.Normal,
                        style = Numbers, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (researchOnly) Text("Research only · ${r.gatesPassed} of ${r.gates.size} questions", color = P.Faint, fontSize = 11.sp)
                    r.guardrail?.let { Text("⚠ $it", color = P.Warning, fontSize = 12.sp, lineHeight = 16.sp) }
                }
                Box(Modifier.width(GATES_W)) { GateDots(r.gates) }
                Box(Modifier.width(DONE_W), contentAlignment = Alignment.Center) {
                    if (entry != null) DoneMark(entry, onFollowed = { onFollowed(entry) }, onDidnt = { onMark(entry) })
                }
            }
        }
    }
}

private val GATES_W = 80.dp
private val DONE_W = 48.dp

/** ✓ followed, ✕ didn't, an empty ring until you say; a tap asks which. */
@Composable
private fun DoneMark(entry: JournalEntry, onFollowed: () -> Unit, onDidnt: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val (ring, mark, label) = when (entry.followed) {
        "yes" -> Triple(P.Success, "✓", "Followed")
        "no" -> Triple(P.Danger, "✕", "Didn't follow: ${entry.mistake}")
        else -> Triple(P.BorderStrong, "", "Not marked yet")
    }
    Box {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, ring, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Mark this ticket") { asking = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Text(mark, color = ring, fontSize = 15.sp)
        }
        DropdownMenu(expanded = asking, onDismissRequest = { asking = false }, modifier = Modifier.background(P.Elevated)) {
            DropdownMenuItem(text = { Text("Followed the ticket", color = P.Text) }, onClick = { asking = false; onFollowed() })
            DropdownMenuItem(text = { Text("Didn't follow it…", color = P.Text) }, onClick = { asking = false; onDidnt() })
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
        Text("On paper means 60 journalled days, following at least 8 tickets in 10.",
            color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// --- strategies ---

/** One strategy in full: its curve, four numbers that matter, and the details on request. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeaturedStrategy(r: StrategyReport, vm: DeskViewModel) {
    var expanded by rememberSaveable(r.key) { mutableStateOf(false) }
    val t = if (r.mine) null else DeskStrategies.templates.firstOrNull { it.key == r.key }
    Card(Modifier.padding(top = 16.dp), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.label, color = P.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).semantics { heading() })
            GateDots(r.gates)
            Text("  ${r.gatesPassed}/${r.gates.size}", color = P.Faint, fontSize = 12.sp, style = Numbers)
        }
        Spacer(Modifier.height(14.dp))
        EquityChart(r.chart, height = 170.dp)
        Text("Growth of 1 on a log scale · strategy (green) and buy-and-hold (grey); the shaded years were held out.",
            color = P.Faint, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Figure("Held-out Sharpe", DeskText.num(r.heldOut?.sharpe), Modifier.weight(1f))
            Figure("Same-risk hold", DeskText.num(r.volMatchedHeldOut?.sharpe), Modifier.weight(1f))
            Figure("Worst drop", r.heldOut?.let { "−" + DeskText.plainPct(abs(it.maxDrawdown)) } ?: "–", Modifier.weight(1f))
            Figure("Luck check", r.robustInSample?.let { DeskText.num(it.dsr) } ?: "–", Modifier.weight(1f))
        }
        r.robustInSample?.let { rb ->
            Text(
                rb.minTrackRecordYears?.let { "Needs about ${DeskText.num(it, 1)} years of results to tell from zero" }
                    ?: "At this Sharpe, no track record would tell it from zero",
                color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
            )
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.verdict.first, color = if (r.verdict.first == "Research only") P.Muted else P.Accent, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Action("The six questions") { vm.openReport(r.key) }
        }
        if (!expanded) {
            Row(Modifier.padding(top = 2.dp)) {
                Action(if (t != null) "Details and settings" else "Details", muted = true) { expanded = true }
                Spacer(Modifier.weight(1f))
                if (r.mine) Action("Edit the rule") { vm.openRule(r.key.removePrefix(DeskViewModel.MINE)) }
                else Action("Make my copy") { vm.copyShelf(r.key) }
            }
            return@Card
        }
        Text(t?.blurb ?: RuleWords.rule(r.rule), color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))

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

        SubLabel("THE SIX QUESTIONS")
        r.gates.forEach { g -> GateRow(g) }

        val params = r.params
        if (t != null && params != null) {
            SubLabel("SETTINGS" + if (r.isDefault) " · DEFAULT" else " · CHANGED")
            t.knobs.forEach { k ->
                val v = params[k.key]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.label, color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Action("−", muted = true) { vm.set(r.key, k.key, v - k.step) }
                    Text(DeskText.num(v, if (k.step < 0.01) 4 else if (k.step < 1) 2 else 0) + k.unit, color = P.Text, fontSize = 14.sp)
                    Action("+", muted = true) { vm.set(r.key, k.key, v + k.step) }
                }
            }
            Text("Each new setting counts as a trial: the more you try, the higher the luck bar. " +
                "To change more than these two, make your own copy.", color = P.Faint, fontSize = 12.sp)
        }

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
            if (r.mine) Action("Edit the rule") { vm.openRule(r.key.removePrefix(DeskViewModel.MINE)) }
            else Action("Make my copy") { vm.copyShelf(r.key) }
            if (!r.isDefault && !r.mine) Action("Back to default", muted = true) { vm.reset(r.key) }
            Action("Hide", muted = true) { vm.hide(r.key) }
            Action("Less", muted = true) { expanded = false }
        }
    }
}

/** A figure with its label above, in fixed-width digits. */
@Composable
internal fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = P.Text, fontSize = 22.sp, style = Numbers, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Another strategy, in one line; a tap shows it in full. */
@Composable
private fun StrategyRow(r: StrategyReport, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClickLabel = "Show ${r.label}", onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(r.label, color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Sharpe ${DeskText.num(r.heldOut?.sharpe)} held out", color = P.Muted, fontSize = 13.sp, style = Numbers)
        Spacer(Modifier.width(14.dp))
        GateDots(r.gates)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun MetricRow(label: String, m: Metrics?, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.weight(1.1f))
        Text(DeskText.pct(m?.cagr), color = color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(DeskText.num(m?.sharpe), color = color, fontSize = 13.sp, modifier = Modifier.weight(0.8f))
        Text(m?.let { DeskText.plainPct(abs(it.maxDrawdown)) } ?: "–", color = color, fontSize = 13.sp, modifier = Modifier.weight(0.9f))
    }
}

@Composable
internal fun GateRow(g: Gate) {
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

// --- your rules ---

/** Your rules, with a way to start one: new, or a copy from the shelf. */
@Composable
private fun YourRules(ui: DeskUi, vm: DeskViewModel) {
    var shelf by remember { mutableStateOf(false) }
    Label("YOUR RULES")
    Card {
        if (ui.rules.isEmpty()) {
            Text("Write a strategy as a rule from blocks: when these hold, then hold this. Start from scratch, or copy one " +
                "from the shelf and change it. Each version you test is one more try, and the luck question counts them.",
                color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        ui.rules.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = P.Border)
            val rep = ui.reports.firstOrNull { it.key == m.key }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${m.name}") { vm.openRule(m.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            if (m.from.startsWith("shelf:")) "from the shelf" else "your rule",
                            if (m.tries == 1) "1 try" else "${m.tries} tries",
                            when {
                                m.problem != null -> "can't be read"
                                !m.tested -> "not tested yet"
                                m.changed -> "changed since the test"
                                else -> null
                            }
                        ).joinToString(" · "),
                        color = if (m.problem != null) P.Warning else P.Faint, fontSize = 12.sp
                    )
                }
                if (rep != null) {
                    GateDots(rep.gates)
                    Spacer(Modifier.width(6.dp))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Action("New rule") { vm.newRule() }
            Box {
                Action("Copy from the shelf", muted = true) { shelf = true }
                DropdownMenu(expanded = shelf, onDismissRequest = { shelf = false }, modifier = Modifier.background(P.Elevated)) {
                    DeskStrategies.templates.forEach { t ->
                        DropdownMenuItem(text = { Text(t.label, color = P.Text) }, onClick = { shelf = false; vm.copyShelf(t.key) })
                    }
                }
            }
        }
    }
}

/** Six small marks: filled for passed, ringed for not passed, faint for not checked. */
@Composable
internal fun GateDots(gates: List<Gate>) {
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
internal fun EquityChart(c: Chart, height: androidx.compose.ui.unit.Dp = 130.dp) {
    val accent = P.Accent
    val grey = P.Faint
    val grid = P.Border
    val shade = P.Elevated
    Canvas(Modifier.fillMaxWidth().height(height)) {
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
        drawRect(shade, topLeft = Offset(sx, 0f), size = androidx.compose.ui.geometry.Size(size.width - sx, size.height))
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
internal fun Card(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(P.Card).border(1.dp, P.Border, shape).padding(padding)
    ) { content() }
}

@Composable
internal fun Label(text: String) {
    Text(
        text, color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp).semantics { heading() }
    )
}

/** Fixed-width digits, so figures line up. */
internal val Numbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
internal fun OutlineButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = P.Text, fontSize = 13.sp,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, P.BorderStrong, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
internal fun SubLabel(text: String) {
    Text(text, color = P.Faint, fontSize = 10.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
internal fun Action(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (muted) P.Muted else P.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
internal fun Button(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = P.OnAccent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(P.Accent).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}
