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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.desk.RuleCheck
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.desk.Signal
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The pieces the rule editor's sentences are made of (1.48): words, chips you tap to change a
 * part, and the small editors behind them. Every editor takes only what the rule's limits allow
 * ([RuleCheck]); nothing typed here is ever run, it only becomes part of a rule's data.
 */

/** A part of a sentence you can change: tap it to edit. */
@Composable
internal fun Chip(label: String, muted: Boolean = false, description: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        color = if (muted) P.Muted else P.Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .heightIn(min = 42.dp)
            .clip(shape)
            .background(if (muted) P.Elevated else P.AccentSoft)
            .clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 10.dp, vertical = 11.dp)
    )
}

/** The words between chips, set to line up with them. */
@Composable
internal fun Words(text: String, faint: Boolean = false) {
    Text(text, color = if (faint) P.Faint else P.Text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 11.dp))
}

/** A chip that opens a short list of choices. */
@Composable
internal fun MenuChip(label: String, options: List<Pair<String, () -> Unit>>, muted: Boolean = false, description: String? = null) {
    var unfolded by remember { mutableStateOf(false) }
    Box {
        Chip(label, muted, description) { unfolded = true }
        DropdownMenu(expanded = unfolded, onDismissRequest = { unfolded = false }, modifier = Modifier.background(P.Elevated)) {
            options.forEach { (text, act) ->
                DropdownMenuItem(text = { Text(text, color = P.Text) }, onClick = { unfolded = false; act() })
            }
        }
    }
}

// --- numbers ---

/**
 * How a number is entered: its range in the rule's own units (fractions for percents), whether
 * it's shown as a percent, and whether it must be whole.
 */
internal class NumSpec(val min: Double, val max: Double, val percent: Boolean = false, val whole: Boolean = false, val suffix: String = "") {
    fun show(x: Double): String = RuleWords.num(if (percent) x * 100 else x)
    fun label(x: Double): String = show(x) + (if (percent) "%" else "") + suffix

    /** The typed number in the rule's units, or null when it isn't one or is out of range. */
    fun parse(text: String): Double? {
        val v = text.trim().replace(',', '.').replace('−', '-').toDoubleOrNull() ?: return null
        if (!v.isFinite()) return null
        if (whole && abs(v - v.roundToInt()) > 1e-9) return null
        val x = if (percent) v / 100.0 else v
        if (x < min - 1e-12 || x > max + 1e-12) return null
        return if (whole) v.roundToInt().toDouble() / (if (percent) 100.0 else 1.0) else x
    }

    fun range(): String = "From ${label(min)} to ${label(max)}"

    companion object {
        val DAYS = NumSpec(1.0, RuleCheck.MAX_DAYS.toDouble(), whole = true, suffix = " days")
        val LONG_DAYS = NumSpec(5.0, RuleCheck.MAX_DAYS.toDouble(), whole = true, suffix = " days")
        val ROW = NumSpec(1.0, 250.0, whole = true, suffix = " days")
        val WEIGHT = NumSpec(-RuleCheck.MAX_GROSS, RuleCheck.MAX_GROSS, percent = true)
        val ANY = NumSpec(-1e9, 1e9)
        val ANY_PCT = NumSpec(-1e7, 1e7, percent = true)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = P.Accent,
    unfocusedBorderColor = P.Border,
    focusedTextColor = P.Text,
    unfocusedTextColor = P.Text,
    cursorColor = P.Accent,
    focusedLabelColor = P.Accent,
    unfocusedLabelColor = P.Muted
)

@Composable
internal fun NumberField(label: String, text: String, onChange: (String) -> Unit, spec: NumSpec) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { onChange(it.take(14)) },
            label = { Text(label) },
            singleLine = true,
            suffix = if (spec.percent || spec.suffix.isNotBlank()) { { Text(if (spec.percent) "%" else spec.suffix.trim(), color = P.Muted) } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = if (spec.min < 0) KeyboardType.Text else KeyboardType.Decimal),
            isError = text.isNotBlank() && spec.parse(text) == null,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.range(), color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (spec.min < 0) {
                TextButton(onClick = { onChange(if (text.trim().startsWith("-")) text.trim().removePrefix("-") else "-" + text.trim()) }) {
                    Text("±", color = P.Accent, fontSize = 16.sp)
                }
            }
        }
    }
}

/** One number, typed, within its range. */
@Composable
internal fun NumberDialog(title: String, value: Double, spec: NumSpec, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(spec.show(value)) }
    val parsed = spec.parse(text)
    SmallDialog(title, onDismiss, canSave = parsed != null, onSave = { parsed?.let(onSave) }) {
        NumberField(title, text, { text = it }, spec)
    }
}

/** A chip showing a number; a tap opens its editor. */
@Composable
internal fun NumberChip(title: String, value: Double, spec: NumSpec, label: String = spec.label(value), onSave: (Double) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Chip(label, description = "$title: $label") { editing = true }
    if (editing) NumberDialog(title, value, spec, onDismiss = { editing = false }, onSave = onSave)
}

// --- dialogs ---

@Composable
internal fun SmallDialog(
    title: String,
    onDismiss: () -> Unit,
    canSave: Boolean = true,
    saveLabel: String = "Done",
    onSave: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text(title, color = P.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = { onSave(); onDismiss() }, enabled = canSave) { Text(saveLabel, color = if (canSave) P.Accent else P.Faint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) } }
    )
}

@Composable
internal fun TextInput(label: String, text: String, onChange: (String) -> Unit, upper: Boolean = false, error: Boolean = false, hint: String? = null) {
    OutlinedTextField(
        value = text,
        onValueChange = { onChange((if (upper) it.uppercase(Locale.ROOT) else it).take(80)) },
        label = { Text(label) },
        placeholder = if (hint == null) null else { { Text(hint, color = P.Faint) } },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(capitalization = if (upper) KeyboardCapitalization.Characters else KeyboardCapitalization.None),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Tappable suggestions under a field: the tickers or series the prices have. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Suggestions(options: List<String>, selected: Set<String> = emptySet(), onPick: (String) -> Unit) {
    if (options.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.take(40).forEach { o ->
            val on = o in selected
            val shape = RoundedCornerShape(8.dp)
            Text(
                o, color = if (on) P.OnAccent else P.Text, fontSize = 13.sp,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(if (on) P.Accent else P.Elevated)
                    .border(1.dp, if (on) P.Accent else P.Border, shape)
                    .clickable(role = Role.Button) { onPick(o) }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            )
        }
    }
}

/** A ticker, typed or picked from the prices there are. */
@Composable
internal fun TickerDialog(title: String, value: String, options: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    val ok = RuleCheck.isTicker(text)
    SmallDialog(title, onDismiss, canSave = ok, onSave = { onSave(text) }) {
        TextInput("Ticker", text, { text = it }, upper = true, error = text.isNotBlank() && !ok, hint = "GLD")
        if (text.isNotBlank() && text !in options) {
            Text("No prices for $text yet: the Lobster can fetch them, or import a file.", color = P.Warning, fontSize = 12.sp)
        }
        Suggestions(options, setOf(text)) { text = it }
    }
}

@Composable
internal fun TickerChip(title: String, value: String, options: List<String>, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Chip(value, description = "$title: $value") { editing = true }
    if (editing) TickerDialog(title, value, options, onDismiss = { editing = false }, onSave = onSave)
}

/** A list of tickers (a rotation's), picked or typed. */
@Composable
internal fun TickersDialog(title: String, value: List<String>, options: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var list by remember { mutableStateOf(value) }
    var typed by remember { mutableStateOf("") }
    val ok = list.size in 2..RuleCheck.MAX_LIST && list.all { RuleCheck.isTicker(it) }
    SmallDialog(title, onDismiss, canSave = ok, onSave = { onSave(list) }) {
        Text("${list.size} chosen (2 to ${RuleCheck.MAX_LIST})", color = P.Muted, fontSize = 13.sp)
        Suggestions((options + list).distinct(), list.toSet()) { t -> list = if (t in list) list - t else list + t }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TextInput("Add a ticker", typed, { typed = it }, upper = true, error = typed.isNotBlank() && !RuleCheck.isTicker(typed)) }
            TextButton(onClick = { if (RuleCheck.isTicker(typed) && typed !in list) list = list + typed; typed = "" }) { Text("Add", color = P.Accent) }
        }
    }
}

/** A series (a rate, an index), typed or picked. */
@Composable
internal fun SeriesDialog(title: String, value: String, options: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    val ok = RuleCheck.isSeries(text)
    SmallDialog(title, onDismiss, canSave = ok, onSave = { onSave(text) }) {
        TextInput("Series", text, { text = it.trim() }, error = text.isNotBlank() && !ok, hint = "DTB3")
        Suggestions(options, setOf(text)) { text = it }
    }
}

// --- signals ---

private enum class Kind(val label: String) {
    PRICE("Its close"), AVERAGE("An average"), MOMENTUM("Its return over a while"), HIGH("The high of the days before"),
    LOW("The low of the days before"), OFF_HIGH("How far off its high"), STRETCH("How stretched (z-score)"),
    RATIO("The ratio of two"), RSI("RSI"), VOLATILITY("Volatility"), SERIES("A series: a rate or an index"),
    CHANGE("A series' change"), GAP("One series minus another");

    val readsAsset: Boolean get() = this != SERIES && this != CHANGE && this != GAP
    val hasDays: Boolean get() = this !in setOf(PRICE, RATIO, SERIES, GAP)
}

private fun kindOf(s: Signal): Kind = when (s) {
    is Signal.Price -> Kind.PRICE; is Signal.Average -> Kind.AVERAGE; is Signal.Momentum -> Kind.MOMENTUM
    is Signal.High -> Kind.HIGH; is Signal.Low -> Kind.LOW; is Signal.OffHigh -> Kind.OFF_HIGH
    is Signal.Stretch -> Kind.STRETCH; is Signal.Ratio -> Kind.RATIO; is Signal.Rsi -> Kind.RSI
    is Signal.Volatility -> Kind.VOLATILITY; is Signal.Series -> Kind.SERIES; is Signal.Change -> Kind.CHANGE
    is Signal.Gap -> Kind.GAP
}

private fun defaultDays(k: Kind): Int = when (k) {
    Kind.AVERAGE -> 200; Kind.MOMENTUM -> 120; Kind.HIGH, Kind.LOW -> 50; Kind.OFF_HIGH -> 252
    Kind.STRETCH -> 60; Kind.RSI -> 14; Kind.VOLATILITY -> 20; Kind.CHANGE -> 20; else -> 0
}

private fun daysOf(s: Signal): Int? = when (s) {
    is Signal.Average -> s.days; is Signal.Momentum -> s.days; is Signal.High -> s.days; is Signal.Low -> s.days
    is Signal.OffHigh -> s.days; is Signal.Stretch -> s.days; is Signal.Rsi -> s.days; is Signal.Volatility -> s.days
    is Signal.Change -> s.days; else -> null
}

/**
 * Picks a signal and its settings. In a rotation's score ([rotation]) the asset is "each one in
 * the list", so it isn't asked for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignalDialog(
    title: String,
    value: Signal,
    tickers: List<String>,
    series: List<String>,
    rotation: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Signal) -> Unit
) {
    var kind by remember { mutableStateOf(kindOf(value)) }
    var asset by remember { mutableStateOf(value.assets.firstOrNull() ?: tickers.firstOrNull() ?: "GLD") }
    var asset2 by remember { mutableStateOf((value as? Signal.Stretch)?.asset2 ?: (value as? Signal.Ratio)?.asset2 ?: "") }
    var days by remember { mutableStateOf((daysOf(value) ?: 0).let { if (it > 0) it.toString() else "" }) }
    var exp by remember { mutableStateOf((value as? Signal.Average)?.exp ?: false) }
    var skip by remember { mutableStateOf(((value as? Signal.Momentum)?.skip ?: 0).toString()) }
    var id by remember { mutableStateOf((value as? Signal.Series)?.id ?: (value as? Signal.Change)?.id ?: (value as? Signal.Gap)?.id ?: "GVZ") }
    var id2 by remember { mutableStateOf((value as? Signal.Gap)?.id2 ?: "") }
    val a = if (rotation) "*" else asset

    val built: Signal? = run {
        val d = days.trim().toIntOrNull()
        when (kind) {
            Kind.PRICE -> Signal.Price(a)
            Kind.AVERAGE -> d?.let { Signal.Average(a, it, exp) }
            Kind.MOMENTUM -> d?.let { dd -> skip.trim().toIntOrNull()?.let { Signal.Momentum(a, dd, it) } }
            Kind.HIGH -> d?.let { Signal.High(a, it) }
            Kind.LOW -> d?.let { Signal.Low(a, it) }
            Kind.OFF_HIGH -> d?.let { Signal.OffHigh(a, it) }
            Kind.STRETCH -> d?.let { Signal.Stretch(a, it, asset2.ifBlank { null }) }
            Kind.RATIO -> if (asset2.isBlank()) null else Signal.Ratio(a, asset2)
            Kind.RSI -> d?.let { Signal.Rsi(a, it) }
            Kind.VOLATILITY -> d?.let { Signal.Volatility(a, it) }
            Kind.SERIES -> Signal.Series(id)
            Kind.CHANGE -> d?.let { Signal.Change(id, it) }
            Kind.GAP -> if (id2.isBlank()) null else Signal.Gap(id, id2)
        }
    }
    val problems = built?.let { RuleCheck.signalProblems(it, rotation) } ?: listOf("Fill in every part")
    val kinds = Kind.entries.filter { !rotation || it.readsAsset }

    SmallDialog(title, onDismiss, canSave = problems.isEmpty(), onSave = { built?.let(onSave) }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEach { k ->
                val on = k == kind
                val shape = RoundedCornerShape(8.dp)
                Text(
                    k.label, color = if (on) P.OnAccent else P.Text, fontSize = 13.sp,
                    modifier = Modifier.heightIn(min = 36.dp).clip(shape).background(if (on) P.Accent else P.Elevated)
                        .clickable(role = Role.RadioButton) {
                            if (kind != k) {
                                kind = k
                                if (k.hasDays) days = defaultDays(k).toString()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
            }
        }
        if (kind.readsAsset) {
            if (rotation) {
                Text("Read for each asset in the list, to rank them.", color = P.Muted, fontSize = 13.sp)
            } else {
                TextInput("Asset", asset, { asset = it }, upper = true, error = !RuleCheck.isTicker(asset))
                Suggestions(tickers, setOf(asset)) { asset = it }
            }
            if (kind == Kind.STRETCH || kind == Kind.RATIO) {
                TextInput(if (kind == Kind.RATIO) "Against" else "Against (leave blank for its own price)", asset2, { asset2 = it }, upper = true,
                    error = asset2.isNotBlank() && !RuleCheck.isTicker(asset2))
                Suggestions(tickers.filter { it != asset }, setOf(asset2)) { asset2 = if (asset2 == it) "" else it }
            }
        } else {
            TextInput("Series", id, { id = it.trim() }, error = !RuleCheck.isSeries(id))
            Suggestions(series, setOf(id)) { id = it }
            if (kind == Kind.GAP) {
                TextInput("Minus", id2, { id2 = it.trim() }, error = id2.isNotBlank() && !RuleCheck.isSeries(id2))
                Suggestions(series.filter { it != id }, setOf(id2)) { id2 = it }
            }
        }
        if (kind.hasDays) {
            TextInput("Days", days, { days = it.filter(Char::isDigit).take(4) })
        }
        if (kind == Kind.AVERAGE) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Exponential (weights recent days more)", color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = exp, onCheckedChange = { exp = it }, colors = SwitchDefaults.colors(checkedTrackColor = P.Accent, checkedThumbColor = P.OnAccent))
            }
        }
        if (kind == Kind.MOMENTUM) {
            TextInput("Skip the last … days (0 for none)", skip, { skip = it.filter(Char::isDigit).take(4) })
        }
        built?.let { Text("Reads: " + RuleWords.signal(it), color = P.Muted, fontSize = 13.sp) }
        problems.firstOrNull()?.let { Text(it, color = P.Warning, fontSize = 13.sp) }
    }
}

@Composable
internal fun SignalChip(title: String, value: Signal, label: String, tickers: List<String>, series: List<String>, rotation: Boolean = false, onSave: (Signal) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Chip(label, description = "$title: $label") { editing = true }
    if (editing) SignalDialog(title, value, tickers, series, rotation, onDismiss = { editing = false }, onSave = onSave)
}

// --- the calendar ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarDialog(
    months: List<Int>,
    weekdays: List<Int>,
    dates: List<String>,
    around: Int,
    onDismiss: () -> Unit,
    onSave: (List<Int>, List<Int>, List<String>, Int) -> Unit
) {
    var m by remember { mutableStateOf(months.toSet()) }
    var w by remember { mutableStateOf(weekdays.toSet()) }
    var d by remember { mutableStateOf(dates.joinToString(", ")) }
    var ar by remember { mutableStateOf(around.toString()) }
    val list = d.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
    val datesOk = list.size <= 50 && list.all { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
    val arN = ar.trim().toIntOrNull()
    val ok = datesOk && arN != null && arN in 0..30
    SmallDialog("The calendar", onDismiss, canSave = ok, onSave = { onSave(m.sorted(), w.sorted(), list, arN ?: 0) }) {
        Text("Months (none: any month)", color = P.Muted, fontSize = 13.sp)
        Suggestions((1..12).map { RuleWords.month(it) }, m.map { RuleWords.month(it) }.toSet()) { name ->
            val k = (1..12).first { RuleWords.month(it) == name }
            m = if (k in m) m - k else m + k
        }
        Text("Weekdays (none: any day)", color = P.Muted, fontSize = 13.sp)
        Suggestions((1..7).map { RuleWords.weekday(it) }, w.map { RuleWords.weekday(it) }.toSet()) { name ->
            val k = (1..7).first { RuleWords.weekday(it) == name }
            w = if (k in w) w - k else w + k
        }
        TextInput("Dates (yyyy-mm-dd, commas between)", d, { d = it }, error = !datesOk, hint = "2026-12-16")
        TextInput("Within this many days of them (0 to 30)", ar, { ar = it.filter(Char::isDigit).take(2) }, error = !ok && datesOk)
    }
}
