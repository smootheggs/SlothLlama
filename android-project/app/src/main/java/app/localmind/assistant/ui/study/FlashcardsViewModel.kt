package app.localmind.assistant.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.FlashcardRepository
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.data.study.StudyLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashcardsUiState(
    val cards: List<Flashcard> = emptyList(),
    /** The card being reviewed (the most overdue), or null when nothing is due. */
    val current: Flashcard? = null,
    val dueCount: Int = 0,
    val revealed: Boolean = false,
    val now: Long = System.currentTimeMillis()
)

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val repository: FlashcardRepository
) : ViewModel() {

    private val revealed = MutableStateFlow(false)
    /** Ticks every 30 s so cards that become due (e.g. "Again" in 10 min) show up without leaving. */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<FlashcardsUiState> = combine(repository.cards, revealed, clock) { cards, shown, now ->
        val due = cards.filter { it.isDue(now) }.sortedBy { it.dueAt }
        FlashcardsUiState(cards = cards, current = due.firstOrNull(), dueCount = due.size, revealed = shown, now = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FlashcardsUiState())

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }

    fun reveal() {
        revealed.value = true
    }

    fun grade(grade: Grade) {
        val card = uiState.value.current ?: return
        viewModelScope.launch {
            repository.review(card, grade)
            revealed.value = false
            clock.value = System.currentTimeMillis()
        }
    }

    fun delete(card: Flashcard) {
        viewModelScope.launch { repository.delete(card) }
    }

    fun add(language: StudyLanguage, term: String, pronunciation: String, meaning: String, example: String) {
        viewModelScope.launch {
            val added = repository.add(language, term, pronunciation, meaning, example)
            _message.value = if (added) "Saved “${term.trim()}” to flashcards" else "“${term.trim()}” is already a flashcard"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
