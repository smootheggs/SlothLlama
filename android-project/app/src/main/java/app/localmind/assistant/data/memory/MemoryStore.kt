package app.localmind.assistant.data.memory

import android.content.Context
import app.localmind.assistant.data.library.SearchTokens
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.UserMemoryEntity
import app.localmind.assistant.data.meaning.MeaningModel
import app.localmind.assistant.data.meaning.Vectors
import app.localmind.assistant.domain.attachments.Attachments
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Something the user asked SlothLlama to remember, in their own words. */
data class Memory(val id: String, val text: String, val updatedAt: Long)

/**
 * Memory of you: short facts the user chose to keep ("I'm vegetarian", "I'm learning German for
 * a move to Berlin"). Only what the user adds is kept: in a chat with "remember that …", or in
 * Settings → Memory, where each one can be edited or deleted. They go into chats as background,
 * all of them while they're few, otherwise the ones closest to the question.
 * They live in the app's database (the user_memories table), so they're in backups too.
 */
@Singleton
class MemoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: LocalMindDao,
    private val meaning: MeaningModel
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ON, true))
    /** Memories are used in chats (they're kept either way). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ON, on).apply()
        _enabled.value = on
    }

    val memories: Flow<List<Memory>> = dao.observeMemories().map { list -> list.filter { it.isApprovedByUser }.map { it.toMemory() } }

    /** Saves [text]; the same fact said again just moves to the top. */
    suspend fun add(text: String): Memory {
        val clean = MemoryRules.clean(text)
        require(clean.isNotEmpty()) { "There's nothing to remember." }
        val now = System.currentTimeMillis()
        val existing = dao.getAllMemories().firstOrNull { it.factText.equals(clean, ignoreCase = true) }
        val entity = existing?.copy(factText = clean, updatedAt = now) ?: UserMemoryEntity(
            id = UUID.randomUUID().toString(),
            factText = clean,
            scope = "GLOBAL",
            personaId = null,
            isApprovedByUser = true,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertMemory(entity)
        return entity.toMemory()
    }

    suspend fun update(id: String, text: String) {
        val clean = MemoryRules.clean(text)
        if (clean.isEmpty()) return delete(id)
        val existing = dao.getAllMemories().firstOrNull { it.id == id } ?: return
        dao.upsertMemory(existing.copy(factText = clean, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = dao.deleteMemory(id)

    suspend fun deleteAll() = dao.deleteAllMemories()

    suspend fun all(): List<Memory> = dao.getAllMemories().filter { it.isApprovedByUser }.map { it.toMemory() }

    /** "Forget that …": removes the memory that best matches [text]; returns it, or null if none is close. */
    suspend fun forget(text: String): Memory? {
        val best = MemoryRules.closest(text, all()) ?: return null
        dao.deleteMemory(best.id)
        return best
    }

    /** The memories to give the model with [question] (none when memory is off). */
    suspend fun forPrompt(question: String): List<String> {
        if (!_enabled.value) return emptyList()
        val all = all()
        if (all.isEmpty()) return emptyList()
        if (all.sumOf { Attachments.estimateTokens(it.text) + 2 } <= MemoryRules.BUDGET_TOKENS) {
            return all.take(MemoryRules.MAX_ITEMS).map { it.text }
        }
        val ranked = rankByMeaning(question, all) ?: MemoryRules.rankByWords(question, all)
        return MemoryRules.withinBudget(ranked).map { it.text }
    }

    private val vectors = HashMap<String, FloatArray>()

    private suspend fun rankByMeaning(question: String, all: List<Memory>): List<Memory>? {
        val q = meaning.embedQuery(question) ?: return null
        val scored = all.map { m ->
            val v = synchronized(vectors) { vectors[m.text] } ?: meaning.embedDocument("none", m.text)
                ?.also { synchronized(vectors) { vectors[m.text] = it } }
                ?: return null
            m to Vectors.similarity(q, v)
        }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun UserMemoryEntity.toMemory() = Memory(id, factText, updatedAt)

    private companion object {
        const val PREFS = "memory"
        const val KEY_ON = "on"
    }
}

/** The plain rules behind memory, kept apart so they can be tested. */
object MemoryRules {
    const val MAX_CHARS = 300
    /** About how much of the prompt memories may take. */
    const val BUDGET_TOKENS = 350
    const val MAX_ITEMS = 40

    fun clean(text: String): String =
        text.trim().trim('"', '“', '”').trim().removeSuffix(".").trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_CHARS)

    /** Most shared words first; ties keep the newest first. */
    fun rankByWords(question: String, all: List<Memory>): List<Memory> {
        val q = SearchTokens.forQuery(question).toSet()
        return all.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Memory>> { (_, m) -> SearchTokens.forIndex(m.text).count { it in q } }.thenBy { it.index })
            .map { it.value }
    }

    fun withinBudget(ranked: List<Memory>): List<Memory> {
        var used = 0
        return ranked.take(MAX_ITEMS).takeWhile { m ->
            used += Attachments.estimateTokens(m.text) + 2
            used <= BUDGET_TOKENS
        }
    }

    /** The memory sharing most words with [text], if it shares at least half of [text]'s words. */
    fun closest(text: String, all: List<Memory>): Memory? {
        val words = SearchTokens.forQuery(text).toSet()
        if (words.isEmpty()) return null
        return all.map { m -> m to SearchTokens.forIndex(m.text).toSet().count { it in words } }
            .filter { (_, hits) -> hits * 2 >= words.size }
            .maxByOrNull { it.second }
            ?.first
    }
}
