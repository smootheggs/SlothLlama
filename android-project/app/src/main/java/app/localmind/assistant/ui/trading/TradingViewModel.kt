package app.localmind.assistant.ui.trading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.data.trading.IdeaBook
import dagger.hilt.android.qualifiers.ApplicationContext
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.trading.Idea
import app.localmind.assistant.domain.trading.IdeaCard
import app.localmind.assistant.domain.trading.IdeaDraft
import app.localmind.assistant.domain.trading.IdeaStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** SlothDesk's Ideas and Calculators tabs: the idea book and the desk calculators. */
@HiltViewModel
class TradingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val book: IdeaBook,
    private val askInbox: AskInbox,
    private val attachments: AttachmentStore,
    private val desk: DeskStore
) : ViewModel() {

    /** The calculators' last inputs, kept between visits (on this tablet only, not in backups). */
    val inputs = CalcInputs(context.getSharedPreferences("trading_inputs", Context.MODE_PRIVATE))

    /** The latest numbers SlothDesk has that the calculators can start from. */
    val deskFacts: StateFlow<DeskFacts?> = desk.prices
        .map { b -> b?.let { DeskFacts.from(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ideas: StateFlow<List<Idea>> = book.ideas
    val stats: StateFlow<IdeaStats> = book.ideas
        .map { IdeaStats.of(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, IdeaStats.of(emptyList(), LocalDate.now()))
    val draft: StateFlow<IdeaDraft?> = book.draft

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { book.load() }
        viewModelScope.launch { runCatching { desk.load() } }
    }

    fun newIdea() = book.setDraft(IdeaDraft(reviewOn = LocalDate.now().plusMonths(1).toString()))

    /** A Trader reply's idea card, into the editor. Returns false when the reply has no card. */
    fun draftFrom(reply: String): Boolean {
        val d = IdeaCard.parse(reply) ?: return false
        book.setDraft(d.copy(reviewOn = LocalDate.now().plusMonths(1).toString()))
        return true
    }

    fun editDraft(d: IdeaDraft) = book.setDraft(d)

    fun cancelDraft() = book.setDraft(null)

    fun saveDraft() {
        val d = draft.value ?: return
        if (!canSave(d)) return
        viewModelScope.launch {
            book.add(d)
            book.setDraft(null)
            _message.value = "Saved. The thesis and levels are fixed now; add notes as it plays out."
        }
    }

    fun addNote(idea: Idea, text: String) {
        viewModelScope.launch { book.addNote(idea.id, text) }
    }

    fun setReviewOn(idea: Idea, date: String) {
        viewModelScope.launch { book.setReviewOn(idea.id, date) }
    }

    fun close(idea: Idea, outcome: String, reason: String, exit: String, whatHappened: String, lesson: String) {
        viewModelScope.launch {
            book.close(idea.id, outcome, reason, exit.trim().replace(",", "").toDoubleOrNull(), whatHappened, lesson)
            _message.value = "Reviewed"
        }
    }

    fun delete(idea: Idea) {
        viewModelScope.launch { book.delete(idea.id) }
    }

    /** The Trader takes the other side of an idea, before or after. */
    fun askTrader(idea: Idea) {
        val prompt = if (idea.isOpen) {
            "Challenge this idea as a senior trader would: is the thesis clear, is \"wrong if\" specific enough, " +
                "do the levels fit the horizon, what am I missing, and how would I pitch it to a client? Use only the numbers in it."
        } else {
            "Review this closed idea with me: was I right for the reason I gave, what should I have seen earlier, " +
                "and what's the one habit to keep or change? Use only the numbers in it."
        }
        ask(prompt, "Idea: ${idea.instrument}", idea.asText())
    }

    /** A calculator's inputs and results, for the Trader to explain (the sums are already done). */
    fun askAboutNumbers(title: String, text: String) {
        ask(
            "Explain these numbers the way you would to a client, and what a trader would watch in them. " +
                "They're already worked out: use them as they are and don't calculate new ones.",
            title, text
        )
    }

    private fun ask(prompt: String, title: String, text: String) {
        askInbox.post(
            LaunchRequest.Shared(prompt = prompt, document = attachments.textDocument(title, text), personaId = BuiltInPersonas.TRADER.id)
        )
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        /** An idea needs what it's on, why, and what would prove it wrong. */
        fun canSave(d: IdeaDraft) = d.instrument.isNotBlank() && d.thesis.isNotBlank() && d.wrongIf.isNotBlank()
    }
}

/** A latest value from SlothDesk's prices, with its date. */
data class DeskFact(val value: Double, val date: String)

/** What the calculators can borrow from SlothDesk: the 3-month T-bill and USD/JPY. */
data class DeskFacts(val usdRate: DeskFact?, val usdJpy: DeskFact?) {
    companion object {
        fun from(b: PriceBundle): DeskFacts {
            fun last(name: String, closes: Boolean): DeskFact? {
                val s = b.series[name] ?: return null
                val v = (if (closes) s.closes else s.values).lastOrNull() ?: return null
                val d = s.dates.lastOrNull() ?: return null
                return DeskFact(v, d)
            }
            return DeskFacts(usdRate = last("DTB3", closes = false), usdJpy = last("USDJPY", closes = true))
        }
    }
}

/**
 * The calculators' inputs as text, kept in a small settings file so the numbers are still there
 * next time. Reading one inside a composable follows its changes.
 */
class CalcInputs(private val prefs: SharedPreferences) {
    private val values = mutableStateMapOf<String, String>().apply {
        prefs.all.forEach { (k, v) -> (v as? String)?.let { put(k, it) } }
    }

    fun get(key: String, default: String = ""): String = values[key] ?: default

    fun set(key: String, value: String) {
        values[key] = value
        prefs.edit().putString(key, value).apply()
    }

    /** True once you've typed something for [key] (so a borrowed number doesn't overwrite it). */
    fun has(key: String): Boolean = key in values
}
