package app.localmind.assistant.data.study

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.study.ReverseSide
import app.localmind.assistant.domain.study.Study
import app.localmind.assistant.domain.study.StudySettings
import app.localmind.assistant.domain.study.StudyState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the review keeps beside the cards (1.51): your review settings, each card's meaning→word
 * schedule and today's new-card count, in private storage (study/study.json), written whole
 * through a temporary file; in backups. The cards themselves stay in the database.
 */
@Singleton
class StudyBook @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "study").apply { mkdirs() }
    private val file = File(dir, "study.json")
    private val lock = Mutex()
    private var loaded = false

    private val _state = MutableStateFlow(StudyState())
    val state: StateFlow<StudyState> = _state.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _state.value = withContext(Dispatchers.IO) {
            if (!file.exists()) StudyState() else runCatching { Study.decode(file.readText()) }.getOrElse {
                Log.w(TAG, "Couldn't read study.json; set aside", it)
                runCatching { file.copyTo(File(dir, "study.unreadable.json"), overwrite = true) }
                StudyState()
            }
        }
        loaded = true
    }

    private suspend fun change(edit: (StudyState) -> StudyState) {
        load()
        lock.withLock {
            val next = Study.sound(edit(_state.value))
            if (next == _state.value) return@withLock
            _state.value = next
            withContext(Dispatchers.IO) {
                val tmp = File(dir, file.name + ".tmp")
                tmp.writeText(Study.encode(next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    check(tmp.renameTo(file)) { "Couldn't save the review settings" }
                }
            }
        }
    }

    suspend fun settings(edit: (StudySettings) -> StudySettings) = change { it.copy(settings = edit(it.settings)) }

    /** The meaning→word side of card [id]; null takes it back to never reviewed (an undo). */
    suspend fun setReverse(id: String, side: ReverseSide?) = change {
        it.copy(reverse = if (side == null) it.reverse - id else it.reverse + (id to side))
    }

    /** [shown] more new cards seen [today] (negative: an undo). */
    suspend fun countNew(today: String, shown: Int = 1) = change { Study.countNew(it, today, shown) }

    /** Drops the schedules of cards that no longer exist. */
    suspend fun prune(ids: Set<String>) = change { s -> s.copy(reverse = s.reverse.filterKeys { it in ids }) }

    suspend fun backupText(): String {
        load()
        return Study.encode(_state.value)
    }

    /** A backup's schedules for cards this tablet has none for; settings stay as they are here. */
    suspend fun mergeBackup(text: String) {
        val theirs = Study.decode(text)
        change { mine -> mine.copy(reverse = theirs.reverse + mine.reverse) }
    }

    private companion object {
        const val TAG = "StudyBook"
    }
}
