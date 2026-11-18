package app.slothlobster.jobs

import java.security.MessageDigest

/** What changed between two looks at a watched page or topic. */
data class Change(val added: List<String>, val removed: List<String>) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
}

object Changes {

    const val MAX_LINES = 400
    private const val MAX_LINE = 300
    private const val SHOWN = 15

    /** The lines worth comparing: trimmed, without repeats, not too many or too long. */
    fun normalise(lines: List<String>): List<String> =
        lines.map { it.trim().replace(Regex("\\s+"), " ").take(MAX_LINE) }
            .filter { it.length >= 3 }
            .distinct()
            .take(MAX_LINES)

    fun hash(lines: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Lines that appeared and disappeared. For a news topic only new headlines count (old ones
     * falling off the feed isn't news). Tiny changes (a clock, a counter) are ignored.
     */
    fun between(old: List<String>, new: List<String>, topic: Boolean): Change {
        val before = old.toSet()
        val after = new.toSet()
        val added = new.filter { it !in before }
        val removed = if (topic) emptyList() else old.filter { it !in after }
        val size = added.sumOf { it.length } + removed.sumOf { it.length }
        if (!topic && size < 40) return Change(emptyList(), emptyList())
        if (topic && added.isEmpty()) return Change(emptyList(), emptyList())
        return Change(added.take(SHOWN), removed.take(SHOWN))
    }

    /** The alert's text, in Markdown. */
    fun describe(change: Change, url: String): String = buildString {
        if (change.added.isNotEmpty()) {
            append("**New**\n\n")
            change.added.forEach { append("- ").append(it.removePrefix("- ").removePrefix("#").trim()).append('\n') }
        }
        if (change.removed.isNotEmpty()) {
            if (isNotEmpty()) append('\n')
            append("**Gone**\n\n")
            change.removed.forEach { append("- ").append(it.removePrefix("- ").removePrefix("#").trim()).append('\n') }
        }
        append("\nSource: ").append(url)
    }
}
