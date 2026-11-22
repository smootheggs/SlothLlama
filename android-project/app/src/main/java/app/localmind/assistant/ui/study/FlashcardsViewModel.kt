package app.localmind.assistant.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.FlashcardRepository
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.data.study.Scheduler
import app.localmind.assistant.data.study.StudyBook
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.domain.study.Answers
import app.localmind.assistant.domain.study.Ask
import app.localmind.assistant.domain.study.Checked
import app.localmind.assistant.domain.study.Item
import app.localmind.assistant.domain.study.Plan
import app.localmind.assistant.domain.study.ReverseSide
import app.localmind.assistant.domain.study.Session
import app.localmind.assistant.domain.study.Study
import app.localmind.assistant.domain.study.StudySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/** The card being reviewed, as the screen shows it. */
data class ReviewView(
    val card: Flashcard,
    val item: Item,
    val revealed: Boolean,
    val typed: String,
    val checked: Checked?,
    val done: Int,
    val total: Int,
    val practice: Boolean,
    val canUndo: Boolean,
    /** When each grade would bring the card back ("10m", "3d"); empty in practice. */
    val hints: Map<Grade, String>,
    val leech: Boolean
)

/** The end of a session. */
data class ReviewSummary(val reviewed: Int, val right: Int, val missed: List<Pair<Flashcard, Item>>, val practice: Boolean, val nextLine: String)

data class FlashcardsUiState(
    val cards: List<Flashcard> = emptyList(),
    val settings: StudySettings = StudySettings(),
    /** Today's review with these settings. */
    val plan: Plan = Plan(emptyList(), 0, 0),
    /** Cards ready to review now (Home's "N due"). */
    val dueCount: Int = 0,
    /** Today's review per language, for choosing. */
    val byLanguage: List<Pair<StudyLanguage, Int>> = emptyList(),
    val review: ReviewView? = null,
    val summary: ReviewSummary? = null,
    /** The last answer can be taken back. */
    val canUndo: Boolean = false,
    val ready: Boolean = false,
    val now: Long = System.currentTimeMillis()
)

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val repository: FlashcardRepository,
    private val study: StudyBook
) : ViewModel() {

    /** What one answer changed, to put back. */
    private class Undo(val before: Session, val card: Flashcard?, val reverseId: String?, val reverse: ReverseSide?, val countedNew: Boolean)

    private data class Live(
        val session: Session? = null,
        val revealed: Boolean = false,
        val typed: String = "",
        val checked: Checked? = null,
        val summary: ReviewSummary? = null,
        val canUndo: Boolean = false
    )

    private val live = MutableStateFlow(Live())
    private var undo: Undo? = null
    private val busy = Mutex()
    /** Ticks every 30 s so cards that come due (an "Again" in 10 min) appear without leaving. */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The card just deleted, for Undo. */
    private val _deleted = MutableStateFlow<Flashcard?>(null)
    val deleted: StateFlow<Flashcard?> = _deleted.asStateFlow()

    private fun today(): String = LocalDate.now().toString()

    val uiState: StateFlow<FlashcardsUiState> = combine(repository.cards, study.state, live, clock) { cards, st, l, now ->
        val plan = Study.plan(cards, st, now, today())
        val all = Study.plan(cards, st.copy(settings = st.settings.copy(languages = emptyList())), now, today())
        val byId = cards.associateBy { it.id }
        val byLanguage = all.items.groupBy { byId[it.id]?.language }.mapNotNull { (k, v) -> k?.let { it to v.size } }
            .sortedByDescending { it.second }
        val review = l.session?.current?.let { item ->
            byId[item.id]?.let { card ->
                val face = if (item.ask == Ask.MEANING) Study.reverseCard(card, st.reverse[card.id]) else card
                val hints = if (l.session.practice) emptyMap() else
                    Grade.entries.associateWith { Scheduler.label(Scheduler.next(face, it, now), now) }
                ReviewView(card, item, l.revealed, l.typed, l.checked, l.session.done, l.session.total, l.session.practice,
                    l.canUndo, hints, Study.isLeech(card))
            }
        }
        FlashcardsUiState(cards, st.settings, plan, plan.items.size, byLanguage, review, l.summary, l.canUndo, ready = true, now = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FlashcardsUiState())

    init {
        viewModelScope.launch { study.load() }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                clock.value = System.currentTimeMillis()
            }
        }
        // Schedules of cards deleted since last time go with them (once, so an undone delete keeps its own).
        viewModelScope.launch {
            val cards = repository.cards.first()
            if (cards.isNotEmpty()) study.prune(cards.map { it.id }.toSet())
        }
    }

    // --- settings ---

    fun setAskWith(name: String) = viewModelScope.launch { study.settings { it.copy(askWith = name) } }
    fun setShowReading(on: Boolean) = viewModelScope.launch { study.settings { it.copy(showReading = on) } }
    fun setTyped(on: Boolean) = viewModelScope.launch { study.settings { it.copy(typed = on) } }
    fun setLanguages(codes: List<String>) = viewModelScope.launch { study.settings { it.copy(languages = codes) } }

    // --- a session ---

    fun start() {
        val plan = uiState.value.plan
        if (plan.items.isEmpty()) return
        undo = null
        live.value = Live(session = Session.of(plan.items))
    }

    /** Goes over the cards missed, without changing their schedules. */
    fun practiseMissed() {
        val missed = live.value.summary?.missed?.map { it.second.copy(isNew = false) }.orEmpty()
        if (missed.isEmpty()) return
        undo = null
        live.value = Live(session = Session.of(missed, practice = true))
    }

    fun reveal() {
        val l = live.value
        if (l.session?.current == null || l.revealed) return
        val review = uiState.value.review
        val checked = if (uiState.value.settings.typed && review != null) Answers.check(review.card, review.item.ask, l.typed) else null
        live.value = l.copy(revealed = true, checked = checked)
    }

    fun type(text: String) {
        live.value = live.value.copy(typed = text.take(200))
    }

    fun grade(grade: Grade) = viewModelScope.launch {
        busy.withLock {
            val l = live.value
            val s = l.session ?: return@withLock
            if (!l.revealed) return@withLock
            val item = s.current ?: return@withLock
            val card = uiState.value.cards.firstOrNull { it.id == item.id }
            var before: Flashcard? = null
            var reverseBefore: ReverseSide? = null
            var countedNew = false
            if (card != null && !s.practice) {
                if (item.ask == Ask.WORD) {
                    before = card
                    repository.review(card, grade)
                } else {
                    reverseBefore = study.state.value.reverse[card.id]
                    val next = Scheduler.next(Study.reverseCard(card, reverseBefore), grade)
                    study.setReverse(card.id, ReverseSide(next.memory, next.intervalDays, next.dueAt))
                }
                if (item.isNew && item.key !in s.first) {
                    study.countNew(today())
                    countedNew = true
                }
            }
            undo = Undo(s, before, if (item.ask == Ask.MEANING && !s.practice) item.id else null, reverseBefore, countedNew)
            val next = s.answer(grade)
            clock.value = System.currentTimeMillis()
            live.value = if (next.finished) Live(summary = summaryOf(next), canUndo = true) else Live(session = next, canUndo = true)
        }
    }

    /** Puts the last answer back: the card's schedule as it was, and the card in front again. */
    fun undoLast() = viewModelScope.launch {
        busy.withLock {
            val u = undo ?: return@withLock
            u.card?.let { repository.restore(it) }
            u.reverseId?.let { study.setReverse(it, u.reverse) }
            if (u.countedNew) study.countNew(today(), -1)
            undo = null
            live.value = Live(session = u.before, canUndo = false)
        }
    }

    /** Ends the session early; the summary shows what was done. */
    fun end() {
        val s = live.value.session ?: return
        undo = null
        live.value = if (s.done > 0) Live(summary = summaryOf(s)) else Live()
    }

    fun closeSummary() {
        undo = null
        live.value = Live()
    }

    private fun summaryOf(s: Session): ReviewSummary {
        val byId = uiState.value.cards.associateBy { it.id }
        val missed = s.missed.mapNotNull { item -> byId[item.id]?.let { it to item } }
        val now = System.currentTimeMillis()
        val soon = uiState.value.cards.filter { it.dueAt > now }.minByOrNull { it.dueAt }
        val nextLine = soon?.let {
            val hours = ((it.dueAt - now) / 3_600_000L).toInt()
            val count = uiState.value.cards.count { c -> c.dueAt <= it.dueAt + 3_600_000L && c.dueAt > now }
            val cardsWord = if (count == 1) "1 card" else "$count cards"
            when {
                hours < 1 -> "Next: $cardsWord within the hour."
                hours < 24 -> "Next: $cardsWord in about $hours ${if (hours == 1) "hour" else "hours"}."
                else -> "Next: $cardsWord in ${hours / 24} ${if (hours / 24 == 1) "day" else "days"}."
            }
        } ?: "Nothing else is scheduled."
        return ReviewSummary(s.done, s.rightFirstTime, missed, s.practice, nextLine)
    }

    // --- the cards ---

    fun delete(card: Flashcard) {
        viewModelScope.launch {
            repository.delete(card)
            _deleted.value = card
        }
    }

    fun undoDelete() {
        val card = _deleted.value ?: return
        _deleted.value = null
        viewModelScope.launch { repository.restore(card) }
    }

    fun clearDeleted() {
        _deleted.value = null
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
