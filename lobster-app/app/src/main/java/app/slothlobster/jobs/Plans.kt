package app.slothlobster.jobs

/** What a research job searches for. */
object Plans {

    const val MAX_SEARCHES = 5

    /**
     * The question (first line) and the searches to run: the order's own [queries], then any
     * further lines of its text ("- " and "search:" are trimmed), else the question itself.
     */
    fun searches(text: String, queries: List<String>): Pair<String, List<String>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val question = lines.firstOrNull().orEmpty()
        val extra = lines.drop(1).map { it.removePrefix("-").removePrefix("•").trim().removePrefix("search:").trim() }
        val all = (queries.map { it.trim() } + extra).filter { it.length >= 2 }.distinct().take(MAX_SEARCHES)
        return question to all.ifEmpty { listOf(question) }
    }

    /** How many results to take from each search so the total stays near [total]. */
    fun perSearch(total: Int, searches: Int): Int = maxOf(2, (total + searches - 1) / maxOf(1, searches))
}
