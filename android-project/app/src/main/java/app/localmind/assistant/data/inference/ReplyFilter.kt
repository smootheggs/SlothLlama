package app.localmind.assistant.data.inference

/**
 * Turns the raw token stream into what the user should see:
 * - hides <think>…</think> reasoning blocks (Qwen 3, DeepSeek-R1 distills, …)
 * - ends the reply at the first stop marker, if any
 * - holds back a trailing "<…" that might still become a tag or marker
 */
class ReplyFilter(
    private val stopMarkers: List<String> = emptyList(),
    /** True when the prompt already opened a <think> block, so the output starts mid-reasoning. */
    startsThinking: Boolean = false
) {

    private val raw = StringBuilder(if (startsThinking) THINK_OPEN else "")
    private var emitted = 0

    /** Set once a stop marker appeared; callers should stop generating. */
    var stopped = false
        private set

    /** Raw text so far (including any hidden reasoning). */
    val rawText: String get() = raw.toString()

    /** Feeds a piece of raw output; returns the newly visible text (possibly empty). */
    fun push(piece: String): String {
        if (stopped || piece.isEmpty()) return ""
        raw.append(piece)
        var visible = visibleText(raw.toString())
        val markerAt = stopMarkers.map { visible.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (markerAt != null) {
            visible = visible.substring(0, markerAt)
            stopped = true
        }
        val end = if (stopped) visible.length else safeEnd(visible)
        if (end <= emitted) return ""
        val delta = visible.substring(emitted, end)
        emitted = end
        return delta
    }

    /** Call when generation ends: returns any held-back text and the final visible reply. */
    fun finish(): Pair<String, String> {
        var visible = visibleText(raw.toString())
        stopMarkers.map { visible.indexOf(it) }.filter { it >= 0 }.minOrNull()?.let {
            visible = visible.substring(0, it)
        }
        val tail = if (visible.length > emitted) visible.substring(emitted) else ""
        emitted = visible.length
        return tail to visible
    }

    private fun safeEnd(visible: String): Int {
        val lt = visible.lastIndexOf('<')
        if (lt < 0) return visible.length
        val tail = visible.substring(lt)
        val couldBeTag = tail.length <= MAX_TAG_LENGTH &&
            (THINK_OPEN.startsWith(tail) || stopMarkers.any { it.startsWith(tail) })
        return if (couldBeTag) lt else visible.length
    }

    companion object {
        private const val THINK_OPEN = "<think>"
        private const val THINK_CLOSE = "</think>"
        private const val MAX_TAG_LENGTH = 24

        /** Removes complete think blocks; an unclosed one hides everything after it. */
        fun visibleText(text: String): String {
            val out = StringBuilder()
            var i = 0
            // A closing tag with no opener: the reasoning started before the output did; drop it.
            val firstClose = text.indexOf(THINK_CLOSE)
            val firstOpen = text.indexOf(THINK_OPEN)
            if (firstClose >= 0 && (firstOpen < 0 || firstClose < firstOpen)) i = firstClose + THINK_CLOSE.length
            while (i < text.length) {
                val open = text.indexOf(THINK_OPEN, i)
                if (open < 0) {
                    out.append(text, i, text.length)
                    break
                }
                out.append(text, i, open)
                val close = text.indexOf(THINK_CLOSE, open + THINK_OPEN.length)
                if (close < 0) break // still thinking
                i = close + THINK_CLOSE.length
            }
            return out.toString().trimStart()
        }
    }
}
