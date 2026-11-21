package app.localmind.assistant.domain.projects

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The five process groups a project moves through (PMI's names, the last two shortened). */
@Serializable
enum class Phase(val label: String, val what: String) {
    INITIATING("Initiating", "Why, what and who: the brief and the people"),
    PLANNING("Planning", "How and when: milestones and risks"),
    EXECUTING("Executing", "Doing the work, with an update each week"),
    MONITORING("Monitoring", "Checking the work against the plan"),
    CLOSING("Closing", "Handing over, and what you learned")
}

/** How it's going, as you judge it: the app never sets this for you. */
@Serializable
enum class Rag(val label: String) { ON_TRACK("On track"), AT_RISK("At risk"), OFF_TRACK("Off track") }

/** What the brief says. Blank means not written yet; it reaches a write-up as a [bracket]. */
@Serializable
data class Brief(
    val goal: String = "",
    val sponsor: String = "",
    val scope: String = "",
    val outOfScope: String = "",
    val constraints: String = "",
    val success: String = "",
    /** yyyy-MM-dd, or blank. */
    val deadline: String = ""
)

@Serializable
data class Milestone(
    val id: String,
    val title: String,
    /** yyyy-MM-dd, or blank. */
    val due: String = "",
    /** The day you ticked it, yyyy-MM-dd; blank while it's open. */
    val doneOn: String = ""
) {
    val done: Boolean get() = doneOn.isNotEmpty()
}

/** A risk: likelihood and impact are 1 (low) to 3 (high); the score is worked out in code. */
@Serializable
data class Risk(
    val id: String,
    val title: String,
    val likelihood: Int = 2,
    val impact: Int = 2,
    val owner: String = "",
    val response: String = "",
    val raisedOn: String = "",
    val closedOn: String = ""
) {
    val open: Boolean get() = closedOn.isEmpty()
    val score: Int get() = Projects.score(likelihood, impact)
}

/** Someone with a stake: the classic power/interest grid, two levels each. */
@Serializable
data class Stakeholder(
    val id: String,
    val name: String,
    val role: String = "",
    val highInfluence: Boolean = false,
    val highInterest: Boolean = false
) {
    val approach: String get() = Projects.approach(highInfluence, highInterest)
}

@Serializable
data class Decision(val id: String, val on: String, val title: String, val why: String = "")

/** A dated note on how it's going, with the status you gave it. Newest first in a project. */
@Serializable
data class StatusUpdate(val id: String, val on: String, val status: Rag, val text: String)

/** What a document is. [byCode] ones are built on the tablet from your facts, with no model. */
@Serializable
enum class DocKind(val label: String, val byCode: Boolean, val what: String) {
    CHARTER("Project charter", false, "Purpose, scope, milestones and people, to agree at the start"),
    ONE_PAGER("One-pager", false, "The ask on one page, for a busy sponsor"),
    STATUS("Status report", false, "This week: status, done, next, risks, decisions needed"),
    RISKS("Risk register", true, "Every risk, scored likelihood × impact"),
    STAKEHOLDERS("Stakeholder map", true, "Who to manage closely, keep satisfied, keep informed, monitor"),
    RETRO("Retrospective", false, "What went well, what didn't, what you'll change"),
    CLOSEOUT("Closeout report", false, "Outcome against the goal, handover and lessons"),
    DECISION("Decision record", false, "Written by the Grill from your answers")
}

@Serializable
data class ProjectDoc(val id: String, val kind: DocKind, val title: String, val made: Long, val text: String)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val phase: Phase = Phase.INITIATING,
    val brief: Brief = Brief(),
    val milestones: List<Milestone> = emptyList(),
    val risks: List<Risk> = emptyList(),
    val stakeholders: List<Stakeholder> = emptyList(),
    val decisions: List<Decision> = emptyList(),
    /** Newest first. */
    val updates: List<StatusUpdate> = emptyList(),
    /** Newest first. */
    val docs: List<ProjectDoc> = emptyList(),
    val archived: Boolean = false
) {
    /** The status from your latest update; null until you've written one. */
    val status: StatusUpdate? get() = updates.firstOrNull()
}

@Serializable
data class ProjectFile(val format: Int = Projects.FORMAT, val projects: List<Project> = emptyList())

/** A write-up or a grilling that a project asked for in a chat, until its reply is kept. */
data class ProjectAsk(val projectId: String, val kind: DocKind, val personaId: String)

/** What a project's card and Home show: all counted here, none of it by a model. */
data class Glance(
    val done: Int,
    val total: Int,
    val percent: Int,
    val overdue: List<Milestone>,
    val next: Milestone?,
    val openRisks: Int,
    val highRisks: Int,
    val status: StatusUpdate?,
    /** Days until the deadline (negative once it's passed); null without a deadline. */
    val deadlineIn: Long?
)

/**
 * Projects: the record you keep (brief, milestones, risks, people, decisions, updates) and the
 * documents made from it. Everything that is a number or a date sum is worked out here, so a
 * write-up only has to put your facts into words; what you haven't written stays a [bracket].
 */
object Projects {
    const val FORMAT = 1
    const val MAX_NAME = 80
    const val MAX_TEXT = 2_000
    const val MAX_DOC = 24_000
    private const val UPDATES_IN_FACTS = 6

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    // --- numbers ---

    fun score(likelihood: Int, impact: Int): Int = likelihood.coerceIn(1, 3) * impact.coerceIn(1, 3)

    /** 1–2 low, 3–4 medium, 6–9 high (a 3 × 3 grid has no 5, 7 or 8). */
    fun band(score: Int): String = when {
        score >= 6 -> "High"
        score >= 3 -> "Medium"
        else -> "Low"
    }

    fun level(n: Int): String = when (n.coerceIn(1, 3)) {
        1 -> "low"
        2 -> "medium"
        else -> "high"
    }

    fun approach(highInfluence: Boolean, highInterest: Boolean): String = when {
        highInfluence && highInterest -> "Manage closely"
        highInfluence -> "Keep satisfied"
        highInterest -> "Keep informed"
        else -> "Monitor"
    }

    val APPROACHES = listOf("Manage closely", "Keep satisfied", "Keep informed", "Monitor")

    fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** "20 Nov 2026"; blank or unreadable → the text as it is. */
    fun day(text: String): String = date(text)?.format(dayFormat) ?: text

    fun day(date: LocalDate): String = date.format(dayFormat)

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** "today", "tomorrow", "in 12 days", "yesterday", "3 days late". */
    fun relative(due: LocalDate, today: LocalDate): String {
        val d = daysBetween(today, due)
        return when {
            d == 0L -> "today"
            d == 1L -> "tomorrow"
            d > 1 -> "in $d days"
            d == -1L -> "1 day late"
            else -> "${-d} days late"
        }
    }

    fun overdue(p: Project, today: LocalDate): List<Milestone> =
        p.milestones.filter { m -> !m.done && date(m.due)?.isBefore(today) == true }.sortedBy { it.due }

    /** The next open milestone: the soonest one not yet late, else the first open one with no date. */
    fun next(p: Project, today: LocalDate): Milestone? {
        val undone = p.milestones.filter { !it.done }
        return undone.filter { m -> date(m.due)?.let { !it.isBefore(today) } == true }.minByOrNull { it.due }
            ?: undone.firstOrNull { date(it.due) == null }
    }

    /** Open risks, highest score first. */
    fun openRisks(p: Project): List<Risk> =
        p.risks.filter { it.open }.sortedWith(compareByDescending<Risk> { it.score }.thenByDescending { it.impact }.thenBy { it.title.lowercase() })

    fun percent(done: Int, total: Int): Int = if (total <= 0) 0 else Math.round(done * 100.0 / total).toInt()

    fun glance(p: Project, today: LocalDate): Glance {
        val done = p.milestones.count { it.done }
        val live = p.risks.filter { it.open }
        return Glance(
            done = done,
            total = p.milestones.size,
            percent = percent(done, p.milestones.size),
            overdue = overdue(p, today),
            next = next(p, today),
            openRisks = live.size,
            highRisks = live.count { it.score >= 6 },
            status = p.status,
            deadlineIn = date(p.brief.deadline)?.let { daysBetween(today, it) }
        )
    }

    /** Milestones in the order a plan reads: dated ones by date, then undated, in the order added. */
    fun ordered(milestones: List<Milestone>): List<Milestone> =
        milestones.withIndex().sortedWith(compareBy<IndexedValue<Milestone>> { date(it.value.due) == null }
            .thenBy { it.value.due }.thenBy { it.index }).map { it.value }

    /** The documents that fit the phase you're in, first; the rest follow. */
    fun suggested(phase: Phase): List<DocKind> {
        val first = when (phase) {
            Phase.INITIATING -> listOf(DocKind.CHARTER, DocKind.ONE_PAGER, DocKind.STAKEHOLDERS)
            Phase.PLANNING -> listOf(DocKind.CHARTER, DocKind.RISKS, DocKind.STAKEHOLDERS)
            Phase.EXECUTING, Phase.MONITORING -> listOf(DocKind.STATUS, DocKind.RISKS, DocKind.ONE_PAGER)
            Phase.CLOSING -> listOf(DocKind.CLOSEOUT, DocKind.RETRO, DocKind.STATUS)
        }
        return first + DocKind.entries.filter { it !in first && it != DocKind.DECISION }
    }

    // --- the facts sheet a write-up is made from ---

    private fun or(text: String, gap: String) = text.trim().ifEmpty { "[$gap]" }.replace('\n', ' ')

    /**
     * Everything you've written about [p], with the counts and day sums done, as plain text for a
     * chat. Gaps are marked [like this] so the model can see them and keep them.
     */
    fun factsSheet(p: Project, today: LocalDate): String = buildString {
        val g = glance(p, today)
        appendLine("Facts sheet: ${p.name}")
        appendLine("Today: ${day(today)}")
        appendLine("Phase: ${p.phase.label} (${p.phase.ordinal + 1} of ${Phase.entries.size})")
        appendLine("Status: " + (p.status?.let { "${it.status.label} (set ${day(it.on)})" } ?: "[status not set]"))
        appendLine()
        appendLine("Brief")
        appendLine("- Goal: ${or(p.brief.goal, "goal")}")
        appendLine("- Sponsor: ${or(p.brief.sponsor, "sponsor")}")
        appendLine("- In scope: ${or(p.brief.scope, "scope")}")
        appendLine("- Out of scope: ${or(p.brief.outOfScope, "out of scope")}")
        appendLine("- Constraints: ${or(p.brief.constraints, "constraints")}")
        appendLine("- Success measures: ${or(p.brief.success, "success measures")}")
        val deadline = date(p.brief.deadline)
        appendLine("- Deadline: " + if (deadline == null) "[deadline]" else "${day(deadline)} (${relative(deadline, today).replace("late", "past")})")
        appendLine()
        if (p.milestones.isEmpty()) {
            appendLine("Milestones: [none written yet]")
        } else {
            appendLine("Milestones (${g.done} of ${g.total} done, ${g.percent}%)")
            ordered(p.milestones).forEach { m ->
                val due = date(m.due)
                appendLine(
                    when {
                        m.done -> "- Done ${day(m.doneOn)}: ${m.title}" + (due?.let { " (due ${day(it)})" } ?: "")
                        due == null -> "- Open, no date: ${m.title}"
                        due.isBefore(today) -> "- LATE, due ${day(due)} (${relative(due, today)}): ${m.title}"
                        else -> "- Due ${day(due)} (${relative(due, today)}): ${m.title}"
                    }
                )
            }
            g.next?.let { n -> appendLine("Next milestone: ${n.title}" + (date(n.due)?.let { ", ${day(it)} (${relative(it, today)})" } ?: ", [date]")) }
        }
        appendLine()
        val live = openRisks(p)
        if (live.isEmpty()) appendLine("Open risks: none written") else {
            appendLine("Open risks (score = likelihood × impact, 1 to 9; highest first)")
            live.forEach { r ->
                appendLine("- ${band(r.score)} (${r.score}): ${r.title}. Likelihood ${level(r.likelihood)}, impact ${level(r.impact)}. " +
                    "Owner: ${or(r.owner, "owner")}. Response: ${or(r.response, "response")}.")
            }
        }
        val closed = p.risks.count { !it.open }
        if (closed > 0) appendLine("Closed risks: $closed")
        appendLine()
        if (p.stakeholders.isEmpty()) appendLine("Stakeholders: [none written yet]") else {
            appendLine("Stakeholders")
            p.stakeholders.forEach { s -> appendLine("- ${s.name}, ${or(s.role, "role")}: ${s.approach.lowercase()}") }
        }
        appendLine()
        if (p.decisions.isEmpty()) appendLine("Decisions: none written") else {
            appendLine("Decisions")
            p.decisions.sortedByDescending { it.on }.forEach { d ->
                appendLine("- ${day(d.on)}: ${d.title}" + if (d.why.isBlank()) "" else ". Why: ${d.why.trim().replace('\n', ' ')}")
            }
        }
        appendLine()
        if (p.updates.isEmpty()) appendLine("Updates: none written") else {
            appendLine("Updates (newest first)")
            p.updates.take(UPDATES_IN_FACTS).forEach { u -> appendLine("- ${day(u.on)}, ${u.status.label}: ${u.text.trim().replace('\n', ' ')}") }
        }
    }.trimEnd()

    // --- documents built in code ---

    /** The risk register as a dated document: every open risk with its score, then the closed ones. */
    fun riskRegister(p: Project, today: LocalDate): String = buildString {
        appendLine("# Risk register · ${p.name}")
        appendLine("${day(today)} · score = likelihood × impact, 1 to 9")
        appendLine()
        val live = openRisks(p)
        if (live.isEmpty()) appendLine("No open risks written.")
        listOf("High", "Medium", "Low").forEach { tier ->
            val these = live.filter { band(it.score) == tier }
            if (these.isNotEmpty()) {
                appendLine("## $tier")
                these.forEach { r ->
                    appendLine("- **${r.title}** · ${r.score} (likelihood ${level(r.likelihood)}, impact ${level(r.impact)}) · owner ${or(r.owner, "owner")}")
                    appendLine("  Response: ${or(r.response, "response")}" + if (r.raisedOn.isBlank()) "" else " · raised ${day(r.raisedOn)}")
                }
                appendLine()
            }
        }
        val closed = p.risks.filter { !it.open }
        if (closed.isNotEmpty()) {
            appendLine("## Closed")
            closed.sortedByDescending { it.closedOn }.forEach { r -> appendLine("- ${r.title} · closed ${day(r.closedOn)}") }
        }
    }.trimEnd()

    /** The power/interest grid, as a list under each approach. */
    fun stakeholderMap(p: Project, today: LocalDate): String = buildString {
        appendLine("# Stakeholder map · ${p.name}")
        appendLine(day(today))
        appendLine()
        if (p.stakeholders.isEmpty()) appendLine("No stakeholders written.")
        val why = mapOf(
            "Manage closely" to "high influence, high interest: involve in decisions",
            "Keep satisfied" to "high influence, lower interest: consult before it affects them",
            "Keep informed" to "lower influence, high interest: regular updates",
            "Monitor" to "lower influence, lower interest: light touch"
        )
        APPROACHES.forEach { a ->
            val these = p.stakeholders.filter { it.approach == a }
            if (these.isNotEmpty()) {
                appendLine("## $a")
                appendLine(why.getValue(a))
                these.forEach { s -> appendLine("- **${s.name}**" + if (s.role.isBlank()) "" else ", ${s.role.trim()}") }
                appendLine()
            }
        }
    }.trimEnd()

    fun built(kind: DocKind, p: Project, today: LocalDate): String? = when (kind) {
        DocKind.RISKS -> riskRegister(p, today)
        DocKind.STAKEHOLDERS -> stakeholderMap(p, today)
        else -> null
    }

    // --- what a chat is asked ---

    private const val RULES = "Use only the facts sheet and what I tell you. Copy dates, day counts, percentages and scores exactly as written. " +
        "Anything the facts sheet doesn't have stays in [square brackets] for me to fill."

    /** The message that waits in the chat box for a write-up (you can change it before sending). */
    fun prompt(kind: DocKind, p: Project, today: LocalDate): String {
        val what = when (kind) {
            DocKind.CHARTER -> "Write a project charter for ${p.name}. Headings: Purpose; Objectives and success measures; Scope (in and out); " +
                "Milestones; Stakeholders and roles; Constraints and assumptions; Key risks; Approval (sponsor and date)."
            DocKind.ONE_PAGER -> "Write a one-pager on ${p.name} for a busy sponsor, under 250 words. Headings: The ask; Why now; What we'll deliver; " +
                "When; What it needs; Risks."
            DocKind.STATUS -> "Write a status report on ${p.name} for the week ending ${day(today)}. Headings: Summary (two sentences, starting with the " +
                "status as given); Done; Next; Milestones; Risks and issues; Decisions needed."
            DocKind.RETRO -> "Write a retrospective on ${p.name}. Headings: What went well; What didn't; What we'd do differently; Actions (each with an " +
                "owner and a date). If the facts sheet says little about how it went, keep the headings with [brackets] and end with three questions for me."
            DocKind.CLOSEOUT -> "Write a closeout report on ${p.name}. Headings: Outcome against the goal and success measures; Delivered; Not delivered; " +
                "Milestones (planned and done, as written); Open risks and handover; Lessons; Sign-off."
            DocKind.DECISION -> return grillPrompt(p)
            DocKind.RISKS, DocKind.STAKEHOLDERS -> "Read the ${kind.label.lowercase()} in the facts sheet and tell me what stands out, in five lines."
        }
        return "$what $RULES The facts sheet is attached."
    }

    fun grillPrompt(p: Project): String {
        val about = p.brief.goal.trim().ifEmpty { "[what I'm deciding or planning]" }.replace('\n', ' ')
        return "Grill my plan for ${p.name}: $about. The facts sheet attached is what I've written down so far. " +
            "Start with the decision as a question and the branches you'll cover, then ask your first question."
    }

    /** What a kept chat reply is called in the project: "Status report · 20 Nov 2026". */
    fun docTitle(kind: DocKind, today: LocalDate): String = "${kind.label} · ${day(today)}"

    // --- the file ---

    fun encode(projects: List<Project>): String = json.encodeToString(ProjectFile.serializer(), ProjectFile(projects = projects))

    fun parse(text: String): List<Project> = json.decodeFromString(ProjectFile.serializer(), text).projects

    /** A backup's projects join yours; where both have the same project, yours stays. */
    fun merge(mine: List<Project>, theirs: List<Project>): List<Project> {
        val have = mine.map { it.id }.toSet()
        return mine + theirs.filter { it.id !in have }
    }

    /** Replaces the item with [item]'s id, or adds it at the end. */
    fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> =
        if (any { id(it) == id(item) }) map { if (id(it) == id(item)) item else it } else this + item

    fun clip(text: String, max: Int = MAX_TEXT): String = text.trim().take(max)
}
