package app.slothlobster.jobs

/** Narrowing a watch down to the part of a page, and the words, that matter. */
object Watches {

    private fun level(line: String): Int = line.takeWhile { it == '#' }.length

    private fun headingText(line: String): String = line.trimStart('#').trim()

    /** The page's headings (from its readable lines), for choosing a part to watch. */
    fun headings(lines: List<String>): List<String> =
        lines.filter { level(it) > 0 }.map(::headingText).filter { it.length in 2..80 }.distinct().take(40)

    /**
     * The lines under [heading], up to the next heading of the same or a higher level; null if
     * the page has no such heading any more.
     */
    fun section(lines: List<String>, heading: String): List<String>? {
        val want = heading.trim().lowercase()
        if (want.isEmpty()) return lines
        val start = lines.indexOfFirst { level(it) > 0 && headingText(it).lowercase() == want }
            .takeIf { it >= 0 }
            ?: lines.indexOfFirst { level(it) > 0 && headingText(it).lowercase().contains(want) }.takeIf { it >= 0 }
            ?: return null
        val depth = level(lines[start])
        val end = (start + 1 until lines.size).firstOrNull { level(lines[it]) in 1..depth } ?: lines.size
        return lines.subList(start, end)
    }

    fun matches(line: String, keywords: List<String>): Boolean =
        keywords.isEmpty() || keywords.any { line.contains(it, ignoreCase = true) }

    /** "watch <link> for Berlin, remote" → [Berlin, remote]. */
    fun keywordsFrom(text: String): List<String> {
        val rest = text.replace(Regex("""https?://\S+"""), " ")
        val m = Regex("""\b(?:for|mentioning|containing|that mention|with the words?)\s+(.+)$""", RegexOption.IGNORE_CASE).find(rest)
            ?: return emptyList()
        return split(m.groupValues[1])
    }

    /** Keywords typed by hand: commas, "and" or "or" between them. */
    fun split(text: String): List<String> =
        text.split(Regex("""\s*(?:,|;|/|\band\b|\bor\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim().trim('"', '\'', '.', '“', '”') }
            .filter { it.length >= 2 }
            .distinct()
            .take(8)

    /** "watch the ECB rate decision" → "the ECB rate decision" (words to search the news for). */
    fun topicFrom(text: String): String =
        text.trim()
            .replace(Regex("""^(?:please\s+)?(?:watch|keep an eye on|track|follow|alert me (?:about|when)|tell me when|let me know when)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(?:the\s+)?news\s+(?:about|on)\s+""", RegexOption.IGNORE_CASE), "")
            .trim().trimEnd('.', '?', '!')
            .take(120)
}
