package app.localmind.assistant.data.tools

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Note(val id: String, val text: String, val createdAt: Long)

/**
 * Quick notes ("take a note: …" in a chat, or the Notes page). Kept in app preferences rather
 * than the chat database, so they need no schema change and go into backups with the settings.
 */
@Singleton
class NotesStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _notes = MutableStateFlow(read())
    /** Newest first. */
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun add(text: String): Note {
        val note = Note(UUID.randomUUID().toString(), text.trim(), System.currentTimeMillis())
        _notes.update { listOf(note) + it }
        save()
        return note
    }

    fun update(id: String, text: String) {
        _notes.update { list -> list.map { if (it.id == id) it.copy(text = text.trim()) else it } }
        save()
    }

    fun remove(id: String) {
        _notes.update { list -> list.filterNot { it.id == id } }
        save()
    }

    /** Notes with every word of [query] in them (case and accents ignored), newest first. */
    fun search(query: String): List<Note> {
        val words = fold(query).split(Regex("\\W+")).filter { it.length >= 2 && it !in STOP }
        if (words.isEmpty()) return emptyList()
        val all = _notes.value
        val strict = all.filter { n -> val t = fold(n.text); words.all { it in t } }
        return strict.ifEmpty { all.filter { n -> val t = fold(n.text); words.any { it in t } } }
    }

    /** Re-reads the notes (after a backup is restored). */
    fun reload() {
        _notes.value = read()
    }

    private fun read(): List<Note> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() }.orEmpty()

    private fun save() {
        prefs.edit().putString(KEY, json.encodeToString(LIST, _notes.value)).apply()
    }

    private fun fold(s: String) =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    companion object {
        /** Also listed in BackupManager's settings files. */
        const val PREFS = "notes"
        private const val KEY = "items"
        private val LIST = ListSerializer(Note.serializer())
        private val STOP = setOf("the", "a", "an", "my", "about", "for", "of", "to", "in", "on", "and", "or", "what", "did", "i")
    }
}
