package app.localmind.assistant.data.core

import android.content.Context
import app.localmind.assistant.domain.core.CoreLanguage
import app.localmind.assistant.domain.core.CoreMark
import app.localmind.assistant.domain.core.CoreWord
import app.localmind.assistant.domain.core.CoreWords
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One language's list, loaded, with its search index. */
class LoadedCoreList(val language: CoreLanguage, val words: List<CoreWord>, val index: Map<String, List<Int>>)

/**
 * The Core words lists (read from the app's own assets, nothing downloaded) and your marks on
 * them: new, learning, known. Marks live in the "core_words" settings file, so they're in backups.
 */
@Singleton
class CoreWordsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val lists = HashMap<CoreLanguage, LoadedCoreList>()
    private var marksRead = false
    private val lock = Mutex()
    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    private val _marks = MutableStateFlow<Map<String, CoreMark>>(emptyMap())
    /** Every mark, keyed "<language>:<word key>"; words without one are new. */
    val marks: StateFlow<Map<String, CoreMark>> = _marks.asStateFlow()

    suspend fun load(language: CoreLanguage): LoadedCoreList = lock.withLock {
        lists[language]?.let { return it }
        withContext(Dispatchers.IO) {
            if (!marksRead) {
                _marks.value = readMarks()
                marksRead = true
            }
            val text = context.assets.open("core/${language.code}.json").bufferedReader().use { it.readText() }
            val words = CoreWords.parse(text).words.sortedBy { it.rank }
            LoadedCoreList(language, words, CoreWords.index(language, words)).also { lists[language] = it }
        }
    }

    fun markOf(language: CoreLanguage, word: CoreWord): CoreMark = _marks.value[markKey(language, word)] ?: CoreMark.NEW

    fun setMark(language: CoreLanguage, word: CoreWord, mark: CoreMark) {
        val key = markKey(language, word)
        val next = _marks.value.toMutableMap()
        if (mark == CoreMark.NEW) next.remove(key) else next[key] = mark
        _marks.value = next
        prefs.edit().apply { if (mark == CoreMark.NEW) remove(key) else putInt(key, mark.code) }.apply()
    }

    /** The language last open, so the page comes back to it. */
    var lastLanguage: CoreLanguage
        get() = CoreLanguage.fromCode(prefs.getString(LAST, null) ?: CoreLanguage.MANDARIN.code)
        set(value) = prefs.edit().putString(LAST, value.code).apply()

    private fun readMarks(): Map<String, CoreMark> =
        prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to CoreMark.fromCode(it) } }.toMap()

    companion object {
        const val FILE = "core_words"
        private const val LAST = "_language"
        fun markKey(language: CoreLanguage, word: CoreWord) = language.code + ":" + word.key
    }
}
