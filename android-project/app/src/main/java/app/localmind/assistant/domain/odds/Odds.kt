package app.localmind.assistant.domain.odds

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A price, kept as decimal odds (the stake comes back included: 1.87 pays 1.87 for 1). American
 * and fractional are the same price written another way.
 */
data class Price(val decimal: Double) {
    init {
        require(decimal > 1.0) { "Decimal odds are above 1" }
    }

    /** The chance the price implies, margin included. */
    val implied: Double get() = 1.0 / decimal

    /** −115, +110; evens is +100. */
    val american: Int get() = Odds.americanFromDecimal(decimal)

    /** 20/23, 11/10; evens is 1/1. */
    val fraction: Pair<Int, Int> get() = Odds.fractionFromDecimal(decimal)
}

/**
 * Odds and chances: converting between the three ways prices are written, taking a book's margin
 * out, fair odds for a chance, and how far a chance sits from the market's. All worked out here;
 * a model never touches these numbers.
 */
object Odds {

    fun decimalFromAmerican(american: Int): Double = when {
        american >= 100 -> 1.0 + american / 100.0
        american <= -100 -> 1.0 + 100.0 / -american
        else -> throw IllegalArgumentException("American odds are at least +100 or at most −100")
    }

    fun americanFromDecimal(decimal: Double): Int =
        if (decimal >= 2.0) ((decimal - 1.0) * 100).roundToInt() else -(100 / (decimal - 1.0)).roundToInt()

    fun decimalFromFraction(num: Int, den: Int): Double {
        require(num > 0 && den > 0) { "A fraction is two whole numbers above 0" }
        return 1.0 + num.toDouble() / den
    }

    /**
     * The fraction closest to the price's winnings per unit, with a denominator up to [maxDen]
     * (ties go to the smaller one): 1.87 → 20/23, 1.83 → 5/6, 1.91 → 10/11, 2.10 → 11/10.
     */
    fun fractionFromDecimal(decimal: Double, maxDen: Int = 25): Pair<Int, Int> {
        val x = decimal - 1.0
        var best = 1 to 1
        var bestErr = Double.MAX_VALUE
        for (den in 1..maxDen) {
            val num = (x * den).roundToInt()
            if (num < 1) continue
            val err = abs(x - num.toDouble() / den)
            if (err < bestErr - 1e-9) {
                best = num to den
                bestErr = err
                if (err < 1e-9) break
            }
        }
        return best
    }

    private val AMERICAN = Regex("""^[+\-−–]\s*\d{3,5}$""")
    private val DECIMAL = Regex("""^\d{1,3}(?:[.,]\d{1,3})?$""")
    private val FRACTION = Regex("""^(\d{1,3})\s*/\s*(\d{1,3})$""")

    /**
     * Reads one price as written: "+110", "−115", "1.87" (or "1,87"), "20/23", "evens". Null when
     * it isn't one. A bare whole number of 100 or more is taken as American (+150), since decimal
     * odds that long are rare and are written with a point.
     */
    fun parse(text: String): Price? {
        val s = text.trim().lowercase().replace('−', '-').replace('–', '-')
        if (s == "evens" || s == "evs" || s == "even" || s == "ev") return Price(2.0)
        return runCatching {
            when {
                AMERICAN.matches(s) -> Price(decimalFromAmerican(s.replace(" ", "").replace("+", "").toInt()))
                FRACTION.matches(s) -> FRACTION.matchEntire(s)!!.let { m -> Price(decimalFromFraction(m.groupValues[1].toInt(), m.groupValues[2].toInt())) }
                DECIMAL.matches(s) -> {
                    val v = s.replace(',', '.').toDouble()
                    if (!s.contains('.') && !s.contains(',') && v >= 100) Price(decimalFromAmerican(v.toInt())) else Price(v)
                }
                else -> null
            }
        }.getOrNull()
    }

    /** How much the implied chances add up to over 100%: the book's margin (0.048 = 4.8%). */
    fun margin(prices: List<Price>): Double = prices.sumOf { it.implied } - 1.0

    /**
     * The market's own chances, with the margin taken out in proportion (each implied chance
     * divided by their total). Simple and standard; it slightly flatters long shots.
     */
    fun fair(prices: List<Price>): List<Double> {
        val total = prices.sumOf { it.implied }
        return prices.map { it.implied / total }
    }

    /** The price at which a chance breaks even: 1 ÷ chance. */
    fun fairOdds(chance: Double): Double {
        require(chance > 0.0 && chance < 1.0) { "A chance is between 0 and 1" }
        return 1.0 / chance
    }

    /** Your chance above (or below) the market's, in points: 0.557 vs 0.510 is +4.7. */
    fun edgePoints(yours: Double, market: Double): Double = (yours - market) * 100.0

    /** "1.87", "−115", "20/23", as written in each style. */
    fun show(price: Price): Triple<String, String, String> {
        val a = price.american
        val (n, d) = price.fraction
        return Triple(
            String.format(java.util.Locale.ROOT, "%.2f", price.decimal),
            if (a > 0) "+$a" else "−${-a}",
            "$n/$d"
        )
    }
}
