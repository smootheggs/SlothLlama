package app.localmind.assistant.ui.forecasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.forecasts.ForecastBook
import app.localmind.assistant.data.trading.IdeaBook
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Forecasts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Forecasts: your calls, with SlothDesk's ideas read in beside them. */
@HiltViewModel
class ForecastsViewModel @Inject constructor(
    private val book: ForecastBook,
    private val ideas: IdeaBook
) : ViewModel() {

    /** Yours and SlothDesk's ideas (read in place), soonest check first. */
    val calls: StateFlow<List<Call>> = combine(book.calls, ideas.ideas) { mine, desk ->
        (mine + desk.mapNotNull { Forecasts.fromIdea(it) }).sortedBy { it.checkOn }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    private val _area = MutableStateFlow<Area?>(null)
    /** The tab: one area, or all (null). */
    val area: StateFlow<Area?> = _area.asStateFlow()

    init {
        viewModelScope.launch { book.load() }
        viewModelScope.launch { ideas.load() }
    }

    fun open(id: String?) {
        _openId.value = id
    }

    fun showArea(area: Area?) {
        _area.value = area
    }

    /** A new call with its first chances; it opens straight away. */
    fun create(
        question: String,
        area: Area,
        kind: CallKind,
        options: List<String>,
        line: String,
        checkOn: String,
        chances: List<Double>,
        reason: String,
        market: List<Double>?,
        marketWhere: String
    ) {
        val q = question.trim().take(Forecasts.MAX_QUESTION)
        if (q.isEmpty() || Forecasts.date(checkOn) == null) return
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val call = Call(
            id = UUID.randomUUID().toString(),
            question = q,
            area = area,
            kind = kind,
            options = if (kind == CallKind.ONE_OF) options.map { it.trim().take(80) }.filter { it.isNotEmpty() }.take(Forecasts.MAX_OPTIONS) else emptyList(),
            line = if (kind == CallKind.NUMBER) line.trim().take(40) else "",
            checkOn = checkOn,
            createdAt = now,
            estimates = listOf(Chance(today, now, chances, reason.trim().take(400))),
            market = market?.let { listOf(Chance(today, now, it, marketWhere.trim().take(80))) } ?: emptyList()
        )
        viewModelScope.launch {
            book.save(call)
            _openId.value = call.id
        }
    }

    fun changeChance(id: String, chances: List<Double>, reason: String) = viewModelScope.launch { book.addChance(id, chances, reason) }

    fun addMarket(id: String, chances: List<Double>, where: String) = viewModelScope.launch { book.addMarket(id, chances, where) }

    fun moveCheck(id: String, checkOn: String) {
        if (Forecasts.date(checkOn) == null) return
        viewModelScope.launch { book.update(id) { it.copy(checkOn = checkOn) } }
    }

    fun mark(id: String, happened: Int?, void: Boolean = false) = viewModelScope.launch { book.mark(id, happened, void) }

    /** How sound the reasoning was, rated after marking: "sound", "partly", "no", or blank to clear it. */
    fun rate(id: String, reasoning: String) = viewModelScope.launch { book.rate(id, reasoning) }

    fun delete(id: String) = viewModelScope.launch {
        if (_openId.value == id) _openId.value = null
        book.delete(id)
    }
}
