package app.localmind.assistant.domain.trading

import kotlin.math.abs

/**
 * The sums a precious-metals desk does all day: forwards from rates and lease rates, metal in
 * other currencies through the FX swap, local prices against London, weights, and positions.
 * Worked out here so a chat never has to (the models get arithmetic wrong).
 *
 * Rates are decimals (0.045 = 4.5%); days are calendar days; [basis] is the day-count year
 * (360 for USD money markets, 365 for some others: check your desk's convention).
 */
object MetalsMath {

    const val GRAMS_PER_TROY_OUNCE = 31.1034768

    enum class WeightUnit(val label: String, val grams: Double) {
        TROY_OUNCE("troy oz", GRAMS_PER_TROY_OUNCE),
        GRAM("g", 1.0),
        KILOGRAM("kg", 1000.0),
        /** The Hong Kong gold tael (司馬兩), as the Chinese Gold & Silver Exchange trades it. */
        HK_TAEL("HK tael", 37.429),
        /** The mainland tael (市两). */
        CHINESE_TAEL("市两", 50.0),
        TOLA("tola", 11.6638038)
    }

    /** Exchange-traded contract sizes, in troy ounces. */
    enum class Contract(val label: String, val ounces: Double) {
        GC("COMEX gold (GC)", 100.0),
        MGC("COMEX micro gold (MGC)", 10.0),
        SI("COMEX silver (SI)", 5000.0),
        SIL("COMEX silver 1,000 oz (SIL)", 1000.0),
        PL("NYMEX platinum (PL)", 50.0),
        PA("NYMEX palladium (PA)", 100.0)
    }

    fun convert(amount: Double, from: WeightUnit, to: WeightUnit): Double = amount * from.grams / to.grams

    /** Fine weight: gross weight × fineness (a 400 oz bar at 0.9950 holds 398 fine oz). */
    fun fineWeight(gross: Double, fineness: Double): Double = gross * fineness

    // --- forwards ---

    /** The swap rate a metal forward earns: the money rate minus the lease rate (GOFO-style). */
    fun swapRate(moneyRate: Double, leaseRate: Double): Double = moneyRate - leaseRate

    /** Outright forward: spot × (1 + swap rate × days / basis). */
    fun forward(spot: Double, swapRate: Double, days: Int, basis: Int = 360): Double =
        spot * (1 + swapRate * days / basis)

    /** The swap rate a forward (or a futures price, for an EFP) implies against spot. */
    fun impliedSwapRate(spot: Double, forward: Double, days: Int, basis: Int = 360): Double =
        (forward / spot - 1) * basis / days

    /** The lease rate implied by a forward and the money rate. */
    fun impliedLease(spot: Double, forward: Double, moneyRate: Double, days: Int, basis: Int = 360): Double =
        moneyRate - impliedSwapRate(spot, forward, days, basis)

    // --- FX ---

    /**
     * An FX outright from the two money rates (covered interest parity) for BASE/QUOTE quoted
     * as QUOTE per BASE: spot × (1 + quote rate × d / quote basis) / (1 + base rate × d / base basis).
     */
    fun fxForward(spot: Double, baseRate: Double, baseBasis: Int, quoteRate: Double, quoteBasis: Int, days: Int): Double =
        spot * (1 + quoteRate * days / quoteBasis) / (1 + baseRate * days / baseBasis)

    /** An FX outright from swap points: spot + points × pip (0.0001 for EUR/USD, 0.01 for USD/JPY). */
    fun fxFromPoints(spot: Double, points: Double, pip: Double): Double = spot + points * pip

    /** Swap points between spot and an outright, in pips. */
    fun points(spot: Double, outright: Double, pip: Double): Double = (outright - spot) / pip

    /**
     * Metal in another currency: XAU/USD and an FX rate give XAU/CCY. [usdIsBase] is true for
     * USD/JPY, USD/CHF, USD/CNH, USD/HKD (multiply) and false for EUR/USD, GBP/USD, AUD/USD (divide).
     */
    fun metalIn(metalUsd: Double, fx: Double, usdIsBase: Boolean): Double = if (usdIsBase) metalUsd * fx else metalUsd / fx

    /** Everything about a metal forward in another currency, from the USD legs. */
    data class CrossForward(
        val spotLocal: Double,
        val forwardLocal: Double,
        /** The metal's swap rate in the other currency, implied by the two forwards. */
        val localSwapRate: Double,
        val fxOutright: Double
    )

    fun crossForward(
        metalSpotUsd: Double, metalForwardUsd: Double, fxSpot: Double, fxOutright: Double,
        usdIsBase: Boolean, days: Int, localBasis: Int
    ): CrossForward {
        val s = metalIn(metalSpotUsd, fxSpot, usdIsBase)
        val f = metalIn(metalForwardUsd, fxOutright, usdIsBase)
        return CrossForward(s, f, impliedSwapRate(s, f, days, localBasis), fxOutright)
    }

    // --- location ---

    /** London (XAU/USD per oz) in a local currency per gram. */
    fun londonPerGram(metalUsd: Double, usdLocal: Double): Double = metalUsd * usdLocal / GRAMS_PER_TROY_OUNCE

    /**
     * A local price per gram (Shanghai's Au9999 in CNY, say) against London, in USD per ounce:
     * positive is a premium. [usdLocal] is local currency per USD.
     */
    fun localPremium(localPerGram: Double, usdLocal: Double, metalUsd: Double): Double =
        localPerGram * GRAMS_PER_TROY_OUNCE / usdLocal - metalUsd

    // --- positions ---

    fun notional(ounces: Double, price: Double): Double = ounces * price

    /** Profit on [ounces] from [entry] to [exit]; negative is a loss. */
    fun pnl(ounces: Double, entry: Double, exit: Double, long: Boolean): Double =
        ounces * (exit - entry) * (if (long) 1 else -1)

    fun contracts(ounces: Double, contract: Contract): Double = ounces / contract.ounces

    /**
     * An idea's result in units of what it risked: (exit − entry) / |entry − stop|, signed by
     * direction. Null when there's no risk to measure against.
     */
    fun rMultiple(long: Boolean, entry: Double, stop: Double, exit: Double): Double? {
        val risk = abs(entry - stop)
        if (risk == 0.0) return null
        return (exit - entry) * (if (long) 1 else -1) / risk
    }

    /** Usual day-count basis by currency; your desk may differ. */
    fun basisFor(currency: String): Int = when (currency.uppercase()) {
        "GBP", "HKD", "AUD", "NZD", "SGD", "CNH" -> 365
        else -> 360
    }

    /** Whether USD is the base of the usual quote with [currency] (USD/JPY yes, EUR/USD no). */
    fun usdIsBase(currency: String): Boolean = currency.uppercase() !in setOf("EUR", "GBP", "AUD", "NZD")

    /** Pip size of the usual quote with [currency]. */
    fun pipFor(currency: String): Double = if (currency.uppercase() == "JPY") 0.01 else 0.0001
}
