package app.localmind.assistant.data.books

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Reads a spreadsheet's rows as header → cell text: an Excel file (.xlsx, read here without any
 * library: it's a zip of XML files) or a CSV. Values only, never formulas or macros. The sheet
 * read is the first one with a "Title" column ("Shelf" or "Library" first).
 */
object SheetReader {

    private const val MAX_ROWS = 20_000
    private const val MAX_PART_BYTES = 40L * 1024 * 1024

    fun read(bytes: ByteArray, name: String): List<Map<String, String>> =
        if (name.endsWith(".csv", ignoreCase = true) || name.endsWith(".tsv", ignoreCase = true) || !isZip(bytes)) {
            csv(String(bytes, Charsets.UTF_8).removePrefix("﻿"), if (name.endsWith(".tsv", ignoreCase = true)) '\t' else null)
        } else xlsx(bytes)

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    // --- xlsx ---

    fun xlsx(bytes: ByteArray): List<Map<String, String>> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name
                if (n == "xl/workbook.xml" || n == "xl/sharedStrings.xml" || n == "xl/_rels/workbook.xml.rels" ||
                    (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
                ) {
                    val data = readCapped(zip, MAX_PART_BYTES - total)
                    total += data.size
                    parts[n] = data
                }
            }
        }
        val strings = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) }.orEmpty()
        val sheets = sheetPaths(parts)
        val preferred = sheets.sortedBy { (name, _) ->
            when (name.lowercase()) { "shelf" -> 0; "library" -> 1; "books" -> 2; else -> 3 }
        }
        for ((_, path) in preferred) {
            val rows = parts[path]?.let { cells(it, strings) } ?: continue
            val table = toTable(rows)
            if (table.isNotEmpty()) return table
        }
        return emptyList()
    }

    /** Reads the current zip entry, refusing one that unpacks past [limit] bytes (a zip bomb, or not a book list). */
    private fun readCapped(zip: ZipInputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            check(total <= limit) { "That spreadsheet is too large." }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** (sheet name, part path) in workbook order. */
    private fun sheetPaths(parts: Map<String, ByteArray>): List<Pair<String, String>> {
        val rels = HashMap<String, String>()
        parts["xl/_rels/workbook.xml.rels"]?.let { xml ->
            parse(xml, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                    if (qName.endsWith("Relationship")) {
                        val target = a.getValue("Target") ?: return
                        rels[a.getValue("Id") ?: return] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                    }
                }
            })
        }
        val out = mutableListOf<Pair<String, String>>()
        parts["xl/workbook.xml"]?.let { xml ->
            parse(xml, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                    if (qName == "sheet" || qName.endsWith(":sheet")) {
                        val rid = (0 until a.length).firstOrNull { a.getQName(it).endsWith(":id") }?.let { a.getValue(it) }
                        val path = rid?.let { rels[it] } ?: return
                        out += (a.getValue("name") ?: "") to path
                    }
                }
            })
        }
        if (out.isEmpty()) parts.keys.filter { it.startsWith("xl/worksheets/") }.sorted().forEach { out += "" to it }
        return out
    }

    private fun sharedStrings(xml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        parse(xml, object : DefaultHandler() {
            val sb = StringBuilder()
            var inT = false
            var inPhonetic = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (qName) {
                    "si" -> sb.setLength(0)
                    "t" -> inT = !inPhonetic
                    "rPh" -> inPhonetic = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT) sb.appendRange(ch, start, start + length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                    "si" -> out += sb.toString()
                }
            }
        })
        return out
    }

    /** Row index → (column index → text). */
    private fun cells(xml: ByteArray, strings: List<String>): Map<Int, Map<Int, String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        parse(xml, object : DefaultHandler() {
            var row = -1
            var col = -1
            var type = ""
            var inValue = false
            val sb = StringBuilder()
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (qName) {
                    "row" -> row = (a.getValue("r")?.toIntOrNull() ?: (row + 2)) - 1
                    "c" -> {
                        val ref = a.getValue("r")
                        col = if (ref != null) column(ref) else col + 1
                        type = a.getValue("t") ?: ""
                        sb.setLength(0)
                    }
                    "v", "t" -> inValue = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) sb.appendRange(ch, start, start + length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        if (row < 0 || row >= MAX_ROWS) return
                        val raw = sb.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "n", "" -> number(raw)
                            else -> raw
                        }
                        if (text.isNotEmpty()) rows.getOrPut(row) { mutableMapOf() }[col] = text
                    }
                }
            }
        })
        return rows
    }

    /** "1844.0" → "1844"; other numbers as written. */
    private fun number(raw: String): String {
        val d = raw.trim().toDoubleOrNull() ?: return raw.trim()
        return if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else raw.trim()
    }

    /** "AB12" → 27. */
    internal fun column(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    /** The first row with a "Title" header names the columns; the rows below it are the data. */
    private fun toTable(rows: Map<Int, Map<Int, String>>): List<Map<String, String>> {
        val headerAt = rows.entries.take(10).firstOrNull { (_, cells) -> cells.values.any { it.trim().equals("title", ignoreCase = true) } }
            ?: return emptyList()
        val header = headerAt.value
        return rows.filterKeys { it > headerAt.key }.values.map { cells ->
            header.mapNotNull { (c, name) -> cells[c]?.let { name.trim() to it } }.toMap()
        }.filter { it.isNotEmpty() }
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        // No external entities or DTDs: a spreadsheet is data.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        f.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    }

    // --- csv ---

    fun csv(text: String, separator: Char? = null): List<Map<String, String>> {
        val sep = separator ?: if (text.lineSequence().firstOrNull().orEmpty().count { it == ';' } > text.lineSequence().firstOrNull().orEmpty().count { it == ',' }) ';' else ','
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { record += field.toString(); field = StringBuilder() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    record += field.toString(); field = StringBuilder()
                    if (record.any { it.isNotBlank() }) records += record
                    record = mutableListOf()
                    if (records.size > MAX_ROWS) break
                }
                else -> field.append(ch)
            }
            i++
        }
        record += field.toString()
        if (record.any { it.isNotBlank() }) records += record
        val headerAt = records.take(10).indexOfFirst { r -> r.any { it.trim().equals("title", ignoreCase = true) } }
        if (headerAt < 0) return emptyList()
        val header = records[headerAt]
        return records.drop(headerAt + 1).map { r ->
            header.indices.mapNotNull { c -> r.getOrNull(c)?.trim()?.takeIf { it.isNotEmpty() }?.let { header[c].trim() to it } }.toMap()
        }.filter { it.isNotEmpty() }
    }
}
