package app.localmind.assistant.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.later.LaterItem
import app.localmind.assistant.data.later.ReadingList
import app.localmind.assistant.data.library.LibraryItem
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.LobsterSource
import app.localmind.assistant.data.lobster.MailboxState
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.tools.Note
import app.localmind.assistant.data.tools.NotesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where a text in the reader came from. */
enum class ReaderSource { CATCH, LIBRARY, NOTE, LATER, EXPRESS }

/** A text open in the reader. [key] names it for the saved reading position. */
data class ReaderDoc(
    val key: String,
    val source: ReaderSource,
    /** The catch's file name, the library item's id, the note's id, the Later item's id, or "paper|story key". */
    val ref: String,
    val title: String,
    val meta: String,
    val text: String,
    val sources: List<LobsterSource> = emptyList(),
    /** [text] split for showing; worked out off the main thread when the text is opened. */
    val blocks: List<ReaderBlock> = emptyList(),
    /** An Express story's page (opened through the Lobster) and its lead picture's file name. */
    val url: String = "",
    val picture: String? = null
)

/** What a paragraph's long-press menu can ask the chat to do with it. */
enum class ParagraphAsk(val label: String, val prompt: String) {
    EXPLAIN("Explain", "Explain the attached passage simply, and say what it means"),
    SIMPLIFY("Simplify", "Rewrite the attached passage in plain, simple words"),
    TRANSLATE("Translate", "Translate the attached passage into English"),
    POINTS("Five points", "Sum up the attached passage in five short bullet points")
}

/**
 * The reader: catches from the Lobster, library documents and notes, read in full. Everything
 * stays on the tablet; links go to the Lobster as Fetch orders, because SlothLlama never goes
 * online itself. Text size and where you stopped reading are kept in app preferences.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val mailbox: LobsterMailbox,
    private val library: LibraryStore,
    private val notes: NotesStore,
    private val attachments: AttachmentStore,
    private val askInbox: AskInbox,
    private val later: ReadingList,
    private val atlas: app.localmind.assistant.data.atlas.AtlasRepository,
    private val editions: app.localmind.assistant.data.edition.EditionStore
) : ViewModel() {

    /** The papers' lead pictures, for an Express story's. */
    val pictures: app.localmind.assistant.data.edition.StoryPictures get() = editions.pictures

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val mailboxState: StateFlow<MailboxState> = mailbox.state
    val libraryItems: StateFlow<List<LibraryItem>?> = library.items
    val noteList: StateFlow<List<Note>> = notes.notes
    /** The Later shelf, newest first. */
    val laterItems: StateFlow<List<LaterItem>> = later.items

    private val _doc = MutableStateFlow<ReaderDoc?>(null)
    val doc: StateFlow<ReaderDoc?> = _doc.asStateFlow()

    private val _places = MutableStateFlow<List<app.localmind.assistant.domain.atlas.PlaceHit>>(emptyList())
    /** The places the open text names, most named first: found on the tablet with the Atlas's names. */
    val places: StateFlow<List<app.localmind.assistant.domain.atlas.PlaceHit>> = _places.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _scale = MutableStateFlow(prefs.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE))
    /** Text size, 0.8× to 1.8×. */
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _whitePage = MutableStateFlow(prefs.getBoolean(KEY_WHITE, false))
    /** Read on a white page (the rest of the app stays as it is). */
    val whitePage: StateFlow<Boolean> = _whitePage.asStateFlow()

    fun toggleWhitePage() {
        val v = !_whitePage.value
        _whitePage.value = v
        prefs.edit().putBoolean(KEY_WHITE, v).apply()
    }

    private val _picked = MutableStateFlow<Set<String>>(emptySet())
    /** Links picked to send to the Lobster together. */
    val picked: StateFlow<Set<String>> = _picked.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { runCatching { library.refresh() } }
    }

    fun open(source: ReaderSource, ref: String) {
        viewModelScope.launch {
            _loading.value = true
            _doc.value = null
            _places.value = emptyList()
            _picked.value = emptySet()
            try {
                val loaded = load(source, ref)?.let { d ->
                    d.copy(blocks = withContext(Dispatchers.Default) { readerBlocks(d.text) })
                }
                _doc.value = loaded
                if (loaded == null) _message.value = "That isn't there any more."
                loaded?.let { d ->
                    _places.value = runCatching {
                        val finder = atlas.finder()
                        withContext(Dispatchers.Default) { finder.find(d.title + "\n" + d.text.take(40_000), 4) }
                    }.getOrDefault(emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.message?.takeIf { it.isNotBlank() } ?: "That couldn't be opened."
            } finally {
                _loading.value = false
            }
        }
    }

    fun close() {
        _doc.value = null
        _places.value = emptyList()
        _picked.value = emptySet()
    }

    private suspend fun load(source: ReaderSource, ref: String): ReaderDoc? = when (source) {
        ReaderSource.CATCH -> {
            mailbox.refresh()
            mailbox.state.value.catch.firstOrNull { it.fileName == ref }?.item?.let { c ->
                ReaderDoc(
                    key = "catch:${c.id}",
                    source = source,
                    ref = ref,
                    title = c.title.ifBlank { "From the Lobster" },
                    meta = "${c.kind.replaceFirstChar { it.uppercase() }} · from the Lobster",
                    text = c.text,
                    sources = c.sources
                )
            }
        }
        ReaderSource.LIBRARY -> {
            val id = ref.toLongOrNull() ?: return null
            library.fullText(id)?.let { (name, text) ->
                ReaderDoc(key = "lib:$id", source = source, ref = ref, title = name, meta = "Library", text = text)
            }
        }
        ReaderSource.LATER -> later.items.value.firstOrNull { it.id == ref }?.let { item ->
            val text = withContext(Dispatchers.IO) { later.text(item.id) } ?: return null
            ReaderDoc(
                key = "later:${item.id}",
                source = source,
                ref = ref,
                title = item.title,
                meta = listOf(item.site, "Later").filter { it.isNotBlank() }.joinToString(" · "),
                text = text,
                sources = if (item.url.isBlank()) emptyList() else listOf(LobsterSource(item.site, item.url))
            )
        }
        ReaderSource.EXPRESS -> {
            val file = ref.substringBefore('|')
            val key = ref.substringAfter('|', "")
            editions.paper(file)?.let { saved ->
                app.localmind.assistant.domain.edition.Editions.news(saved.edition)
                    .firstOrNull { app.localmind.assistant.domain.edition.Editions.storyKey(it) == key }
                    ?.takeIf { app.localmind.assistant.domain.edition.Editions.hasClean(it) }
                    ?.let { st ->
                        ReaderDoc(
                            key = "express:$key",
                            source = source,
                            ref = ref,
                            title = st.title,
                            meta = listOf(st.outlet, "read clean").filter { it.isNotBlank() }.joinToString(" · "),
                            text = app.localmind.assistant.domain.edition.Editions.cleanText(st),
                            sources = listOf(LobsterSource(st.outlet.ifBlank { ReadingList.siteOf(st.url) }, st.url)),
                            url = st.url,
                            picture = saved.pics[key]
                        )
                    }
            }
        }
        ReaderSource.NOTE -> notes.notes.value.firstOrNull { it.id == ref }?.let { n ->
            val (title, body) = splitNote(n.text)
            ReaderDoc(key = "note:${n.id}", source = source, ref = ref, title = title, meta = "Note", text = body)
        }
    }

    // --- reading comfort ---

    fun bigger() = setScale(_scale.value + STEP)
    fun smaller() = setScale(_scale.value - STEP)

    private fun setScale(value: Float) {
        val v = (Math.round(value * 10) / 10f).coerceIn(MIN_SCALE, MAX_SCALE)
        _scale.value = v
        prefs.edit().putFloat(KEY_SCALE, v).apply()
    }

    /** The block to start at: where this text was left last time. */
    fun position(key: String): Int = prefs.getInt(POS + key, 0)

    fun savePosition(key: String, block: Int) {
        val keys = prefs.getStringSet(KEY_RECENT, emptySet()).orEmpty()
        val e = prefs.edit().putInt(POS + key, block)
        if (key !in keys) {
            // At most 200 texts keep a position; the rest are forgotten.
            val kept = (keys + key).toList().takeLast(MAX_POSITIONS).toSet()
            (keys - kept).forEach { e.remove(POS + it) }
            e.putStringSet(KEY_RECENT, kept)
        }
        e.apply()
    }

    // --- the Later shelf ---

    /** How far a Later article has been read (0 to 1), as the page scrolls. */
    fun laterProgress(id: String, progress: Float) = act {
        withContext(Dispatchers.IO) { later.setProgress(id, progress) }
        null
    }

    /** Finished: off the to-read list, kept in the archive. */
    fun finishLater(id: String) = act {
        withContext(Dispatchers.IO) { later.setArchived(id, true) }
        close()
        "Archived. It's under Archive on the Later shelf."
    }

    fun restoreLater(id: String) = act {
        withContext(Dispatchers.IO) { later.setArchived(id, false) }
        "Back on your Later list"
    }

    fun favouriteLater(id: String, on: Boolean) = act {
        withContext(Dispatchers.IO) { later.setFavourite(id, on) }
        if (on) "Kept as a favourite ★" else "No longer a favourite"
    }

    fun removeLater(id: String) = act {
        withContext(Dispatchers.IO) { later.remove(id) }
        if (_doc.value?.ref == id) close()
        "Deleted from Later"
    }

    /** A copy goes to the Library, where it's searchable by meaning and usable in chats. */
    fun laterToLibrary(id: String) = act {
        val item = later.items.value.firstOrNull { it.id == id } ?: return@act null
        val text = withContext(Dispatchers.IO) { later.text(id) } ?: return@act null
        library.addText(item.title, if (item.url.isBlank()) text else "$text\n\nSource: ${item.url}")
        "Added to your library 📚"
    }

    // --- links: the Lobster fetches them ---

    fun togglePick(url: String) = _picked.update { if (url in it) it - url else it + url }

    fun clearPicks() {
        _picked.value = emptySet()
    }

    fun fetch(url: String) = sendFetches(listOf(url))

    fun fetchPicked() = sendFetches(_picked.value.toList())

    /** The Lobster fetches the page onto the Later shelf. */
    /** An Express story to the Later shelf at once: its clean text is already here. */
    fun keepClean() = act {
        val d = _doc.value ?: return@act null
        if (d.source != ReaderSource.EXPRESS) return@act null
        withContext(Dispatchers.IO) { later.keepText(d.title, d.url, d.text) }
        "Saved for later 🦥 It's on the Later shelf."
    }

    fun saveForLater(url: String) = act {
        mailbox.send(OrderKind.FETCH, url, deep = false, deliver = "later")
        "Saved for later 🦞 It will appear on the Later shelf."
    }

    private fun sendFetches(urls: List<String>) = act {
        if (urls.isEmpty()) return@act null
        urls.forEach { mailbox.send(OrderKind.FETCH, it, deep = false, deliver = "library") }
        _picked.value = emptySet()
        if (urls.size == 1) "Sent to the Lobster 🦞 It will bring the page back."
        else "Sent ${urls.size} links to the Lobster 🦞"
    }

    // --- passages ---

    fun highlightToNotes(passage: String) = act {
        val d = _doc.value
        notes.add("“${passage.trim().take(MAX_NOTE_CHARS)}”\n\n— ${d?.title ?: "Reader"}")
        "Saved to Notes 📝"
    }

    /**
     * A new chat with the request in the box and the passage attached as a document, so text
     * from the web stays reference material for the model rather than becoming your message.
     */
    fun ask(what: ParagraphAsk, passage: String) {
        val title = _doc.value?.title ?: "Reader"
        val document = attachments.textDocument("Passage from $title", passage.trim().take(MAX_PASSAGE_CHARS))
        askInbox.post(LaunchRequest.Shared(prompt = what.prompt + ".", document = document))
    }

    /** The whole text goes to a new chat as an attached document. */
    fun askAboutAll() {
        val d = _doc.value ?: return
        askInbox.post(LaunchRequest.Shared(prompt = "", document = attachments.textDocument(d.title, withSources(d))))
    }

    /** The whole text to the trader, to read it the way a metals desk would. */
    fun askTrader() {
        val d = _doc.value ?: return
        askInbox.post(
            LaunchRequest.Shared(
                prompt = BuiltInPersonas.READ_LIKE_A_TRADER,
                document = attachments.textDocument(d.title, withSources(d)),
                personaId = BuiltInPersonas.TRADER.id
            )
        )
    }

    /** The text as Markdown, for Share. */
    fun markdown(): String? = _doc.value?.let { d -> "# ${d.title}\n\n${withSources(d)}" }

    fun clearMessage() {
        _message.value = null
    }

    private fun withSources(d: ReaderDoc): String = buildString {
        append(d.text.trim())
        if (d.sources.isNotEmpty() && d.sources.none { s -> s.url.isNotBlank() && d.text.contains(s.url) }) {
            append("\n\nSources:\n")
            d.sources.forEachIndexed { i, s -> append("${i + 1}. ${s.title.ifBlank { s.url }} ${s.url}\n") }
        }
    }.trim()

    private fun act(block: suspend () -> String?) {
        viewModelScope.launch {
            try {
                block()?.let { _message.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.message?.takeIf { it.isNotBlank() } ?: "That didn't work."
            }
        }
    }

    companion object {
        private const val PREFS = "reader"
        private const val KEY_SCALE = "scale"
        private const val KEY_WHITE = "white_page"
        private const val KEY_RECENT = "recent"
        private const val POS = "pos:"
        private const val STEP = 0.1f
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.8f
        private const val MAX_POSITIONS = 200
        private const val MAX_NOTE_CHARS = 2_000
        private const val MAX_PASSAGE_CHARS = 4_000

        /** A note's first line is its title when it's short; otherwise "Note". */
        fun splitNote(text: String): Pair<String, String> {
            val t = text.trim()
            val first = t.substringBefore('\n').trim()
            val rest = t.substringAfter('\n', "").trim()
            return if (first.length in 1..120 && rest.isNotEmpty()) first.removePrefix("#").trim() to rest
            else "Note" to t
        }
    }
}
