package app.localmind.assistant.ui.trading

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.trading.MetalsMath
import app.localmind.assistant.domain.trading.MetalsMath.WeightUnit
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class Calc(val label: String) {
    FORWARD("Forward"), CURRENCY("Other currency"), LOCAL("Local vs London"), WEIGHTS("Weights"), POSITION("Position")
}

/** A calculator's inputs and results as label → value, for the screen and for the Trader. */
private class Sheet(val title: String, val inputs: List<Pair<String, String>>, val results: List<Pair<String, String>>, val note: String = "") {
    fun text(): String = buildString {
        appendLine(title)
        appendLine("Inputs:")
        inputs.forEach { (k, v) -> appendLine("- $k: $v") }
        appendLine("Results (worked out by SlothLlama):")
        results.forEach { (k, v) -> appendLine("- $k: $v") }
        if (note.isNotBlank()) appendLine(note)
    }.trimEnd()
}

/**
 * The desk's sums. Every result is worked out in code; "Ask the Trader" sends them as they are.
 * On a wide screen the inputs sit on the left and the results, live, on the right.
 */
@Composable
internal fun Calculators(inputs: CalcInputs, facts: DeskFacts?, wide: Boolean, onAsk: (String, String) -> Unit) {
    val calc = Calc.entries.firstOrNull { it.name == inputs.get("calc") } ?: Calc.FORWARD
    Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Calc.entries.forEach { c -> Pill(c.label, selected = c == calc, quiet = true, small = true) { inputs.set("calc", c.name) } }
    }
    var sheet: Sheet? = null
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f)) { sheet = calcInputs(calc, inputs, facts) }
            Column(Modifier.weight(1f).padding(top = 12.dp)) { Results(sheet, onAsk, first = true) }
        }
    } else {
        sheet = calcInputs(calc, inputs, facts)
        Results(sheet, onAsk, first = false)
    }
}

@Composable
private fun calcInputs(calc: Calc, inputs: CalcInputs, facts: DeskFacts?): Sheet? = when (calc) {
    Calc.FORWARD -> ForwardCalc(inputs, facts)
    Calc.CURRENCY -> CurrencyCalc(inputs, facts)
    Calc.LOCAL -> LocalCalc(inputs)
    Calc.WEIGHTS -> WeightsCalc(inputs)
    Calc.POSITION -> PositionCalc(inputs)
}

@Composable
private fun Results(sheet: Sheet?, onAsk: (String, String) -> Unit, first: Boolean) {
    Text(
        "RESULTS", color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = if (first) 12.dp else 32.dp, bottom = 10.dp)
    )
    if (sheet == null || sheet.results.isEmpty()) {
        Text("Fill in the numbers to see the results.", color = P.Faint, fontSize = 13.sp)
        return
    }
    sheet.results.forEach { (k, v) -> ResultRow(k, v) }
    if (sheet.note.isNotBlank()) Text(sheet.note, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.padding(top = 10.dp)) { QuietButton("Ask the Trader") { onAsk(sheet.title, sheet.text()) } }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = P.Text, fontSize = 15.sp, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun TwoFields(content1: @Composable () -> Unit, content2: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { content1() }
        Box(Modifier.weight(1f)) { content2() }
    }
}

@Composable
private fun About(text: String) {
    Text(text, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
}

private fun money(v: Double) = "%,.2f".format(Locale.US, v)
private fun signed(v: Double) = "%+,.2f".format(Locale.US, v)
private fun pct(v: Double) = "%.3f%%".format(Locale.US, v * 100)
private fun fx(v: Double, ccy: String) = if (ccy == "JPY") "%.3f".format(Locale.US, v) else "%.5f".format(Locale.US, v)
private fun plain(v: Double, dp: Int = 4) = "%,.${dp}f".format(Locale.US, v)

/** "4.5" (percent, as typed) → 0.045. */
private fun String.rate(): Double? = num()?.div(100)

/**
 * A field that can start from a SlothDesk number. Until you type, it shows the borrowed value
 * and says where it came from; once you type, yours is kept.
 */
@Composable
private fun BorrowedField(label: String, inputs: CalcInputs, key: String, borrowed: String?, from: String?) {
    val mine = inputs.has(key)
    Field(label, if (mine) inputs.get(key) else borrowed.orEmpty(), "", number = true) { inputs.set(key, it) }
    if (!mine && borrowed != null && from != null) {
        Text(from, color = P.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 1W, 1M, 3M, 6M, 1Y from today, as calendar days. */
@Composable
private fun TenorChips(days: String, onPick: (String) -> Unit) {
    val today = LocalDate.now()
    val tenors = listOf(
        "1W" to today.plusWeeks(1), "1M" to today.plusMonths(1), "3M" to today.plusMonths(3),
        "6M" to today.plusMonths(6), "1Y" to today.plusYears(1)
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tenors.forEach { (label, date) ->
            val d = ChronoUnit.DAYS.between(today, date).toString()
            Pill(label, selected = days == d, quiet = true, small = true) { onPick(d) }
        }
    }
}

@Composable
private fun BasisChips(basis: String, onPick: (String) -> Unit) {
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("360", "365").forEach { b -> Pill("ACT/$b", selected = basis == b, quiet = true, small = true) { onPick(b) } }
    }
}

private fun rateFrom(f: DeskFact?): String? = f?.let { "%.3f".format(Locale.US, it.value * 100) }

@Composable
private fun ForwardCalc(inputs: CalcInputs, facts: DeskFacts?): Sheet? {
    val days = inputs.get("fwd.days", "91")
    val basis = inputs.get("fwd.basis", "360")
    About("A metal forward from spot, the money rate and the lease rate. Add a quoted forward or a futures price to read the rate it implies (an EFP).")
    Field("Spot (USD/oz)", inputs.get("fwd.spot"), "", number = true) { inputs.set("fwd.spot", it) }
    Field("Days", days, "", number = true) { inputs.set("fwd.days", it) }
    TenorChips(days) { inputs.set("fwd.days", it) }
    TwoFields(
        { BorrowedField("USD rate %", inputs, "fwd.rate", rateFrom(facts?.usdRate), facts?.usdRate?.let { "3-month T-bill on ${it.date}, from SlothDesk" }) },
        { Field("Lease rate %", inputs.get("fwd.lease"), "", number = true) { inputs.set("fwd.lease", it) } }
    )
    BasisChips(basis) { inputs.set("fwd.basis", it) }
    Field("Quoted forward or futures (optional)", inputs.get("fwd.quoted"), "", number = true) { inputs.set("fwd.quoted", it) }

    val s = inputs.get("fwd.spot").num() ?: return null
    val d = days.num()?.toInt()?.takeIf { it > 0 } ?: return null
    val b = basis.toIntOrNull() ?: 360
    val rateText = if (inputs.has("fwd.rate")) inputs.get("fwd.rate") else rateFrom(facts?.usdRate).orEmpty()
    val inputsOut = mutableListOf("Spot" to money(s), "Days" to "$d", "Day count" to "ACT/$b")
    val results = mutableListOf<Pair<String, String>>()
    val r = rateText.rate()
    val l = inputs.get("fwd.lease").rate()
    if (r != null && l != null) {
        inputsOut += "USD rate" to pct(r)
        inputsOut += "Lease rate" to pct(l)
        val swap = MetalsMath.swapRate(r, l)
        val f = MetalsMath.forward(s, swap, d, b)
        results += "Swap rate (rate − lease)" to pct(swap)
        results += "Forward" to money(f)
        results += "Forward points (USD/oz)" to signed(f - s)
    }
    inputs.get("fwd.quoted").num()?.let { q ->
        inputsOut += "Quoted forward" to money(q)
        results += "Implied swap rate" to pct(MetalsMath.impliedSwapRate(s, q, d, b))
        results += "Forward − spot (EFP)" to signed(q - s)
        r?.let { results += "Implied lease rate" to pct(MetalsMath.impliedLease(s, q, it, d, b)) }
    }
    return Sheet(
        "Metal forward", inputsOut, results,
        "Forward = spot × (1 + (rate − lease) × days / $b). A negative swap rate (lease above the money rate) means backwardation: metal is scarce to borrow."
    )
}

private val CURRENCIES = listOf("EUR", "CHF", "JPY", "CNH", "HKD", "GBP", "AUD")

@Composable
private fun CurrencyCalc(inputs: CalcInputs, facts: DeskFacts?): Sheet? {
    val ccy = inputs.get("ccy.ccy", "EUR")
    val days = inputs.get("ccy.days", "91")
    val fromPoints = inputs.get("ccy.mode") == "points"
    val usdBase = MetalsMath.usdIsBase(ccy)
    val pair = if (usdBase) "USD/$ccy" else "$ccy/USD"
    val basis = MetalsMath.basisFor(ccy)
    About("Metal priced in another currency, spot and forward, through the FX swap, and the metal's own swap rate in that currency.")
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CURRENCIES.forEach { c -> Pill(c, selected = c == ccy, quiet = true, small = true) { inputs.set("ccy.ccy", c) } }
    }
    Field("Metal spot (USD/oz)", inputs.get("ccy.spot"), "", number = true) { inputs.set("ccy.spot", it) }
    Field("Days", days, "", number = true) { inputs.set("ccy.days", it) }
    TenorChips(days) { inputs.set("ccy.days", it) }
    TwoFields(
        { BorrowedField("USD rate %", inputs, "ccy.rate", rateFrom(facts?.usdRate), facts?.usdRate?.let { "3-month T-bill on ${it.date}" }) },
        { Field("Lease rate %", inputs.get("ccy.lease"), "", number = true) { inputs.set("ccy.lease", it) } }
    )
    val jpy = facts?.usdJpy?.takeIf { ccy == "JPY" }
    BorrowedField("$pair spot", inputs, "ccy.fx.$ccy", jpy?.let { "%.3f".format(Locale.US, it.value) }, jpy?.let { "USD/JPY close on ${it.date}, from SlothDesk" })
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("FX from rates", selected = !fromPoints, quiet = true, small = true) { inputs.set("ccy.mode", "rates") }
        Pill("FX from swap points", selected = fromPoints, quiet = true, small = true) { inputs.set("ccy.mode", "points") }
    }
    if (fromPoints) Field("$pair swap points (pips)", inputs.get("ccy.points.$ccy"), "", number = true) { inputs.set("ccy.points.$ccy", it) }
    else Field("$ccy rate %", inputs.get("ccy.local.$ccy"), "", number = true) { inputs.set("ccy.local.$ccy", it) }

    val s = inputs.get("ccy.spot").num() ?: return null
    val d = days.num()?.toInt()?.takeIf { it > 0 } ?: return null
    val r = (if (inputs.has("ccy.rate")) inputs.get("ccy.rate") else rateFrom(facts?.usdRate).orEmpty()).rate() ?: return null
    val l = inputs.get("ccy.lease").rate() ?: return null
    val fxText = if (inputs.has("ccy.fx.$ccy")) inputs.get("ccy.fx.$ccy") else jpy?.let { "%.3f".format(Locale.US, it.value) }.orEmpty()
    val x = fxText.num()?.takeIf { it > 0 } ?: return null
    val pip = MetalsMath.pipFor(ccy)
    val rl = inputs.get("ccy.local.$ccy").rate()
    val pts = inputs.get("ccy.points.$ccy").num()
    val outright = if (fromPoints) {
        MetalsMath.fxFromPoints(x, pts ?: return null, pip)
    } else {
        val local = rl ?: return null
        if (usdBase) MetalsMath.fxForward(x, r, 360, local, basis, d) else MetalsMath.fxForward(x, local, basis, r, 360, d)
    }
    val fUsd = MetalsMath.forward(s, MetalsMath.swapRate(r, l), d, 360)
    val c = MetalsMath.crossForward(s, fUsd, x, outright, usdBase, d, basis)
    val inputsOut = mutableListOf(
        "Metal spot" to "${money(s)} USD/oz", "Days" to "$d", "USD rate" to pct(r), "Lease rate" to pct(l), "$pair spot" to fx(x, ccy)
    )
    if (fromPoints) inputsOut += "$pair swap points" to plain(pts ?: 0.0, 2) else inputsOut += "$ccy rate" to pct(rl ?: 0.0)
    val results = mutableListOf(
        "Metal forward in USD" to money(fUsd),
        "$pair outright" to fx(outright, ccy),
        "$pair swap points" to plain(MetalsMath.points(x, outright, pip), 2),
        "Metal spot in $ccy" to money(c.spotLocal),
        "Metal forward in $ccy" to money(c.forwardLocal),
        "Metal swap rate in $ccy" to pct(c.localSwapRate)
    )
    if (!fromPoints && rl != null) results += "$ccy rate − that swap (the lease, from $ccy)" to pct(rl - c.localSwapRate)
    return Sheet(
        "Metal in $ccy through the FX swap", inputsOut, results,
        "USD legs on ACT/360, $ccy on ACT/$basis (check your desk's convention). The metal's swap rate in $ccy is roughly the $ccy rate minus the lease rate: when the two currencies' rates differ, so does the cost of carrying metal in each."
    )
}

private val LOCAL_CCY = listOf("CNY", "HKD", "INR", "JPY", "EUR")
private val LOCAL_UNITS = listOf(WeightUnit.GRAM, WeightUnit.KILOGRAM, WeightUnit.HK_TAEL, WeightUnit.TOLA, WeightUnit.TROY_OUNCE)

private fun unitOf(name: String, default: WeightUnit) = WeightUnit.entries.firstOrNull { it.name == name } ?: default

@Composable
private fun LocalCalc(inputs: CalcInputs): Sheet? {
    val ccy = inputs.get("loc.ccy", "CNY")
    val unit = unitOf(inputs.get("loc.unit"), WeightUnit.GRAM)
    About("A local price (Shanghai per gram, Hong Kong per tael, India per gram…) against London: the premium or discount, in USD per ounce.")
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LOCAL_CCY.forEach { c -> Pill(c, selected = c == ccy, quiet = true, small = true) { inputs.set("loc.ccy", c) } }
    }
    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LOCAL_UNITS.forEach { u -> Pill("per ${u.label}", selected = u == unit, quiet = true, small = true) { inputs.set("loc.unit", u.name) } }
    }
    Field("Local price ($ccy per ${unit.label})", inputs.get("loc.price"), "", number = true) { inputs.set("loc.price", it) }
    TwoFields(
        { Field("USD/$ccy", inputs.get("loc.fx.$ccy"), "", number = true) { inputs.set("loc.fx.$ccy", it) } },
        { Field("London (USD/oz)", inputs.get("loc.xau"), "", number = true) { inputs.set("loc.xau", it) } }
    )

    val p = inputs.get("loc.price").num() ?: return null
    val fx = inputs.get("loc.fx.$ccy").num()?.takeIf { it > 0 } ?: return null
    val london = inputs.get("loc.xau").num()?.takeIf { it > 0 } ?: return null
    val perGram = p / unit.grams
    val londonLocal = MetalsMath.londonPerGram(london, fx) * unit.grams
    val premium = MetalsMath.localPremium(perGram, fx, london)
    return Sheet(
        "Local price against London ($ccy)",
        listOf("Local price" to "${money(p)} $ccy per ${unit.label}", "USD/$ccy" to plain(fx), "London" to "${money(london)} USD/oz"),
        listOf(
            "London in $ccy per ${unit.label}" to money(londonLocal),
            "Local price per gram" to "${plain(perGram, 3)} $ccy",
            if (premium >= 0) "Premium (USD/oz)" to signed(premium) else "Discount (USD/oz)" to signed(premium),
            "Premium (%)" to "%+.2f%%".format(Locale.US, premium / london * 100)
        ),
        "London is loco London and duty-free; a local price may include VAT, duty or a different fineness, so part of the gap is the cost of moving metal, not an arbitrage."
    )
}

@Composable
private fun WeightsCalc(inputs: CalcInputs): Sheet? {
    val unit = unitOf(inputs.get("wt.unit"), WeightUnit.KILOGRAM)
    Field("Amount", inputs.get("wt.amount"), "", number = true) { inputs.set("wt.amount", it) }
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeightUnit.entries.forEach { u -> Pill(u.label, selected = u == unit, quiet = true, small = true) { inputs.set("wt.unit", u.name) } }
    }
    Field("Fineness (optional)", inputs.get("wt.fine"), "0.9999, 0.995…", number = true) { inputs.set("wt.fine", it) }
    val a = inputs.get("wt.amount").num() ?: return null
    val fine = inputs.get("wt.fine").num()?.takeIf { it > 0 && it <= 1 }
    val results = WeightUnit.entries.filter { it != unit }.map { u -> u.label to plain(MetalsMath.convert(a, unit, u), 4) }.toMutableList()
    fine?.let {
        val oz = MetalsMath.convert(MetalsMath.fineWeight(a, it), unit, WeightUnit.TROY_OUNCE)
        results.add(0, "Fine troy oz" to plain(oz, 3))
    }
    return Sheet(
        "Weights",
        listOfNotNull("Amount" to "${plain(a, 4)} ${unit.label}", fine?.let { "Fineness" to plain(it, 4) }),
        results,
        "1 troy oz = 31.1035 g; HK tael 37.429 g; 市两 50 g; tola 11.664 g."
    )
}

private val METALS = listOf("Gold", "Silver", "Platinum", "Palladium")

@Composable
private fun PositionCalc(inputs: CalcInputs): Sheet? {
    val metal = inputs.get("pos.metal", "Gold")
    val unit = unitOf(inputs.get("pos.unit"), WeightUnit.TROY_OUNCE)
    val long = inputs.get("pos.side", "long") == "long"
    Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        METALS.forEach { m -> Pill(m, selected = m == metal, quiet = true, small = true) { inputs.set("pos.metal", m) } }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Long", selected = long, quiet = true, small = true) { inputs.set("pos.side", "long") }
        Pill("Short", selected = !long, quiet = true, small = true) { inputs.set("pos.side", "short") }
    }
    Field("Amount", inputs.get("pos.amount"), "", number = true) { inputs.set("pos.amount", it) }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(WeightUnit.TROY_OUNCE, WeightUnit.KILOGRAM, WeightUnit.GRAM).forEach { u ->
            Pill(u.label, selected = u == unit, quiet = true, small = true) { inputs.set("pos.unit", u.name) }
        }
    }
    TwoFields(
        { Field("Entry (USD/oz)", inputs.get("pos.entry"), "", number = true) { inputs.set("pos.entry", it) } },
        { Field("Price now (USD/oz)", inputs.get("pos.now"), "", number = true) { inputs.set("pos.now", it) } }
    )

    val a = inputs.get("pos.amount").num() ?: return null
    val oz = MetalsMath.convert(a, unit, WeightUnit.TROY_OUNCE)
    val e = inputs.get("pos.entry").num() ?: return null
    val now = inputs.get("pos.now").num()
    val contracts = when (metal) {
        "Gold" -> listOf(MetalsMath.Contract.GC, MetalsMath.Contract.MGC)
        "Silver" -> listOf(MetalsMath.Contract.SI, MetalsMath.Contract.SIL)
        "Platinum" -> listOf(MetalsMath.Contract.PL)
        else -> listOf(MetalsMath.Contract.PA)
    }
    val results = mutableListOf(
        "Troy ounces" to plain(oz, 3),
        "Notional at entry" to "${money(MetalsMath.notional(oz, e))} USD",
        "P&L per 1 USD move" to "${money(oz)} USD"
    )
    now?.let { p ->
        results += "Notional now" to "${money(MetalsMath.notional(oz, p))} USD"
        results += "P&L" to "${signed(MetalsMath.pnl(oz, e, p, long))} USD"
    }
    contracts.forEach { c -> results += c.label to plain(MetalsMath.contracts(oz, c), 2) }
    return Sheet(
        "$metal position",
        listOfNotNull(
            "Direction" to (if (long) "long" else "short"), "Amount" to "${plain(a, 4)} ${unit.label}", "Entry" to money(e),
            now?.let { "Price now" to money(it) }
        ),
        results
    )
}
