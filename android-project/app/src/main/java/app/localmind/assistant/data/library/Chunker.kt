package app.localmind.assistant.data.library

import app.localmind.assistant.domain.attachments.Attachments

/**
 * Cuts a document into passages of about [TARGET_TOKENS] tokens along paragraph lines, each
 * remembering the heading it falls under ("# Heading" lines from HTML/EPUB, Markdown headings).
 * A paragraph too long on its own (PDFs often have none) is cut at sentence ends.
 */
object Chunker {
    data class Chunk(val heading: String, val text: String)

    private const val TARGET_TOKENS = 320
    private const val MAX_TOKENS = 480
    private val HEADING = Regex("^#{1,6}\\s+(.+)$")
    private val PARAGRAPHS = Regex("\\n\\s*\\n")
    private val SENTENCE_ENDS = Regex("(?<=[.!?。！？\\n])\\s*")

    private class Builder {
        val out = mutableListOf<Chunk>()
        var heading = ""
        private var chunkHeading = ""
        private val text = StringBuilder()
        private var tokens = 0

        fun add(piece: String) {
            val n = Attachments.estimateTokens(piece)
            if (tokens > 0 && tokens + n > TARGET_TOKENS) flush()
            if (tokens == 0) chunkHeading = heading
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(piece)
            tokens += n
        }

        fun flush() {
            val t = text.toString().trim()
            if (t.isNotEmpty()) out += Chunk(chunkHeading, t)
            text.setLength(0)
            tokens = 0
        }
    }

    fun chunks(document: String): List<Chunk> {
        val b = Builder()
        for (paragraph in document.split(PARAGRAPHS)) {
            var p = paragraph.trim()
            if (p.isEmpty()) continue
            val heading = HEADING.matchEntire(p.substringBefore('\n'))
            if (heading != null) {
                b.flush()
                b.heading = heading.groupValues[1].trim().take(120)
                p = p.substringAfter('\n', "").trim()
                if (p.isEmpty()) continue
            }
            pieces(p).forEach(b::add)
        }
        b.flush()
        return b.out
    }

    /** A paragraph, or pieces of it no bigger than [MAX_TOKENS]. */
    private fun pieces(paragraph: String): List<String> {
        if (Attachments.estimateTokens(paragraph) <= MAX_TOKENS) return listOf(paragraph)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (u in paragraph.split(SENTENCE_ENDS)) {
            if (u.isBlank()) continue
            if (sb.isNotEmpty() && Attachments.estimateTokens(sb.toString()) + Attachments.estimateTokens(u) > TARGET_TOKENS) {
                out += sb.toString().trim()
                sb.setLength(0)
            }
            if (Attachments.estimateTokens(u) > MAX_TOKENS) {
                // No punctuation at all: cut by length.
                u.chunked(TARGET_TOKENS * 2).forEach { out += it.trim() }
                continue
            }
            sb.append(u).append(' ')
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }
}
