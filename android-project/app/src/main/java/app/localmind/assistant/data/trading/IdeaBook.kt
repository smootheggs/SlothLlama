package app.localmind.assistant.data.trading

import android.content.Context
import app.localmind.assistant.domain.trading.Idea
import app.localmind.assistant.domain.trading.IdeaDraft
import app.localmind.assistant.domain.trading.IdeaNote
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class IdeaFile(val ideas: List<Idea> = emptyList())

/**
 * The idea book: your trade ideas, what you thought when you had them, and how they turned
 * out. One file in private storage (trading/ideas.json), written whole through a temporary
 * file; in backups. Nothing here is sent anywhere.
 */
@Singleton
class IdeaBook @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(File(context.filesDir, "trading").apply { mkdirs() }, "ideas.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private var loaded = false

    private val _ideas = MutableStateFlow<List<Idea>>(emptyList())
    /** Newest first. */
    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()

    private val _draft = MutableStateFlow<IdeaDraft?>(null)
    /** An idea waiting in the editor (from a Trader card, say). */
    val draft: StateFlow<IdeaDraft?> = _draft.asStateFlow()

    fun setDraft(draft: IdeaDraft?) {
        _draft.value = draft
    }

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _ideas.value = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(IdeaFile.serializer(), file.readText()).ideas }.getOrDefault(emptyList())
        }
        loaded = true
    }

    private suspend fun change(edit: (List<Idea>) -> List<Idea>) {
        load()
        lock.withLock {
            val next = edit(_ideas.value).sortedByDescending { it.createdAt }
            _ideas.value = next
            withContext(Dispatchers.IO) { writeAtomic(json.encodeToString(IdeaFile.serializer(), IdeaFile(next))) }
        }
    }

    /** Saves a draft as a new idea; its thesis and levels are fixed from here on. */
    suspend fun add(d: IdeaDraft, now: Long = System.currentTimeMillis()): Idea {
        val idea = Idea(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            instrument = d.instrument.trim(),
            direction = d.direction,
            entry = d.entry.toPrice(),
            stop = d.stop.toPrice(),
            target = d.target.toPrice(),
            horizon = d.horizon.trim(),
            reviewOn = d.reviewOn,
            size = d.size.trim(),
            thesis = d.thesis.trim(),
            wrongIf = d.wrongIf.trim(),
            clientAngle = d.clientAngle.trim(),
            conviction = d.conviction.coerceIn(50, 95),
            source = d.source
        )
        change { listOf(idea) + it }
        return idea
    }

    suspend fun addNote(id: String, text: String, now: Long = System.currentTimeMillis()) {
        if (text.isBlank()) return
        change { list -> list.map { if (it.id == id) it.copy(notes = it.notes + IdeaNote(now, text.trim())) else it } }
    }

    suspend fun setReviewOn(id: String, date: String) {
        change { list -> list.map { if (it.id == id && it.isOpen) it.copy(reviewOn = date) else it } }
    }

    /** Closes an idea with its review. */
    suspend fun close(
        id: String, outcome: String, reason: String, exit: Double?, whatHappened: String, lesson: String,
        now: Long = System.currentTimeMillis()
    ) {
        change { list ->
            list.map {
                if (it.id == id && it.isOpen) it.copy(
                    closedAt = now, outcome = outcome, reason = reason, exit = exit,
                    whatHappened = whatHappened.trim(), lesson = lesson.trim()
                ) else it
            }
        }
    }

    suspend fun delete(id: String) {
        change { list -> list.filterNot { it.id == id } }
    }

    suspend fun backupText(): String {
        load()
        return json.encodeToString(IdeaFile.serializer(), IdeaFile(_ideas.value))
    }

    /** A backup's ideas join yours; where both have the same idea, yours stays. */
    suspend fun mergeBackup(text: String) {
        val theirs = json.decodeFromString(IdeaFile.serializer(), text).ideas
        change { mine ->
            val have = mine.map { it.id }.toSet()
            mine + theirs.filter { it.id !in have }
        }
    }

    private fun writeAtomic(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save the idea book" }
        }
    }

    companion object {
        /** "2,385.50" → 2385.5; blank or not a number → null. */
        fun String.toPrice(): Double? = trim().replace(",", "").toDoubleOrNull()
    }
}
