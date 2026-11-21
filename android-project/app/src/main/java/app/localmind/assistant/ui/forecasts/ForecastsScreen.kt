package app.localmind.assistant.ui.forecasts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Band
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.forecasts.Summary
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.ConfirmDialog
import app.localmind.assistant.ui.common.EmptyLine
import app.localmind.assistant.ui.common.Section
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.common.showDay
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Forecasts: the ledger of every call (yours, and SlothDesk's ideas read in place), how well your
 * chances match what happened, and one call's page. Back steps from a call to the ledger.
 */
@Composable
fun ForecastsScreen(viewModel: ForecastsViewModel, onBack: () -> Unit, onDesk: () -> Unit) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val openId by viewModel.openId.collectAsStateWithLifecycle()
    val area by viewModel.area.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val opened = calls.firstOrNull { it.id == openId }

    BackHandler(enabled = openId != null) { viewModel.open(null) }
    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        if (opened != null) CallPage(opened, today, viewModel, onBack = { viewModel.open(null) }, onDesk = onDesk)
        else Ledger(calls, area, today, onBack, viewModel)
    }
}

@Composable
private fun Ledger(calls: List<Call>, area: Area?, today: LocalDate, onBack: () -> Unit, vm: ForecastsViewModel) {
    var adding by remember { mutableStateOf(false) }
    val shown = if (area == null) calls else calls.filter { it.area == area }
    val summary = remember(shown, today) { Forecasts.summary(shown, today) }
    val toCheck = shown.filter { it.open }.sortedBy { it.checkOn }
    val checked = shown.filter { !it.open }.sortedByDescending { it.markedOn }

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(title = "Forecasts", subtitle = "Every call with a chance and a date", onBack = onBack) {
            TextButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = P.Accent, modifier = Modifier.size(18.dp))
                Text(" New call", color = P.Accent, fontSize = 14.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoicePill("All", area == null, { vm.showArea(null) })
            Area.entries.forEach { a -> ChoicePill(a.label, area == a, { vm.showArea(a) }) }
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 900.dp
            Column(
                Modifier.widthIn(max = 1100.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (calls.isEmpty()) {
                    Intro(onNew = { adding = true })
                } else {
                    SummaryCard(summary, wide)
                    Section("To check", note = if (toCheck.isEmpty()) null else "Soonest first; the date is yours to move") {
                        if (toCheck.isEmpty()) EmptyLine("Nothing open here.")
                        toCheck.forEach { c -> CallRow(c, today) { vm.open(c.id) } }
                    }
                    if (checked.isNotEmpty()) {
                        Section("Checked") { checked.take(30).forEach { c -> CallRow(c, today) { vm.open(c.id) } } }
                    }
                }
                Text(
                    "Chances and scores are worked out on the tablet. SlothDesk's ideas show here as they are; review them in SlothDesk.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
    if (adding) {
        NewCallDialog(area ?: Area.OTHER, onDismiss = { adding = false }) { q, a, kind, options, line, check, chances, reason, market, where ->
            vm.create(q, a, kind, options, line, check, chances, reason, market, where)
        }
    }
}

@Composable
private fun Intro(onNew: () -> Unit) {
    Column(Modifier.widthIn(max = 620.dp).padding(horizontal = 12.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Say how likely, then see", color = P.Text, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        Text(
            "A call is a question about what will happen, your chance for each answer, and a day to check it: a market " +
                "level, a game, a vote, a risk on the watchlist. Change your chance whenever you learn something, with a line on why. " +
                "On the day, mark what happened.",
            color = P.Muted, fontSize = 16.sp, lineHeight = 24.sp
        )
        Text(
            "The tablet keeps the score: how often your 70% calls come true, and the Brier score, where 0 is never wrong " +
                "and 0.25 is saying 50% on everything. SlothDesk's ideas join in at the conviction you saved them with.",
            color = P.Muted, fontSize = 16.sp, lineHeight = 24.sp
        )
        Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) { Text("New call") }
    }
}

@Composable
private fun SummaryCard(s: Summary, wide: Boolean) {
    val inner: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Fact(if (s.checked == 0) "—" else "${s.checked}", if (s.checked == 1) "call checked" else "calls checked")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Fact(s.brier?.let { String.format(java.util.Locale.ENGLISH, "%.2f", it) } ?: "—", "Brier score · lower is better")
                s.brier?.let { b -> Gauge(b / 0.25) }
                Text("0.25 is saying 50% on every yes-or-no; 0 is never wrong.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
            }
            bandLine(s.bands)?.let { (line, sub) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line, color = P.Text, fontSize = 14.sp, lineHeight = 20.sp)
                    Text(sub, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Fact("${s.toCheck}", "to check now", if (s.toCheck > 0) P.Accent else P.Text)
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallLabel("When you say it, does it happen?")
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CalibrationChart(s.bands, Modifier.size(260.dp))
                Box(Modifier.weight(1f).padding(top = 8.dp)) { inner() }
            }
        } else {
            CalibrationChart(s.bands, Modifier.fillMaxWidth().height(240.dp))
            inner()
        }
    }
}

/** The band with the most calls that's furthest off, in words: "Your 80% calls came true 4 times in 6." */
private fun bandLine(bands: List<Band>): Pair<String, String>? {
    val b = bands.filter { it.count >= 3 }.maxByOrNull { kotlin.math.abs(it.said - it.happened) * sqrt(it.count.toDouble()) } ?: return null
    val hits = Math.round(b.happened * b.count).toInt()
    val line = "Your ${b.from}% calls came true $hits ${if (hits == 1) "time" else "times"} in ${b.count}."
    val gap = b.happened - b.said
    val sub = when {
        b.count < 10 && kotlin.math.abs(gap) > 0.1 -> "A small number still, so take it lightly."
        gap < -0.1 -> "Over-sure there, so far."
        gap > 0.1 -> "Under-sure there: you could say more."
        else -> "About right."
    }
    return line to sub
}

@Composable
private fun Fact(value: String, label: String, color: Color = P.Text) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        Text(label, color = P.Muted, fontSize = 12.sp)
    }
}

/** A thin bar: how far along toward a coin flip (1 = 0.25). */
@Composable
private fun Gauge(fraction: Double) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(P.Elevated)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02, 1.0).toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(P.Accent))
    }
}

/** What you said (across) against how often it happened (up), 50–100%; the dashed line is spot on. */
@Composable
private fun CalibrationChart(bands: List<Band>, modifier: Modifier) {
    val grid = P.Border
    val diag = P.BorderStrong
    val dot = P.Accent
    val faint = P.Faint
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize().padding(start = 26.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                val w = size.width
                val h = size.height
                fun x(p: Double) = ((p - 0.5) / 0.5).toFloat().coerceIn(0f, 1f) * w
                fun y(p: Double) = h - ((p - 0.5) / 0.5).toFloat().coerceIn(0f, 1f) * h
                drawRect(grid, style = Stroke(1f))
                listOf(0.6, 0.7, 0.8, 0.9).forEach { v -> drawLine(grid, Offset(x(v), 0f), Offset(x(v), h), 1f) }
                drawLine(diag, Offset(0f, h), Offset(w, 0f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                bands.forEach { b ->
                    val r = (6f + 2.2f * sqrt(b.count.toFloat())).coerceAtMost(22f)
                    drawCircle(dot.copy(alpha = 0.85f), r, Offset(x(b.said), y(max(b.happened, 0.5))))
                }
            }
            Text("100", color = faint, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart))
            Text("50", color = faint, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart))
        }
        Row(Modifier.fillMaxWidth().padding(start = 26.dp)) {
            Text("50%", color = faint, fontSize = 10.sp)
            Text("what you said", color = faint, fontSize = 10.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("100%", color = faint, fontSize = 10.sp)
        }
    }
}

/** "56%" for a yes-or-no's yes (or above the line); a several-way call shows its favourite. */
private fun headline(c: Call): String {
    val ch = c.latest?.chances ?: return "—"
    return if (c.kind == CallKind.ONE_OF) {
        val top = ch.indices.maxByOrNull { ch[it] } ?: 0
        "${c.outcomes.getOrNull(top).orEmpty()} ${Forecasts.percent(ch[top])}%"
    } else "${Forecasts.percent(ch[0])}%"
}

@Composable
private fun CallRow(c: Call, today: LocalDate, onClick: () -> Unit) {
    val check = Forecasts.date(c.checkOn)
    val due = Forecasts.due(c, today)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            c.area.label.uppercase(), color = if (c.area == Area.WATCHLIST) P.Warning else P.Faint, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, modifier = Modifier.width(74.dp)
        )
        Text(c.question, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(headline(c), color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Box(Modifier.width(92.dp), contentAlignment = Alignment.CenterEnd) {
            when {
                c.void -> Text("didn't count", color = P.Faint, fontSize = 12.sp)
                c.happened != null -> {
                    val top = c.latest?.chances?.let { ch -> ch.indices.maxByOrNull { ch[it] } }
                    if (c.happened == top) Icon(Icons.Outlined.Check, contentDescription = "Went your way", tint = P.Success, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Outlined.Close, contentDescription = "Didn't go your way", tint = P.Warning, modifier = Modifier.size(18.dp))
                }
                check != null -> Text(
                    if (due) "check now" else Forecasts.relative(check, today), color = if (due) P.Accent else P.Faint,
                    fontSize = 12.sp, fontWeight = if (due) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

// --- one call ---

private sealed interface Open {
    data object Chance : Open
    data object Market : Open
    data object Move : Open
    data object Delete : Open
}

@Composable
private fun CallPage(c: Call, today: LocalDate, vm: ForecastsViewModel, onBack: () -> Unit, onDesk: () -> Unit) {
    var dialog by remember { mutableStateOf<Open?>(null) }
    val check = Forecasts.date(c.checkOn)
    val score = remember(c) { Forecasts.score(c) }
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = "A call",
            subtitle = c.area.label + " · " + if (c.readOnly) "an idea in SlothDesk" else "yours",
            onBack = onBack
        ) {
            if (!c.readOnly) {
                IconButton(onClick = { dialog = Open.Move }) { Icon(Icons.Outlined.Event, contentDescription = "Move the check date", tint = P.Text) }
                IconButton(onClick = { dialog = Open.Delete }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete the call", tint = P.Text) }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 820.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(c.question, color = P.Text, fontSize = 24.sp, lineHeight = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                ChancesNow(c)
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    c.market.lastOrNull()?.let { m ->
                        Fact("${Forecasts.percent(m.chances[0])}%", "a market's chance" + if (m.reason.isNotBlank()) " · ${m.reason}" else "", P.Muted)
                    }
                    check?.let { d ->
                        val days = ChronoUnit.DAYS.between(today, d)
                        Fact(
                            when {
                                !c.open -> showDay(c.markedOn.ifBlank { c.checkOn })
                                days >= 0 -> if (days == 1L) "1 day" else "$days days"
                                else -> "${-days} days ago"
                            },
                            if (!c.open) "marked" else if (days >= 0) "to the check date, ${showDay(c.checkOn)}" else "the check date passed"
                        )
                    }
                    score?.let { Fact(String.format(java.util.Locale.ENGLISH, "%.2f", it), "this call's Brier score") }
                }
                if (c.kind != CallKind.ONE_OF && (c.estimates.size > 1 || c.market.isNotEmpty())) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallLabel("How your chance moved", Modifier.weight(1f))
                            Text("— you", color = P.Accent, fontSize = 12.sp)
                            if (c.market.isNotEmpty()) Text("   - - market", color = P.Muted, fontSize = 12.sp)
                        }
                        Trail(c, today, Modifier.fillMaxWidth().height(120.dp))
                        Text(
                            "Scoring uses the chance you held on each day, so changing your mind early counts in your favour.",
                            color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp
                        )
                    }
                }
                Section("Your reasons") {
                    c.estimates.withIndex().reversed().forEach { (i, e) ->
                        val before = c.estimates.getOrNull(i - 1)
                        val change = chanceText(c, e.chances) + (before?.let { "  (was ${chanceText(c, it.chances)})" } ?: "")
                        Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${showDay(e.on)} · $change", color = P.Faint, fontSize = 12.sp)
                            Text(e.reason.ifBlank { "No reason written" }, color = if (e.reason.isBlank()) P.Faint else P.Text, fontSize = 15.sp, lineHeight = 21.sp)
                        }
                    }
                }
                if (c.readOnly) {
                    Text(
                        "This is an idea in SlothDesk, at the conviction you saved it with. Its review there marks it here.",
                        color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    OutlinedButton(onClick = onDesk) { Text("Open SlothDesk", color = P.Text) }
                } else {
                    Actions(c, onChance = { dialog = Open.Chance }, onMarket = { dialog = Open.Market }, onMark = { h, v -> vm.mark(c.id, h, v) })
                }
            }
        }
    }
    val close = { dialog = null }
    when (dialog) {
        Open.Chance -> ChanceDialog(c, onDismiss = close) { chances, reason -> vm.changeChance(c.id, chances, reason) }
        Open.Market -> MarketDialog(onDismiss = close) { chances, where -> vm.addMarket(c.id, chances, where) }
        Open.Move -> MoveDialog(c.checkOn, onDismiss = close) { vm.moveCheck(c.id, it) }
        Open.Delete -> ConfirmDialog("Delete this call?", c.question, "Delete", onDismiss = close, onConfirm = { vm.delete(c.id) })
        null -> Unit
    }
}

private fun chanceText(c: Call, chances: List<Double>): String =
    if (c.kind == CallKind.ONE_OF) c.outcomes.zip(chances).joinToString(" · ") { (o, p) -> "$o ${Forecasts.percent(p)}%" }
    else "${Forecasts.percent(chances[0])}%"

/** Your latest chances: one big number, or a bar for each outcome. */
@Composable
private fun ChancesNow(c: Call) {
    val ch = c.latest?.chances ?: return
    if (c.kind == CallKind.ONE_OF) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            c.outcomes.zip(ch).forEachIndexed { i, (o, p) ->
                val happened = c.happened == i
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(o, color = if (happened) P.Accent else P.Text, fontSize = 15.sp, fontWeight = if (happened) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.width(180.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.Elevated)) {
                        Box(Modifier.fillMaxWidth(p.toFloat().coerceIn(0.01f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.Accent))
                    }
                    Text("${Forecasts.percent(p)}%", color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(48.dp))
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${Forecasts.percent(ch[0])}%", color = P.Accent, fontSize = 44.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Text(
                "your chance of " + (if (c.kind == CallKind.NUMBER) c.outcomes[0].lowercase() else "yes") +
                    when (c.happened) { 0 -> " · it happened"; 1 -> " · it didn't"; else -> "" },
                color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }
}

/** Your chance (solid) and the market's (dashed) from the first day to now, on a 0–100% scale. */
@Composable
private fun Trail(c: Call, today: LocalDate, modifier: Modifier) {
    val you = P.Accent
    val market = P.Muted
    val grid = P.Border
    val start = listOfNotNull(c.estimates.firstOrNull()?.let { Forecasts.date(it.on) }, c.market.firstOrNull()?.let { Forecasts.date(it.on) }).minOrNull() ?: today
    val end = (Forecasts.date(c.markedOn) ?: today).let { if (it.isAfter(start)) it else start.plusDays(1) }
    val span = ChronoUnit.DAYS.between(start, end).toFloat().coerceAtLeast(1f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun xOf(day: String) = (Forecasts.date(day)?.let { ChronoUnit.DAYS.between(start, it).toFloat() / span } ?: 0f).coerceIn(0f, 1f) * w
        fun yOf(p: Double) = h - p.toFloat() * h
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), 1f)
        fun steps(points: List<app.localmind.assistant.domain.forecasts.Chance>, color: Color, dashed: Boolean, width: Float) {
            if (points.isEmpty()) return
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = xOf(p.on)
                val y = yOf(p.chances[0])
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(w, yOf(points.last().chances[0]))
            drawPath(path, color, style = Stroke(width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 9f)) else null))
            if (!dashed) points.forEach { p -> drawCircle(color, 5f, Offset(xOf(p.on), yOf(p.chances[0]))) }
        }
        steps(c.market, market, true, 3f)
        steps(c.estimates, you, false, 4f)
    }
}

@Composable
private fun Actions(c: Call, onChance: () -> Unit, onMarket: () -> Unit, onMark: (Int?, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (c.open) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onChance, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) { Text("Change my chance") }
                if (c.kind != CallKind.ONE_OF) OutlinedButton(onClick = onMarket) { Text("A market's price", color = P.Text) }
            }
            SmallLabel("What happened", Modifier.padding(top = 6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                c.outcomes.forEachIndexed { i, o ->
                    val label = when (c.kind) {
                        CallKind.YES_NO -> if (i == 0) "It happened" else "It didn't"
                        else -> o
                    }
                    ChoicePill(label, false, { onMark(i, false) })
                }
                ChoicePill("Doesn't count", false, { onMark(null, true) }, color = P.Muted)
            }
        } else {
            TextButton(onClick = { onMark(null, false) }) { Text("Reopen it", color = P.Muted) }
        }
    }
}
