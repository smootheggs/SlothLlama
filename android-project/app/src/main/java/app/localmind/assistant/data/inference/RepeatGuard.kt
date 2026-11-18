package app.localmind.assistant.data.inference

/**
 * Spots a reply stuck in a loop: the same block of text (8–160 characters) three times in a row
 * at the end, like "繁：你好（nei5 hou5） 繁：你好（nei5 hou5） 繁：你好（nei5 hou5）".
 * Shorter repeats (哈哈哈, "no no no") are normal and aren't counted.
 */
object RepeatGuard {
    private const val MIN_PERIOD = 8
    private const val MAX_PERIOD = 160
    private const val COPIES = 3

    /** The length of the repeating block at the end of [text], or 0 if it isn't looping. */
    fun period(text: CharSequence): Int {
        val n = text.length
        for (p in MIN_PERIOD..MAX_PERIOD) {
            if (n < p * COPIES) break
            var same = true
            for (k in 0 until p * (COPIES - 1)) {
                if (text[n - 1 - k] != text[n - 1 - k - p]) { same = false; break }
            }
            if (same && text.substring(n - p).isNotBlank()) return p
        }
        return 0
    }

    /** [text] with the trailing repeats removed, keeping the first copy. */
    fun trim(text: String): String {
        var t = text.trimEnd()
        while (true) {
            val p = period(t)
            if (p == 0) return t
            t = t.substring(0, t.length - p).trimEnd()
            // Stop at one copy: once only two remain, period() no longer fires; drop the second.
            if (period(t) == 0 && t.length >= 2 * p && t.substring(t.length - p) == t.substring(t.length - 2 * p, t.length - p)) {
                return t.substring(0, t.length - p).trimEnd()
            }
        }
    }
}
