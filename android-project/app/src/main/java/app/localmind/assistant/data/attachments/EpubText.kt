package app.localmind.assistant.data.attachments

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Text out of EPUB books and HTML pages, keeping headings (as "# …") and paragraphs. */
object EpubText {

    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    /** The book's chapters in reading order (the OPF spine), as plain text. */
    fun read(input: InputStream): String {
        val files = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val lower = name.lowercase()
                if (!entry.isDirectory && (lower.endsWith(".opf") || lower.endsWith(".xml") ||
                        lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm"))
                ) {
                    val bytes = zip.readBytes()
                    total += bytes.size
                    check(total <= MAX_TOTAL_BYTES) { "This EPUB is too large to read." }
                    files[name] = bytes
                }
            }
        }
        val chapters = spineOrder(files).ifEmpty {
            files.keys.filter { it.endsWith("html", ignoreCase = true) || it.endsWith(".htm", ignoreCase = true) }.sorted()
        }
        return chapters.mapNotNull { files[it] }
            .joinToString("\n\n") { htmlToText(String(it, Charsets.UTF_8)) }
            .trim()
            .ifEmpty { error("This EPUB has no readable text.") }
    }

    /** Chapter paths from META-INF/container.xml → the OPF's manifest and spine. */
    private fun spineOrder(files: Map<String, ByteArray>): List<String> {
        val container = files["META-INF/container.xml"] ?: return emptyList()
        val opfPath = Jsoup.parse(String(container, Charsets.UTF_8), "", Parser.xmlParser())
            .selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() } ?: return emptyList()
        val opf = files[opfPath] ?: return emptyList()
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val doc = Jsoup.parse(String(opf, Charsets.UTF_8), "", Parser.xmlParser())
        val manifest = doc.select("manifest > item").associate { it.attr("id") to it.attr("href") }
        return doc.select("spine > itemref").mapNotNull { ref ->
            manifest[ref.attr("idref")]?.let { href -> normalize(base + java.net.URLDecoder.decode(href, "UTF-8")) }
        }
    }

    /** Resolves "a/b/../c.xhtml" → "a/c.xhtml". */
    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(part)
            }
        }
        return out.joinToString("/")
    }

    /** Headings become "# Heading" lines; paragraphs and list items are separated by blank lines. */
    fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        // Wikipedia pages (Kiwix) also carry reference lists and navigation boxes: not prose.
        doc.select(
            "script, style, nav, noscript, header, footer, sup.reference, .mw-editsection, " +
                "ol.references, .reflist, .mw-references-wrap, .navbox, .navbox-styles, .metadata, .noprint"
        ).remove()
        val blocks = doc.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre, dt, dd, figcaption, td")
        if (blocks.isEmpty()) return doc.body()?.text().orEmpty()
        val selected: MutableSet<org.jsoup.nodes.Element> =
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
        selected.addAll(blocks)
        val out = StringBuilder()
        for (el in blocks) {
            // Skip blocks nested in another selected block (a <p> inside a <li>), so text isn't doubled.
            if (el.parents().any { it in selected }) continue
            val text = el.text().trim()
            if (text.isEmpty()) continue
            if (el.tagName().length == 2 && el.tagName()[0] == 'h' && el.tagName()[1].isDigit()) {
                out.append("\n# ").append(text).append("\n\n")
            } else {
                out.append(text).append("\n\n")
            }
        }
        return out.toString().trim()
    }
}
