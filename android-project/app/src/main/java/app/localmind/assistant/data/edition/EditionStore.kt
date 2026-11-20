package app.localmind.assistant.data.edition

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.edition.DeskNote
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

    /** A desk persona asked for notes from a chat: (edition id, persona id), until the notes are kept. */
    private val _pendingDesk = MutableStateFlow<Pair<String, String>?>(null)
    val pendingDesk: StateFlow<Pair<String, String>?> = _pendingDesk.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { loadBlocking() }

    private fun loadBlocking() {
        synchronized(lock) {
            if (loaded) return
            _setup.value = if (!setupFile.exists()) EditionSetup() else runCatching { Editions.parseSetup(setupFile.readText()) }.getOrElse {
                runCatching { setupFile.copyTo(File(dir, "setup.unreadable.json"), overwrite = true) }
                EditionSetup()
            }
            prune()
            refresh()
            loaded = true
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
        val latest = files.firstOrNull()?.let(::read)
        val list = files.mapIndexedNotNull { i, f ->
            val stamp = f.lastModified()
            cache[f.name]?.takeIf { it.first == stamp }?.second
                ?: (if (i == 0) latest else read(f))?.let { s -> entryOf(f, s).also { cache[f.name] = stamp to it } }
        }
        cache.keys.retainAll(files.map { it.name }.toSet())
        _entries.value = list
        _latest.value = latest
    }

    /** Papers made over 30 days ago go (by the time in their name), and never more than 60 are kept. */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
        paperFiles().forEachIndexed { i, f ->
            val made = f.name.take(13).toLongOrNull() ?: f.lastModified()
            if (i >= MAX_KEPT || made < cutoff) f.delete()
        }
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
            write(File(dir, name), Editions.encodeSaved(SavedEdition(edition, received = System.currentTimeMillis())))
            prune()
            refresh()
        }
        return true
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
    }
}
