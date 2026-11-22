package app.localmind.assistant.domain.desk

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * SlothDesk's simulator, a line-by-line port of the Colab engine (slothdesk.py 0.6) so the
 * tablet and the notebook give the same numbers on the same data. Rules it keeps:
 *  - signals use data up to a day's close and fill at the NEXT day's open;
 *  - every trade pays slippage and commission, plus a surcharge after a big overnight gap;
 *  - idle cash earns the T-bill, short-sale proceeds earn only the rebate set, borrowed cash
 *    pays the T-bill plus a margin spread; shorts pay a borrow fee; FX carry accrues daily
 *    minus a swap haircut;
 *  - options are modelled (Black-Scholes on the volatility index), not real quotes.
 * Nothing here can reach a broker.
 */
data class Costs(
    val slippageBp: Double = 5.0,
    val fxSlippageBp: Double = 2.0,
    val commissionBp: Double = 0.0,
    val gapSurchargeBp: Double = 10.0,
    val gapThresholdPct: Double = 1.5,
    val borrowBpPerYear: Double = 50.0,
    val borrowStressBpPerYear: Double = 300.0,
    val borrowStressVol: Double = 0.35,
    val shortRebateFraction: Double = 0.0,
    val marginSpreadBpPerYear: Double = 100.0,
    val fxSwapHaircutBpPerYear: Double = 35.0
)

/** Target weights decided at each date's close, with why a target changed ("" when it didn't). */
class Targets(
    val dates: List<LocalDate>,
    val assets: List<String>,
    /** weights[a][i]: weight of asset a decided at dates[i]; NaN = no decision that day. */
    val weights: List<DoubleArray>,
    val reasons: Array<String>
)

data class Order(
    val date: LocalDate, val asset: String, val action: String, val units: Double,
    val price: Double, val cost: Double, val reason: String
)

/** One position from entry to exit (for the covered call, one call from sale to expiry). */
data class Episode(
    val entry: LocalDate, val exit: LocalDate?, val days: Long, val returnPct: Double,
    val entryReason: String, val exitReason: String, val optionPnl: Double? = null
)

/** Where the covered call stands at the last close, for tonight's ticket. */
data class CallState(
    val openStrike: Double?, val openExpiry: LocalDate?, val contracts: Int,
    val sellNextOpen: Boolean, val shares: Double,
    val lastClose: Double, val lastVol: Double, val lastRate: Double, val lastDate: LocalDate
)

class SimResult(
    val name: String,
    val dates: List<LocalDate>,
    val equity: DoubleArray,
    val orders: Int,
    val costsPaid: Double,
    val exposure: Double,
    val log: List<Order>,
    val episodes: List<Episode>,
    val callState: CallState? = null
)

object DeskEngine {

    private const val EPS = 1e-12

    fun simulate(
        prices: Map<String, Bars>,
        targets: Targets,
        rf: Line,
        name: String,
        costs: Costs = Costs(),
        capital: Double = 100_000.0,
        costMult: Double = 1.0,
        carry: Map<String, Line> = emptyMap(),
        fxAssets: Set<String> = setOf("USDJPY"),
        cryptoAssets: Set<String> = emptySet(),
        cryptoSlippageBp: Double = 10.0,
        /** Days a year the calendar has: 252 trading days, or 365 for crypto alone. */
        periodsPerYear: Double = 252.0
    ): SimResult {
        val assets = targets.assets
        val dates = targets.dates
        val n = dates.size
        val bars = assets.associateWith { prices.getValue(it).on(dates) }
        val opens = assets.associateWith { bars.getValue(it).opens }
        val closes = assets.associateWith { bars.getValue(it).closes }
        val rvol20 = assets.associateWith { a ->
            DeskMath.rollingStd(DeskMath.pctChange(closes.getValue(a)), 20).map { it * sqrt(periodsPerYear) }.toDoubleArray()
        }
        val rfd = rf.asOf(dates).map { if (it.isNaN()) 0.0 else it }
        val carryD = carry.mapValues { (_, s) -> s.asOf(dates).map { if (it.isNaN()) 0.0 else it } }
        val swapCut = costs.fxSwapHaircutBpPerYear / 1e4

        var cash = capital
        val units = HashMap<String, Double>().apply { assets.forEach { put(it, 0.0) } }
        val lastTarget = HashMap<String, Double>().apply { assets.forEach { put(it, 0.0) } }
        var pending: Map<String, Double>? = null
        var pendingReason = ""
        val equity = DoubleArray(n)
        var orders = 0
        var costsPaid = 0.0
        var exposed = 0
        val log = ArrayList<Order>()
        val episodes = ArrayList<Episode>()
        var epEntry: LocalDate? = null
        var epBase = 0.0
        var epEntryReason = ""
        var epExitReason = ""
        var epClosing = false
        var prevEq = capital

        for (i in 0 until n) {
            val d = dates[i]
            // 1) execute yesterday's decision at today's open
            val p = pending
            if (p != null) {
                val changed = assets.filter { abs(p.getValue(it) - lastTarget.getValue(it)) > EPS }
                if (changed.isNotEmpty()) {
                    val wasFlat = units.values.all { abs(it) < EPS }
                    val eqOpen = cash + assets.sumOf { units.getValue(it) * opens.getValue(it)[i] }
                    for (a in changed) {
                        val px = opens.getValue(a)[i]
                        val want = p.getValue(a) * eqOpen / px
                        val delta = want - units.getValue(a)
                        if (abs(delta) < EPS) continue
                        var bp = when (a) {
                            in fxAssets -> costs.fxSlippageBp
                            in cryptoAssets -> cryptoSlippageBp
                            else -> costs.slippageBp
                        }
                        val pc = if (i > 0) closes.getValue(a)[i - 1] else Double.NaN
                        if (!pc.isNaN() && pc != 0.0 && abs(px / pc - 1) * 100 > costs.gapThresholdPct) bp += costs.gapSurchargeBp
                        val fee = abs(delta * px) * (bp + costs.commissionBp) / 1e4 * costMult
                        cash -= delta * px + fee
                        log += Order(d, a, if (delta > 0) "buy" else "sell", delta, px, fee, pendingReason)
                        units[a] = want
                        costsPaid += fee
                        orders++
                        lastTarget[a] = p.getValue(a)
                    }
                    val nowFlat = units.values.all { abs(it) < EPS }
                    if (wasFlat && !nowFlat) {
                        epEntry = d; epBase = prevEq; epEntryReason = pendingReason
                    } else if (!wasFlat && nowFlat && epEntry != null) {
                        epExitReason = pendingReason; epClosing = true
                    }
                }
                pending = null
            }

            // 2) end-of-day interest, borrow and carry
            val shortProceeds = assets.sumOf { max(0.0, -units.getValue(it) * closes.getValue(it)[i]) }
            val freeCash = cash - shortProceeds
            val r = rfd[i]
            var interest = if (freeCash >= 0) freeCash * r / periodsPerYear
            else freeCash * (r + costs.marginSpreadBpPerYear / 1e4) / periodsPerYear
            interest += shortProceeds * r * costs.shortRebateFraction / periodsPerYear
            cash += interest
            for (a in assets) {
                val notional = units.getValue(a) * closes.getValue(a)[i]
                if (notional < 0) {
                    val v = rvol20.getValue(a)[i]
                    val rate = if (!v.isNaN() && v > costs.borrowStressVol) costs.borrowStressBpPerYear else costs.borrowBpPerYear
                    val fee = -notional * rate / 1e4 / periodsPerYear * costMult
                    cash -= fee
                    costsPaid += fee
                }
                val cd = carryD[a]
                if (cd != null && notional != 0.0) {
                    val c0 = cd[i]
                    val c = if (notional > 0) c0 - swapCut * costMult else -c0 - swapCut * costMult
                    cash += abs(notional) * c / periodsPerYear
                }
            }

            val eq = cash + assets.sumOf { units.getValue(it) * closes.getValue(it)[i] }
            equity[i] = eq
            if (units.values.any { abs(it) > EPS }) exposed++
            val entry = epEntry
            if (entry != null && epClosing) {
                episodes += Episode(entry, d, ChronoUnit.DAYS.between(entry, d), (eq / epBase - 1) * 100, epEntryReason, epExitReason)
                epEntry = null
                epClosing = false
            }
            prevEq = eq

            // 3) decide at today's close -> executes tomorrow
            if (assets.none { targets.weights[assets.indexOf(it)][i].isNaN() }) {
                pending = assets.indices.associate { k -> assets[k] to targets.weights[k][i] }
                pendingReason = targets.reasons[i]
            }
        }
        val open = epEntry
        if (open != null) {
            episodes += Episode(open, null, ChronoUnit.DAYS.between(open, dates.last()), (equity.last() / epBase - 1) * 100,
                epEntryReason, "open: still held")
        }
        return SimResult(name, dates, equity, orders, costsPaid, exposed.toDouble() / n, log, episodes)
    }

    // --- target weights: the strategies themselves are rules now (RuleEngine) ---

    fun buyAndHold(dates: List<LocalDate>, asset: String, weight: Double = 1.0): Targets =
        Targets(dates, listOf(asset), listOf(DoubleArray(dates.size) { weight }), Array(dates.size) { "" })

    fun commonDates(a: Bars, b: Bars): List<LocalDate> {
        val other = b.dates.toHashSet()
        return a.dates.filter { it in other }.sorted()
    }

    // --- modelled options: Black-Scholes, European, no dividend ---

    fun bsCall(s: Double, k: Double, t: Double, r: Double, sigma: Double): Double {
        if (t <= 0) return max(s - k, 0.0)
        if (sigma <= 0) return max(s - k * exp(-r * t), 0.0)
        val d1 = (ln(s / k) + (r + 0.5 * sigma * sigma) * t) / (sigma * sqrt(t))
        val d2 = d1 - sigma * sqrt(t)
        return s * DeskMath.normCdf(d1) - k * exp(-r * t) * DeskMath.normCdf(d2)
    }

    fun strikeForDelta(s: Double, t: Double, r: Double, sigma: Double, delta: Double, step: Double): Double {
        val d1 = DeskMath.normInv(delta)
        val k = s * exp(-(d1 * sigma * sqrt(t)) + (r + 0.5 * sigma * sigma) * t)
        // Python's round() goes to the even number on a tie, as Math.rint does.
        return max(step, Math.rint(k / step) * step)
    }

    /** The last trading day on or before start + [dte] calendar days (at least the day after start). */
    fun snapExpiry(dates: List<LocalDate>, startIndex: Int, dte: Int): LocalDate {
        val target = dates[startIndex].plusDays(dte.toLong())
        var pos = -1
        for (i in dates.indices) if (dates[i] <= target) pos = i else break
        return dates[minOf(max(pos, startIndex + 1), dates.size - 1)]
    }

    /**
     * Covered call on [Params] "asset" (GLD): buy whole 100-share lots on the second day; when
     * no call is open, sell one call per 100 shares at the next open, struck for the target delta
     * from that open and the volatility known at the prior close; cash-settle at the expiry
     * close and keep the shares. A wider half-spread when the volatility index is above its
     * one-year 80th percentile. No call is sold on the last day of data (its expiry isn't known).
     */
    fun coveredCall(prices: Map<String, Bars>, vol: Line, rf: Line, p: Params, name: String,
                    costs: Costs = Costs(), capital: Double = 100_000.0, costMult: Double = 1.0): SimResult {
        val a = p.text("asset", "GLD")
        val all = prices.getValue(a)
        val vAll = vol.asOf(all.dates)
        val first = vAll.indexOfFirst { !it.isNaN() }
        require(first >= 0) { "No volatility data for the covered call" }
        val dates = all.dates.subList(first, all.size)
        val bars = all.on(dates)
        val v = vAll.copyOfRange(first, all.size)
        val vHi = DeskMath.rollingQuantile(v, 252, 126, 0.80)
        val r = rf.asOf(dates).map { if (it.isNaN()) 0.0 else it }
        val skew = p["skew_vol_pts"] / 100.0
        val step = p["strike_step"]
        val delta = p["delta"]
        val dte = p.int("dte")

        var shares = 0.0
        var cash = capital
        var costsPaid = 0.0
        var orders = 0
        var shortK: Double? = null
        var shortExpiry: LocalDate? = null
        var contracts = 0
        var opened: LocalDate? = null
        var got = 0.0
        var wantSell = false
        val equity = DoubleArray(dates.size)
        val log = ArrayList<Order>()
        val episodes = ArrayList<Episode>()

        for (i in dates.indices) {
            val d = dates[i]
            val s = bars.closes[i]
            val sOpen = bars.opens[i]
            if (i == 1 && shares == 0.0) {
                val unit = sOpen * (1 + costs.slippageBp / 1e4 * costMult)
                val nShares = floor(cash / unit / 100) * 100
                val fee = nShares * sOpen * costs.slippageBp / 1e4 * costMult
                shares = nShares
                cash -= nShares * sOpen + fee
                costsPaid += fee
                orders++
                log += Order(d, a, "buy", nShares, sOpen, fee, "entry_shares: buy ${nShares.toInt()} shares to cover calls")
            }
            if (wantSell && shares >= 100 && i > 0 && i < dates.size - 1) {
                val prev = i - 1
                val sig = v[prev] + skew
                val expiry = snapExpiry(dates, i, dte)
                val t = ChronoUnit.DAYS.between(d, expiry) / 365.0
                val k = strikeForDelta(sOpen, t, r[prev], sig, delta, step)
                val prem = bsCall(sOpen, k, t, r[prev], sig)
                val stressed = !vHi[prev].isNaN() && v[prev] > vHi[prev]
                val spread = if (stressed) p["stress_spread_pct"] else p["spread_pct"]
                val nC = (shares / 100).toInt()
                val haircut = prem * spread / 100.0 * costMult
                val fee = p["fee_per_contract"] * nC * costMult
                got = (prem - haircut) * 100 * nC - fee
                cash += got
                costsPaid += haircut * 100 * nC + fee
                orders++
                shortK = k; shortExpiry = expiry; contracts = nC; opened = d
                log += Order(d, "$a call ${"%.0f".format(k)} $expiry", "sell", -nC.toDouble(), prem - haircut,
                    haircut * 100 * nC + fee,
                    "entry_call: sold %.2f-delta call, vol %.1f".format(delta, sig * 100) + if (stressed) " (wide spread: GVZ high)" else "")
                wantSell = false
            }

            cash *= 1.0 + r[i] / 252.0
            val k0 = shortK
            val exp0 = shortExpiry
            if (k0 != null && exp0 != null && d >= exp0) {
                val itm = max(s - k0, 0.0)
                val paid = itm * 100 * contracts
                cash -= paid
                val reason = if (itm > 0) "exit_itm: settled %.2f a share in the money".format(itm)
                else "exit_worthless: expired worthless, premium kept"
                log += Order(d, "$a call ${"%.0f".format(k0)} $exp0", "settle", contracts.toDouble(), itm, 0.0, reason)
                val o = opened ?: d
                episodes += Episode(o, d, ChronoUnit.DAYS.between(o, d), (got - paid) / (shares * s) * 100,
                    "entry_call", reason, optionPnl = got - paid)
                shortK = null; shortExpiry = null
            }
            var liability = 0.0
            val k1 = shortK
            val exp1 = shortExpiry
            if (k1 != null && exp1 != null) {
                val tLeft = max(ChronoUnit.DAYS.between(d, exp1).toDouble(), 0.0) / 365.0
                liability = bsCall(s, k1, tLeft, r[i], v[i] + skew) * 100 * contracts
            }
            equity[i] = cash + shares * s - liability
            if (shortK == null) wantSell = true
        }
        val last = dates.size - 1
        val state = CallState(shortK, shortExpiry, if (shortK != null) contracts else 0, wantSell, shares,
            bars.closes[last], v[last] + skew, r[last], dates[last])
        return SimResult(name, dates, equity, orders, costsPaid, 1.0, log, episodes, state)
    }

    /** Positions opened: days flat followed by a day with any weight. */
    fun countEntries(t: Targets): Int {
        var n = 0
        var was = false
        for (i in t.dates.indices) {
            val active = t.weights.sumOf { abs(if (it[i].isNaN()) 0.0 else it[i]) } > 0
            if (active && !was) n++
            was = active
        }
        return n
    }
}
