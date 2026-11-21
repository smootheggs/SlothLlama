package app.localmind.assistant.data.music

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.music.Song
import app.localmind.assistant.domain.music.Songs
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
 * The songbook: chord sheets you pasted, in private storage (music/songs.json), written whole
 * through a temporary file; in backups. Nothing is fetched; the sheets are only what you put in.
 */
@Singleton
class SongBook @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "music").apply { mkdirs() }
    private val file = File(dir, "songs.json")
    private val lock = Mutex()
    private var loaded = false

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /** Newest first. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _songs.value = withContext(Dispatchers.IO) {
            if (!file.exists()) emptyList() else runCatching { Songs.decode(file.readText()) }.getOrElse {
                Log.w(TAG, "Couldn't read songs.json; set aside", it)
                runCatching { file.copyTo(File(dir, "songs.unreadable.json"), overwrite = true) }
                emptyList()
            }
        }
        loaded = true
    }

    private suspend fun change(edit: (List<Song>) -> List<Song>) {
        load()
        lock.withLock {
            val next = edit(_songs.value)
            if (next == _songs.value) return@withLock
            _songs.value = next
            withContext(Dispatchers.IO) {
                val tmp = File(dir, file.name + ".tmp")
                tmp.writeText(Songs.encode(next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    check(tmp.renameTo(file)) { "Couldn't save the songbook" }
                }
            }
        }
    }

    suspend fun save(song: Song) = change { list ->
        if (list.any { it.id == song.id }) list.map { if (it.id == song.id) song else it } else listOf(song) + list
    }

    suspend fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    suspend fun backupText(): String {
        load()
        return Songs.encode(_songs.value)
    }

    suspend fun mergeBackup(text: String) {
        val theirs = Songs.decode(text)
        change { mine -> Songs.merge(mine, theirs) }
    }

    private companion object {
        const val TAG = "SongBook"
    }
}
