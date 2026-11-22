package app.localmind.assistant.ui.desk

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.desk.Chart
import app.localmind.assistant.domain.desk.Gate
import app.localmind.assistant.domain.desk.Metrics
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
 * The SlothDesk page (1.49): the path every idea takes (Idea → Test → Paper → Review) on its
 * front, with tonight's tickets and the Sunday review; trade ideas, price calls and the
 * calculators on their tabs. The report, the rule editor, an idea, its paper days and the
 * review open over it. Paper trading only; nothing here reaches a broker.
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
    val ideaId by viewModel.idea.collectAsStateWithLifecycle()
    val paperKey by viewModel.paperKey.collectAsStateWithLifecycle()
    val reviewing by viewModel.reviewing.collectAsStateWithLifecycle()
    val lobster by viewModel.lobster.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }
    var tab by rememberSaveable { mutableStateOf(DeskTab.STRATEGIES) }
    // A trade idea's card saved from a Trader chat opens on the Trade ideas tab; an idea for
    // the path opens its form over the front.
    val draft by trading.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft != null, draft?.kind) { if (draft != null && draft?.kind != "rule") tab = DeskTab.IDEAS }

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

    // What opens over the Desk, topmost first; Back closes them in turn.
    val opened = reportKey?.let { k -> ui.reports.firstOrNull { it.key == k } }
    val editRule = editing?.let { id -> ui.rules.firstOrNull { it.id == id } }
    val newIdea = draft?.takeIf { it.kind == "rule" }
    if (reportKey != null || editRule != null || paperKey != null || newIdea != null || ideaId != null || reviewing) {
        Box(Modifier.fillMaxSize().background(P.Canvas)) {
            when {
                reportKey != null -> {
                    BackHandler { viewModel.closeReport() }
                    if (opened != null) ReportScreen(opened, ui, viewModel, onBack = viewModel::closeReport)
                    else Column(Modifier.fillMaxSize()) {
                        SlothTopBar(title = "The six questions", onBack = viewModel::closeReport)
                        Text(ui.working ?: "This rule isn't on the Desk right now: it may be waiting for prices.",
                            color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                    }
                }
                editRule != null -> {
                    BackHandler { viewModel.closeRule() }
                    RuleEditor(editRule, ui, viewModel, onBack = viewModel::closeRule)
                }
                paperKey != null -> {
                    BackHandler { viewModel.closePaper() }
                    PaperScreen(paperKey!!, ui, viewModel, onBack = viewModel::closePaper)
                }
                newIdea != null -> {
                    BackHandler { trading.cancelDraft() }
                    NewIdeaScreen(newIdea, trading, onBack = trading::cancelDraft)
                }
                ideaId != null -> {
                    BackHandler { viewModel.closeIdea() }
                    IdeaScreen(ideaId!!, ui, viewModel, onBack = viewModel::closeIdea)
                }
                else -> {
                    BackHandler { viewModel.closeReview() }
                    ReviewScreen(ui, viewModel, onBack = viewModel::closeReview)
                }
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
                                    text = { Text("Show hidden shelf strategies (${ui.state.hidden.size})", color = P.Text) },
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
            when (tab) {
                DeskTab.CALLS -> {
                    val priceCalls: PriceCallsViewModel = hiltViewModel()
                    BoxWithConstraints(Modifier.fillMaxSize()) { PriceCallsPane(priceCalls, maxWidth >= WIDE, onAskPrices = { viewModel.updateNow() }) }
                }
                DeskTab.STRATEGIES -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val dailyMissing = remember(ui.state) { viewModel.dailyMissing() }
                    DeskFront(ui, viewModel, trading, wide = maxWidth >= WIDE, prices = {
                        PricesLine(ui, lobsterReady, onUpdate = viewModel::updateNow, onImport = pick, onOpenLobster = onOpenLobster,
                            dailyMissing = dailyMissing, onUpdateDaily = viewModel::updateDaily)
                    })
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) { TradingPane(trading, tab, maxWidth >= WIDE, snackbar) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

// --- prices ---

/** One line about the prices, with Update now; the rest is in ⋯. A first visit gets the full story. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PricesLine(
    ui: DeskUi,
    lobsterReady: Boolean,
    onUpdate: () -> Unit,
    onImport: () -> Unit,
    onOpenLobster: () -> Unit,
    /** What your rules read that the daily order doesn't fetch yet (1.50). */
    dailyMissing: List<String> = emptyList(),
    onUpdateDaily: () -> Unit = {}
) {
    if (!ui.ready) {
        Text("Opening…", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        return
    }
    if (!ui.hasData) {
        Card(Modifier.padding(top = 12.dp)) {
            Text("No prices yet", color = P.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                "SlothDesk tests your ideas as rules on daily prices, here on the tablet, writes tonight's tickets " +
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
    // What your rules read and the prices don't have yet (1.50): the Lobster fetches any of it.
    val needs = ui.waiting.values.flatten().distinct()
    if (needs.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your rules need ${needs.take(6).joinToString(", ")}" + if (needs.size > 6) " and ${needs.size - 6} more" else "",
                color = P.Warning, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (lobsterReady) Action("Fetch them", onClick = onUpdate) else Action("Import a file", onClick = onImport)
        }
    }
    if (lobsterReady && dailyMissing.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("The daily order doesn't fetch ${dailyMissing.take(4).joinToString(", ") { it.substringAfter(':') }} yet",
                color = P.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Action("Add to it", onClick = onUpdateDaily)
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
internal fun Footer() {
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
