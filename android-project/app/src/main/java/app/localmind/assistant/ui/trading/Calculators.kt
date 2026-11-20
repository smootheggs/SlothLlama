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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.trading.MetalsMath
import app.localmind.assistant.domain.trading.MetalsMath.WeightUnit
import app.localmind.assistant.ui.theme.SlothPalette as P
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

/** The desk's sums. Every result is worked out in code; "Ask the Trader" sends them as they are. */
@Composable
internal fun Calculators(onAsk: (String, String) -> Unit) {
    var calc by rememberSaveable { mutableStateOf(Calc.FORWARD) }
    Row(Modifier.padding(top = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Calc.entries.forEach { c -> Pill(c.label, selected = c == calc, quiet = true, small = true) { calc = c } }
    }
    val sheet: Sheet? = when (calc) {
        Calc.FORWARD -> ForwardCalc()
        Calc.CURRENCY -> CurrencyCalc()
        Calc.LOCAL -> LocalCalc()
        Calc.WEIGHTS -> WeightsCalc()
        Calc.POSITION -> PositionCalc()
    }
    if (sheet != null && sheet.results.isNotEmpty()) {
        SectionLabel("Results")
        sheet.results.forEach { (k, v) -> ResultRow(k, v) }
        if (sheet.note.isNotBlank()) Text(sheet.note, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 14.dp)) { QuietButton("Ask the Trader") { onAsk(sheet.title, sheet.text()) } }
    } else {
        Text("Fill in the numbers above to see the results.", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
    }
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

private fun money(v: Double) = "%,.2f".format(Locale.US, v)
private fun signed(v: Double) = "%+,.2f".format(Locale.US, v)
private fun pct(v: Double) = "%.3f%%".format(Locale.US, v * 100)
private fun fx(v: Double, ccy: String) = if (ccy == "JPY") "%.3f".format(Locale.US, v) else "%.5f".format(Locale.US, v)
private fun plain(v: Double, dp: Int = 4) = "%,.${dp}f".format(Locale.US, v)

/** "4.5" (percent, as typed) → 0.045. */
private fun String.rate(): Double? = num()?.div(100)

@Composable
private fun BasisChips(basis: Int, onPick: (Int) -> Unit) {
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(360, 365).forEach { b -> Pill("ACT/$b", selected = basis == b, quiet = true, small = true) { onPick(b) } }
    }
}

@Composable
private fun ForwardCalc(): Sheet? {
    var spot by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var lease by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("90") }
    var basis by rememberSaveable { mutableStateOf(360) }
    var quoted by rememberSaveable { mutableStateOf("") }
    Text(
        "A metal forward from spot, the money rate and the lease rate. Add a quoted forward or a futures price to read the rate it implies (an EFP).",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp)
    )
    TwoFields({ Field("Spot (USD/oz)", spot, "", number = true) { spot = it } }, { Field("Days", days, "", number = true) { days = it } })
    TwoFields({ Field("USD rate %", rate, "SOFR term, say", number = true) { rate = it } }, { Field("Lease rate %", lease, "", number = true) { lease = it } })
    BasisChips(basis) { basis = it }
    Field("Quoted forward or futures (optional)", quoted, "", number = true) { quoted = it }

    val s = spot.num() ?: return null
    val d = days.num()?.toInt()?.takeIf { it > 0 } ?: return null
    val inputs = mutableListOf("Spot" to money(s), "Days" to "$d", "Day count" to "ACT/$basis")
    val results = mutableListOf<Pair<String, String>>()
    val r = rate.rate()
    val l = lease.rate()
    if (r != null && l != null) {
        inputs += "USD rate" to pct(r)
        inputs += "Lease rate" to pct(l)
        val swap = MetalsMath.swapRate(r, l)
        val f = MetalsMath.forward(s, swap, d, basis)
        results += "Swap rate (rate − lease)" to pct(swap)
        results += "Forward" to money(f)
        results += "Forward points (USD/oz)" to signed(f - s)
    }
    quoted.num()?.let { q ->
        inputs += "Quoted forward" to money(q)
        val implied = MetalsMath.impliedSwapRate(s, q, d, basis)
        results += "Implied swap rate" to pct(implied)
        results += "Forward − spot (EFP)" to signed(q - s)
        r?.let { results += "Implied lease rate" to pct(MetalsMath.impliedLease(s, q, it, d, basis)) }
    }
    return Sheet(
        "Metal forward", inputs, results,
        "Forward = spot × (1 + (rate − lease) × days / $basis). A negative swap rate (lease above the money rate) means backwardation: metal is scarce to borrow."
    )
}

private val CURRENCIES = listOf("EUR", "CHF", "JPY", "CNH", "HKD", "GBP", "AUD")

@Composable
private fun CurrencyCalc(): Sheet? {
    var ccy by rememberSaveable { mutableStateOf("EUR") }
    var spot by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var lease by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("90") }
    var fxSpot by rememberSaveable { mutableStateOf("") }
    var fromPoints by rememberSaveable { mutableStateOf(false) }
    var localRate by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("") }
    val usdBase = MetalsMath.usdIsBase(ccy)
    val pair = if (usdBase) "USD/$ccy" else "$ccy/USD"
    val basis = MetalsMath.basisFor(ccy)
    Text(
        "Metal priced in another currency, spot and forward, through the FX swap: and the metal's own swap rate in that currency.",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp)
    )
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CURRENCIES.forEach { c -> Pill(c, selected = c == ccy, quiet = true, small = true) { ccy = c } }
    }
    TwoFields({ Field("Metal spot (USD/oz)", spot, "", number = true) { spot = it } }, { Field("Days", days, "", number = true) { days = it } })
    TwoFields({ Field("USD rate %", rate, "", number = true) { rate = it } }, { Field("Lease rate %", lease, "", number = true) { lease = it } })
    Field("$pair spot", fxSpot, "", number = true) { fxSpot = it }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("FX from rates", selected = !fromPoints, quiet = true, small = true) { fromPoints = false }
        Pill("FX from swap points", selected = fromPoints, quiet = true, small = true) { fromPoints = true }
    }
    if (fromPoints) Field("$pair swap points (pips)", points, "", number = true) { points = it }
    else Field("$ccy rate %", localRate, "", number = true) { localRate = it }

    val s = spot.num() ?: return null
    val d = days.num()?.toInt()?.takeIf { it > 0 } ?: return null
    val r = rate.rate() ?: return null
    val l = lease.rate() ?: return null
    val x = fxSpot.num()?.takeIf { it > 0 } ?: return null
    val pip = MetalsMath.pipFor(ccy)
    val rl = localRate.rate()
    val outright = if (fromPoints) {
        val p = points.num() ?: return null
        MetalsMath.fxFromPoints(x, p, pip)
    } else {
        val local = rl ?: return null
        if (usdBase) MetalsMath.fxForward(x, r, 360, local, basis, d) else MetalsMath.fxForward(x, local, basis, r, 360, d)
    }
    val fUsd = MetalsMath.forward(s, MetalsMath.swapRate(r, l), d, 360)
    val c = MetalsMath.crossForward(s, fUsd, x, outright, usdBase, d, basis)
    val inputs = mutableListOf(
        "Metal spot" to "${money(s)} USD/oz", "Days" to "$d", "USD rate" to pct(r), "Lease rate" to pct(l),
        "$pair spot" to fx(x, ccy)
    )
    if (fromPoints) inputs += "$pair swap points" to (points.num()?.let { plain(it, 2) } ?: "") else inputs += "$ccy rate" to pct(rl ?: 0.0)
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
        "Metal in $ccy through the FX swap", inputs, results,
        "USD legs on ACT/360, $ccy on ACT/$basis (check your desk's convention). The metal's swap rate in $ccy is roughly the $ccy rate minus the lease rate: when the two currencies' rates differ, so does the cost of carrying metal in each."
    )
}

private val LOCAL_CCY = listOf("CNY", "HKD", "INR", "JPY", "EUR")
private val LOCAL_UNITS = listOf(WeightUnit.GRAM, WeightUnit.KILOGRAM, WeightUnit.HK_TAEL, WeightUnit.TOLA, WeightUnit.TROY_OUNCE)

@Composable
private fun LocalCalc(): Sheet? {
    var ccy by rememberSaveable { mutableStateOf("CNY") }
    var unit by rememberSaveable { mutableStateOf(WeightUnit.GRAM) }
    var local by rememberSaveable { mutableStateOf("") }
    var usdLocal by rememberSaveable { mutableStateOf("") }
    var xau by rememberSaveable { mutableStateOf("") }
    Text(
        "A local price (Shanghai per gram, Hong Kong per tael, India per gram…) against London: the premium or discount, in USD per ounce.",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp)
    )
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LOCAL_CCY.forEach { c -> Pill(c, selected = c == ccy, quiet = true, small = true) { ccy = c } }
    }
    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LOCAL_UNITS.forEach { u -> Pill("per ${u.label}", selected = u == unit, quiet = true, small = true) { unit = u } }
    }
    Field("Local price ($ccy per ${unit.label})", local, "", number = true) { local = it }
    TwoFields({ Field("USD/$ccy", usdLocal, "", number = true) { usdLocal = it } }, { Field("London (USD/oz)", xau, "", number = true) { xau = it } })

    val p = local.num() ?: return null
    val fx = usdLocal.num()?.takeIf { it > 0 } ?: return null
    val london = xau.num()?.takeIf { it > 0 } ?: return null
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
private fun WeightsCalc(): Sheet? {
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(WeightUnit.KILOGRAM) }
    var fineness by rememberSaveable { mutableStateOf("") }
    Field("Amount", amount, "", number = true) { amount = it }
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeightUnit.entries.forEach { u -> Pill(u.label, selected = u == unit, quiet = true, small = true) { unit = u } }
    }
    Field("Fineness (optional)", fineness, "0.9999, 0.995…", number = true) { fineness = it }
    val a = amount.num() ?: return null
    val fine = fineness.num()?.takeIf { it in 0.0..1.0 }
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
private fun PositionCalc(): Sheet? {
    var metal by rememberSaveable { mutableStateOf("Gold") }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(WeightUnit.TROY_OUNCE) }
    var entry by rememberSaveable { mutableStateOf("") }
    var now by rememberSaveable { mutableStateOf("") }
    var long by rememberSaveable { mutableStateOf(true) }
    Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        METALS.forEach { m -> Pill(m, selected = m == metal, quiet = true, small = true) { metal = m } }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Long", selected = long, quiet = true, small = true) { long = true }
        Pill("Short", selected = !long, quiet = true, small = true) { long = false }
    }
    Field("Amount", amount, "", number = true) { amount = it }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(WeightUnit.TROY_OUNCE, WeightUnit.KILOGRAM, WeightUnit.GRAM).forEach { u ->
            Pill(u.label, selected = u == unit, quiet = true, small = true) { unit = u }
        }
    }
    TwoFields({ Field("Entry (USD/oz)", entry, "", number = true) { entry = it } }, { Field("Price now (USD/oz)", now, "", number = true) { now = it } })

    val a = amount.num() ?: return null
    val oz = MetalsMath.convert(a, unit, WeightUnit.TROY_OUNCE)
    val e = entry.num() ?: return null
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
    now.num()?.let { p ->
        results += "Notional now" to "${money(MetalsMath.notional(oz, p))} USD"
        results += "P&L" to "${signed(MetalsMath.pnl(oz, e, p, long))} USD"
    }
    contracts.forEach { c -> results += c.label to plain(MetalsMath.contracts(oz, c), 2) }
    return Sheet(
        "$metal position",
        listOfNotNull(
            "Direction" to if (long) "long" else "short", "Amount" to "${plain(a, 4)} ${unit.label}", "Entry" to money(e),
            now.num()?.let { "Price now" to money(it) }
        ),
        results
    )
}
