package app.localmind.assistant.data.books

import android.content.Context
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.domain.books.Gist
import app.localmind.assistant.domain.books.Kinds
import app.localmind.assistant.domain.books.Status
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
private data class ShelfFile(val books: List<Book> = emptyList())

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

    suspend fun load() = withContext(Dispatchers.IO) { loadBlocking() }

    private fun loadBlocking() {
        synchronized(lock) {
            if (loaded) return
            _books.value = runCatching { json.decodeFromString(ShelfFile.serializer(), file.readText()).books }.getOrDefault(emptyList())
            loaded = true
        }
    }

    /** Applies [edit] to the whole shelf and saves it. Blocking. */
    fun changeBlocking(edit: (List<Book>) -> List<Book>) {
        loadBlocking()
        synchronized(lock) {
            val next = edit(_books.value)
            if (next == _books.value) return
            writeAtomic(json.encodeToString(ShelfFile.serializer(), ShelfFile(next)))
            _books.value = next
        }
    }

    suspend fun change(edit: (List<Book>) -> List<Book>) = withContext(Dispatchers.IO) { changeBlocking(edit) }

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
        return json.encodeToString(ShelfFile.serializer(), ShelfFile(_books.value))
    }

    /** A backup's books join yours; where both have the same book, the one changed last stays. */
    suspend fun mergeBackup(text: String) {
        val theirs = json.decodeFromString(ShelfFile.serializer(), text).books
        change { mine ->
            val byId = mine.associateBy { it.id }.toMutableMap()
            theirs.forEach { t -> if ((byId[t.id]?.updatedAt ?: -1) < t.updatedAt) byId[t.id] = t }
            mine.map { byId.getValue(it.id) } + theirs.filter { t -> mine.none { it.id == t.id } }
        }
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
