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

    private val _asking = MutableStateFlow<String?>(null)
    /** The idea whose rule the Trader was last asked to draft (1.49), for the draft to come back to. */
    val asking: StateFlow<String?> = _asking.asStateFlow()

    fun setAsking(id: String?) {
        _asking.value = id
    }

    private val _focus = MutableStateFlow<String?>(null)
    /** An idea for SlothDesk to open (just saved, or a draft just came back for it). */
    val focus: StateFlow<String?> = _focus.asStateFlow()

    fun setFocus(id: String?) {
        _focus.value = id
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
            thesis = d.thesis.trim().take(MAX_TEXT),
            wrongIf = d.wrongIf.trim().take(MAX_TEXT),
            clientAngle = d.clientAngle.trim().take(MAX_TEXT),
            conviction = d.conviction.coerceIn(50, 95),
            source = d.source,
            kind = if (d.kind == "rule") "rule" else "trade",
            why = d.why.trim().take(MAX_TEXT),
            from = d.from.trim().take(300)
        )
        change { listOf(idea) + it }
        return idea
    }

    suspend fun addNote(id: String, text: String, now: Long = System.currentTimeMillis()) {
        if (text.isBlank()) return
        change { list -> list.map { if (it.id == id) it.copy(notes = it.notes + IdeaNote(now, text.trim())) else it } }
    }

    /** Links an open idea to the rule it's tested with (or unlinks it, with ""). */
    suspend fun setRule(id: String, ruleId: String) {
        change { list -> list.map { if (it.id == id && it.isOpen) it.copy(ruleId = ruleId) else it } }
    }

    /** Keeps the Trader's latest rule card for an open idea (as text, capped; read on the Desk). */
    suspend fun setDraftCard(id: String, card: String) {
        change { list -> list.map { if (it.id == id && it.isOpen) it.copy(draftCard = card.take(MAX_CARD)) else it } }
    }

    /**
     * A rule card from a Trader reply: kept on the idea that asked for it, or (when none did, or
     * it's closed) on a new idea named after the card. Returns the idea's id.
     */
    suspend fun offerRuleCard(card: String, name: String, now: Long = System.currentTimeMillis()): String {
        load()
        val asked = _asking.value?.let { id -> _ideas.value.firstOrNull { it.id == id && it.isOpen } }
        val id = if (asked != null) {
            setDraftCard(asked.id, card)
            asked.id
        } else {
            val idea = add(IdeaDraft(thesis = name, kind = "rule", source = "trader",
                reviewOn = java.time.LocalDate.now().plusMonths(3).toString()), now)
            setDraftCard(idea.id, card)
            idea.id
        }
        _focus.value = id
        return id
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
        /** The longest a written field is kept, and a kept rule card. */
        const val MAX_TEXT = 2000
        const val MAX_CARD = 12_000

        /** "2,385.50" → 2385.5; blank or not a number → null. */
        fun String.toPrice(): Double? = trim().replace(",", "").toDoubleOrNull()
    }
}
