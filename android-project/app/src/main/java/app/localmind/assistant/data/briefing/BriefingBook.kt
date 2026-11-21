package app.localmind.assistant.data.briefing

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.briefing.Briefing
import app.localmind.assistant.domain.briefing.Briefings
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
 * Pasted briefings (editions/briefings.json), kept 30 days like the papers, newest first; in
 * backups. Only what you pasted: nothing is fetched.
 */
@Singleton
class BriefingBook @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "editions").apply { mkdirs() }
    private val file = File(dir, "briefings.json")
    private val lock = Mutex()
    private var loaded = false

    private val _briefings = MutableStateFlow<List<Briefing>>(emptyList())
    val briefings: StateFlow<List<Briefing>> = _briefings.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _briefings.value = withContext(Dispatchers.IO) {
            if (!file.exists()) emptyList() else runCatching { Briefings.kept(Briefings.decode(file.readText()), System.currentTimeMillis()) }.getOrElse {
                Log.w(TAG, "Couldn't read briefings.json; set aside", it)
                runCatching { file.copyTo(File(dir, "briefings.unreadable.json"), overwrite = true) }
                emptyList()
            }
        }
        loaded = true
    }

    private suspend fun change(edit: (List<Briefing>) -> List<Briefing>) {
        load()
        lock.withLock {
            val next = Briefings.kept(edit(_briefings.value), System.currentTimeMillis())
            if (next == _briefings.value) return@withLock
            _briefings.value = next
            withContext(Dispatchers.IO) {
                val tmp = File(dir, file.name + ".tmp")
                tmp.writeText(Briefings.encode(next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    check(tmp.renameTo(file)) { "Couldn't save the briefings" }
                }
            }
        }
    }

    suspend fun add(b: Briefing) = change { listOf(b) + it.filterNot { o -> o.id == b.id } }

    suspend fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    suspend fun backupText(): String {
        load()
        return Briefings.encode(_briefings.value)
    }

    suspend fun mergeBackup(text: String) {
        val theirs = Briefings.decode(text)
        change { mine -> Briefings.merge(mine, theirs) }
    }

    private companion object {
        const val TAG = "BriefingBook"
    }
}
