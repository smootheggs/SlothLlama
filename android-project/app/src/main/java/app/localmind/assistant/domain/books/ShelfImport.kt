package app.localmind.assistant.domain.books

/**
 * Turns a spreadsheet's rows (header → cell) into books. Columns are found by name, so both the
 * tidied SlothBooks layout and a raw list ("Publication Year", "Original Language",
 * "Fiction/Nonfiction"…) work; anything else (ISBNs, links, confidence) is left out. Every book
 * starts unread: an import brings in the catalogue, never your reading.
 */
object ShelfImport {

    data class Preview(
        /** Books new to the shelf, ready to add. */
        val fresh: List<Book>,
        /** Rows already on the shelf (same title, author and edition language): skipped. */
        val known: Int,
        /** Rows without a title: skipped. */
        val blank: Int,
        /** Pairs in the file with the same title and author but different editions, to check. */
        val twins: List<Pair<Book, Book>>
    )

    private val TITLE = listOf("title", "name", "book")
    private val AUTHOR = listOf("author", "authors", "by", "writer")
    private val YEAR = listOf("year", "publication year", "published", "first published", "pub year")
    private val FORM = listOf("form")
    private val GENRE = listOf("genre", "category")
    private val SUBGENRE = listOf("subgenre", "sub-genre", "subcategory")
    private val FICTION = listOf("fiction/nonfiction", "fiction", "fiction or nonfiction", "type")
    private val LANGUAGE = listOf("written in", "original language", "language")
    private val READ_IN = listOf("reading in", "read in", "edition language")
    private val COUNTRY = listOf("country", "author country")
    private val REGION = listOf("region", "continent")
    private val SERIES = listOf("series", "cycle")
    private val NUMBER = listOf("number", "series number", "#", "no.", "series #")
    private val ABOUT = listOf("about", "description", "summary", "blurb")
    private val NOTES = listOf("notes", "note")
    private val FORMAT = listOf("format", "copy")

    fun preview(rows: List<Map<String, String>>, shelf: List<Book>, now: Long, id: () -> String): Preview {
        val have = shelf.map { it.key }.toMutableSet()
        val fresh = mutableListOf<Book>()
        var known = 0
        var blank = 0
        for (row in rows) {
            val b = toBook(row, now, id) ?: run { blank++; null } ?: continue
            if (!have.add(b.key)) { known++; continue }
            fresh += b
        }
        val twins = fresh.groupBy { Book.keyOf(it.title, it.author) }.values
            .filter { it.size == 2 }
            .map { it[0] to it[1] }
        return Preview(fresh, known, blank, twins)
    }

    fun toBook(row: Map<String, String>, now: Long, id: () -> String): Book? {
        val cells = row.mapKeys { it.key.trim().lowercase() }
        fun get(names: List<String>): String = names.firstNotNullOfOrNull { n -> cells[n]?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()
        val title = get(TITLE)
        if (title.isEmpty()) return null
        val genre = get(GENRE)
        val subgenre = get(SUBGENRE)
        val fictionCell = get(FICTION).lowercase()
        val fiction = when {
            fictionCell.startsWith("non") -> false
            fictionCell.startsWith("fic") -> true
            else -> null
        }
        val form = get(FORM).let { f -> Forms.ALL.firstOrNull { it.equals(f, ignoreCase = true) } }
            ?: Forms.guess(genre, subgenre, fiction)
        val format = get(FORMAT).let { f -> FORMATS.firstOrNull { it.equals(f, ignoreCase = true) } }.orEmpty()
        val book = Book(
            id = id(),
            kind = if (form == Forms.PAPER) Kinds.PAPER else Kinds.BOOK,
            title = title,
            author = get(AUTHOR),
            year = year(get(YEAR)),
            form = form,
            // A raw sheet calls every novel's genre "Fiction": the subgenre says more.
            genre = if (genre.equals("fiction", ignoreCase = true) && subgenre.isNotEmpty()) subgenre.substringBefore('/').trim() else genre,
            subgenre = subgenre,
            language = get(LANGUAGE),
            readIn = get(READ_IN),
            country = get(COUNTRY),
            region = get(REGION),
            series = get(SERIES),
            seriesNo = get(NUMBER).toDoubleOrNull()?.toInt(),
            about = get(ABOUT),
            notes = get(NOTES),
            format = format,
            addedAt = now,
            updatedAt = now
        )
        // Language courses, cookbooks and guides go on the Reference shelf rather than To read.
        return if (ReferenceRule.looksLikeReference(book)) book.copy(status = Status.REFERENCE) else book
    }

    /** "1844", "1844.0", "-440", "440 BC", "c. 1400" → a year; anything else → null. */
    fun year(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        val n = Regex("-?\\d{1,4}").find(t)?.value?.toIntOrNull() ?: return null
        return if (("bc" in t || "bce" in t) && n > 0) -n else n
    }
}
