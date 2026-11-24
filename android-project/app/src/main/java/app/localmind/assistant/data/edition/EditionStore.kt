package app.localmind.assistant.data.edition

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.edition.DeskNote
import app.localmind.assistant.domain.edition.EdStory
import app.localmind.assistant.domain.edition.EditionOut
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.edition.SavedEdition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** An edition in the archive list: enough to show it without reading the whole paper. */
data class EditionEntry(
    val file: String,
    val id: String,
    val name: String,
    val made: Long,
    val stories: Int,
    val read: Int,
    val headlines: List<String>
)

/**
 * The editions the Lobster brought (editions/<made>-<id>.json, kept 30 days), what you've read in
 * each, the desk's notes, and the edition's settings (editions/setup.json). Private storage; the
 * settings are in backups, the papers aren't (they're news, and the Lobster can make more).
 *
 * Calls that block on file IO are made from a background thread; a lock keeps them in order.
 */
@Singleton
class EditionStore @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "editions").apply { mkdirs() }
    private val setupFile = File(dir, "setup.json")
    /** The lead pictures of stories read clean, kept beside their papers. */
    val pictures = StoryPictures(File(dir, "pics"))
    private val lock = Any()
    @Volatile private var loaded = false

    private val _setup = MutableStateFlow(EditionSetup())
    val setup: StateFlow<EditionSetup> = _setup.asStateFlow()

    private val _entries = MutableStateFlow<List<EditionEntry>>(emptyList())
    /** Newest first. */
    val entries: StateFlow<List<EditionEntry>> = _entries.asStateFlow()

    private val _latest = MutableStateFlow<SavedEdition?>(null)
    /** The newest edition, read whole (for Home and the page's first view). */
    val latest: StateFlow<SavedEdition?> = _latest.asStateFlow()
    /** The file [latest] was read from. */
    @Volatile private var latestName: String? = null

    private val _ready = MutableStateFlow(false)
    /**
     * (1.54.2) True once the papers have been read from storage. Until then the Express shows
     * that it's opening, not "no edition yet" (which it used to, for the moment it took).
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * A story to open in the Express, until it does: its link, read clean (asked from the Lobster's
     * tab) or [asTap], as a tap on it in the paper would (from the Atlas's pins).
     */
    data class OpenAsk(val url: String, val asTap: Boolean = false)

    private val _openAsk = MutableStateFlow<OpenAsk?>(null)
    val openAsk: StateFlow<OpenAsk?> = _openAsk.asStateFlow()

    fun askOpen(url: String, asTap: Boolean = false) {
        _openAsk.value = OpenAsk(url.take(2_000), asTap)
    }

    fun openAnswered() {
        _openAsk.value = null
    }

    /** A desk persona asked for notes from a chat: (edition id, persona id), until the notes are kept. */
    private val _pendingDesk = MutableStateFlow<Pair<String, String>?>(null)
    val pendingDesk: StateFlow<Pair<String, String>?> = _pendingDesk.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { loadBlocking() }

    private fun loadBlocking() {
        synchronized(lock) {
            if (loaded) return
            try {
                _setup.value = if (!setupFile.exists()) EditionSetup() else runCatching { Editions.parseSetup(setupFile.readText()) }.getOrElse {
                    runCatching { setupFile.copyTo(File(dir, "setup.unreadable.json"), overwrite = true) }
                    EditionSetup()
                }
                prune()
                refresh()
                loaded = true
            } finally {
                // Even if storage failed, the page stops saying it's opening (and says what it has).
                _ready.value = true
            }
        }
    }

    private fun paperFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != setupFile.name && !f.name.contains("unreadable") }
            .orEmpty().sortedByDescending { it.name }

    private fun read(f: File): SavedEdition? = runCatching { Editions.parseSaved(f.readText()) }
        .onFailure { Log.w(TAG, "Couldn't read ${f.name}", it) }
        .getOrNull()

    /** Archive lines by file, with the file's time when read, so only changed papers are read again. */
    private val cache = HashMap<String, Pair<Long, EditionEntry>>()

    private fun entryOf(f: File, s: SavedEdition) = EditionEntry(
        f.name, s.edition.id, s.edition.name, s.edition.made, s.stories.size, s.readCount,
        s.edition.top.take(3).map { it.title }.ifEmpty { s.edition.sections.flatMap { it.stories }.take(3).map { it.title } }
    )

    private fun refresh() {
        val files = paperFiles()
        // (1.54.2) The newest paper that reads: one that doesn't (cut short, or from a newer
        // Lobster) no longer leaves the page empty while the archive still lists the others.
        var latestFile: File? = null
        var latest: SavedEdition? = null
        for (f in files.take(LATEST_TRIES)) {
            val s = read(f) ?: continue
            latestFile = f
            latest = s
            break
        }
        val list = files.mapNotNull { f ->
            val stamp = f.lastModified()
            cache[f.name]?.takeIf { it.first == stamp }?.second
                ?: (if (f == latestFile) latest else read(f))?.let { s -> entryOf(f, s).also { cache[f.name] = stamp to it } }
        }
        cache.keys.retainAll(files.map { it.name }.toSet())
        _entries.value = list
        latestName = latestFile?.name
        _latest.value = latest
    }

    /** Papers made over 30 days ago go (by the time in their name), and never more than 60 are kept. */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
        paperFiles().forEachIndexed { i, f ->
            val made = f.name.take(13).toLongOrNull() ?: f.lastModified()
            if (i >= MAX_KEPT || made < cutoff) f.delete()
        }
        pictures.prune(paperFiles().map { StoryPictures.base(it.name) }.toSet())
    }

    // --- papers ---

    /** Keeps an edition the Lobster sent (its file's text). False if it isn't one. Blocking. */
    fun keepBlocking(text: String): Boolean {
        loadBlocking()
        val edition = runCatching { Editions.parse(text) }.getOrElse {
            Log.w(TAG, "Not an edition", it)
            return false
        }
        synchronized(lock) {
            if (_entries.value.any { it.id == edition.id }) return true
            val name = "%013d-%s.json".format(edition.made.coerceAtLeast(0), edition.id.filter { it.isLetterOrDigit() }.take(12))
            val (paper, pics) = keepPictures(edition, name)
            write(File(dir, name), Editions.encodeSaved(SavedEdition(paper, received = System.currentTimeMillis(), pics = pics)))
            prune()
            refresh()
        }
        return true
    }

    /**
     * Each news story's lead picture, decoded and drawn again as a file beside the paper; the paper
     * then keeps only the file's name, by story key. A picture that won't decode is left out.
     */
    private fun keepPictures(e: EditionOut, paperFile: String): Pair<EditionOut, Map<String, String>> {
        val pics = LinkedHashMap<String, String>()
        var n = 0
        fun strip(st: EdStory): EdStory {
            if (st.pic.isBlank()) return st
            val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(st.pic) }.getOrNull()
            if (bytes != null && n < MAX_PICS) {
                val kept = runCatching { pictures.keep(StoryPictures.nameFor(paperFile, n), bytes) }
                    .onFailure { Log.w(TAG, "Couldn't keep a story's picture", it) }.getOrNull()
                if (kept != null) {
                    pics[Editions.storyKey(st)] = kept
                    n++
                }
            }
            return st.copy(pic = "")
        }
        val paper = e.copy(top = e.top.map(::strip), sections = e.sections.map { sec -> sec.copy(stories = sec.stories.map(::strip)) })
        return paper to pics
    }

    /** A news story in a kept paper by its link, newest paper first: the paper's file and the story. */
    suspend fun findNews(url: String): Pair<String, EdStory>? = withContext(Dispatchers.IO) {
        loadBlocking()
        for (f in paperFiles()) {
            val s = (if (f.name == latestName) _latest.value else null) ?: read(f) ?: continue
            Editions.findNews(s.edition, url)?.let { return@withContext f.name to it }
        }
        null
    }

    suspend fun paper(file: String): SavedEdition? = withContext(Dispatchers.IO) {
        loadBlocking()
        val f = File(dir, file)
        if (f.parentFile != dir || !f.isFile) null else read(f)
    }

    private fun change(id: String, edit: (SavedEdition) -> SavedEdition): SavedEdition? {
        synchronized(lock) {
            val entry = _entries.value.firstOrNull { it.id == id } ?: return null
            val f = File(dir, entry.file)
            val saved = read(f) ?: return null
            val next = edit(saved)
            write(f, Editions.encodeSaved(next))
            refresh()
            return next
        }
    }

    /** Ticks a story as read (or not). */
    suspend fun setRead(id: String, key: String, read: Boolean): SavedEdition? = withContext(Dispatchers.IO) {
        change(id) { s -> s.copy(read = if (read) (s.read + key).distinct() else s.read - key) }
    }

    /** Ticks a whole section read (or not) in one write. */
    suspend fun setReadMany(id: String, keys: List<String>, read: Boolean): SavedEdition? = withContext(Dispatchers.IO) {
        change(id) { s -> s.copy(read = if (read) (s.read + keys).distinct() else s.read - keys.toSet()) }
    }

    suspend fun delete(file: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val f = File(dir, file)
            if (f.parentFile == dir && f.name != setupFile.name) f.delete()
            pictures.prune(paperFiles().map { StoryPictures.base(it.name) }.toSet())
            refresh()
        }
    }

    // --- the desk ---

    fun askedDesk(editionId: String, personaId: String) {
        _pendingDesk.value = editionId to personaId
    }

    /** Keeps a desk persona's reply on its edition, replacing that persona's earlier notes. */
    suspend fun keepDesk(text: String): SavedEdition? = withContext(Dispatchers.IO) {
        val (id, persona) = _pendingDesk.value ?: return@withContext null
        val clean = text.trim().take(MAX_NOTE)
        if (clean.isEmpty()) return@withContext null
        change(id) { s -> s.copy(desk = s.desk.filterNot { it.personaId == persona } + DeskNote(persona, clean, System.currentTimeMillis())) }
    }

    suspend fun dropDesk(id: String, personaId: String) = withContext(Dispatchers.IO) {
        change(id) { s -> s.copy(desk = s.desk.filterNot { it.personaId == personaId }) }
    }

    // --- settings ---

    suspend fun saveSetup(edit: (EditionSetup) -> EditionSetup) = withContext(Dispatchers.IO) {
        loadBlocking()
        synchronized(lock) {
            val next = edit(_setup.value)
            write(setupFile, Editions.encodeSetup(next))
            _setup.value = next
        }
    }

    /** The Lobster took the plan (its "edition-plan" note, or an edition answering the order). Blocking. */
    fun acceptedBlocking(at: Long) {
        loadBlocking()
        synchronized(lock) {
            val next = _setup.value.copy(acceptedAt = maxOf(at, _setup.value.acceptedAt))
            write(setupFile, Editions.encodeSetup(next))
            _setup.value = next
        }
    }

    /** For backups: the settings only. */
    suspend fun backupText(): String? = withContext(Dispatchers.IO) {
        loadBlocking()
        if (setupFile.exists()) setupFile.readText() else null
    }

    suspend fun mergeBackup(text: String) = withContext(Dispatchers.IO) {
        val restored = runCatching { Editions.parseSetup(text) }.getOrNull() ?: return@withContext
        loadBlocking()
        synchronized(lock) {
            // A restored plan hasn't been sent from this tablet yet.
            val next = restored.copy(sentAt = 0, acceptedAt = 0, changedAt = System.currentTimeMillis())
            write(setupFile, Editions.encodeSetup(next))
            _setup.value = next
        }
    }

    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save ${file.name}" }
        }
    }

    companion object {
        private const val TAG = "EditionStore"
        private const val KEEP_DAYS = 30
        private const val MAX_KEPT = 60
        private const val MAX_NOTE = 2_000
        private const val MAX_PICS = 60
        /** How many of the newest papers are tried for the page before it gives up. */
        private const val LATEST_TRIES = 5
    }
}
