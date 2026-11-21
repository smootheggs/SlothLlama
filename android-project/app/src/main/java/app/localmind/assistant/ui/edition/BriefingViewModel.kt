package app.localmind.assistant.ui.edition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.atlas.AtlasRepository
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.briefing.BriefingBook
import app.localmind.assistant.data.forecasts.ForecastBook
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.domain.atlas.PlaceHit
import app.localmind.assistant.domain.briefing.BriefStory
import app.localmind.assistant.domain.briefing.Briefing
import app.localmind.assistant.domain.briefing.Briefings
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.persona.BuiltInPersonas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * The briefing part of the Sloth Express: briefings you paste (Grok's, or anyone's in the same
 * shape), read in code; places found with the Atlas's names; watch items made into Forecasts
 * calls; and the Lobster or the Trader asked about a story. The tablet checks none of it.
 */
@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val book: BriefingBook,
    private val atlas: AtlasRepository,
    private val forecasts: ForecastBook,
    private val mailbox: LobsterMailbox,
    private val askInbox: AskInbox,
    private val attachments: AttachmentStore
) : ViewModel() {

    val briefings: StateFlow<List<Briefing>> = book.briefings

    private val _shown = MutableStateFlow<String?>(null)
    /** The briefing shown (its id); null for the newest. */
    val shown: StateFlow<String?> = _shown.asStateFlow()

    private val _places = MutableStateFlow<Map<String, PlaceHit>>(emptyMap())
    /** "briefingId:rank" → the first place the story names. */
    val places: StateFlow<Map<String, PlaceHit>> = _places.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            book.load()
            book.briefings.value.firstOrNull()?.let { placesFor(it) }
        }
    }

    fun show(id: String?) {
        _shown.value = id
        viewModelScope.launch { (book.briefings.value.firstOrNull { it.id == id } ?: book.briefings.value.firstOrNull())?.let { placesFor(it) } }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun placesFor(b: Briefing) {
        val finder = runCatching { atlas.finder() }.getOrNull() ?: return
        val found = b.stories.mapNotNull { s ->
            val hit = finder.find(s.region, 1).firstOrNull() ?: finder.find(s.headline + " " + s.facts, 1).firstOrNull()
            hit?.let { "${b.id}:${s.rank}" to it }
        }.toMap()
        _places.value = _places.value + found
    }

    /** Reads a pasted briefing and keeps it; what it found, in a line. */
    fun paste(text: String, source: String) = viewModelScope.launch {
        val clean = text.trim()
        if (clean.isEmpty()) return@launch
        val b = Briefings.parse(clean, UUID.randomUUID().toString(), System.currentTimeMillis(), source.trim().ifBlank { "Grok" })
        book.add(b)
        _shown.value = b.id
        placesFor(b)
        _message.value = if (b.stories.isEmpty()) "Kept as written: no numbered stories found in it." else
            "${b.stories.size} stories" + if (b.seen.isNotEmpty()) " and ${b.seen.size} things to watch" else ""
    }

    fun delete(id: String) = viewModelScope.launch {
        if (_shown.value == id) _shown.value = null
        book.delete(id)
    }

    /** Has the Lobster research the story, for its catch: the briefing's account checked against sources. */
    fun check(s: BriefStory) = viewModelScope.launch {
        try {
            mailbox.send(OrderKind.RESEARCH, s.headline + if (s.region.isNotBlank()) " (${s.region.substringBefore('/').trim()})" else "")
            _message.value = "Asked the Lobster to look into it 🦞 Its sources come back to the catch."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _message.value = "Couldn't reach the Lobster's mailbox: ${e.message ?: "unknown problem"}"
        }
    }

    /** Has the Lobster watch the news on a topic; it says when there's more. */
    fun watchNews(topic: String) = viewModelScope.launch {
        val t = topic.trim().take(120)
        if (t.isEmpty()) return@launch
        try {
            mailbox.send(OrderKind.WATCH, t)
            _message.value = "The Lobster will watch the news on it 🦞"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _message.value = "Couldn't reach the Lobster's mailbox: ${e.message ?: "unknown problem"}"
        }
    }

    /** Opens the Trader with the story attached; the question waits in the box. */
    fun askTrader(s: BriefStory, source: String) {
        askInbox.post(
            LaunchRequest.Shared(
                prompt = "What could this mean for markets, if anything? Keep to what the story says: it's $source's account, unchecked.",
                document = attachments.textDocument("Briefing · ${s.headline.take(60)}", Briefings.asText(s, source)),
                personaId = BuiltInPersonas.TRADER.id
            )
        )
    }

    /**
     * A watch item in Forecasts: your question (from what the briefing says it saw), checked in a
     * week by default. With a chance if you give one; without, it's only counted, not scored.
     */
    fun watch(question: String, checkOn: String, chance: Double?, why: String) = viewModelScope.launch {
        val q = question.trim().take(Forecasts.MAX_QUESTION)
        if (q.isEmpty() || Forecasts.date(checkOn) == null) return@launch
        val now = System.currentTimeMillis()
        forecasts.save(
            Call(
                id = UUID.randomUUID().toString(), question = q, area = Area.WATCHLIST, kind = CallKind.YES_NO,
                checkOn = checkOn, createdAt = now,
                estimates = chance?.let { listOf(Chance(LocalDate.now().toString(), now, Forecasts.binary(it), why.trim().take(400))) } ?: emptyList(),
                source = "briefing"
            )
        )
        _message.value = "On the watchlist in Forecasts, to check ${Forecasts.relative(Forecasts.date(checkOn)!!, LocalDate.now())}."
    }
}
