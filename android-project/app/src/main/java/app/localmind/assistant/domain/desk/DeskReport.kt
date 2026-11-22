package app.localmind.assistant.domain.desk

import java.time.LocalDate

/** Growth of 1 for the strategy and buy-and-hold, about weekly, for the chart. */
class Chart(val dates: List<LocalDate>, val strategy: DoubleArray, val benchmark: DoubleArray, val split: LocalDate)

/** Why positions closed: count, average return % and share that made money. */
data class ExitReason(val code: String, val count: Int, val avgReturnPct: Double, val wonPct: Double)

/**
 * Everything the Desk shows for one strategy at one setting: a shelf strategy ([params] its
 * settings) or one of your rules ([params] null; key "my:…"). [gates] are the six questions.
 */
class StrategyReport(
    val key: String,
    val label: String,
    val rule: Rule,
    val params: Params?,
    val isDefault: Boolean,
    /** What "simply holding" means here ("GLD"). */
    val benchLabel: String,
    val full: Metrics?,
    val inSample: Metrics?,
    val heldOut: Metrics?,
    val benchFull: Metrics?,
    val benchHeldOut: Metrics?,
    val volMatchedHeldOut: Metrics?,
    val costsDoubledHeldOut: Metrics?,
    val entries: Int,
    val orders: Int,
    val costsPaid: Double,
    val exposure: Double,
    val robustInSample: Robust?,
    val robustHeldOut: Robust?,
    val bootstrap: Bootstrap?,
    val gates: List<Gate>,
    val ticket: DeskStrategies.Ticket,
    val guardrail: String?,
    val chart: Chart,
    val recent: List<Episode>,
    val exitReasons: List<ExitReason>,
    val splitDate: LocalDate
) {
    val gatesPassed: Int get() = gates.count { it.passed == true }
    val gatesUnchecked: Int get() = gates.count { it.passed == null }
    val verdict: Pair<String, String> get() = DeskStats.verdict(gates)
    val mine: Boolean get() = key.startsWith(DeskReports.MINE)
}

object DeskReports {

    /** Your rules' keys start with this ("my:3f9a…"); the shelf's are their names. */
    const val MINE = "my:"

    /** The in-sample daily Sharpe of a run, as recorded in the trial log. */
    fun inSampleSharpe(run: DeskStrategies.Run, data: DeskData): Double {
        val r = run.result
        val cut = DeskStats.splitIndex(r.equity.size)
        if (cut < 31) return Double.NaN
        return DeskStats.moments(DeskStats.excess(r.dates.subList(0, cut), r.equity.copyOfRange(0, cut), data.rf, run.periodsPerYear)).sharpe
    }

    /** A shelf strategy's report at a setting (as before 1.48). */
    fun build(
        key: String,
        data: DeskData,
        params: Params,
        trialSharpes: List<Double?>,
        nearby: String?,
        paper: DeskStats.Paper?,
        costs: Costs = Costs(),
        run: DeskStrategies.Run = DeskStrategies.run(key, data, params, costs),
        doubled: DeskStrategies.Run = DeskStrategies.run(key, data, params, costs, costMult = 2.0)
    ): StrategyReport {
        val t = DeskStrategies.template(key)
        return buildFor(key, t.label, DeskStrategies.rule(key, params), data, trialSharpes, nearby, paper, costs, run, doubled,
            params, params.values == t.defaults.values)
    }

    /**
     * Builds the report for any rule. [trialSharpes] are the in-sample daily Sharpes of every
     * setting tried, this one included; [nearby] is the latest sensitivity verdict, if any.
     */
    fun buildFor(
        key: String,
        label: String,
        rule: Rule,
        data: DeskData,
        trialSharpes: List<Double?>,
        nearby: String?,
        paper: DeskStats.Paper?,
        costs: Costs = Costs(),
        run: DeskStrategies.Run = RuleEngine.run(rule, data, costs),
        doubled: DeskStrategies.Run = RuleEngine.run(rule, data, costs, costMult = 2.0),
        params: Params? = null,
        isDefault: Boolean = false
    ): StrategyReport {
        val ppy = run.periodsPerYear
        val r = run.result
        val dates = r.dates
        val eq = r.equity
        val cut = DeskStats.splitIndex(eq.size)
        val splitDate = dates[cut]
        val rf = data.rf

        val benchAt = HashMap<LocalDate, Double>(run.bench.dates.size * 2)
        run.bench.dates.forEachIndexed { i, d -> benchAt[d] = run.bench.equity[i] }
        val bench = DoubleArray(dates.size) { benchAt[dates[it]] ?: Double.NaN }

        fun m(from: Int, to: Int, d: List<LocalDate> = dates, e: DoubleArray = eq) =
            DeskStats.metrics(d.subList(from, to), e.copyOfRange(from, to), rf, ppy)

        val bKeep = dates.indices.filter { !bench[it].isNaN() }
        val bDates = bKeep.map { dates[it] }
        val bEq = bKeep.map { bench[it] }.toDoubleArray()
        val bCut = bDates.indexOfFirst { it >= splitDate }.let { if (it < 0) bDates.size else it }

        val (vmD, vmE) = DeskStats.volMatched(dates.subList(cut, dates.size), eq.copyOfRange(cut, eq.size),
            bench.copyOfRange(cut, bench.size), rf, ppy)
        val x2 = doubled.result
        val x2Cut = x2.dates.indexOfFirst { it >= splitDate }.let { if (it < 0) x2.dates.size else it }

        val full = m(0, eq.size)
        val inSample = m(0, cut)
        val held = m(cut, eq.size)
        val benchFull = DeskStats.metrics(bDates, bEq, rf, ppy)
        val benchHeld = if (bCut < bDates.size) DeskStats.metrics(bDates.subList(bCut, bDates.size), bEq.copyOfRange(bCut, bEq.size), rf, ppy) else null
        val vmHeld = DeskStats.metrics(vmD, vmE, rf, ppy)
        val x2Held = DeskStats.metrics(x2.dates.subList(x2Cut, x2.dates.size), x2.equity.copyOfRange(x2Cut, x2.equity.size), rf, ppy)
        val robustIn = DeskStats.robust(dates.subList(0, cut), eq.copyOfRange(0, cut), rf, trialSharpes, ppy)
        val questions = DeskStats.questions(run.entries, full, inSample, held, benchHeld, vmHeld, x2Held, robustIn, nearby, paper, rule.benchmark)

        // About weekly points for the chart, always including the first and last day.
        val stepN = maxOf(1, dates.size / 400)
        val pick = (dates.indices step stepN).toMutableList().apply { if (last() != dates.size - 1) add(dates.size - 1) }
        val firstBench = bench.firstOrNull { !it.isNaN() } ?: 1.0
        val chart = Chart(pick.map { dates[it] }, DoubleArray(pick.size) { eq[pick[it]] / eq[0] },
            DoubleArray(pick.size) { (bench[pick[it]] / firstBench) }, splitDate)

        val closedIn = r.episodes.filter { it.exit != null && it.exit < splitDate }
        val reasons = closedIn.groupBy { it.exitReason.substringBefore(":").trim().ifBlank { "unlabelled" } }
            .map { (code, list) ->
                ExitReason(code, list.size, list.map { it.returnPct }.average(), list.count { it.returnPct > 0 } * 100.0 / list.size)
            }.sortedByDescending { it.count }

        return StrategyReport(
            key = key,
            label = label,
            rule = rule,
            params = params,
            isDefault = isDefault,
            benchLabel = rule.benchmark,
            full = full,
            inSample = inSample,
            heldOut = held,
            benchFull = benchFull,
            benchHeldOut = benchHeld,
            volMatchedHeldOut = vmHeld,
            costsDoubledHeldOut = x2Held,
            entries = run.entries,
            orders = r.orders,
            costsPaid = r.costsPaid,
            exposure = r.exposure,
            robustInSample = robustIn,
            robustHeldOut = DeskStats.robust(dates.subList(cut, dates.size), eq.copyOfRange(cut, eq.size), rf, ppy = ppy),
            bootstrap = DeskStats.bootstrap(dates, eq, rf, ppy = ppy),
            gates = questions,
            ticket = DeskStrategies.ticket(rule, run),
            guardrail = DeskStats.guardrail(eq),
            chart = chart,
            recent = r.episodes.takeLast(12).reversed(),
            exitReasons = reasons,
            splitDate = splitDate
        )
    }

    /**
     * Runs the strategy over its grid of nearby settings (in-sample only) and returns the
     * verdict and each setting's in-sample daily Sharpe, for the trial log.
     */
    fun nearby(key: String, data: DeskData, params: Params, costs: Costs = Costs()): Pair<String, List<Pair<Params, Double>>> {
        val t = DeskStrategies.template(key)
        val (xa, ya) = t.grid
        val cells = ArrayList<Pair<Params, Double>>()
        var default = Double.NaN
        val others = ArrayList<Double>()
        for (y in ya.second) for (x in xa.second) {
            val p = params.with(xa.first, x).with(ya.first, y)
            val run = DeskStrategies.run(key, data, p, costs)
            val annual = inSampleAnnual(run, data)
            cells += p to inSampleSharpe(run, data)
            if (x == params[xa.first] && y == params[ya.first]) default = annual else others += annual
        }
        if (default.isNaN()) {
            // The current setting isn't on the grid: compare it with the grid as a whole.
            default = inSampleAnnual(DeskStrategies.run(key, data, params, costs), data)
        }
        return DeskStats.nearbyVerdict(default, others) to cells
    }

    /**
     * The same check for any rule, over [RuleTweaks.grid]: the verdict, and each nearby rule's
     * trial id and in-sample daily Sharpe. A rule with no numbers to tune has nothing to fit.
     */
    fun nearbyRule(rule: Rule, data: DeskData, costs: Costs = Costs()): Pair<String, List<Pair<String, Double>>> {
        val grid = RuleTweaks.grid(rule)
        if (grid.size < 2) return "broad: the rule has no settings to tune, so none to fit" to emptyList()
        val me = RuleFiles.encode(rule)
        var default = Double.NaN
        val others = ArrayList<Double>()
        val cells = ArrayList<Pair<String, Double>>()
        for (g in grid) {
            val run = RuleEngine.run(g, data, costs)
            val annual = inSampleAnnual(run, data)
            cells += RuleTweaks.hash(g, costs) to inSampleSharpe(run, data)
            if (RuleFiles.encode(g) == me) default = annual else others += annual
        }
        return DeskStats.nearbyVerdict(default, others) to cells
    }

    private fun inSampleAnnual(run: DeskStrategies.Run, data: DeskData): Double {
        val r = run.result
        val cut = DeskStats.splitIndex(r.equity.size)
        return DeskStats.metrics(r.dates.subList(0, cut), r.equity.copyOfRange(0, cut), data.rf, run.periodsPerYear)?.sharpe ?: Double.NaN
    }
}
