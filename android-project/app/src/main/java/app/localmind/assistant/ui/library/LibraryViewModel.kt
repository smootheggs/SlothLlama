package app.localmind.assistant.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.library.LibraryItem
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.domain.attachments.LibraryPassage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(private val store: LibraryStore) : ViewModel() {

    val items: StateFlow<List<LibraryItem>?> = store.items
    val working: StateFlow<String?> = store.working

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _results = MutableStateFlow<List<LibraryPassage>?>(null)
    /** What the last test search found (null before one). */
    val results: StateFlow<List<LibraryPassage>?> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { runCatching { store.refresh() } }
    }

    fun addDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var added = 0
            val failed = mutableListOf<String>()
            for (uri in uris) {
                try {
                    store.addDocument(uri)
                    added++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    failed += e.message?.takeIf { it.isNotBlank() } ?: "One file couldn't be read."
                }
            }
            _message.value = when {
                failed.isEmpty() -> if (added == 1) "Added to your library 📚" else "Added $added items to your library 📚"
                added == 0 -> failed.first()
                else -> "Added $added. ${failed.first()}"
            }
        }
    }

    fun addWiki(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val item = store.addWiki(uri)
                "${item.name} is in your library."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.message?.takeIf { it.isNotBlank() } ?: "That file couldn't be opened."
            }
        }
    }

    fun setEnabled(item: LibraryItem, enabled: Boolean) {
        viewModelScope.launch { runCatching { store.setEnabled(item.id, enabled) } }
    }

    fun remove(item: LibraryItem) {
        viewModelScope.launch {
            runCatching { store.remove(item.id) }
                .onSuccess { _message.value = "Removed ${item.name}." }
                .onFailure { _message.value = "Couldn't remove ${item.name}." }
        }
    }

    fun search(question: String) {
        searchJob?.cancel()
        if (question.isBlank()) {
            _results.value = null
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            try {
                _results.value = store.preview(question)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _results.value = emptyList()
                _message.value = "The search failed."
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
