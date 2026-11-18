package app.localmind.assistant.data.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.SpeechLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One CC-CEDICT entry. [pinyin] keeps CC-CEDICT's tone numbers; see [Pinyin.marked]. */
data class DictionaryEntry(
    val traditional: String,
    val simplified: String,
    val pinyin: String,
    val jyutping: String,
    val definitions: List<String>
) {
    /** The headword as the learner reads it: one script, or "简 / 繁" when both are wanted. */
    fun headword(script: ChineseScript, language: SpeechLanguage): String = when {
        traditional == simplified -> simplified
        script == ChineseScript.SIMPLIFIED -> simplified
        script == ChineseScript.TRADITIONAL -> traditional
        language == SpeechLanguage.CANTONESE -> "$traditional / $simplified"
        else -> "$simplified / $traditional"
    }

    fun reading(language: SpeechLanguage): String =
        if (language == SpeechLanguage.CANTONESE) jyutping else Pinyin.marked(pinyin)
}

/**
 * What a tap on a word found. [word] is the matched text. [entries] are its dictionary entries;
 * a Cantonese word that CC-CEDICT lacks has none, only [cantoneseReading], and then
 * [characters] explains it character by character.
 */
data class WordLookup(
    val word: String,
    val entries: List<DictionaryEntry>,
    val cantoneseReading: String? = null,
    val characters: List<DictionaryEntry> = emptyList()
)

/**
 * The offline Chinese dictionary: CC-CEDICT (with Jyutping added) in a read-only SQLite file that
 * CI builds into the APK's assets (scripts/fetch-dictionary.sh). On first use the file is copied
 * out of the APK into no-backup storage, once per dictionary version.
 * If a build has no dictionary, [isAvailable] is false and lookups return nothing.
 */
@Singleton
class ChineseDictionary @Inject constructor(@ApplicationContext private val context: Context) {

    private val mutex = Mutex()
    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var traditionalToSimplified: Map<Char, Char>? = null

    val isAvailable: Boolean by lazy {
        runCatching { context.assets.open("$ASSET_DIR/PINNED").close() }.isSuccess
    }

    /** Copies the dictionary out of the APK ahead of the first lookup (a second or so, once). */
    suspend fun warmUp() {
        database()
    }

    private suspend fun database(): SQLiteDatabase? {
        db?.let { return it }
        if (!isAvailable) return null
        return mutex.withLock {
            db ?: withContext(Dispatchers.IO) {
                runCatching { openLocked() }
                    .onFailure { Log.e(TAG, "Couldn't open the dictionary", it) }
                    .getOrNull()
            }
        }
    }

    private fun openLocked(): SQLiteDatabase {
        val version = context.assets.open("$ASSET_DIR/PINNED").bufferedReader().use { it.readText().trim() }
        val dir = File(context.noBackupFilesDir, "dictionary").apply { mkdirs() }
        val file = File(dir, "dictionary.db")
        val stamp = File(dir, "PINNED")
        if (!file.isFile || !stamp.isFile || stamp.readText().trim() != version) {
            val part = File(dir, "dictionary.db.part")
            context.assets.open("$ASSET_DIR/dictionary.db").use { input ->
                part.outputStream().use { input.copyTo(it, 1 shl 16) }
            }
            check(part.renameTo(file)) { "Couldn't move the dictionary into place" }
            stamp.writeText(version)
        }
        return SQLiteDatabase.openDatabase(
            file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).also { db = it }
    }

    /**
     * The longest dictionary word starting at [offset] in [text] (the way Pleco looks up a tap),
     * or null if the character there isn't Chinese or nothing matches.
     */
    suspend fun lookupAt(text: String, offset: Int): WordLookup? {
        if (offset !in text.indices || !isHan(text[offset])) return null
        val db = database() ?: return null
        return withContext(Dispatchers.IO) {
            var end = offset
            while (end < text.length && end - offset < MAX_WORD && isHan(text[end])) end++
            for (stop in end downTo offset + 1) {
                val word = text.substring(offset, stop)
                val entries = entriesFor(db, word)
                if (entries.isNotEmpty()) return@withContext WordLookup(word, entries)
                if (word.length >= 2) {
                    cantoneseReading(db, word)?.let { reading ->
                        val chars = word.flatMap { entriesFor(db, it.toString()).take(1) }
                        return@withContext WordLookup(word, emptyList(), reading, chars)
                    }
                }
            }
            null
        }
    }

    /** Looks up [word] exactly (traditional or simplified). */
    suspend fun lookup(word: String): WordLookup? {
        val db = database() ?: return null
        return withContext(Dispatchers.IO) {
            val entries = entriesFor(db, word)
            when {
                entries.isNotEmpty() -> WordLookup(word, entries)
                else -> cantoneseReading(db, word)?.let { reading ->
                    WordLookup(word, emptyList(), reading, word.flatMap { entriesFor(db, it.toString()).take(1) })
                }
            }
        }
    }

    /**
     * Search by characters (exact first, then words that start with them), by pinyin with or
     * without tones ("nihao", "ni3 hao3", "nǐ hǎo"), or by English.
     */
    suspend fun search(query: String, limit: Int = 60): List<DictionaryEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val db = database() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val results = LinkedHashMap<String, DictionaryEntry>()
            fun addAll(list: List<DictionaryEntry>) {
                for (e in list) if (results.size < limit) results.putIfAbsent(e.key(), e)
            }
            if (q.any(::isHan)) {
                addAll(entriesFor(db, q))
                addAll(query(db, "(simp GLOB ? OR trad GLOB ?) ORDER BY length(simp), rank DESC, id LIMIT $limit", "$q*", "$q*"))
            } else {
                val key = Pinyin.searchKey(q)
                if (key.isNotEmpty()) {
                    addAll(query(db, "pinyin_key = ? ORDER BY $COMMON_FIRST, rank DESC, id LIMIT $limit", key))
                    if (key.length >= 2) {
                        addAll(query(db, "pinyin_key GLOB ? ORDER BY length(pinyin_key), $COMMON_FIRST, rank DESC, id LIMIT $limit", "$key*"))
                    }
                }
                val english = q.lowercase()
                if (english.length >= 2) {
                    // A definition that is exactly the word ("eat", "to eat") first, everyday words
                    // first among those, then any other mention.
                    val defs = "(char(10) || replace(lower(definitions), '; ', char(10)) || char(10))"
                    addAll(
                        query(
                            db,
                            "definitions LIKE ?1 ESCAPE '\\' ORDER BY " +
                                "(instr($defs, char(10) || ?2 || char(10)) = 0 AND " +
                                "instr($defs, char(10) || 'to ' || ?2 || char(10)) = 0), " +
                                "$COMMON_FIRST, rank DESC, length(simp), id LIMIT $limit",
                            "%" + english.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%",
                            english
                        )
                    )
                }
            }
            results.values.toList()
        }
    }

    /**
     * Every reading of each of [chars]: lowercase pinyin with tone numbers ("hao3") for Mandarin,
     * Jyutping for Cantonese. Lets speaking practice accept a homophone the recogniser wrote.
     */
    suspend fun readingsOf(chars: Collection<Char>, language: SpeechLanguage): Map<Char, Set<String>> {
        val db = database() ?: return emptyMap()
        return withContext(Dispatchers.IO) {
            chars.distinct().associateWith { ch ->
                entriesFor(db, ch.toString())
                    .map { if (language == SpeechLanguage.CANTONESE) it.jyutping else it.pinyin }
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && ' ' !in it }
                    .toSet()
            }
        }
    }

    /**
     * Romanizes [text] word by word (longest dictionary match, so 銀行 is ngan4 hong4 and 行人
     * hang4 jan4), with tone marks for pinyin. Anything that isn't Chinese is kept as it is.
     */
    suspend fun romanize(text: String, language: SpeechLanguage): String {
        val db = database() ?: return ""
        return withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            var i = 0
            while (i < text.length) {
                if (!isHan(text[i])) {
                    if (!text[i].isWhitespace()) out += text[i].toString()
                    i++
                    continue
                }
                var end = i
                while (end < text.length && end - i < MAX_WORD && isHan(text[end])) end++
                var matched = 1
                var reading: String? = null
                for (stop in end downTo i + 1) {
                    val word = text.substring(i, stop)
                    val entry = entriesFor(db, word).firstOrNull()
                    reading = entry?.reading(language)?.takeIf { it.isNotBlank() }
                        ?: if (language == SpeechLanguage.CANTONESE && word.length >= 2) cantoneseReading(db, word) else null
                    if (reading != null) {
                        matched = word.length
                        break
                    }
                }
                out += reading ?: text[i].toString()
                i += matched
            }
            out.joinToString(" ")
                .replace(Regex(" ([，。！？、；：,.!?;:)）」』])"), "$1")
        }
    }

    /** Converts traditional characters to simplified (one-to-one character map from CC-CEDICT). */
    suspend fun toSimplified(text: String): String {
        val map = traditionalToSimplified ?: run {
            val db = database() ?: return text
            withContext(Dispatchers.IO) {
                val m = HashMap<Char, Char>(4096)
                db.rawQuery(
                    "SELECT trad, simp FROM entries WHERE length(trad) = 1 AND length(simp) = 1 AND trad != simp " +
                        "ORDER BY $COMMON_FIRST, id",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        val t = c.getString(0)
                        val s = c.getString(1)
                        // Characters outside the BMP take two Kotlin chars; they're rare, skip them.
                        if (t.length == 1 && s.length == 1) m.putIfAbsent(t[0], s[0])
                    }
                }
                m.also { traditionalToSimplified = it }
            }
        }
        return buildString(text.length) { text.forEach { append(map[it] ?: it) } }
    }

    private fun entriesFor(db: SQLiteDatabase, word: String): List<DictionaryEntry> =
        query(db, "(trad = ? OR simp = ?) ORDER BY $COMMON_FIRST, id", word, word)

    private fun cantoneseReading(db: SQLiteDatabase, word: String): String? =
        db.rawQuery("SELECT jyutping FROM cantonese_words WHERE word = ?", arrayOf(word)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun query(db: SQLiteDatabase, where: String, vararg args: String): List<DictionaryEntry> =
        db.rawQuery("SELECT trad, simp, pinyin, jyutping, definitions FROM entries WHERE $where", args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DictionaryEntry(
                            traditional = c.getString(0),
                            simplified = c.getString(1),
                            pinyin = c.getString(2),
                            jyutping = c.getString(3),
                            definitions = c.getString(4).split('\n').filter { it.isNotBlank() }
                        )
                    )
                }
            }
        }

    private fun DictionaryEntry.key() = "$traditional|$simplified|$pinyin"

    companion object {
        private const val TAG = "ChineseDictionary"
        private const val ASSET_DIR = "dictionary"
        /** Longest word a tap looks for (CC-CEDICT idioms are mostly 4 characters; a few are longer). */
        private const val MAX_WORD = 8

        /**
         * Everyday words before proper nouns (capitalised pinyin: surnames, places) and before
         * entries that are only "variant of …".
         */
        private const val COMMON_FIRST =
            "(substr(pinyin, 1, 1) GLOB '[A-Z]'), (definitions GLOB '*variant of*' AND definitions NOT GLOB '*" + "\n" + "*')"

        /** CJK Unified Ideographs, Extension A and the compatibility block. */
        fun isHan(c: Char): Boolean =
            c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

        /** Licence notices for Settings → About, or null if this build has no dictionary. */
        fun licenseText(context: Context): String? =
            runCatching { context.assets.open("$ASSET_DIR/licenses.txt").bufferedReader().use { it.readText() } }.getOrNull()
    }
}
