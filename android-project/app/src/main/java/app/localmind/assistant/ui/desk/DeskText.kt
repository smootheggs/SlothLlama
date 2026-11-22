package app.localmind.assistant.ui.desk

import app.localmind.assistant.domain.desk.Metrics
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.desk.StrategyReport
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** How SlothDesk writes its numbers, on the page and in the report a chat can explain. */
object DeskText {

    fun pct(x: Double?, digits: Int = 1): String =
        if (x == null || x.isNaN()) "–" else String.format(Locale.US, "%+.${digits}f%%", x * 100)

    fun plainPct(x: Double?, digits: Int = 1): String =
        if (x == null || x.isNaN()) "–" else String.format(Locale.US, "%.${digits}f%%", x * 100)

    fun num(x: Double?, digits: Int = 2): String =
        if (x == null || x.isNaN()) "–" else String.format(Locale.US, "%.${digits}f", x)

    fun line(m: Metrics?): String =
        if (m == null) "not enough days"
        else "CAGR ${pct(m.cagr)} · Sharpe ${num(m.sharpe)} · worst drop ${plainPct(abs(m.maxDrawdown))}"

    /** A plain-text report for "Ask about this": every number already worked out. */
    fun report(r: StrategyReport, lastDate: LocalDate?): String = buildString {
        appendLine("SlothDesk report: ${r.label}")
        appendLine("Data to ${lastDate ?: "?"}. Simulated with costs; trades fill at the next day's open. Paper trading only.")
        r.params?.let { p ->
            appendLine("Settings: " + p.values.entries.joinToString(", ") { "${it.key} ${num(it.value, 3)}" } +
                if (r.isDefault) " (the default rule sheet)" else " (changed from the default)")
        }
        appendLine(RuleWords.rule(r.rule))
        appendLine()
        appendLine("Tonight's ticket (from the close of ${r.ticket.dataDate}): ${r.ticket.nextOpen}. Now: ${r.ticket.positionNow}.")
        r.guardrail?.let { appendLine("Guardrail: $it") }
        appendLine()
        appendLine("Held-out years (from ${r.splitDate}, never used to choose settings):")
        appendLine("  strategy: ${line(r.heldOut)}")
        appendLine("  buy-and-hold: ${line(r.benchHeldOut)}")
        appendLine("  buy-and-hold scaled to the same risk: ${line(r.volMatchedHeldOut)}")
        appendLine("  with every cost doubled: ${line(r.costsDoubledHeldOut)}")
        appendLine("In-sample years: ${line(r.inSample)}")
        appendLine("Whole period: ${line(r.full)}; ${r.entries} entries, ${r.orders} orders, " +
            "${plainPct(r.exposure, 0)} of days in the market, costs paid ${num(r.costsPaid, 0)} on 100,000.")
        r.robustInSample?.let {
            appendLine("Is it luck? Deflated Sharpe ${num(it.dsr)} after ${it.trials} setting(s) tried " +
                "(chance alone could give an annual Sharpe of ${num(it.luckHurdleAnnual)}); " +
                "probability the true Sharpe is above zero ${plainPct(it.psr, 0)}; " +
                (it.minTrackRecordYears?.let { y -> "about ${num(y, 1)} years of results would be needed to tell it from zero." }
                    ?: "no track record would tell it from zero."))
        }
        r.bootstrap?.let {
            appendLine("Reshuffled history (${it.sims} runs): Sharpe ${num(it.sharpe.first)} to ${num(it.sharpe.third)} " +
                "(5th to 95th percentile), worst drop ${plainPct(abs(it.maxDrawdown.third))} to ${plainPct(abs(it.maxDrawdown.first))}.")
        }
        appendLine()
        val (headline, line) = r.verdict
        appendLine("The six questions (${r.gatesPassed} of ${r.gates.size} answered yes): $headline. $line")
        r.gates.forEach { g ->
            appendLine("  ${g.name} ${g.detail} (${g.more})")
        }
        if (r.exitReasons.isNotEmpty()) {
            appendLine()
            appendLine("Why positions closed (in-sample):")
            r.exitReasons.forEach { e ->
                appendLine("  ${e.code}: ${e.count}, average ${num(e.avgReturnPct)}%, ${num(e.wonPct, 0)}% made money")
            }
        }
    }.trim()
}
