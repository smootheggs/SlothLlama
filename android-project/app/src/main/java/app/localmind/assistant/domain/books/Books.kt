package app.localmind.assistant.domain.books

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * A book (or a paper) in SlothBooks. Two halves: the catalogue (what the book is, from your
 * list or typed in) and you (your reading: status, progress, notes, verdict). The catalogue can
 * be corrected; your half is never overwritten by an import.
 */
@Serializable
data class Book(
    val id: String,
    /** [Kinds.BOOK] or [Kinds.PAPER]. */
    val kind: String = Kinds.BOOK,
    // --- the catalogue ---
    val title: String,
    val author: String = "",
    /** Year first published; negative for BCE. */
    val year: Int? = null,
    /** One of [Forms.ALL]. */
    val form: String = "",
    val genre: String = "",
    val subgenre: String = "",
    /** The language it was written in. */
    val language: String = "",
    val country: String = "",
    val region: String = "",
    val series: String = "",
    val seriesNo: Int? = null,
    /** A catalogue line: what it is, not what you thought of it. */
    val about: String = "",
    /** Catalogue notes: awards, editions ("Pulitzer Prize 1980"). */
    val notes: String = "",
    // --- the file, for papers and books kept as files on the tablet ---
    /** content:// address of the PDF or EPUB on the tablet ("" = none). */
    val file: String = "",
    val fileName: String = "",
    /** Where the Lobster fetched it from. */
    val source: String = "",
    /** The Library item that holds its text, once indexed. */
    val libraryId: Long? = null,
    // --- you ---
    /** One of [Status]. */
    val status: String = Status.UNREAD,
    /** Place in the Up next queue (1 = next), null when not queued. */
    val upNext: Int? = null,
    /** yyyy-MM-dd, "" = not yet. */
    val started: String = "",
    val finished: String = "",
    /** How far, 0–100 (%). */
    val progress: Int = 0,
    /** Where you are, in your words ("chapter 20", "p. 212"). */
    val upTo: String = "",
    /** The language you're reading it in ("" = the one it was written in). */
    val readIn: String = "",
    /** Where your copy is: Play Books, PDF, EPUB, Paper. */
    val format: String = "",
    /** 0 = not rated, else 1–5. */
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val why: String = "",
    val verdict: String = "",
    val quotes: List<String> = emptyList(),
    val gist: Gist? = null,
    val recall: List<String> = emptyList(),
    /** How many times you've finished it in the app. */
    val reads: Int = 0,
    /** A paper the Lobster brought that you haven't opened yet. */
    val fresh: Boolean = false,
    /** Ids of your own shelves this book is on ([UserShelf]). */
    val shelves: List<String> = emptyList(),
    /** Your topic for it, when the one worked out ([Topics.of]) doesn't fit. */
    val topic: String = "",
    /** The cover's file name in the app's covers folder ("" = the made cover). */
    val cover: String = "",
    /** Where the cover came from: [CoverSource]. */
    val coverSource: String = "",
    /** Covers the Lobster found to choose from (files "<id>.c1.jpg"…), 0 = none waiting. */
    val coverChoices: Int = 0,
    val addedAt: Long = 0,
    val updatedAt: Long = 0
) {
    val isPaper: Boolean get() = kind == Kinds.PAPER
    val isFiction: Boolean get() = form in Forms.FICTION
    val hasFile: Boolean get() = file.isNotBlank()

    /** The key that says two rows are the same book: title, author and the edition's language. */
    val key: String get() = keyOf(title, author, readIn)

    /** "1844", "c. 440 BC". */
    val yearText: String get() = yearText(year)

    /** The language you read it in, falling back to the original. */
    val readingIn: String get() = readIn.ifBlank { language }

    companion object {
        fun keyOf(title: String, author: String, readIn: String = ""): String =
            listOf(norm(title), norm(author), norm(readIn)).joinToString("|")

        private fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("^(the|a|an) "), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")

        fun yearText(year: Int?): String = when {
            year == null -> ""
            year < 0 -> "c. ${-year} BC"
            year < 1000 -> "c. $year"
            else -> year.toString()
        }
    }
}

/** A summary of a book, written by the Librarian or by you, and marked with where it came from. */
@Serializable
data class Gist(
    val thesis: String = "",
    val claims: List<String> = emptyList(),
    val pushback: String = "",
    /** [GistSource]. */
    val source: String = GistSource.GENERAL,
    val at: Long = 0
) {
    val isEmpty: Boolean get() = thesis.isBlank() && claims.isEmpty() && pushback.isBlank()
}

object GistSource {
    /** Read from the book's own text (a file in the Library). */
    const val TEXT = "text"
    /** From the model's general knowledge, not the text. */
    const val GENERAL = "general"
    const val YOU = "you"

    fun label(source: String): String = when (source) {
        TEXT -> "from the text"
        YOU -> "yours"
        else -> "general knowledge, not the text"
    }
}

/** A shelf you made. The default shelves (All, Reading, Up next, To read, Read, Reference) aren't these and can't be removed. */
@Serializable
data class UserShelf(val id: String, val name: String, val createdAt: Long = 0)

object CoverSource {
    const val PDF = "pdf"
    const val EPUB = "epub"
    const val LOBSTER = "lobster"
    const val PHOTO = "photo"

    /** Covers you chose or that came from the file itself are never replaced by a fetch for the whole shelf. */
    fun keeps(source: String): Boolean = source == PHOTO || source == PDF || source == EPUB
}

object Kinds {
    const val BOOK = "book"
    const val PAPER = "paper"
}

object Status {
    const val UNREAD = "unread"
    const val READING = "reading"
    const val READ = "read"
    const val ASIDE = "aside"
    const val REFERENCE = "reference"

    val ALL = listOf(UNREAD, READING, READ, ASIDE, REFERENCE)

    fun label(status: String): String = when (status) {
        READING -> "Reading"
        READ -> "Read"
        ASIDE -> "Set aside"
        REFERENCE -> "Reference"
        else -> "To read"
    }
}

object Forms {
    const val NOVEL = "Novel"
    const val DRAMA = "Drama"
    const val POETRY = "Poetry"
    const val STORIES = "Stories"
    const val COMICS = "Comics"
    const val NONFICTION = "Nonfiction"
    const val PAPER = "Paper"

    val ALL = listOf(NOVEL, DRAMA, POETRY, STORIES, COMICS, NONFICTION, PAPER)
    val FICTION = setOf(NOVEL, DRAMA, POETRY, STORIES, COMICS)

    /** A form from a raw sheet's genre, subgenre and fiction/nonfiction columns. */
    fun guess(genre: String, subgenre: String, fiction: Boolean?): String {
        val s = subgenre.lowercase()
        val g = genre.lowercase()
        val isFiction = fiction ?: (g == "fiction" || g == "comics" || g == "poetry")
        if (!isFiction) return NONFICTION
        return when {
            g == "comics" || "comic" in s || "manga" in s || "graphic" in s -> COMICS
            "drama" in s || "play" in s || "tragedy" in s -> DRAMA
            g == "poetry" || "poetry" in s || "epic" in s || "poem" in s -> POETRY
            "short stor" in s || "stories" in s -> STORIES
            else -> NOVEL
        }
    }
}

/** Where your copy lives. */
val FORMATS = listOf("Play Books", "PDF", "EPUB", "Paper", "Other")

/** Mood and pace tags, a short fixed list so they can be counted and compared. */
val MOODS = listOf(
    "adventurous", "challenging", "dark", "emotional", "funny", "hopeful", "informative",
    "inspiring", "lighthearted", "mysterious", "reflective", "sad", "tense"
)
val PACES = listOf("slow", "medium pace", "fast")

/** Languages offered for "Reading in" (any other can be typed). */
val READING_LANGUAGES = listOf("English", "French", "German", "Spanish", "Italian", "Chinese", "Japanese")

/** Everything a status change sets as well: dates, progress and the read count. */
fun Book.withStatus(status: String, today: LocalDate): Book {
    if (status == this.status) return this
    val day = today.toString()
    return when (status) {
        Status.READING -> copy(
            status = status, started = if (this.status == Status.READ || started.isBlank()) day else started,
            finished = if (this.status == Status.READ) "" else finished,
            progress = if (this.status == Status.READ) 0 else progress,
            upNext = null
        )
        Status.READ -> copy(status = status, finished = day, started = started.ifBlank { day }, progress = 100, reads = reads + 1, upNext = null)
        else -> copy(status = status)
    }
}

/** Rough eras for counting, in order. */
object Eras {
    val ORDER = listOf("Before 1800", "1800s", "1900–1949", "1950–1999", "2000s", "2010s", "2020s")

    fun of(year: Int?): String? = when {
        year == null -> null
        year < 1800 -> ORDER[0]
        year < 1900 -> ORDER[1]
        year < 1950 -> ORDER[2]
        year < 2000 -> ORDER[3]
        year < 2010 -> ORDER[4]
        year < 2020 -> ORDER[5]
        else -> ORDER[6]
    }
}

/** A series or cycle on your shelf, in its order, with how much of it you've read. */
data class SeriesProgress(val name: String, val books: List<Book>) {
    val read: Int get() = books.count { it.status == Status.READ }
    /** The first one in order you haven't finished. */
    val next: Book? get() = books.firstOrNull { it.status != Status.READ }
}

/** Counts for the Insights tab, all worked out here, never by the model. */
data class ShelfStats(
    val books: Int,
    val papers: Int,
    val read: Int,
    val reading: List<Book>,
    val fiction: Int,
    val nonfiction: Int,
    val eras: List<Pair<String, Int>>,
    val languages: List<Pair<String, Int>>,
    val regions: List<Pair<String, Int>>,
    val series: List<SeriesProgress>,
    val readThisYear: Int,
    /** Books in a language other than English, by language (for "read it in the original"). */
    val originals: Map<String, Int>
) {
    companion object {
        fun of(all: List<Book>, today: LocalDate): ShelfStats {
            val books = all.filter { !it.isPaper }
            fun count(f: (Book) -> String?) = books.mapNotNull(f).filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
            val eras = books.mapNotNull { Eras.of(it.year) }.groupingBy { it }.eachCount()
            return ShelfStats(
                books = books.size,
                papers = all.size - books.size,
                read = books.count { it.status == Status.READ },
                reading = books.filter { it.status == Status.READING },
                fiction = books.count { it.isFiction },
                nonfiction = books.count { !it.isFiction },
                eras = Eras.ORDER.map { it to (eras[it] ?: 0) },
                languages = count { it.language },
                regions = count { it.region },
                series = seriesOf(books),
                readThisYear = books.count { it.status == Status.READ && it.finished.startsWith(today.year.toString()) },
                originals = books.filter { it.language.isNotBlank() && it.language != "English" }
                    .groupingBy { it.language }.eachCount()
            )
        }

        /** Series with at least two books on the shelf, biggest first; each in its own order. */
        fun seriesOf(books: List<Book>): List<SeriesProgress> =
            books.filter { it.series.isNotBlank() }
                .groupBy { it.series }
                .filter { it.value.size >= 2 }
                .map { (name, list) -> SeriesProgress(name, list.sortedWith(compareBy<Book>({ it.seriesNo ?: Int.MAX_VALUE }, { it.year ?: 0 }, { it.title }))) }
                .sortedWith(compareByDescending<SeriesProgress> { it.books.size }.thenBy { it.name })
    }
}

/** Why a book on your shelf goes with another. */
data class Link(val book: Book, val why: String)

object Shelf {
    /** Up to [max] books on your shelf that go with [book]: the same series, author, then genre and era. */
    fun connections(book: Book, all: List<Book>, max: Int = 4): List<Link> {
        val others = all.filter { it.id != book.id && !it.isPaper }
        val out = LinkedHashMap<String, Link>()
        if (book.series.isNotBlank()) {
            others.filter { it.series == book.series }
                .sortedWith(compareBy({ it.seriesNo ?: Int.MAX_VALUE }, { it.year ?: 0 }))
                .forEach { out.putIfAbsent(it.id, Link(it, it.seriesNo?.let { n -> "${book.series} · $n" } ?: book.series)) }
        }
        if (book.author.isNotBlank()) {
            others.filter { it.author == book.author }.sortedBy { it.year ?: 0 }
                .forEach { out.putIfAbsent(it.id, Link(it, "same author")) }
        }
        if (book.genre.isNotBlank()) {
            val era = Eras.of(book.year)
            others.filter { it.genre == book.genre && it.author != book.author && Eras.of(it.year) == era && it.region == book.region }
                .sortedBy { it.title }
                .forEach { out.putIfAbsent(it.id, Link(it, "same genre and era")) }
        }
        return out.values.take(max)
    }

    /**
     * Books on your shelf worth reading next, each with a reason, worked out from what you're
     * reading and have read. The Librarian is given this list rather than guessing.
     */
    fun picks(all: List<Book>, max: Int = 8): List<Link> {
        val books = all.filter { !it.isPaper }
        val unread = books.filter { it.status == Status.UNREAD }
        val out = LinkedHashMap<String, Link>()
        books.filter { it.upNext != null }.sortedBy { it.upNext }
            .filter { it.status == Status.UNREAD }
            .forEach { out.putIfAbsent(it.id, Link(it, "already Up next")) }
        val active = books.filter { it.status == Status.READING || it.status == Status.READ }
            .sortedByDescending { it.updatedAt }
        for (b in active) {
            if (b.series.isNotBlank()) {
                ShelfStats.seriesOf(books).firstOrNull { it.name == b.series }?.next
                    ?.takeIf { it.status == Status.UNREAD }
                    ?.let { out.putIfAbsent(it.id, Link(it, "next in ${b.series}")) }
            }
            unread.filter { it.author == b.author }.minByOrNull { it.year ?: 0 }
                ?.let { out.putIfAbsent(it.id, Link(it, "same author as ${b.title}")) }
            unread.filter { it.genre == b.genre && it.genre.isNotBlank() && it.author != b.author }.take(2)
                .forEach { out.putIfAbsent(it.id, Link(it, "same genre as ${b.title}")) }
            if (out.size >= max) break
        }
        // Then the shelf's quieter corners: regions with few books.
        val regions = books.groupingBy { it.region }.eachCount()
        unread.filter { it.region.isNotBlank() && (regions[it.region] ?: 0) <= 10 }
            .sortedBy { regions[it.region] ?: 0 }
            .take(2)
            .forEach { out.putIfAbsent(it.id, Link(it, "one of few from ${it.region}")) }
        return out.values.take(max)
    }

    /** Search by title, author, series or genre, ignoring case and accents. */
    fun matches(book: Book, query: String): Boolean {
        val q = Book.keyOf(query, "").substringBefore('|')
        if (q.isBlank()) return true
        return listOf(book.title, book.author, book.series, book.genre).any { Book.keyOf(it, "").substringBefore('|').contains(q) }
    }
}

/**
 * What the Librarian is given when you open a chat from SlothBooks: the book's card as it
 * stands (with your progress, so it can keep to it) and, for recommendations, picks worked out
 * from your shelf. Data, not instructions.
 */
object LibrarianContext {
    fun forBook(book: Book, all: List<Book>): String = buildString {
        appendLine("Book: ${book.title}${if (book.author.isNotBlank()) " — ${book.author}" else ""}")
        catalogue(book)
        appendLine("Status: ${Status.label(book.status)}")
        if (book.status == Status.READING) {
            appendLine("Progress: ${book.progress}%${if (book.upTo.isNotBlank()) " (up to ${book.upTo})" else ""}")
            appendLine("Spoilers: the reader has NOT read past this point. Say nothing about what happens later.")
        } else if (book.status == Status.UNREAD) {
            appendLine("Spoilers: the reader hasn't started it. Keep to the premise.")
        }
        if (book.readIn.isNotBlank() && book.readIn != book.language) appendLine("Reading it in: ${book.readIn}")
        if (book.why.isNotBlank()) appendLine("Why they're reading it: ${book.why}")
        if (book.verdict.isNotBlank()) appendLine("Their verdict: ${book.verdict}")
        if (book.rating > 0) appendLine("Their rating: ${book.rating} of 5")
        if (book.tags.isNotEmpty()) appendLine("Their tags: ${book.tags.joinToString(", ")}")
        book.quotes.take(5).forEach { appendLine("A passage they kept: $it") }
        book.gist?.takeIf { !it.isEmpty }?.let { g ->
            appendLine("Saved gist (${GistSource.label(g.source)}): ${g.thesis}")
            g.claims.forEach { appendLine("- $it") }
        }
        val links = Shelf.connections(book, all)
        if (links.isNotEmpty()) {
            appendLine()
            appendLine("On their shelf too:")
            links.forEach { appendLine("- ${line(it.book)} (${it.why})") }
        }
    }.trim()

    fun forShelf(all: List<Book>, today: LocalDate): String = buildString {
        val s = ShelfStats.of(all, today)
        appendLine("Their shelf (counted by the app): ${s.books} books, ${s.fiction} fiction and ${s.nonfiction} nonfiction; ${s.read} read in the app so far.")
        if (s.reading.isNotEmpty()) appendLine("Reading now: ${s.reading.joinToString("; ") { "${line(it)}, ${it.progress}%" }}")
        val done = all.filter { it.status == Status.READ }.sortedByDescending { it.finished }.take(8)
        if (done.isNotEmpty()) {
            appendLine("Recently finished:")
            done.forEach { appendLine("- ${line(it)}${if (it.rating > 0) ", rated ${it.rating}/5" else ""}${if (it.verdict.isNotBlank()) ": ${it.verdict}" else ""}") }
        }
        val picks = Shelf.picks(all)
        if (picks.isNotEmpty()) {
            appendLine("Unread books on their shelf that fit (choose from these first):")
            picks.forEach { appendLine("- ${line(it.book)} (${it.why})") }
        }
        appendLine("Languages written in: ${s.languages.take(6).joinToString(", ") { "${it.first} ${it.second}" }}")
        appendLine("Regions: ${s.regions.joinToString(", ") { "${it.first} ${it.second}" }}")
    }.trim()

    private fun StringBuilder.catalogue(b: Book) {
        val bits = listOfNotNull(
            b.yearText.takeIf { it.isNotBlank() }, b.form.takeIf { it.isNotBlank() }, b.genre.takeIf { it.isNotBlank() },
            b.language.takeIf { it.isNotBlank() }?.let { "written in $it" },
            b.series.takeIf { it.isNotBlank() }?.let { s -> b.seriesNo?.let { "$s #$it" } ?: s }
        )
        if (bits.isNotEmpty()) appendLine("Catalogue: ${bits.joinToString(" · ")}")
        if (b.about.isNotBlank()) appendLine("About: ${b.about}")
    }

    private fun line(b: Book): String = "${b.title}${if (b.author.isNotBlank()) " by ${b.author}" else ""}${if (b.year != null) " (${b.yearText})" else ""}"

    /** What goes in the box for "Draft a gist". */
    fun gistPrompt(book: Book): String =
        "Write a gist of ${book.title} as a card with these lines: Book, Thesis (one sentence), " +
            "three to five Claim lines, Pushback (what critics say against it), and two Recall questions. " +
            "Use only what you know about the book; if you aren't sure of something, leave it out."
}

/** Reads the Librarian's gist card out of a reply (lines like "Thesis: …", "Claim: …"). */
object GistCard {
    private val FIELD = Regex("""^\s*(?:[-*•]|\d+[.)])?\s*\**\s*(Book|Thesis|Claims?|Pushback|Recall)\s*\**\s*[:：]\s*\**\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val ITEM = Regex("""^\s*(?:[-*•]|\d+[.)])\s+(.*)$""")

    data class Parsed(val book: String, val gist: Gist, val recall: List<String>)

    fun has(reply: String): Boolean = parse(reply) != null

    fun parse(reply: String, now: Long = 0): Parsed? {
        var book = ""
        var thesis = ""
        var pushback = ""
        val claims = mutableListOf<String>()
        val recall = mutableListOf<String>()
        var list: MutableList<String>? = null
        for (raw in reply.lines()) {
            val m = FIELD.matchEntire(raw)
            if (m != null) {
                val value = m.groupValues[2].trim().trim('*').trim()
                list = null
                when (m.groupValues[1].lowercase()) {
                    "book" -> if (book.isEmpty()) book = value
                    "thesis" -> if (thesis.isEmpty()) thesis = value
                    "pushback" -> if (pushback.isEmpty()) pushback = value
                    "claim", "claims" -> { if (value.isNotEmpty()) claims += value; list = claims }
                    "recall" -> { if (value.isNotEmpty()) recall += value; list = recall }
                }
                continue
            }
            val item = ITEM.matchEntire(raw)?.groupValues?.get(1)?.trim()
            if (item != null && list != null) list.add(item.trim('*').trim())
            else if (raw.isNotBlank()) list = null
        }
        if (thesis.isEmpty() || claims.isEmpty()) return null
        return Parsed(book, Gist(thesis, claims.take(6), pushback, GistSource.GENERAL, now), recall.take(4))
    }

    /** The book on [shelf] a card names ("Title — Author", or just the title). */
    fun find(name: String, shelf: List<Book>, fallback: Book? = null): Book? {
        if (name.isBlank()) return fallback
        val parts = name.split(" — ", " - ", " by ", limit = 2)
        val title = Book.keyOf(parts[0], "").substringBefore('|')
        val author = parts.getOrNull(1)?.let { Book.keyOf(it, "").substringBefore('|') }.orEmpty()
        val sameTitle = shelf.filter { Book.keyOf(it.title, "").substringBefore('|') == title }
        return sameTitle.firstOrNull { author.isEmpty() || Book.keyOf(it.author, "").substringBefore('|').contains(author.take(6)) }
            ?: sameTitle.singleOrNull()
            ?: fallback?.takeIf { Book.keyOf(it.title, "").substringBefore('|') == title }
    }
}
