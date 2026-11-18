package app.localmind.assistant.data.library

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.localmind.assistant.data.attachments.EpubText
import app.localmind.assistant.domain.attachments.Attachments
import app.localmind.assistant.domain.attachments.LibraryPassage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.kiwix.libkiwix.JNIKiwix
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Query
import org.kiwix.libzim.Searcher
import org.kiwix.libzim.SuggestionSearcher
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wikipedia (and any other Kiwix .zim file) read in place, offline, with libkiwix. The file stays
 * where it is (picked with the system file picker, which lets the app keep reading it); nothing
 * is copied. libzim is used from one thread only.
 */
@Singleton
class KiwixShelf @Inject constructor(@ApplicationContext private val context: Context) {

    class Info(val title: String, val detail: String, val articles: Int)

    private class Opened(val pfd: ParcelFileDescriptor, val archive: Archive) {
        var searcher: Searcher? = null
        var suggester: SuggestionSearcher? = null
    }

    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "kiwix").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private var natives: Boolean? = null
    private val opened = HashMap<String, Opened>()

    /** Opens [uri] to check it is a ZIM file and read its title and size. */
    suspend fun describe(uri: Uri, fileName: String): Info = withContext(thread) {
        val zim = openZim(uri)
        val a = zim.archive
        val title = meta(a, "Title")?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
        val language = runCatching { a.mainLanguage }.getOrNull()?.takeIf { it.isNotBlank() }
        val articles = runCatching { a.articleCount }.getOrDefault(0)
        val search = if (runCatching { a.hasFulltextIndex() }.getOrDefault(false)) "full-text search" else "title search"
        val detail = listOfNotNull("%,d articles".format(articles), language, search).joinToString(" · ")
        Info(title, detail, articles)
    }

    /**
     * Up to [maxArticles] articles for [query]: titles matching its key words first (the most
     * reliable), then full-text hits. Each gives its opening and the paragraphs with the most
     * query words, about [tokensPerArticle] tokens in all.
     */
    suspend fun search(
        uri: Uri,
        shelfName: String,
        query: String,
        maxArticles: Int = 2,
        tokensPerArticle: Int = 420
    ): List<LibraryPassage> = withContext(thread) {
        val keywords = SearchTokens.keywords(query)
        if (keywords.isEmpty()) return@withContext emptyList()
        val zim = try {
            openZim(uri)
        } catch (e: Throwable) {
            Log.w(TAG, "Can't open $shelfName", e)
            return@withContext emptyList()
        }
        val paths = LinkedHashMap<String, String>()
        val phrase = keywords.joinToString(" ")
        suggestions(zim, phrase, paths, maxArticles)
        if (paths.size < maxArticles) fullText(zim, phrase, paths, maxArticles)
        if (paths.isEmpty() && keywords.size > 1) {
            for (k in keywords.take(2)) suggestions(zim, k, paths, maxArticles)
        }
        val terms = SearchTokens.forQuery(query).toSet()
        paths.entries.take(maxArticles).mapNotNull { (path, title) ->
            runCatching { passage(zim.archive, path, title, shelfName, terms, tokensPerArticle) }
                .onFailure { Log.w(TAG, "Couldn't read $path", it) }
                .getOrNull()
        }
    }

    /** Forgets an open file (it was removed from the library). */
    suspend fun close(uri: Uri) = withContext(thread) {
        opened.remove(uri.toString())?.let { z ->
            runCatching { z.searcher?.dispose() }
            runCatching { z.suggester?.dispose() }
            runCatching { z.archive.dispose() }
            runCatching { z.pfd.close() }
        }
        Unit
    }

    private fun openZim(uri: Uri): Opened {
        opened[uri.toString()]?.let { return it }
        if (natives == null) {
            natives = try {
                JNIKiwix(context)
                true
            } catch (e: Throwable) {
                Log.e(TAG, "libkiwix didn't load", e)
                false
            }
        }
        check(natives == true) { "Wikipedia files can't be read on this device (the Kiwix library didn't load)." }
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Can't open that file.")
        val archive = try {
            Archive(pfd.fileDescriptor)
        } catch (e: Throwable) {
            runCatching { pfd.close() }
            Log.w(TAG, "Not a ZIM file", e)
            error("That isn't a Kiwix .zim file, or it is damaged.")
        }
        return Opened(pfd, archive).also { opened[uri.toString()] = it }
    }

    private fun meta(a: Archive, name: String): String? = runCatching { a.getMetadata(name) }.getOrNull()

    private fun suggestions(zim: Opened, text: String, into: MutableMap<String, String>, max: Int) {
        if (!runCatching { zim.archive.hasTitleIndex() }.getOrDefault(false)) {
            // Older files: an exact title is still found.
            runCatching { zim.archive.getEntryByTitle(text) }.getOrNull()?.let { into.putIfAbsent(it.path, it.title) }
            return
        }
        runCatching {
            val searcher = zim.suggester ?: SuggestionSearcher(zim.archive).also { zim.suggester = it }
            val search = searcher.suggest(text)
            val results = search.getResults(0, max + 2)
            while (results.hasNext() && into.size < max) {
                val item = results.next()
                into.putIfAbsent(item.path, item.title)
            }
            runCatching { results.dispose() }
            runCatching { search.dispose() }
        }.onFailure { Log.w(TAG, "Title search failed", it) }
    }

    private fun fullText(zim: Opened, text: String, into: MutableMap<String, String>, max: Int) {
        if (!runCatching { zim.archive.hasFulltextIndex() }.getOrDefault(false)) return
        runCatching {
            val searcher = zim.searcher ?: Searcher(zim.archive).also { zim.searcher = it }
            val query = Query(text)
            val search = searcher.search(query)
            val results = search.getResults(0, max + 2)
            while (results.hasNext() && into.size < max) {
                val entry = results.next()
                into.putIfAbsent(entry.path, entry.title)
            }
            runCatching { results.dispose() }
            runCatching { search.dispose() }
            runCatching { query.dispose() }
        }.onFailure { Log.w(TAG, "Full-text search failed", it) }
    }

    /** The article's opening plus its paragraphs with the most query words, in page order. */
    private fun passage(
        a: Archive,
        path: String,
        title: String,
        shelfName: String,
        terms: Set<String>,
        budget: Int
    ): LibraryPassage? {
        val item = a.getEntryByPath(path).getItem(true)
        if (!item.mimetype.startsWith("text/html")) return null
        val blob = item.data
        val html = String(blob.data, Charsets.UTF_8)
        runCatching { blob.dispose() }
        val paragraphs = EpubText.htmlToText(html).split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("# ") }
        if (paragraphs.isEmpty()) return null
        val scored = paragraphs.mapIndexed { i, p ->
            val hits = SearchTokens.forIndex(p).count { it in terms }
            Triple(i, p, hits)
        }
        val chosen = sortedSetOf(0)
        var used = Attachments.estimateTokens(paragraphs[0])
        for ((i, p, _) in scored.drop(1).filter { it.third > 0 }.sortedByDescending { it.third }) {
            val n = Attachments.estimateTokens(p)
            if (used + n > budget) continue
            chosen += i
            used += n
            if (used > budget * 0.85) break
        }
        val text = chosen.joinToString("\n\n") { paragraphs[it] }.let {
            // A very long first paragraph on its own.
            if (Attachments.estimateTokens(it) > budget * 1.5) it.take(budget * 4) + "…" else it
        }
        return LibraryPassage(title = title.ifBlank { path }, where = shelfName, text = text)
    }

    private companion object {
        const val TAG = "KiwixShelf"
    }
}
