package app.localmind.assistant.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.dictionary.ChineseDictionary
import app.localmind.assistant.data.dictionary.DictionaryEntry
import app.localmind.assistant.data.dictionary.WordLookup
import app.localmind.assistant.data.persona.ChineseStyleStore
import app.localmind.assistant.data.study.FlashcardRepository
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.domain.persona.ChineseStyle
import app.localmind.assistant.domain.persona.SpeechLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tap-to-look-up in chats, and the Dictionary screen's search. */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionary: ChineseDictionary,
    private val flashcards: FlashcardRepository,
    styleStore: ChineseStyleStore
) : ViewModel() {

    val isAvailable: Boolean get() = dictionary.isAvailable
    val style: StateFlow<ChineseStyle> = styleStore.style

    private val _lookup = MutableStateFlow<WordLookup?>(null)
    /** The word in the bottom sheet, or null when it's closed. */
    val lookup: StateFlow<WordLookup?> = _lookup.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<List<DictionaryEntry>> = _query
        .debounce(150)
        .mapLatest { q -> runCatching { dictionary.search(q) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lookupJob: Job? = null

    init {
        viewModelScope.launch { runCatching { dictionary.warmUp() } }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    /** A tap at [offset] in [text]: looks up the word starting there (nothing happens off Chinese). */
    fun lookUpAt(text: String, offset: Int) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            dictionary.lookupAt(text, offset)?.let { _lookup.value = it }
        }
    }

    fun open(entry: DictionaryEntry) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _lookup.value = dictionary.lookup(entry.traditional)?.let { found ->
                // Keep the tapped entry first when a headword has several readings.
                found.copy(entries = listOf(entry) + found.entries.filter { it != entry })
            } ?: WordLookup(entry.simplified, listOf(entry))
        }
    }

    fun dismiss() {
        lookupJob?.cancel()
        _lookup.value = null
    }

    fun save(entry: DictionaryEntry, language: SpeechLanguage) {
        viewModelScope.launch {
            val added = flashcards.add(
                language = StudyLanguage.from(language),
                term = entry.headword(style.value.script, language),
                pronunciation = entry.reading(language),
                meaning = entry.definitions.take(3).joinToString("; "),
                example = ""
            )
            _message.value = if (added) "Added to flashcards" else "Already in your flashcards"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
