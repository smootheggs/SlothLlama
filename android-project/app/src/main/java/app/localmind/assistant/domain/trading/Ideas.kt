package app.localmind.assistant.domain.trading

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** A dated follow-up on an idea. Notes are only added, never edited, so the record stays honest. */
@Serializable
data class IdeaNote(val at: Long, val text: String)

/**
 * A trade idea in the idea book. What you thought (thesis, what would prove it wrong, the
 * levels, how sure you were) is fixed once saved; afterwards you only add notes and, at the
 * end, the review. That keeps hindsight out of the record.
 */
@Serializable
data class Idea(
    val id: String,
    val createdAt: Long,
    val instrument: String,
    /** "long", "short", "spread" or "hedge". */
    val direction: String,
    val entry: Double? = null,
    val stop: Double? = null,
    val target: Double? = null,
    /** In words, as the idea had it ("2–4 weeks", "into the Fed"). */
    val horizon: String = "",
    /** When to look at it again, yyyy-MM-dd ("" = no date). */
    val reviewOn: String = "",
    val size: String = "",
    val thesis: String = "",
    val wrongIf: String = "",
    /** What you'd say to a client about it, if anything. */
    val clientAngle: String = "",
    /** How sure you were, 50–95 (%). */
    val conviction: Int = 60,
    /** "you", or "trader" when it came from the Trader persona's card. */
    val source: String = "you",
    val notes: List<IdeaNote> = emptyList(),
    // The review, written when the idea is closed.
    val closedAt: Long = 0,
    val exit: Double? = null,
    /** "right", "wrong", "mixed" or "no call" (it never got going). */
    val outcome: String = "",
    /** "right reason", "partly", "wrong reason" ("" before review). */
    val reason: String = "",
    val whatHappened: String = "",
    val lesson: String = ""
) {
    val isOpen: Boolean get() = closedAt == 0L
    val isLong: Boolean get() = direction != "short"

    /** Open, and its review date has come. */
    fun isDue(today: LocalDate): Boolean =
        isOpen && reviewOn.isNotBlank() && runCatching { !LocalDate.parse(reviewOn).isAfter(today) }.getOrDefault(false)

    /** The result in units of the risk taken, when entry, stop and exit are all known. */
    val r: Double?
        get() {
            val e = entry ?: return null
            val s = stop ?: return null
            val x = exit ?: return null
            if (direction != "long" && direction != "short") return null
            return MetalsMath.rMultiple(isLong, e, s, x)
        }

    /** Reward to risk at the start: |target − entry| / |entry − stop|. */
    val rewardToRisk: Double?
        get() {
            val e = entry ?: return null
            val s = stop ?: return null
            val t = target ?: return null
            val risk = kotlin.math.abs(e - s)
            return if (risk == 0.0) null else kotlin.math.abs(t - e) / risk
        }

    /** The idea as plain text, for a chat or a share. */
    fun asText(): String = buildString {
        appendLine("Instrument: $instrument")
        appendLine("Direction: $direction")
        entry?.let { appendLine("Entry: ${fmt(it)}") }
        stop?.let { appendLine("Stop: ${fmt(it)}") }
        target?.let { appendLine("Target: ${fmt(it)}") }
        rewardToRisk?.let { appendLine("Reward to risk: ${"%.1f".format(java.util.Locale.US, it)}") }
        if (horizon.isNotBlank()) appendLine("Horizon: $horizon")
        if (size.isNotBlank()) appendLine("Size: $size")
        if (thesis.isNotBlank()) appendLine("Thesis: $thesis")
        if (wrongIf.isNotBlank()) appendLine("Wrong if: $wrongIf")
        if (clientAngle.isNotBlank()) appendLine("Client angle: $clientAngle")
        appendLine("Conviction: $conviction%")
        notes.forEach { appendLine("Note (${java.time.Instant.ofEpochMilli(it.at).toString().take(10)}): ${it.text}") }
        if (!isOpen) {
            appendLine("Outcome: $outcome" + (if (reason.isNotBlank()) ", $reason" else ""))
            exit?.let { appendLine("Exit: ${fmt(it)}") }
            r?.let { appendLine("Result: ${"%+.2f".format(java.util.Locale.US, it)} R") }
            if (whatHappened.isNotBlank()) appendLine("What happened: $whatHappened")
            if (lesson.isNotBlank()) appendLine("Lesson: $lesson")
        }
    }.trimEnd()

    companion object {
        fun fmt(v: Double): String =
            if (kotlin.math.abs(v) >= 100) "%,.2f".format(java.util.Locale.US, v) else "%.4f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
    }
}

/** An idea before it's saved: everything editable. */
data class IdeaDraft(
    val instrument: String = "",
    val direction: String = "long",
    val entry: String = "",
    val stop: String = "",
    val target: String = "",
    val horizon: String = "",
    val reviewOn: String = "",
    val size: String = "",
    val thesis: String = "",
    val wrongIf: String = "",
    val clientAngle: String = "",
    val conviction: Int = 60,
    val source: String = "you"
)

/** Reads the Trader persona's idea card out of a reply (lines like "Entry: 2,385"). */
object IdeaCard {
    private val FIELD = Regex("""^\s*[-*•]?\s*\**\s*(Instrument|Direction|Entry|Stop|Target|Horizon|Size|Thesis|Wrong if)\s*\**\s*[:：]\s*\**\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""-?\d[\d,]*(?:\.\d+)?""")

    /** True when [reply] has a card worth saving: at least an instrument and a thesis or a "wrong if". */
    fun has(reply: String): Boolean = parse(reply) != null

    fun parse(reply: String): IdeaDraft? {
        val fields = HashMap<String, String>()
        for (line in reply.lines()) {
            val m = FIELD.matchEntire(line) ?: continue
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim().trim('*').trim()
            if (value.isNotEmpty() && key !in fields) fields[key] = value
        }
        val instrument = fields["instrument"] ?: return null
        if (fields["thesis"] == null && fields["wrong if"] == null) return null
        val dir = fields["direction"].orEmpty().lowercase()
        return IdeaDraft(
            instrument = instrument,
            direction = when {
                "short" in dir || "sell" in dir -> "short"
                "spread" in dir || "switch" in dir -> "spread"
                "hedge" in dir -> "hedge"
                else -> "long"
            },
            entry = number(fields["entry"]),
            stop = number(fields["stop"]),
            target = number(fields["target"]),
            horizon = fields["horizon"].orEmpty(),
            size = fields["size"].orEmpty(),
            thesis = fields["thesis"].orEmpty(),
            wrongIf = fields["wrong if"].orEmpty(),
            source = "trader"
        )
    }

    /** The first number in a field ("around 2,385 on a dip" → "2385"); "" when there's none. */
    fun number(text: String?): String = text?.let { NUMBER.find(it)?.value?.replace(",", "") }.orEmpty()
}

/** How the closed ideas went: a mirror for your reasoning, not a score to chase. */
data class IdeaStats(
    val open: Int,
    val due: Int,
    val closed: Int,
    val right: Int,
    val wrong: Int,
    val mixed: Int,
    /** Of the right calls, how many were right for the reason you gave. */
    val rightForReason: Int,
    val averageR: Double?,
    /** Conviction band (50, 60, 70, 80, 90) → (ideas, right). */
    val calibration: Map<Int, Pair<Int, Int>>
) {
    companion object {
        fun of(ideas: List<Idea>, today: LocalDate): IdeaStats {
            val closed = ideas.filter { !it.isOpen && it.outcome != "no call" && it.outcome.isNotBlank() }
            val rs = closed.mapNotNull { it.r }
            return IdeaStats(
                open = ideas.count { it.isOpen },
                due = ideas.count { it.isDue(today) },
                closed = closed.size,
                right = closed.count { it.outcome == "right" },
                wrong = closed.count { it.outcome == "wrong" },
                mixed = closed.count { it.outcome == "mixed" },
                rightForReason = closed.count { it.outcome == "right" && it.reason == "right reason" },
                averageR = if (rs.isEmpty()) null else rs.average(),
                calibration = closed.groupBy { (it.conviction.coerceIn(50, 99) / 10) * 10 }
                    .mapValues { (_, list) -> list.size to list.count { it.outcome == "right" } }
                    .toSortedMap()
            )
        }
    }
}
