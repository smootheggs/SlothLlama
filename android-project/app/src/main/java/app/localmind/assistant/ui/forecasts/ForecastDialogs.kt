package app.localmind.assistant.ui.forecasts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.odds.Odds
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.DateField
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A market's chance from what you type: "56%" or a whole number 1–99 is a percent; anything else
 * is read as a price (1.87, −115, 20/23, evens) and its implied chance taken, margin included.
 */
internal fun marketChance(text: String): Double? {
    val s = text.trim().removeSuffix("%").trim()
    if (s.isEmpty()) return null
    val whole = s.toIntOrNull()
    if (text.trim().endsWith("%") || (whole != null && whole in 1..99 && !s.startsWith("+") && !s.startsWith("-"))) {
        val v = s.replace(',', '.').toDoubleOrNull() ?: return null
        return if (v > 0 && v < 100) v / 100 else null
    }
    return Odds.parse(text)?.implied?.takeIf { it > 0.0 && it < 1.0 }
}

@Composable
private fun NumberBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border, focusedTextColor = P.Text, unfocusedTextColor = P.Text,
            cursorColor = P.Accent, focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted
        ),
        modifier = modifier
    )
}

/** One chance, 1–99%, on a slider with the number large beside it. */
@Composable
private fun ChanceSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()}%", color = P.Accent, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value, onValueChange = { onChange(it.roundToInt().toFloat()) }, valueRange = 1f..99f, steps = 97,
            colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Elevated)
        )
    }
}

/** Percent boxes for a several-way call, with what they add up to (they're scaled to 100). */
@Composable
private fun SplitBoxes(names: List<String>, percents: List<String>, onChange: (Int, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(n.ifBlank { "Outcome ${i + 1}" }, color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                NumberBox("%", percents.getOrElse(i) { "" }, { onChange(i, it) }, Modifier.width(110.dp))
            }
        }
        val total = percents.sumOf { it.toDoubleOrNull() ?: 0.0 }
        Text(
            if (total.roundToInt() == 100) "Adds to 100%." else "Adds to ${total.roundToInt()}%: kept in proportion, scaled to 100.",
            color = if (total.roundToInt() == 100) P.Faint else P.Warning, fontSize = 12.sp
        )
    }
}

@Composable
internal fun NewCallDialog(
    area: Area,
    onDismiss: () -> Unit,
    onCreate: (String, Area, CallKind, List<String>, String, String, List<Double>, String, List<Double>?, String) -> Unit
) {
    var kind by remember { mutableStateOf(CallKind.YES_NO) }
    var question by remember { mutableStateOf("") }
    var where by remember { mutableStateOf(area) }
    var line by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "", "")) }
    var percents by remember { mutableStateOf(listOf("", "", "")) }
    var yes by remember { mutableFloatStateOf(60f) }
    var checkOn by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var reason by remember { mutableStateOf("") }
    var market by remember { mutableStateOf("") }
    var marketWhere by remember { mutableStateOf("") }

    val named = options.map { it.trim() }
    val split = Forecasts.normalise(named.indices.filter { named[it].isNotEmpty() }.map { percents.getOrElse(it) { "" }.toDoubleOrNull() ?: 0.0 })
    val chances: List<Double>? = when (kind) {
        CallKind.ONE_OF -> if (named.count { it.isNotEmpty() } >= 2) split else null
        else -> Forecasts.binary(yes / 100.0)
    }
    val marketP = if (kind == CallKind.ONE_OF) null else marketChance(market)
    val canSave = question.isNotBlank() && chances != null && Forecasts.date(checkOn) != null &&
        (kind != CallKind.NUMBER || line.isNotBlank()) && (market.isBlank() || marketP != null)

    EditDialog("New call", onDismiss, {
        onCreate(
            question, where, kind, named.filter { it.isNotEmpty() }, line, checkOn, chances!!, reason,
            marketP?.let { Forecasts.binary(it) }, marketWhere
        )
        onDismiss()
    }, canSave = canSave, saveLabel = "Make the call") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallKind.entries.forEach { k -> ChoicePill(k.label, kind == k, { kind = k }) }
        }
        FormField(
            "The question", question, { question = it.take(Forecasts.MAX_QUESTION) }, lines = 2,
            hint = when (kind) {
                CallKind.YES_NO -> "Will the Senate pass the bill by March?"
                CallKind.ONE_OF -> "Who wins the title?"
                CallKind.NUMBER -> "Gold's close on 4 December"
            }
        )
        when (kind) {
            CallKind.NUMBER -> {
                FormField("The line", line, { line = it.take(40) }, hint = "2,650")
                ChanceSlider("Your chance it ends above ${line.ifBlank { "the line" }}", yes) { yes = it }
            }
            CallKind.YES_NO -> ChanceSlider("Your chance of yes", yes) { yes = it }
            CallKind.ONE_OF -> {
                options.forEachIndexed { i, o ->
                    FormField("Outcome ${i + 1}", o, { v -> options = options.toMutableList().also { it[i] = v.take(80) } })
                }
                if (options.size < Forecasts.MAX_OPTIONS) {
                    TextButton(onClick = { options = options + ""; percents = percents + "" }) { Text("Add an outcome", color = P.Accent) }
                }
                val shown = named.indices.filter { named[it].isNotEmpty() }
                if (shown.size >= 2) {
                    SplitBoxes(shown.map { named[it] }, shown.map { percents.getOrElse(it) { "" } }) { j, v ->
                        val i = shown[j]
                        percents = percents.toMutableList().also { while (it.size <= i) it.add(""); it[i] = v }
                    }
                }
            }
        }
        DateField("Check on", checkOn) { if (it.isNotBlank()) checkOn = it }
        FormField("Why (optional)", reason, { reason = it.take(400) }, lines = 2, hint = "What your chance rests on: a base rate, a reason")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Area.entries.forEach { a -> ChoicePill(a.label, where == a, { where = a }) }
        }
        if (kind != CallKind.ONE_OF) {
            FormField("A market's chance (optional)", market, { market = it.take(12) }, hint = "33% or a price: 1.87, −115, 20/23")
            if (market.isNotBlank()) {
                Text(
                    marketP?.let { "Read as ${Forecasts.percent(it)}% (a price's margin stays in)." } ?: "Not a percent or a price.",
                    color = if (marketP == null) P.Warning else P.Faint, fontSize = 12.sp
                )
                FormField("Where from (optional)", marketWhere, { marketWhere = it.take(80) })
            }
        }
    }
}

@Composable
internal fun ChanceDialog(c: Call, onDismiss: () -> Unit, onSave: (List<Double>, String) -> Unit) {
    val last = c.latest?.chances.orEmpty()
    var yes by remember { mutableFloatStateOf(((last.getOrNull(0) ?: 0.6) * 100).roundToInt().toFloat().coerceIn(1f, 99f)) }
    var percents by remember { mutableStateOf(c.outcomes.indices.map { i -> last.getOrNull(i)?.let { Forecasts.percent(it).toString() } ?: "" }) }
    var reason by remember { mutableStateOf("") }
    val chances = if (c.kind == CallKind.ONE_OF) Forecasts.normalise(percents.map { it.toDoubleOrNull() ?: 0.0 }) else Forecasts.binary(yes / 100.0)
    EditDialog("Change my chance", onDismiss, { onSave(chances!!, reason); onDismiss() }, canSave = chances != null && chances != last) {
        if (c.kind == CallKind.ONE_OF) {
            SplitBoxes(c.outcomes, percents) { i, v -> percents = percents.toMutableList().also { it[i] = v } }
        } else {
            ChanceSlider(if (c.kind == CallKind.NUMBER) "Your chance it ends ${c.outcomes[0].lowercase()}" else "Your chance of yes", yes) { yes = it }
        }
        FormField("What changed", reason, { reason = it.take(400) }, lines = 3, hint = "One line on why, for when you look back")
    }
}

@Composable
internal fun MarketDialog(onDismiss: () -> Unit, onSave: (List<Double>, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var where by remember { mutableStateOf("") }
    val p = marketChance(text)
    EditDialog("A market's price", onDismiss, { onSave(Forecasts.binary(p!!), where); onDismiss() }, canSave = p != null) {
        FormField("Chance or price", text, { text = it.take(12) }, hint = "33% or 1.87, −115, 20/23")
        if (text.isNotBlank()) {
            Text(
                p?.let { "Read as ${Forecasts.percent(it)}%. A single price keeps its margin; the Sports page takes it out when it has both sides." } ?: "Not a percent or a price.",
                color = if (p == null) P.Warning else P.Faint, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        FormField("Where from (optional)", where, { where = it.take(80) }, hint = "a betting exchange, a poll average…")
    }
}

@Composable
internal fun MoveDialog(checkOn: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var day by remember { mutableStateOf(checkOn) }
    EditDialog("Check date", onDismiss, { onSave(day); onDismiss() }, canSave = Forecasts.date(day) != null && day != checkOn) {
        DateField("Check on", day) { if (it.isNotBlank()) day = it }
    }
}
