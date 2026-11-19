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
import app.localmind.assistant.data.meaning.ChatBusy
import app.localmind.assistant.data.meaning.MeaningModel
import app.localmind.assistant.data.meaning.MeaningModelState
import app.localmind.assistant.data.meaning.Vectors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

/** Search by meaning's reading of the library: [done] of [total] passages have a vector. */
data class MeaningIndex(val done: Int = 0, val total: Int = 0, val running: Boolean = false)

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
    private val notes: NotesStore,
    private val meaning: MeaningModel
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

    // --- Search by meaning -------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var indexJob: Job? = null

    private val _meaningIndex = MutableStateFlow(MeaningIndex())
    /** How far the library has been read for meaning. */
    val meaningIndex: StateFlow<MeaningIndex> = _meaningIndex.asStateFlow()

    /** Vectors held in memory for searching, for one model; null when they need reading again. */
    private class VectorCache(val model: String, val chunks: LongArray, val docs: LongArray, val dims: Int, val data: FloatArray)
    @Volatile private var cache: VectorCache? = null

    /**
     * Gives every document passage a vector, in the background, a few at a time, pausing while a
     * reply is being written. Safe to call often: it does nothing when already running or done.
     */
    @Synchronized
    fun startMeaningIndex() {
        if (indexJob?.isActive == true) return
        indexJob = scope.launch {
            try {
                indexMeaning()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Reading for meaning stopped", e)
            } finally {
                _meaningIndex.value = _meaningIndex.value.copy(running = false)
            }
        }
    }

    /** After the meaning model changes or is removed. */
    suspend fun meaningModelChanged() {
        indexJob?.cancel()
        withContext(io) {
            val model = meaning.readyId
            if (model == null) db.writableDatabase.execSQL("DELETE FROM vectors")
            else db.writableDatabase.execSQL("DELETE FROM vectors WHERE model != ?", arrayOf(model))
            cache = null
            _meaningIndex.value = counts(model)
        }
        startMeaningIndex()
    }

    private suspend fun indexMeaning() {
        val model = meaning.readyId ?: run {
            _meaningIndex.value = withContext(io) { counts(null) }
            return
        }
        withContext(io) {
            db.writableDatabase.execSQL("DELETE FROM vectors WHERE model != ?", arrayOf(model))
            _meaningIndex.value = counts(model).copy(running = true)
        }
        while (true) {
            if (meaning.readyId != model) return
            ChatBusy.busy.first { !it }
            val batch = withContext(io) {
                db.readableDatabase.rawQuery(
                    "SELECT c.id, d.name, c.heading, c.text FROM chunks c JOIN documents d ON d.id = c.doc_id " +
                        "WHERE d.kind = ? AND NOT EXISTS (SELECT 1 FROM vectors v WHERE v.chunk_id = c.id) " +
                        "ORDER BY d.enabled DESC, c.id LIMIT $INDEX_BATCH",
                    arrayOf(LibraryKind.DOCUMENT.name)
                ).use { c ->
                    buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2) + "\n" + c.getString(3))) }
                }
            }
            if (batch.isEmpty()) break
            val made = batch.mapNotNull { (id, name, text) ->
                meaning.embedDocument(name.substringBeforeLast('.'), text)?.let { id to it }
            }
            if (made.isEmpty()) {
                Log.w(TAG, "The meaning model gave no vectors; stopping for now")
                break
            }
            withContext(io) {
                val w = db.writableDatabase
                w.beginTransaction()
                try {
                    val insert = w.compileStatement("INSERT OR REPLACE INTO vectors (chunk_id, model, vec) VALUES (?, ?, ?)")
                    made.forEach { (id, v) ->
                        insert.clearBindings()
                        insert.bindLong(1, id)
                        insert.bindString(2, model)
                        insert.bindBlob(3, Vectors.toBytes(v))
                        insert.executeInsert()
                    }
                    w.setTransactionSuccessful()
                } finally {
                    w.endTransaction()
                }
                cache = null
                _meaningIndex.value = counts(model).copy(running = true)
            }
        }
        _meaningIndex.value = withContext(io) { counts(model) }
    }

    private fun counts(model: String?): MeaningIndex {
        val r = db.readableDatabase
        val total = r.rawQuery(
            "SELECT COUNT(*) FROM chunks c JOIN documents d ON d.id = c.doc_id WHERE d.kind = ?",
            arrayOf(LibraryKind.DOCUMENT.name)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val done = if (model == null) 0 else r.rawQuery("SELECT COUNT(*) FROM vectors WHERE model = ?", arrayOf(model))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return MeaningIndex(done = minOf(done, total), total = total)
    }

    /** Refreshes the counts shown in the Library (cheap). */
    suspend fun refreshMeaningIndex() = withContext(io) {
        _meaningIndex.value = counts(meaning.readyId).copy(running = indexJob?.isActive == true)
    }

    private fun vectorCache(model: String): VectorCache? {
        cache?.takeIf { it.model == model }?.let { return it }
        val chunks = ArrayList<Long>()
        val docs = ArrayList<Long>()
        var dims = 0
        var data = FloatArray(0)
        db.readableDatabase.rawQuery(
            "SELECT v.chunk_id, c.doc_id, v.vec FROM vectors v JOIN chunks c ON c.id = v.chunk_id WHERE v.model = ?",
            arrayOf(model)
        ).use { c ->
            while (c.moveToNext()) {
                val v = Vectors.fromBytes(c.getBlob(2))
                if (dims == 0) {
                    dims = v.size
                    data = FloatArray(dims * maxOf(c.count, 1))
                }
                if (v.size != dims) continue
                System.arraycopy(v, 0, data, chunks.size * dims, dims)
                chunks += c.getLong(0)
                docs += c.getLong(1)
            }
        }
        if (chunks.isEmpty()) return null
        return VectorCache(model, chunks.toLongArray(), docs.toLongArray(), dims, data).also { cache = it }
    }

    /** The passages closest in meaning to [question] among enabled documents: (chunk id, similarity). */
    private suspend fun searchByMeaning(question: String, enabledDocs: Set<Long>): List<Pair<Long, Float>> {
        val model = meaning.readyId ?: return emptyList()
        val vectors = withContext(Dispatchers.IO) { vectorCache(model) } ?: return emptyList()
        val q = meaning.embedQuery(question) ?: return emptyList()
        return withContext(Dispatchers.Default) {
            val scored = ArrayList<Pair<Long, Float>>()
            for (i in vectors.chunks.indices) {
                if (vectors.docs[i] !in enabledDocs) continue
                scored += vectors.chunks[i] to Vectors.similarity(q, vectors.data, i, vectors.dims)
            }
            scored.sortByDescending { it.second }
            scored.take(MEANING_CANDIDATES)
        }
    }

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
        startMeaningIndex()
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
            w.execSQL("DELETE FROM vectors WHERE chunk_id IN (SELECT id FROM chunks WHERE doc_id = ?)", arrayOf(id))
            w.execSQL("DELETE FROM chunks WHERE doc_id = ?", arrayOf(id))
            w.execSQL("DELETE FROM documents WHERE id = ?", arrayOf(id))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        cache = null
        _meaningIndex.value = counts(meaning.readyId).copy(running = indexJob?.isActive == true)
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
        // Catch up on anything not yet read for meaning (does nothing when there's nothing to do).
        if (meaning.state.value is MeaningModelState.Ready) startMeaningIndex()
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

    /**
     * Word search (BM25) merged with search by meaning when a meaning model is set up: a passage
     * that says the same thing in other words (or another language) can be found too.
     */
    private suspend fun searchDocuments(question: String, limit: Int): List<LibraryPassage> {
        val byWords = searchByWords(question)
        val enabled = withContext(Dispatchers.IO) {
            db.readableDatabase.rawQuery("SELECT id FROM documents WHERE enabled = 1 AND kind = ?", arrayOf(LibraryKind.DOCUMENT.name))
                .use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
        }
        val byMeaning = if (enabled.isEmpty() || meaning.state.value !is MeaningModelState.Ready) emptyList()
        else runCatching { searchByMeaning(question, enabled) }
            .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "Meaning search failed", it) }
            .getOrDefault(emptyList())
        return withContext(Dispatchers.IO) {
            val docOf = HashMap<Long, Long>()
            byWords.forEach { (chunk, doc) -> docOf[chunk] = doc }
            val ranked = if (byMeaning.isEmpty()) byWords.map { it.first }
            else Vectors.fuse(byWords.map { it.first }, byMeaning, MEANING_FLOOR)
            val perDoc = HashMap<Long, Int>()
            ranked.asSequence()
                .filter { chunk ->
                    val doc = docOf[chunk] ?: docOfChunk(chunk) ?: return@filter false
                    val n = perDoc.getOrDefault(doc, 0)
                    if (n >= MAX_PER_DOCUMENT) false else { perDoc[doc] = n + 1; true }
                }
                .take(limit)
                .mapNotNull { passage(it) }
                .toList()
        }
    }

    /** Passages with enough of the question's words, best first: (chunk id, document id). */
    private suspend fun searchByWords(question: String): List<Pair<Long, Long>> {
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
            scored.take(WORD_CANDIDATES).map { it.first to it.second }
        }
    }

    private fun docOfChunk(chunkId: Long): Long? =
        db.readableDatabase.rawQuery("SELECT doc_id FROM chunks WHERE id = ?", arrayOf(chunkId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

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

    /**
     * A document put back together from its passages, for the reader: (name, text), with each new
     * heading as a Markdown heading. Null for a Wikipedia file (read in place, not stored) or a
     * document that's gone. Stops at [maxChars] so a very long book can't fill the memory.
     */
    suspend fun fullText(id: Long, maxChars: Int = 2_000_000): Pair<String, String>? = withContext(io) {
        val r = db.readableDatabase
        val name = r.rawQuery("SELECT name, kind FROM documents WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst() || c.getString(1) == LibraryKind.WIKI.name) null else c.getString(0)
        } ?: return@withContext null
        val sb = StringBuilder()
        var heading = ""
        r.rawQuery("SELECT heading, text FROM chunks WHERE doc_id = ? ORDER BY ord", arrayOf(id.toString())).use { c ->
            while (c.moveToNext() && sb.length < maxChars) {
                val h = c.getString(0)
                if (h.isNotBlank() && h != heading) sb.append("## ").append(h).append("\n\n")
                heading = h
                sb.append(c.getString(1)).append("\n\n")
            }
        }
        if (sb.length >= maxChars) sb.append("\n\n(The rest of this book is in the library but too long to show here.)")
        name to sb.toString().trim()
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
        const val WORD_CANDIDATES = 60
        const val MEANING_CANDIDATES = 60
        /**
         * How close in meaning a passage must be when none of its words match the question
         * (cosine similarity with EmbeddingGemma; unrelated text scores well below this).
         */
        const val MEANING_FLOOR = 0.42f
        const val INDEX_BATCH = 16
    }
}
