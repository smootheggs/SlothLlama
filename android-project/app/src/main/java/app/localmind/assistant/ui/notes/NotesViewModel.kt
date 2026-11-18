package app.localmind.assistant.ui.notes

import androidx.lifecycle.ViewModel
import app.localmind.assistant.data.tools.Note
import app.localmind.assistant.data.tools.NotesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(private val store: NotesStore) : ViewModel() {
    val notes: StateFlow<List<Note>> = store.notes

    fun add(text: String) {
        if (text.isNotBlank()) store.add(text)
    }

    fun update(note: Note, text: String) {
        if (text.isBlank()) store.remove(note.id) else store.update(note.id, text)
    }

    fun remove(note: Note) = store.remove(note.id)
}
