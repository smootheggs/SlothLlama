package app.localmind.assistant.data.books

import android.content.Context
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.domain.books.Gist
import app.localmind.assistant.domain.books.Kinds
import app.localmind.assistant.domain.books.ReferenceRule
import app.localmind.assistant.domain.books.Status
import app.localmind.assistant.domain.books.UserShelf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ShelfFile(
    val books: List<Book> = emptyList(),
    /** Shelves you made. */
    val shelves: List<UserShelf> = emptyList(),
    /** 2: books like language courses and cookbooks moved to Reference (1.30). */
    val version: Int = 1
)

/**
 * SlothBooks' shelf: every book and paper with your reading. One file in private storage
 * (books/shelf.json), written whole through a temporary file; in backups. The PDFs and EPUBs
 * themselves stay in Documents/SlothBooks on the tablet ([PaperFiles]).
 *
 * Calls block on file IO, so they're made from a background thread (the mailbox's, or through
 * the suspend wrappers); a lock keeps them in order.
 */
@Singleton
class BookShelf @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(File(context.filesDir, "books").apply { mkdirs() }, "shelf.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Any()
    @Volatile private var loaded = false

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _shelves = MutableStateFlow<List<UserShelf>>(emptyList())
    /** Your own shelves, oldest first. */
    val shelves: StateFlow<List<UserShelf>> = _shelves.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { loadBlocking() }

    private fun loadBlocking() {
        synchronized(lock) {
            if (loaded) return
            val f = if (!file.exists()) ShelfFile(version = VERSION) else runCatching {
                json.decodeFromString(ShelfFile.serializer(), file.readText())
            }.getOrElse {
                // Never write over a shelf that won't read: keep it aside, and start afresh.
                runCatching { file.copyTo(File(file.parentFile, "shelf.unreadable.json"), overwrite = true) }
                ShelfFile(version = VERSION)
            }
            _shelves.value = f.shelves
            _books.value = if (f.version < 2) {
                // Once: books you'd look things up in leave To read for Reference.
                f.books.map { if (it.status == Status.UNREAD && ReferenceRule.looksLikeReference(it)) it.copy(status = Status.REFERENCE) else it }
                    .also { if (f.books.isNotEmpty()) writeAtomic(encode(it, f.shelves)) }
            } else f.books
            loaded = true
        }
    }

    /** Applies [edit] to the whole shelf and saves it. Blocking. */
    fun changeBlocking(edit: (List<Book>) -> List<Book>) {
        loadBlocking()
        synchronized(lock) {
            val next = edit(_books.value)
            if (next == _books.value) return
            writeAtomic(encode(next, _shelves.value))
            _books.value = next
        }
    }

    suspend fun change(edit: (List<Book>) -> List<Book>) = withContext(Dispatchers.IO) { changeBlocking(edit) }

    private fun encode(books: List<Book>, shelves: List<UserShelf>): String =
        json.encodeToString(ShelfFile.serializer(), ShelfFile(books, shelves, VERSION))

    // --- your shelves ---

    /** Makes a shelf called [name] (or returns the one you already have by that name). */
    suspend fun createShelf(name: String, now: Long = System.currentTimeMillis()): UserShelf = withContext(Dispatchers.IO) {
        loadBlocking()
        synchronized(lock) {
            val clean = name.trim().take(MAX_SHELF_NAME)
            require(clean.isNotEmpty()) { "A shelf needs a name." }
            _shelves.value.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return@synchronized it }
            val shelf = UserShelf(UUID.randomUUID().toString(), clean, now)
            val next = _shelves.value + shelf
            writeAtomic(encode(_books.value, next))
            _shelves.value = next
            shelf
        }
    }

    suspend fun renameShelf(id: String, name: String) = withContext(Dispatchers.IO) {
        loadBlocking()
        synchronized(lock) {
            val clean = name.trim().take(MAX_SHELF_NAME)
            require(clean.isNotEmpty()) { "A shelf needs a name." }
            val next = _shelves.value.map { if (it.id == id) it.copy(name = clean) else it }
            writeAtomic(encode(_books.value, next))
            _shelves.value = next
        }
    }

    /** Removes the shelf; its books stay on the shelf as a whole, just not on this one. */
    suspend fun deleteShelf(id: String) = withContext(Dispatchers.IO) {
        loadBlocking()
        synchronized(lock) {
            val shelves = _shelves.value.filterNot { it.id == id }
            val books = _books.value.map { b -> if (id in b.shelves) b.copy(shelves = b.shelves - id) else b }
            writeAtomic(encode(books, shelves))
            _shelves.value = shelves
            _books.value = books
        }
    }

    suspend fun setOnShelf(bookId: String, shelfId: String, on: Boolean, now: Long = System.currentTimeMillis()) = update(bookId, now) { b ->
        b.copy(shelves = if (on) (b.shelves + shelfId).distinct() else b.shelves - shelfId)
    }

    /** Replaces the book with [book]'s id (adding it if it's new), stamped with [now]. */
    suspend fun put(book: Book, now: Long = System.currentTimeMillis()) = change { list ->
        val stamped = book.copy(updatedAt = now, addedAt = book.addedAt.takeIf { it > 0 } ?: now)
        if (list.any { it.id == book.id }) list.map { if (it.id == book.id) stamped else it } else list + stamped
    }

    suspend fun update(id: String, now: Long = System.currentTimeMillis(), edit: (Book) -> Book) = change { list ->
        list.map { if (it.id == id) edit(it).copy(updatedAt = now) else it }
    }

    suspend fun addAll(books: List<Book>) = change { it + books }

    suspend fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    /** Saves a gist (and recall questions, if there are any new ones) to a book's card. */
    suspend fun saveGist(id: String, gist: Gist, recall: List<String>, now: Long = System.currentTimeMillis()) = update(id, now) { b ->
        b.copy(gist = gist, recall = (b.recall + recall).distinct().take(8))
    }

    /** Moves [id] in the Up next queue: to the end when [queued], out of it otherwise; numbers stay 1, 2, 3… */
    suspend fun queue(id: String, queued: Boolean, now: Long = System.currentTimeMillis()) = change { list ->
        val ordered = list.filter { it.upNext != null && it.id != id }.sortedBy { it.upNext }.map { it.id }
            .let { if (queued) it + id else it }
        val pos = ordered.withIndex().associate { (i, v) -> v to i + 1 }
        list.map { b ->
            val n = pos[b.id]
            if (n != b.upNext) b.copy(upNext = n, updatedAt = if (b.id == id) now else b.updatedAt) else b
        }
    }

    /** Adds a file kept on the tablet (from the Lobster or opened here) as a paper or a book. Blocking. */
    fun addFileBlocking(title: String, kind: String, uri: String, fileName: String, source: String, fresh: Boolean, now: Long = System.currentTimeMillis()): Book {
        val book = Book(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            form = if (kind == Kinds.PAPER) "Paper" else "",
            file = uri,
            fileName = fileName,
            source = source,
            format = if (fileName.endsWith(".epub", ignoreCase = true)) "EPUB" else "PDF",
            status = Status.UNREAD,
            fresh = fresh,
            addedAt = now,
            updatedAt = now
        )
        changeBlocking { it + book }
        return book
    }

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun backupText(): String {
        load()
        return encode(_books.value, _shelves.value)
    }

    /** A backup's books join yours; where both have the same book, the one changed last stays. */
    suspend fun mergeBackup(text: String) {
        val file = json.decodeFromString(ShelfFile.serializer(), text)
        val theirs = file.books
        withContext(Dispatchers.IO) {
            loadBlocking()
            synchronized(lock) {
                val have = _shelves.value.map { it.id }.toSet()
                val next = _shelves.value + file.shelves.filter { it.id !in have }
                if (next != _shelves.value) {
                    writeAtomic(encode(_books.value, next))
                    _shelves.value = next
                }
            }
        }
        change { mine ->
            val byId = mine.associateBy { it.id }.toMutableMap()
            theirs.forEach { t -> if ((byId[t.id]?.updatedAt ?: -1) < t.updatedAt) byId[t.id] = t }
            mine.map { byId.getValue(it.id) } + theirs.filter { t -> mine.none { it.id == t.id } }
        }
    }

    companion object {
        private const val VERSION = 2
        private const val MAX_SHELF_NAME = 40
    }

    private fun writeAtomic(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save the shelf" }
        }
    }
}
