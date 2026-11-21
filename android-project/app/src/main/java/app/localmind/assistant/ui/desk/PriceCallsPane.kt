package app.localmind.assistant.ui.desk

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.forecasts.PriceCalls
import app.localmind.assistant.domain.forecasts.PriceCheck
import app.localmind.assistant.domain.forecasts.PriceTerms
import app.localmind.assistant.domain.forecasts.Verdict
import app.localmind.assistant.ui.common.CallItSheet
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.DateField
import app.localmind.assistant.ui.common.EmptyLine
import app.localmind.assistant.ui.common.Preset
import app.localmind.assistant.ui.common.Section
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.common.showDay
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * SlothDesk's Price calls tab: results to confirm, a chart of the series with your level, the calls
 * still open, and a level to call. Sideways on a wide screen, the chart and calls sit on the left
 * and the checks and the new call on the right.
 */
@Composable
fun PriceCallsPane(vm: PriceCallsViewModel, wide: Boolean, onAskPrices: () -> Unit) {
    val prices by vm.prices.collectAsStateWithLifecycle()
    val calls by vm.calls.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.clearMessage() } }

    val today = LocalDate.now()
    val available = PriceCalls.SERIES.filter { PriceCallsViewModel.closes(prices, it) != null }
    var series by remember { mutableStateOf<String?>(null) }
    val chosen = series?.takeIf { it in available } ?: available.firstOrNull()
    var above by remember { mutableStateOf(true) }
    var levelText by remember { mutableStateOf("") }
    val level = levelText.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    val open = calls.filter { it.open }
    val checks = open.mapNotNull { c -> PriceCallsViewModel.checkOf(c, prices, today)?.let { c to it } }
    val waiting = open.filter { c -> checks.none { it.first.id == c.id } }
    val done = calls.filter { !it.open }.take(8)

    val checksPart: @Composable () -> Unit = {
        checks.forEach { (c, check) -> CheckCard(c, check, vm) }
    }
    val formPart: @Composable () -> Unit = {
        NewLevel(
            available, chosen, { series = it }, above, { above = it }, levelText, { levelText = it.take(12) },
            last = chosen?.let { lastClose(prices, it) }, level = level, vm = vm, onAskPrices = onAskPrices
        )
    }
    val chartPart: @Composable () -> Unit = {
        chosen?.let { name -> PriceChart(prices, name, level ?: open.firstOrNull { it.price?.series == name }?.price?.level) }
    }
    val listPart: @Composable () -> Unit = {
        Section("Open price calls", note = if (waiting.isEmpty()) null else "Checked against the closes after the day") {
            if (waiting.isEmpty()) EmptyLine("None open. Call a level: the close on the day settles it.")
            waiting.forEach { c -> OpenRow(c, prices) }
        }
        if (done.isNotEmpty()) {
            Section("Settled") { done.forEach { c -> DoneRow(c) } }
        }
        Text(
            "Settled on the day's close in SlothDesk's prices (the Lobster's), or the last trading day before it. USD/JPY is " +
                "checked against FRED's daily rate too when SlothDesk has it. Nothing is marked until you confirm. Paper only.",
            color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val sideBySide = wide && maxWidth > maxHeight
        if (sideBySide) {
            Row(Modifier.widthIn(max = 1400.dp).fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    chartPart()
                    listPart()
                    Spacer(Modifier.height(32.dp))
                }
                Column(Modifier.width(480.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    checksPart()
                    formPart()
                    Spacer(Modifier.height(32.dp))
                }
            }
        } else {
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                checksPart()
                chartPart()
                listPart()
                formPart()
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

private fun lastClose(b: PriceBundle?, name: String): Pair<String, Double>? =
    PriceCallsViewModel.closes(b, name)?.let { s -> s.dates.lastOrNull()?.let { d -> d to s.closes.last() } }

private fun pct(x: Double): String = String.format(Locale.ENGLISH, "%.1f%%", abs(x) * 100)

/** A result the prices propose, with where it came from; or why it needs you. */
@Composable
private fun CheckCard(c: Call, check: PriceCheck, vm: PriceCallsViewModel) {
    var reasoning by remember(c.id) { mutableStateOf("") }
    var byHand by remember(c.id) { mutableStateOf(check.verdict == Verdict.ASK) }
    val said = c.latest?.chances?.firstOrNull()?.let { "${Forecasts.percent(it)}%" } ?: "—"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.AccentSoft).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("Check this result", Modifier.weight(1f))
            Text("nothing is marked until you confirm", color = P.Faint, fontSize = 12.sp)
        }
        Text(c.question, color = P.Text, fontSize = 17.sp, lineHeight = 24.sp, fontFamily = FontFamily.Serif)
        check.close?.let { close ->
            Fact2("Close, ${PriceCalls.showDate(check.day)}", "${PriceCalls.level(close)} · ${PriceCalls.sourceName(check.source)}")
        }
        when {
            check.second != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Second source", color = P.Muted, fontSize = 13.sp, modifier = Modifier.width(130.dp))
                if (check.verdict != Verdict.ASK) Icon(Icons.Outlined.Check, contentDescription = "Agrees", tint = P.Accent, modifier = Modifier.size(15.dp))
                Text("${PriceCalls.level(check.second)} · ${PriceCalls.sourceName(check.secondSource)}", color = P.Text, fontSize = 13.sp)
            }
            check.close != null -> Fact2("Second source", "none for ${PriceCalls.label(c.price?.series ?: "")} in SlothDesk's prices")
        }
        if (check.verdict != Verdict.ASK) {
            Fact2("So it", (if (check.verdict == Verdict.HAPPENED) "happened" else "didn't happen") + " (you said $said)", P.Accent)
        } else {
            Text(check.why, color = P.Warning, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("The reasoning, if you like:", color = P.Faint, fontSize = 12.sp)
            listOf("sound" to "Sound", "partly" to "Partly", "no" to "Shaky").forEach { (key, label) ->
                ChoicePill(label, reasoning == key, { reasoning = if (reasoning == key) "" else key }, color = P.Muted)
            }
        }
        if (!byHand) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.confirm(c, check, reasoning) }, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) {
                    Text(if (check.verdict == Verdict.HAPPENED) "Confirm: it happened" else "Confirm: it didn't")
                }
                OutlinedButton(onClick = { byHand = true }) { Text("Something's wrong", color = P.Text) }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("It happened", false, { vm.markByHand(c, 0, false, check.evidence, reasoning) })
                ChoicePill("It didn't", false, { vm.markByHand(c, 1, false, check.evidence, reasoning) })
                ChoicePill("Doesn't count", false, { vm.markByHand(c, null, true, check.evidence, "") }, color = P.Muted)
            }
        }
    }
}

@Composable
private fun Fact2(label: String, value: String, color: androidx.compose.ui.graphics.Color = P.Text) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = P.Muted, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(value, color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/** The last 90 closes of [name], with the level dashed. */
@Composable
private fun PriceChart(b: PriceBundle?, name: String, level: Double?) {
    val s = PriceCallsViewModel.closes(b, name) ?: return
    val tail = s.closes.takeLast(90)
    if (tail.size < 2) return
    val line = P.Text
    val accent = P.Accent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("${PriceCalls.label(name)} · the last ${tail.size} closes", Modifier.weight(1f))
            Text("${PriceCalls.level(tail.last())} on ${showDay(s.dates.last())}", color = P.Muted, fontSize = 12.sp)
        }
        val lo = (listOfNotNull(level) + tail).min()
        val hi = (listOfNotNull(level) + tail).max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            fun y(v: Double) = (h - ((v - lo) / span * (h - 8f) + 4f)).toFloat()
            val path = Path()
            tail.forEachIndexed { i, v ->
                val x = i * w / (tail.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, line, style = Stroke(3f))
            level?.let { lv -> drawLine(accent, Offset(0f, y(lv)), Offset(w, y(lv)), 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))) }
        }
        if (level != null) Text("Dashed: the level ${PriceCalls.level(level)}", color = P.Faint, fontSize = 12.sp)
    }
}

@Composable
private fun OpenRow(c: Call, b: PriceBundle?) {
    val t = c.price ?: return
    val last = lastClose(b, t.series)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(c.question, color = P.Text, fontSize = 15.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        last?.let { (_, close) ->
            val d = PriceCalls.distance(t, close)
            Column(horizontalAlignment = Alignment.End) {
                Text(PriceCalls.level(close), color = P.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("level ${pct(d)} ${if (d >= 0) "above" else "below"}", color = P.Faint, fontSize = 11.sp)
            }
        }
        Text(c.latest?.chances?.firstOrNull()?.let { "${Forecasts.percent(it)}%" } ?: "—", color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun DoneRow(c: Call) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.question, color = P.Text, fontSize = 15.sp, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.evidence.isNotBlank()) Text(c.evidence, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            c.void -> Text("didn't count", color = P.Faint, fontSize = 12.sp)
            Forecasts.wentYourWay(c) == true -> Icon(Icons.Outlined.Check, contentDescription = "Went your way", tint = P.Success, modifier = Modifier.size(18.dp))
            else -> Icon(Icons.Outlined.Close, contentDescription = "Didn't go your way", tint = P.Warning, modifier = Modifier.size(18.dp))
        }
    }
}

/** Call a level: the series, above or below, the level and the day; your chance goes on the Call it sheet. */
@Composable
private fun NewLevel(
    available: List<String>,
    chosen: String?,
    onSeries: (String) -> Unit,
    above: Boolean,
    onAbove: (Boolean) -> Unit,
    levelText: String,
    onLevel: (String) -> Unit,
    last: Pair<String, Double>?,
    level: Double?,
    vm: PriceCallsViewModel,
    onAskPrices: () -> Unit
) {
    var on by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
    var calling by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SmallLabel("Call a level")
        if (available.isEmpty()) {
            Text("SlothDesk has no prices yet. Ask the Lobster for them, then call a level.", color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = onAskPrices) { Text("Ask the Lobster for prices", color = P.Lobster) }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { s -> ChoicePill(PriceCalls.label(s), s == chosen, { onSeries(s) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("above", above, { onAbove(true) })
            ChoicePill("below", !above, { onAbove(false) })
            OutlinedTextField(
                value = levelText, onValueChange = onLevel, singleLine = true, label = { Text("Level") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border, focusedTextColor = P.Text, unfocusedTextColor = P.Text,
                    cursorColor = P.Accent, focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted
                ),
                modifier = Modifier.width(130.dp)
            )
        }
        last?.let { (day, close) ->
            Text(
                "Last close ${PriceCalls.level(close)} (${showDay(day)})" +
                    (level?.let { lv -> " · the level is ${pct((lv - close) / close)} ${if (lv >= close) "above" else "below"} it" } ?: ""),
                color = P.Faint, fontSize = 12.sp
            )
        }
        DateField("At the close on", on) { if (it.isNotBlank()) on = it }
        Button(
            onClick = { calling = true },
            enabled = chosen != null && level != null && (Forecasts.date(on)?.isAfter(LocalDate.now()) == true),
            colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)
        ) { Text("Call it") }
        Text(
            "Settles on that day's close (the last trading day before it if markets are shut).",
            color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp
        )
    }
    if (calling && chosen != null && level != null) {
        val terms = PriceTerms(chosen, above, level, on)
        CallItSheet(
            where = "SlothDesk · price call",
            question = PriceCalls.question(terms),
            context = last?.let { (_, close) -> "Last close ${PriceCalls.level(close)}: the level is ${pct((level - close) / close)} ${if (level >= close) "above" else "below"} it." } ?: "",
            start = 0.5,
            presets = listOf(0.25, 0.4, 0.5, 0.6, 0.75).map { Preset("${Forecasts.percent(it)}", it) },
            settles = "Checked against the close on ${showDay(on)}; you confirm the result.",
            fixedCheckOn = on,
            onDismiss = { calling = false },
            onCall = { _, chance, _, why -> vm.call(terms, chance ?: 0.5, why) }
        )
    }
}
