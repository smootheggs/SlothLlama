package app.localmind.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.backup.AutoBackup
import app.localmind.assistant.data.backup.AutoBackupState
import app.localmind.assistant.data.desk.DeskState
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.home.HomePrefs
import app.localmind.assistant.data.home.HomeSettings
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.meaning.MeaningModel
import app.localmind.assistant.data.meaning.MeaningModelState
import app.localmind.assistant.data.study.CardMemory
import app.localmind.assistant.data.study.FlashcardRepository
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.domain.home.DailyWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Home page's own state: backups, search by meaning, its settings, the Desk and your practice. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    autoBackup: AutoBackup,
    meaning: MeaningModel,
    private val prefs: HomePrefs,
    private val desk: DeskStore,
    private val flashcards: FlashcardRepository,
    dao: LocalMindDao,
    later: app.localmind.assistant.data.later.ReadingList
) : ViewModel() {
    /** The Later shelf, for its tile. */
    val laterItems: StateFlow<List<app.localmind.assistant.data.later.LaterItem>> = later.items

    val backup: StateFlow<AutoBackupState> = autoBackup.state

    val meaningOn: StateFlow<Boolean> = meaning.state
        .map { it is MeaningModelState.Ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), meaning.state.value is MeaningModelState.Ready)

    val settings: StateFlow<HomeSettings> = prefs.settings

    val deskState: StateFlow<DeskState> = desk.state

    private val since = System.currentTimeMillis() - 8 * DAY

    val written: StateFlow<List<Pair<String, Long>>> = dao.observeWrittenSince(since)
        .map { rows -> rows.map { it.personaId to it.at } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastByPersona: StateFlow<Map<String, Long>> = dao.observeLastWrittenByPersona()
        .map { rows -> rows.associate { it.personaId to it.at } }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val reviews: StateFlow<List<Pair<StudyLanguage, Long>>> = flashcards.cards
        .map { cards -> cards.mapNotNull { c -> CardMemory.decode(c.memory)?.lastReview?.takeIf { it > 0 }?.let { c.language to it } } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A one-off note for the page ("Added to flashcards"). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { runCatching { desk.load() } }
    }

    fun update(change: (HomeSettings) -> HomeSettings) = prefs.update(change)

    /** Adds the word of the day to the flashcards (once). */
    fun addToFlashcards(word: DailyWord, traditional: Boolean) {
        viewModelScope.launch {
            val language = if (word.cantonese) StudyLanguage.CANTONESE else StudyLanguage.MANDARIN
            val added = runCatching {
                flashcards.add(language, word.written(traditional), word.reading, word.meaning, "")
            }.getOrDefault(false)
            _message.value = if (added) "Added to ${language.label} flashcards" else "Already in your flashcards"
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
