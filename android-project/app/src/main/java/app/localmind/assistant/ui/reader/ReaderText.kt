package app.localmind.assistant.ui.reader

import app.localmind.assistant.domain.edition.Editions

/**
 * The reader's view of a text: headings, paragraphs, bullets, numbered items, quotes and code,
 * each with its links pulled out. Markdown from the Lobster (briefs, fetched pages, research) and
 * plain text from the library both read well; anything unusual falls back to a paragraph.
 */
sealed interface ReaderBlock {
    val body: Inline
    /** What the block says, with Markdown link and bold syntax taken out. */
    val text: String get() = body.text
    val links: List<LinkSpan> get() = body.links

    data class Heading(val level: Int, override val body: Inline) : ReaderBlock
    data class Paragraph(override val body: Inline) : ReaderBlock
    data class Bullet(override val body: Inline) : ReaderBlock
    data class Numbered(val number: String, override val body: Inline) : ReaderBlock
    data class Quote(override val body: Inline) : ReaderBlock
    data class Code(override val body: Inline) : ReaderBlock
    /**
     * Lines the page put around an article (links to other stories, dates, captions), folded in the
     * reader. Its own text is empty, so reading aloud and finding in the page pass over it.
     */
    data class Aside(val lines: List<Inline>, override val body: Inline = Inline("")) : ReaderBlock
}

/** A block's text, its links, and the stretches shown in bold (start until end). */
data class Inline(val text: String, val links: List<LinkSpan> = emptyList(), val bold: List<LinkSpan> = emptyList())

/** A link inside a block's [ReaderBlock.text]: characters [start] until [end]. */
data class LinkSpan(val start: Int, val end: Int, val url: String)

private val HEADING = Regex("^(#{1,6})\\s+(.+?)\\s*#*$")
private val BULLET = Regex("^[-*•+]\\s+(.+)$")
private val NUMBERED = Regex("^(\\d{1,3})[.)]\\s+(.+)$")
private val QUOTE = Regex("^>\\s?(.*)$")
/** A Markdown link, a bold run, or a bare web address. */
private val TOKEN = Regex(
    "\\[([^\\[\\]\\n]{1,300})]\\((https?://[^\\s)]{1,2000})\\)" +
        "|\\*\\*([^*\\n]{1,500})\\*\\*" +
        "|https?://[^\\s<>()\\[\\]\"']{2,2000}"
)
private val TRAILING = ".,;:!?。，、".toSet()

/** Splits [markdown] into blocks. Long texts (whole books) are fine: one pass over the lines. */
fun readerBlocks(markdown: String): List<ReaderBlock> {
    val out = ArrayList<ReaderBlock>()
    val paragraph = StringBuilder()
    val quote = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        val t = paragraph.toString().trim()
        if (t.isNotEmpty()) out += ReaderBlock.Paragraph(parseInline(t))
        paragraph.setLength(0)
    }
    fun flushQuote() {
        val t = quote.toString().trim()
        if (t.isNotEmpty()) out += ReaderBlock.Quote(parseInline(t))
        quote.setLength(0)
    }

    for (raw in markdown.lineSequence()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out += ReaderBlock.Code(Inline(code.toString().trimEnd('\n')))
                code.setLength(0)
                inCode = false
            } else {
                flushParagraph(); flushQuote()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(raw).append('\n')
            continue
        }
        val t = line.trim()
        if (t.isEmpty()) {
            flushParagraph(); flushQuote()
            continue
        }
        if (t.startsWith(Editions.ASIDE_TOKEN)) {
            flushParagraph(); flushQuote()
            val said = t.removePrefix(Editions.ASIDE_TOKEN).trim()
            if (said.isEmpty()) continue
            // Neighbouring lines fold together, however the text spaced them.
            val last = out.lastOrNull()
            if (last is ReaderBlock.Aside) out[out.lastIndex] = last.copy(lines = last.lines + parseInline(said))
            else out += ReaderBlock.Aside(listOf(parseInline(said)))
            continue
        }
        val quoted = QUOTE.matchEntire(t)
        if (quoted != null) {
            flushParagraph()
            if (quote.isNotEmpty()) quote.append('\n')
            quote.append(quoted.groupValues[1])
            continue
        }
        flushQuote()
        val heading = HEADING.matchEntire(t)
        val bullet = BULLET.matchEntire(t)
        val numbered = NUMBERED.matchEntire(t)
        when {
            heading != null -> {
                flushParagraph()
                out += ReaderBlock.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2]))
            }
            // "---" and "***" are rules, not bullets.
            t.all { it == '-' || it == '*' || it == '_' } && t.length >= 3 -> flushParagraph()
            bullet != null -> {
                flushParagraph()
                out += ReaderBlock.Bullet(parseInline(bullet.groupValues[1]))
            }
            numbered != null -> {
                flushParagraph()
                out += ReaderBlock.Numbered(numbered.groupValues[1], parseInline(numbered.groupValues[2]))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(t)
            }
        }
    }
    if (inCode) out += ReaderBlock.Code(Inline(code.toString().trimEnd('\n')))
    flushParagraph(); flushQuote()
    return out
}

/**
 * Replaces `[label](url)` with its label, `**bold**` with its words, and finds bare web
 * addresses, noting where each sits. Only http and https count: nothing else goes to the Lobster.
 */
fun parseInline(text: String): Inline {
    val sb = StringBuilder()
    val links = ArrayList<LinkSpan>()
    val bold = ArrayList<LinkSpan>()
    var cursor = 0
    for (m in TOKEN.findAll(text)) {
        sb.append(text, cursor, m.range.first)
        cursor = m.range.last + 1
        val start = sb.length
        when {
            m.groups[1] != null -> {
                sb.append(m.groupValues[1])
                links += LinkSpan(start, sb.length, m.groupValues[2])
            }
            m.groups[3] != null -> {
                sb.append(m.groupValues[3])
                bold += LinkSpan(start, sb.length, "")
            }
            else -> {
                var url = m.value
                while (url.isNotEmpty() && url.last() in TRAILING) url = url.dropLast(1)
                sb.append(url)
                links += LinkSpan(start, sb.length, url)
                // Punctuation after the address stays in the text, outside the link.
                sb.append(m.value, url.length, m.value.length)
            }
        }
    }
    sb.append(text, cursor, text.length)
    return Inline(sb.toString(), links, bold)
}

/** Every distinct link in [blocks], in reading order. */
fun allLinks(blocks: List<ReaderBlock>): List<String> =
    blocks.flatMap { b -> b.links.map { it.url } }.distinct()

/** Indexes of the blocks that contain [query], ignoring case. */
fun findMatches(blocks: List<ReaderBlock>, query: String): List<Int> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return blocks.indices.filter { blocks[it].text.contains(q, ignoreCase = true) }
}

/** Roughly how long the text takes to read, at 230 words (or 500 Chinese characters) a minute. */
fun readingMinutes(text: String): Int {
    val han = text.count { it.code in 0x3400..0x9FFF }
    val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, Math.round(words / 230.0 + han / 500.0).toInt())
}
