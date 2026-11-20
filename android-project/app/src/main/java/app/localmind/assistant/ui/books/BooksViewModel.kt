package app.localmind.assistant.ui.books

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.books.BookShelf
import app.localmind.assistant.data.books.CoverStore
import app.localmind.assistant.domain.books.CoverSource
import app.localmind.assistant.domain.books.DefaultShelf
import app.localmind.assistant.domain.books.GroupBy
import app.localmind.assistant.domain.books.ShelfPick
import app.localmind.assistant.domain.books.UserShelf
import app.localmind.assistant.data.books.PaperFiles
import app.localmind.assistant.data.books.SheetReader
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.domain.books.Gist
import app.localmind.assistant.domain.books.GistCard
import app.localmind.assistant.domain.books.GistSource
import app.localmind.assistant.domain.books.Kinds
import app.localmind.assistant.domain.books.LibrarianContext
import app.localmind.assistant.domain.books.ShelfImport
import app.localmind.assistant.domain.books.ShelfStats
import app.localmind.assistant.domain.books.Status
import app.localmind.assistant.domain.books.withStatus
import app.localmind.assistant.domain.persona.BuiltInPersonas
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** How the shelf is shown. */
enum class BookView { LIST, SMALL, LARGE }

/** SlothBooks: the shelf, its cards, papers kept as files, the counts, and the way into the Librarian. */
@HiltViewModel
class BooksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shelf: BookShelf,
    private val files: PaperFiles,
    private val covers: CoverStore,
    private val library: LibraryStore,
    private val askInbox: AskInbox,
    private val attachments: AttachmentStore
) : ViewModel() {

    val books: StateFlow<List<Book>> = shelf.books

    /** Your own shelves (the default ones aren't in here). */
    val shelves: StateFlow<List<UserShelf>> = shelf.shelves

    private val prefs = context.getSharedPreferences("books", Context.MODE_PRIVATE)

    private val _view = MutableStateFlow(BookView.entries.firstOrNull { it.name == prefs.getString("view", null) } ?: BookView.LIST)
    /** List, small covers or large covers; remembered. */
    val view: StateFlow<BookView> = _view.asStateFlow()

    private val _group = MutableStateFlow(GroupBy.entries.firstOrNull { it.name == prefs.getString("group", null) } ?: GroupBy.AUTHOR)
    val group: StateFlow<GroupBy> = _group.asStateFlow()

    private val _pick = MutableStateFlow<ShelfPick>(ShelfPick.Default(DefaultShelf.ALL))
    /** The shelf the list shows. */
    val pick: StateFlow<ShelfPick> = _pick.asStateFlow()

    fun setView(v: BookView) {
        _view.value = v
        prefs.edit().putString("view", v.name).apply()
    }

    fun setGroup(g: GroupBy) {
        _group.value = g
        prefs.edit().putString("group", g.name).apply()
    }

    fun choose(p: ShelfPick) {
        _pick.value = p
    }

    // --- your shelves ---

    fun createShelf(name: String, addTo: Book? = null) = act {
        val made = shelf.createShelf(name)
        if (addTo != null) shelf.setOnShelf(addTo.id, made.id, true) else _pick.value = ShelfPick.Mine(made.id)
        if (addTo != null) "${addTo.title} is on ${made.name}" else null
    }

    fun renameShelf(s: UserShelf, name: String) = act { shelf.renameShelf(s.id, name); null }

    fun deleteShelf(s: UserShelf) = act {
        shelf.deleteShelf(s.id)
        if ((_pick.value as? ShelfPick.Mine)?.id == s.id) _pick.value = ShelfPick.Default(DefaultShelf.ALL)
        "${s.name} is gone; its books are still on your shelf"
    }

    fun setOnShelf(book: Book, s: UserShelf, on: Boolean) = act { shelf.setOnShelf(book.id, s.id, on); null }

    // --- covers ---

    /** The kept cover for [book] at about [widthPx] wide, or null for the made one. Blocking: call off the main thread. */
    fun coverBitmap(book: Book, widthPx: Int): android.graphics.Bitmap? =
        book.cover.takeIf { it.isNotBlank() }?.let { covers.load(it, widthPx) }

    val stats: StateFlow<ShelfStats> = shelf.books.map { ShelfStats.of(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfStats.of(emptyList(), LocalDate.now()))

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** What's being done right now ("Reading your list…"), or null. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _preview = MutableStateFlow<ShelfImport.Preview?>(null)
    /** A spreadsheet read and waiting for "Bring in". */
    val preview: StateFlow<ShelfImport.Preview?> = _preview.asStateFlow()

    private val _reading = MutableStateFlow<Book?>(null)
    /** A PDF open in the viewer. */
    val reading: StateFlow<Book?> = _reading.asStateFlow()

    /** The book last talked about, for a gist card that doesn't name one. */
    private var lastBookId: String? = null

    init {
        viewModelScope.launch {
            shelf.load()
            indexFiles()
        }
    }

    // --- bringing books in ---

    /** Reads a spreadsheet (.xlsx or .csv) and shows what it would add. */
    fun readSheet(uri: Uri) = act("Reading your list…") {
        val name = files.displayName(uri)
        val rows = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open $name.")
            check(bytes.size < MAX_SHEET_BYTES) { "$name is too large for a book list." }
            SheetReader.read(bytes, name)
        }
        check(rows.isNotEmpty()) { "No books found in $name: it needs a column called Title." }
        _preview.value = ShelfImport.preview(rows, shelf.books.value, System.currentTimeMillis()) { shelf.newId() }
        null
    }

    fun confirmImport() = act("Bringing in your books…") {
        val p = _preview.value ?: return@act null
        shelf.addAll(p.fresh)
        _preview.value = null
        if (p.fresh.size == 1) "1 book on your shelf 📚" else "${p.fresh.size} books on your shelf 📚"
    }

    fun cancelImport() {
        _preview.value = null
    }

    /** A new book, or an edit to one: the catalogue half. */
    fun save(book: Book) = act {
        val clean = book.copy(title = book.title.trim(), author = book.author.trim())
        if (clean.title.isEmpty()) return@act "A book needs a title."
        val isNew = shelf.books.value.none { it.id == clean.id }
        if (isNew && shelf.books.value.any { it.key == clean.key }) return@act "${clean.title} is already on your shelf."
        shelf.put(clean)
        if (isNew) "${clean.title} is on your shelf" else null
    }

    fun newBook(paper: Boolean = false): Book =
        Book(id = shelf.newId(), kind = if (paper) Kinds.PAPER else Kinds.BOOK, title = "", form = if (paper) "Paper" else "")

    /** A PDF or EPUB picked on the tablet: copied into Documents/SlothBooks and added. */
    fun addFile(uri: Uri, paper: Boolean) = act("Keeping the file…") {
        val book = withContext(Dispatchers.IO) {
            val (saved, name) = files.copyIn(uri, if (paper) PaperFiles.PAPERS else PaperFiles.BOOKS)
            shelf.addFileBlocking(PaperFiles.titleFrom(name), if (paper) Kinds.PAPER else Kinds.BOOK, saved.toString(), name, "", fresh = false)
        }
        indexFiles()
        "Kept in Documents/${PaperFiles.ROOT}/${if (paper) PaperFiles.PAPERS else PaperFiles.BOOKS} · ${book.title}"
    }

    fun delete(book: Book) = act {
        shelf.delete(book.id)
        if (book.cover.isNotBlank()) withContext(Dispatchers.IO) { covers.delete(book.cover) }
        "${book.title} is off the shelf" + if (book.hasFile) " (the file stays in Documents/${PaperFiles.ROOT})" else ""
    }

    // --- your half ---

    fun setStatus(book: Book, status: String) = act {
        shelf.update(book.id) { it.withStatus(status, LocalDate.now()) }
        if (status == Status.READ && book.status != Status.READ) "Finished · ${book.title}" else null
    }

    fun setProgress(book: Book, percent: Int, upTo: String) = act {
        shelf.update(book.id) { it.copy(progress = percent.coerceIn(0, 100), upTo = upTo.trim()) }
        null
    }

    fun queue(book: Book, queued: Boolean) = act {
        shelf.queue(book.id, queued)
        if (queued) "Up next · ${book.title}" else null
    }

    fun edit(book: Book, change: (Book) -> Book) = act {
        shelf.update(book.id, edit = change)
        null
    }

    fun seen(book: Book) {
        if (book.fresh) edit(book) { it.copy(fresh = false) }
    }

    fun clearGist(book: Book) = edit(book) { it.copy(gist = null) }

    /** Your own gist, typed on the card. */
    fun saveOwnGist(book: Book, thesis: String) = edit(book) {
        it.copy(gist = Gist(thesis.trim(), it.gist?.claims.orEmpty(), it.gist?.pushback.orEmpty(), GistSource.YOU, System.currentTimeMillis()))
    }

    /**
     * Saves the gist card in a Librarian reply to the book it names. Returns what happened, for
     * the chat to show.
     */
    suspend fun saveGistFrom(reply: String): String {
        shelf.load()
        val parsed = GistCard.parse(reply, System.currentTimeMillis()) ?: return "There's no gist card in that reply."
        val last = shelf.books.value.firstOrNull { it.id == lastBookId }
        val book = GistCard.find(parsed.book, shelf.books.value, fallback = last.takeIf { parsed.book.isBlank() })
            ?: return "Couldn't find \"${parsed.book}\" on your shelf."
        // A chat answers from the model's general knowledge, whatever the shelf holds.
        shelf.saveGist(book.id, parsed.gist.copy(source = GistSource.GENERAL), parsed.recall)
        return "Gist saved to ${book.title}'s card"
    }

    // --- the Librarian ---

    /** Opens a chat with the Librarian about [book], its card attached and the box left for you. */
    fun talk(book: Book, prompt: String = "") {
        lastBookId = book.id
        val text = LibrarianContext.forBook(book, shelf.books.value)
        askInbox.post(
            LaunchRequest.Shared(
                prompt = prompt,
                document = attachments.textDocument("${book.title} · your card", text),
                personaId = BuiltInPersonas.LIBRARIAN.id
            )
        )
    }

    fun draftGist(book: Book) = talk(book, LibrarianContext.gistPrompt(book))

    fun quizMe(book: Book) = talk(
        book,
        "Quiz me on ${book.title}: one question at a time, starting with my recall questions if there are any. Wait for my answer each time."
    )

    /** The whole shelf, for "what should I read next?". */
    fun askShelf(prompt: String = "What should I read next from my shelf, and why?") {
        askInbox.post(
            LaunchRequest.Shared(
                prompt = prompt,
                document = attachments.textDocument("Your shelf", LibrarianContext.forShelf(shelf.books.value, LocalDate.now())),
                personaId = BuiltInPersonas.LIBRARIAN.id
            )
        )
    }

    // --- files ---

    fun open(book: Book) {
        seen(book)
        if (!files.exists(book.file)) {
            _message.value = "${book.fileName.ifBlank { book.title }} isn't in Documents/${PaperFiles.ROOT} any more."
            return
        }
        _reading.value = book
    }

    fun closeViewer() {
        _reading.value = null
    }

    /** Files on the shelf whose text isn't in the Library yet: indexed one at a time, quietly. Covers come from the files too. */
    private suspend fun indexFiles() {
        for (b in shelf.books.value.filter { it.hasFile && it.cover.isBlank() && it.coverSource.isBlank() }) {
            val epub = b.fileName.endsWith(".epub", ignoreCase = true)
            val made = withContext(Dispatchers.IO) {
                val name = CoverStore.nameFor(b.id)
                if (epub) covers.fromEpub(name, Uri.parse(b.file)) else covers.fromPdf(name, Uri.parse(b.file))
            }
            // A file with no usable first page or cover keeps the made one, and isn't tried again.
            shelf.update(b.id) { it.copy(cover = made.orEmpty(), coverSource = if (made == null) "none" else if (epub) CoverSource.EPUB else CoverSource.PDF) }
        }
        for (b in shelf.books.value.filter { it.hasFile && it.libraryId == null }) {
            try {
                val item = library.addDocument(Uri.parse(b.file))
                shelf.update(b.id) { it.copy(libraryId = item.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A scan with no text, or a file that's gone: the card still works without it.
                shelf.update(b.id) { it.copy(libraryId = NOT_INDEXED) }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun act(working: String? = null, block: suspend () -> String?) {
        viewModelScope.launch {
            if (working != null) _busy.value = working
            try {
                block()?.let { _message.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.message?.takeIf { it.isNotBlank() } ?: "That didn't work."
            } finally {
                if (working != null) _busy.value = null
            }
        }
    }

    companion object {
        private const val MAX_SHEET_BYTES = 30 * 1024 * 1024
        /** Marks a file the Library couldn't read, so it isn't tried on every visit. */
        const val NOT_INDEXED = -1L
    }
}
