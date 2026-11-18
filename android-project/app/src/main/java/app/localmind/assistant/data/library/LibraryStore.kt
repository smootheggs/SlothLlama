package app.localmind.assistant.data.library

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.dictionary.ChineseDictionary
import app.localmind.assistant.data.tools.Note
import app.localmind.assistant.data.tools.NotesStore
import app.localmind.assistant.domain.attachments.Attachments
import app.localmind.assistant.domain.attachments.LibraryPassage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.ln

/** A book, document or Wikipedia file in the library. */
data class LibraryItem(
    val id: Long,
    val name: String,
    val kind: LibraryKind,
    /** "312 pages · 1,204 passages", "6,4 M articles · en · full-text search" … */
    val detail: String,
    val addedAt: Long,
    /** Switched off: kept, but not searched. */
    val enabled: Boolean
)

enum class LibraryKind { DOCUMENT, WIKI }

/**
 * The private library: documents (PDF, EPUB, HTML, text) indexed on the tablet, and Kiwix files
 * (Wikipedia and others) read in place. [search] finds the passages that best match a question;
 * the chat sends them along with it. Nothing leaves the device.
 *
 * Ranking is BM25 over the full-text index's match statistics, so rare words count for more
 * than common ones and long passages don't win just by being long.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachments: AttachmentStore,
    private val dictionary: ChineseDictionary,
    private val kiwix: KiwixShelf,
    private val notes: NotesStore
) {
    private val db by lazy { LibraryDb(context) }
    /** One writer at a time (adding a 500-page book takes a few seconds). */
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _items = MutableStateFlow<List<LibraryItem>?>(null)
    /** Everything in the library, newest first (null until first read). */
    val items: StateFlow<List<LibraryItem>?> = _items.asStateFlow()

    private val _working = MutableStateFlow<String?>(null)
    /** What is being added right now, for the library screen. */
    val working: StateFlow<String?> = _working.asStateFlow()

    suspend fun refresh() = withContext(io) { _items.value = readItems() }

    /** Reads a document in full, cuts it into passages and indexes them. */
    suspend fun addDocument(uri: Uri): LibraryItem = withContext(io) {
        val name = displayName(uri)
        _working.value = "Reading $name…"
        try {
            val doc = attachments.extractText(uri)
            index(doc.name, doc.text, doc.pages)
        } finally {
            _working.value = null
        }
    }

    /** Adds text that isn't a file: something shared from another app, or the Lobster's catch. */
    suspend fun addText(title: String, text: String): LibraryItem = withContext(io) {
        val name = title.trim().ifEmpty { "Untitled" }.take(120)
        try {
            index(name, text, 0)
        } finally {
            _working.value = null
        }
    }

    private suspend fun index(docName: String, text: String, pages: Int): LibraryItem {
        _working.value = "Indexing $docName…"
        val chunks = Chunker.chunks(text)
        check(chunks.isNotEmpty()) { "$docName has no text to index." }
        val titleTokens = SearchTokens.forIndex(dictionary.toSimplified(docName.substringBeforeLast('.')))
        val tokenLines = chunks.map { c ->
            (titleTokens + SearchTokens.forIndex(dictionary.toSimplified(c.heading + "\n" + c.text))).joinToString(" ")
        }
        val detail = buildList {
            if (pages > 0) add("%,d pages".format(pages))
            add("%,d passages".format(chunks.size))
            if (pages > AttachmentStore.MAX_LIBRARY_PDF_PAGES) add("first %,d pages".format(AttachmentStore.MAX_LIBRARY_PDF_PAGES))
        }.joinToString(" · ")
        val w = db.writableDatabase
        w.beginTransaction()
        val id = try {
            val newId = w.insertOrThrow("documents", null, ContentValues().apply {
                put("name", docName)
                put("kind", LibraryKind.DOCUMENT.name)
                put("detail", detail)
                put("chars", text.length)
                put("chunks", chunks.size)
                put("added_at", System.currentTimeMillis())
            })
            val insertChunk = w.compileStatement("INSERT INTO chunks (doc_id, ord, heading, text) VALUES (?, ?, ?, ?)")
            val insertTokens = w.compileStatement("INSERT INTO chunks_fts (docid, tokens) VALUES (?, ?)")
            chunks.forEachIndexed { i, c ->
                insertChunk.clearBindings()
                insertChunk.bindLong(1, newId)
                insertChunk.bindLong(2, i.toLong())
                insertChunk.bindString(3, c.heading)
                insertChunk.bindString(4, c.text)
                val chunkId = insertChunk.executeInsert()
                insertTokens.clearBindings()
                insertTokens.bindLong(1, chunkId)
                insertTokens.bindString(2, tokenLines[i])
                insertTokens.executeInsert()
            }
            w.setTransactionSuccessful()
            newId
        } finally {
            w.endTransaction()
        }
        _items.value = readItems()
        return _items.value.orEmpty().first { it.id == id }
    }

    /** Adds a Kiwix .zim file (read in place: the app keeps permission to read it). */
    suspend fun addWiki(uri: Uri): LibraryItem = withContext(io) {
        val fileName = displayName(uri)
        _working.value = "Opening $fileName…"
        try {
            check(fileName.endsWith(".zim", ignoreCase = true)) { "$fileName isn't a Kiwix .zim file." }
            val existing = db.readableDatabase.rawQuery("SELECT id FROM documents WHERE uri = ?", arrayOf(uri.toString())).use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
            check(existing == null) { "$fileName is already in the library." }
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { Log.w(TAG, "No lasting permission for $fileName", it) }
            val info = try {
                kiwix.describe(uri, fileName)
            } catch (e: Throwable) {
                releasePermission(uri)
                throw e
            }
            val id = db.writableDatabase.insertOrThrow("documents", null, ContentValues().apply {
                put("name", info.title)
                put("kind", LibraryKind.WIKI.name)
                put("uri", uri.toString())
                put("detail", "$fileName · ${info.detail}")
                put("added_at", System.currentTimeMillis())
            })
            _items.value = readItems()
            _items.value.orEmpty().first { it.id == id }
        } finally {
            _working.value = null
        }
    }

    suspend fun remove(id: Long) = withContext(io) {
        val uri = uriOf(id)
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.execSQL("DELETE FROM chunks_fts WHERE docid IN (SELECT id FROM chunks WHERE doc_id = ?)", arrayOf(id))
            w.execSQL("DELETE FROM chunks WHERE doc_id = ?", arrayOf(id))
            w.execSQL("DELETE FROM documents WHERE id = ?", arrayOf(id))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        if (uri != null) {
            val parsed = Uri.parse(uri)
            kiwix.close(parsed)
            releasePermission(parsed)
        }
        _items.value = readItems()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(io) {
        db.writableDatabase.execSQL("UPDATE documents SET enabled = ? WHERE id = ?", arrayOf(if (enabled) 1 else 0, id))
        _items.value = readItems()
    }

    /** True when anything is switched on to be searched. */
    suspend fun hasAnything(): Boolean = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery("SELECT 1 FROM documents WHERE enabled = 1 LIMIT 1", null).use { it.moveToFirst() }
    }

    /**
     * The passages that best answer [question], at most [limit] and about [maxTokens] in all:
     * your own notes first (up to two that clearly match), then documents and Wikipedia files
     * taking turns, best first.
     */
    suspend fun search(question: String, limit: Int = 4, maxTokens: Int = 1_500): List<LibraryPassage> {
        val q = question.trim()
        if (q.isEmpty()) return emptyList()
        val fromNotes = runCatching { searchNotes(q) }.getOrDefault(emptyList())
        val local = runCatching { searchDocuments(q, limit) }
            .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "Library search failed", it) }
            .getOrDefault(emptyList())
        val wikis = withContext(Dispatchers.IO) {
            db.readableDatabase.rawQuery(
                "SELECT name, uri FROM documents WHERE kind = ? AND enabled = 1 AND uri IS NOT NULL ORDER BY added_at",
                arrayOf(LibraryKind.WIKI.name)
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
        }
        val wiki = wikis.flatMap { (name, uri) -> kiwix.search(Uri.parse(uri), name, q, maxArticles = if (local.isEmpty()) 2 else 1) }
        val merged = fromNotes.toMutableList()
        val a = local.iterator()
        val b = wiki.iterator()
        while ((a.hasNext() || b.hasNext()) && merged.size < limit) {
            if (a.hasNext()) merged += a.next()
            if (b.hasNext() && merged.size < limit) merged += b.next()
        }
        var used = 0
        return merged.takeWhile { p ->
            used += Attachments.estimateTokens(p.text)
            used <= maxTokens || p === merged.first()
        }
    }

    /** Notes sharing at least half the question's words, best first (at most two). */
    private suspend fun searchNotes(question: String): List<LibraryPassage> {
        val all = notes.notes.value
        if (all.isEmpty()) return emptyList()
        val terms = SearchTokens.forQuery(dictionary.toSimplified(question)).toSet()
        if (terms.isEmpty()) return emptyList()
        val needed = maxOf(1, ceil(terms.size * 0.5).toInt())
        val dates = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        return withContext(Dispatchers.Default) {
            all.mapNotNull { n ->
                val words = SearchTokens.forIndex(dictionary.toSimplified(n.text)).toSet()
                val hits = terms.count { it in words }
                if (hits >= needed) n to hits else null
            }
                .sortedWith(compareByDescending<Pair<Note, Int>> { it.second }.thenByDescending { it.first.createdAt })
                .take(2)
                .map { (n, _) -> LibraryPassage(title = "Your note", where = dates.format(java.util.Date(n.createdAt)), text = n.text.take(1_500)) }
        }
    }

    /** Test search for the library screen: what a question would bring along. */
    suspend fun preview(question: String): List<LibraryPassage> = search(question)

    private suspend fun searchDocuments(question: String, limit: Int): List<LibraryPassage> {
        val terms = SearchTokens.forQuery(dictionary.toSimplified(question)).take(MAX_QUERY_TERMS)
        if (terms.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val match = terms.joinToString(" OR ") { "\"$it\"" }
            val needed = maxOf(1, ceil(terms.size * MIN_TERM_SHARE).toInt())
            val scored = ArrayList<Triple<Long, Long, Double>>()
            db.readableDatabase.rawQuery(
                "SELECT chunks_fts.docid, matchinfo(chunks_fts, 'pcnalx'), c.doc_id FROM chunks_fts " +
                    "JOIN chunks c ON c.id = chunks_fts.docid JOIN documents d ON d.id = c.doc_id " +
                    "WHERE chunks_fts MATCH ? AND d.enabled = 1 LIMIT $MAX_CANDIDATES",
                arrayOf(match)
            ).use { c ->
                while (c.moveToNext()) {
                    val (score, matched) = bm25(c.getBlob(1))
                    if (matched >= needed) scored += Triple(c.getLong(0), c.getLong(2), score)
                }
            }
            scored.sortByDescending { it.third }
            val perDoc = HashMap<Long, Int>()
            val best = scored.filter { (_, doc, _) ->
                val n = perDoc.getOrDefault(doc, 0)
                if (n >= MAX_PER_DOCUMENT) false else { perDoc[doc] = n + 1; true }
            }.take(limit)
            best.mapNotNull { (chunkId, _, _) -> passage(chunkId) }
        }
    }

    /**
     * BM25 for one row from matchinfo 'pcnalx' (32-bit native-order ints: phrases, columns,
     * rows, average length, this row's length, then per phrase: hits here, hits everywhere,
     * rows with a hit). Also returns how many of the query's words the row has.
     */
    private fun bm25(blob: ByteArray): Pair<Double, Int> {
        val ints = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder()).asIntBuffer()
        val phrases = ints.get(0)
        val rows = ints.get(2).toDouble().coerceAtLeast(1.0)
        val avg = ints.get(3).toDouble().coerceAtLeast(1.0)
        val length = ints.get(4).toDouble()
        var score = 0.0
        var matched = 0
        for (i in 0 until phrases) {
            val hits = ints.get(5 + 3 * i).toDouble()
            if (hits <= 0) continue
            matched++
            val docs = ints.get(5 + 3 * i + 2).toDouble()
            val idf = ln((rows - docs + 0.5) / (docs + 0.5) + 1.0)
            score += idf * hits * (K1 + 1) / (hits + K1 * (1 - B + B * length / avg))
        }
        return score to matched
    }

    private fun passage(chunkId: Long): LibraryPassage? =
        db.readableDatabase.rawQuery(
            "SELECT d.name, c.heading, c.text FROM chunks c JOIN documents d ON d.id = c.doc_id WHERE c.id = ?",
            arrayOf(chunkId.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else LibraryPassage(title = c.getString(0), where = c.getString(1), text = c.getString(2))
        }

    private fun readItems(): List<LibraryItem> =
        db.readableDatabase.rawQuery(
            "SELECT id, name, kind, detail, added_at, enabled FROM documents ORDER BY added_at DESC", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LibraryItem(
                            id = c.getLong(0),
                            name = c.getString(1),
                            kind = runCatching { LibraryKind.valueOf(c.getString(2)) }.getOrDefault(LibraryKind.DOCUMENT),
                            detail = c.getString(3),
                            addedAt = c.getLong(4),
                            enabled = c.getInt(5) != 0
                        )
                    )
                }
            }
        }

    private fun uriOf(id: Long): String? =
        db.readableDatabase.rawQuery("SELECT uri FROM documents WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }

    private fun releasePermission(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"

    private companion object {
        const val TAG = "LibraryStore"
        const val K1 = 1.2
        const val B = 0.75
        const val MAX_QUERY_TERMS = 24
        /** A passage must have at least this share of the question's words. */
        const val MIN_TERM_SHARE = 0.4
        const val MAX_CANDIDATES = 4_000
        const val MAX_PER_DOCUMENT = 3
    }
}
