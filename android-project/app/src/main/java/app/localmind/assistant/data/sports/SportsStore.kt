package app.localmind.assistant.data.sports

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.sports.Book
import app.localmind.assistant.domain.sports.Factor
import app.localmind.assistant.domain.sports.League
import app.localmind.assistant.domain.sports.SportsBundle
import app.localmind.assistant.domain.sports.SportsFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What you add to a game: its prices (yours, or the line's), and what you know that results don't. */
@Serializable
data class GameNotes(val book: Book? = null, val factors: List<Factor> = emptyList(), val totalShift: Double = 0.0)

/** Yours: the leagues you follow, your notes per game ("nba:401909305"), and what was asked of the Lobster. */
@Serializable
data class SportsYours(
    val follow: List<String> = League.DEFAULT.map { it.id },
    val notes: Map<String, GameNotes> = emptyMap(),
    /** F1 factors by "f1:<round>". */
    val raceFactors: Map<String, List<Factor>> = emptyMap(),
    val backfillSent: Boolean = false,
    val dailySent: Boolean = false,
    val lastOrderAt: Long = 0
)

/**
 * Sports on the tablet: the results the Lobster brought (sports/results.json, merged as they come;
 * it can always fetch them again, so they aren't backed up) and yours (sports/yours.json: the
 * leagues, your prices and factors; in backups). Both written whole through a temporary file.
 */
@Singleton
class SportsStore @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "sports").apply { mkdirs() }
    private val resultsFile = File(dir, "results.json")
    private val yoursFile = File(dir, "yours.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private var loaded = false

    private val _results = MutableStateFlow<SportsBundle?>(null)
    val results: StateFlow<SportsBundle?> = _results.asStateFlow()

    private val _yours = MutableStateFlow(SportsYours())
    val yours: StateFlow<SportsYours> = _yours.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        withContext(Dispatchers.IO) {
            _results.value = if (resultsFile.exists()) runCatching { SportsFile.parse(resultsFile.readText()) }.getOrNull() else null
            _yours.value = if (yoursFile.exists()) runCatching { json.decodeFromString(SportsYours.serializer(), yoursFile.readText()) }.getOrElse {
                Log.w(TAG, "Couldn't read yours.json; set aside", it)
                runCatching { yoursFile.copyTo(File(dir, "yours.unreadable.json"), overwrite = true) }
                SportsYours()
            } else SportsYours()
        }
        loaded = true
    }

    private fun write(file: File, text: String) {
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save ${file.name}" }
        }
    }

    /** Lays a bundle from the Lobster over what's kept. */
    suspend fun take(bundle: SportsBundle) {
        load()
        lock.withLock {
            val merged = SportsFile.merge(_results.value, bundle)
            _results.value = merged
            withContext(Dispatchers.IO) { write(resultsFile, SportsFile.encode(merged)) }
        }
    }

    suspend fun update(edit: (SportsYours) -> SportsYours) {
        load()
        lock.withLock {
            val next = edit(_yours.value)
            if (next == _yours.value) return@withLock
            _yours.value = next
            withContext(Dispatchers.IO) { write(yoursFile, json.encodeToString(SportsYours.serializer(), next)) }
        }
    }

    suspend fun backupText(): String {
        load()
        return json.encodeToString(SportsYours.serializer(), _yours.value)
    }

    /** A backup's notes join yours; your own settings stay. */
    suspend fun mergeBackup(text: String) {
        val theirs = json.decodeFromString(SportsYours.serializer(), text)
        update { mine -> mine.copy(notes = theirs.notes + mine.notes, raceFactors = theirs.raceFactors + mine.raceFactors) }
    }

    private companion object {
        const val TAG = "SportsStore"
    }
}
