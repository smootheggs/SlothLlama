package app.localmind.assistant.data.world

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.world.WorldBundle
import app.localmind.assistant.domain.world.WorldData
import app.localmind.assistant.domain.world.WorldYours
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * World on the tablet (world/world.json): the markets you follow, as the Lobster last read them, and
 * how the crowd's chance moved. Written whole through a temporary file. In backups: what you follow,
 * the history and the news watches (the markets themselves are read again).
 */
@Singleton
class WorldStore @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "world").apply { mkdirs() }
    private val file = File(dir, "world.json")
    private val lock = Mutex()
    private var loaded = false

    private val _yours = MutableStateFlow(WorldYours())
    val yours: StateFlow<WorldYours> = _yours.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _yours.value = withContext(Dispatchers.IO) {
            if (!file.exists()) WorldYours() else runCatching { WorldData.decode(file.readText()) }.getOrElse {
                Log.w(TAG, "Couldn't read world.json; set aside", it)
                runCatching { file.copyTo(File(dir, "world.unreadable.json"), overwrite = true) }
                WorldYours()
            }
        }
        loaded = true
    }

    private fun write(text: String) {
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save world.json" }
        }
    }

    suspend fun update(edit: (WorldYours) -> WorldYours) {
        load()
        lock.withLock {
            val next = edit(_yours.value)
            if (next == _yours.value) return@withLock
            _yours.value = next
            withContext(Dispatchers.IO) { write(WorldData.encode(next)) }
        }
    }

    /** Lays a bundle from the Lobster over what's kept. */
    suspend fun take(bundle: WorldBundle) = update { WorldData.take(it, bundle, LocalDate.now(), System.currentTimeMillis()) }

    suspend fun backupText(): String {
        load()
        return WorldData.encode(_yours.value.copy(markets = emptyMap(), seenNews = emptyList()))
    }

    /** A backup's follows, history and watches join yours; what you have stays. */
    suspend fun mergeBackup(text: String) {
        val theirs = WorldData.decode(text)
        update { mine ->
            mine.copy(
                follow = (mine.follow + theirs.follow).distinct().take(WorldData.MAX_FOLLOW),
                history = theirs.history + mine.history,
                watches = theirs.watches + mine.watches
            )
        }
    }

    private companion object {
        const val TAG = "WorldStore"
    }
}
