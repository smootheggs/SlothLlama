package app.localmind.assistant.domain.books

/**
 * How SlothBooks organises a long shelf: the default shelves, your own shelves, grouping (by
 * author, genre, topic, series, era, language, region), author stacks, and copies of one book
 * kept as one row. All worked out here, so it's the same in the list and the grids.
 */

/** The shelves that are always there. They can't be renamed or removed. */
enum class DefaultShelf(val label: String) {
    ALL("All"), READING("Reading"), UP_NEXT("Up next"), TO_READ("To read"), READ("Read"), REFERENCE("Reference");

    fun holds(b: Book): Boolean = when (this) {
        ALL -> true
        READING -> b.status == Status.READING
        UP_NEXT -> b.upNext != null && b.status != Status.READ
        TO_READ -> b.status == Status.UNREAD
        READ -> b.status == Status.READ
        REFERENCE -> b.status == Status.REFERENCE
    }
}

/** Which shelf the list shows: a default one or one of yours. */
sealed interface ShelfPick {
    data class Default(val shelf: DefaultShelf) : ShelfPick
    data class Mine(val id: String) : ShelfPick

    fun holds(b: Book): Boolean = when (this) {
        is Default -> shelf.holds(b)
        is Mine -> id in b.shelves
    }
}

/** Books you look things up in rather than read through: they go on the Reference shelf, not To read. */
object ReferenceRule {
    private val GENRES = setOf("language learning", "cooking", "games", "cookbook", "dictionary")
    private val TITLE = listOf("a to z", "a-z", "dictionary", "encyclopedia", "encyclopaedia", "handbook", "guide", "lonely planet", "phrasebook")

    fun looksLikeReference(b: Book): Boolean {
        if (b.isPaper || b.isFiction) return false
        val title = b.title.lowercase()
        return b.genre.lowercase() in GENRES ||
            "guide" in b.subgenre.lowercase() ||
            TITLE.any { it in title }
    }
}

/** A topic for grouping: yours if you set one, else worked out from the genre and subgenre. */
object Topics {
    private val RULES = listOf(
        listOf("china", "chinese", "asian", "japan", "korea", "east asia", "mongol") to "China and East Asia",
        listOf("wwii", "ww2", "world war", "wwi", "ww1", "holocaust", "third reich", "cold war") to "The World Wars and after",
        listOf("middle east", "palestin", "israel", "ottoman", "arab", "persia", "iran") to "The Middle East",
        listOf("medieval", "crusade", "plantagenet", "templar", "middle ages") to "The Middle Ages",
        listOf("roman", "rome", "ancient", "greek", "greece") to "The ancient world",
        listOf("latin america", "pre-columbian", "mexic", "south america", "conquest") to "Latin America",
        listOf("american", "united states", "u.s.", "presiden") to "The United States",
        listOf("africa") to "Africa",
        listOf("europe", "france", "french", "german", "britain", "british", "renaissance", "russia") to "Europe"
    )

    fun of(b: Book): String {
        if (b.topic.isNotBlank()) return b.topic
        if (b.isFiction) return b.genre.ifBlank { b.form }.ifBlank { "Fiction" }
        val g = b.genre.ifBlank { "Other" }
        if (!g.equals("history", ignoreCase = true) && !g.equals("politics", ignoreCase = true)) return g
        val s = b.subgenre.lowercase()
        val hit = RULES.firstOrNull { (words, _) -> words.any { it in s } }?.second
        return if (hit != null) "$g · $hit" else g
    }
}

/** Copies of one book (an illustrated edition, a translation you also own) shown as one row. */
object Works {
    private val EDITION = setOf(
        "illustrated", "edition", "annotated", "unabridged", "complete", "deluxe", "boxed", "set", "anniversary", "the", "a", "an",
        "romance", "of", "volume", "vol"
    )

    /** The key two copies share: the author and the title without edition words. */
    fun key(b: Book): String {
        val tokens = java.text.Normalizer.normalize(b.title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() && it !in EDITION }
        val core = tokens.joinToString(" ").ifBlank { Book.keyOf(b.title, "").substringBefore('|') }
        return core + "|" + Book.keyOf("", b.author).substringAfter('|').substringBefore('|')
    }

    /** Book id → the other copies of the same book on the shelf (only for books that have some). */
    fun copies(books: List<Book>): Map<String, List<Book>> =
        books.filter { !it.isPaper && it.author.isNotBlank() }
            .groupBy { key(it) }
            .values.filter { it.size > 1 }
            .flatMap { group -> group.map { b -> b.id to group.filter { it.id != b.id } } }
            .toMap()
}

/** What the list and grids are grouped by. */
enum class GroupBy(val label: String) {
    AUTHOR("Author"), GENRE("Genre"), TOPIC("Topic"), SERIES("Series"), ERA("Era"), LANGUAGE("Language"), REGION("Region")
}

/** One line of a section: a book (with any other copies), or an author's stack. */
sealed interface Entry {
    val id: String
    data class One(val book: Book, val copies: List<Book> = emptyList()) : Entry {
        override val id: String get() = book.id
    }
    data class Stack(val author: String, val books: List<Book>) : Entry {
        override val id: String get() = "stack:$author"
        val read: Int get() = books.count { it.status == Status.READ }
    }
}

data class Section(val key: String, val title: String, val count: Int, val entries: List<Entry>)

object Grouping {
    /** An author with at least this many books (in a group by author) is folded into a stack. */
    const val STACK_AT = 4

    /** The surname a list sorts by ("Honoré de Balzac" → "balzac", "J.R.R. Tolkien" → "tolkien"). */
    fun surname(author: String): String {
        val first = author.split(",", " and ", " & ").first().trim()
        val parts = first.split(' ').filter { it.isNotBlank() }
        val last = parts.lastOrNull().orEmpty()
        return java.text.Normalizer.normalize(last.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }

    /** "Balzac, Honoré de" for headers. */
    fun filed(author: String): String {
        val first = author.split(",", " and ", " & ").first().trim()
        val parts = first.split(' ').filter { it.isNotBlank() }
        if (parts.size < 2) return first
        val particles = setOf("de", "da", "von", "van", "der", "di", "du", "le", "la")
        val lastAt = parts.lastIndex
        val given = parts.subList(0, lastAt).filterNot { it.lowercase() in particles && parts.indexOf(it) == lastAt - 1 }
        val particle = parts.getOrNull(lastAt - 1)?.takeIf { it.lowercase() in particles }
        return "${parts[lastAt]}, ${given.joinToString(" ")}${particle?.let { " $it" } ?: ""}"
    }

    private fun letter(author: String): String {
        val c = surname(author).firstOrNull()?.uppercaseChar() ?: '#'
        return if (c in 'A'..'Z') c.toString() else "#"
    }

    private val inOrder = compareBy<Book>({ it.seriesNo ?: Int.MAX_VALUE }, { it.year ?: Int.MAX_VALUE }, { Book.keyOf(it.title, "") })

    /**
     * Sections for [books] grouped by [by]. Copies of one book collapse into one row (the one
     * you're reading, else the first added); in a group by author, an author with [STACK_AT] or
     * more books becomes one stack (the screen opens it in place).
     */
    fun sections(books: List<Book>, by: GroupBy, stacks: Boolean = true): List<Section> {
        val copies = Works.copies(books)
        // One row per book: the copy being read (or the first added) stands for the others.
        val seen = HashSet<String>()
        val rows = mutableListOf<Pair<Book, List<Book>>>()
        books.sortedWith(compareByDescending<Book> { it.status == Status.READING }.thenBy { it.addedAt }).forEach { b ->
            if (b.id in seen) return@forEach
            val others = copies[b.id].orEmpty().filter { it in books }
            seen += b.id
            others.forEach { seen += it.id }
            rows += b to others
        }
        fun keyOf(b: Book): Pair<String, String> = when (by) {
            GroupBy.AUTHOR -> letter(b.author).let { it to it }
            GroupBy.GENRE -> (b.genre.ifBlank { b.form.ifBlank { "Other" } }).let { it.lowercase() to it }
            GroupBy.TOPIC -> Topics.of(b).let { it.lowercase() to it }
            GroupBy.SERIES -> (b.series.ifBlank { "~" }).let { it.lowercase() to it.replace("~", "Not in a series") }
            GroupBy.ERA -> (Eras.of(b.year) ?: "Unknown").let { e -> "%02d".format(Eras.ORDER.indexOf(e).let { if (it < 0) 99 else it }) to e }
            GroupBy.LANGUAGE -> b.language.ifBlank { "Unknown" }.let { it.lowercase() to it }
            GroupBy.REGION -> b.region.ifBlank { "Unknown" }.let { it.lowercase() to it }
        }
        val within: Comparator<Book> = when (by) {
            GroupBy.SERIES -> inOrder
            GroupBy.ERA -> compareBy({ it.year ?: Int.MAX_VALUE }, { Book.keyOf(it.title, "") })
            else -> compareBy<Book>({ surname(it.author) }, { it.author }).then(inOrder)
        }
        return rows.groupBy { keyOf(it.first) }
            .toSortedMap(compareBy<Pair<String, String>> { if (by != GroupBy.ERA && it.first == "~") "￿" else it.first })
            .map { (k, list) ->
                val sorted = list.sortedWith { a, b -> within.compare(a.first, b.first) }
                val entries = if (by == GroupBy.AUTHOR && stacks) stack(sorted) else sorted.map { Entry.One(it.first, it.second) }
                Section(k.first, k.second, sorted.sumOf { 1 + it.second.size }, entries)
            }
    }

    private fun stack(rows: List<Pair<Book, List<Book>>>): List<Entry> {
        val byAuthor = rows.groupBy { it.first.author }
        val done = HashSet<String>()
        val out = mutableListOf<Entry>()
        for ((b, others) in rows) {
            val mine = byAuthor.getValue(b.author)
            if (mine.size >= STACK_AT) {
                if (done.add(b.author)) out += Entry.Stack(b.author, mine.map { it.first }.sortedWith(inOrder))
            } else {
                out += Entry.One(b, others)
            }
        }
        return out
    }
}
