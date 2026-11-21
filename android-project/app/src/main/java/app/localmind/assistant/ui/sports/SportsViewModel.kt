package app.localmind.assistant.ui.sports

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.forecasts.ForecastBook
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.sports.GameNotes
import app.localmind.assistant.data.sports.SportsStore
import app.localmind.assistant.data.sports.SportsYours
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.sports.Factor
import app.localmind.assistant.domain.sports.GameRow
import app.localmind.assistant.domain.sports.League
import app.localmind.assistant.domain.sports.Market
import app.localmind.assistant.domain.sports.SportsBundle
import app.localmind.assistant.domain.sports.SportsFile
import app.localmind.assistant.domain.sports.SportsLines
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

enum class SportsTab(val label: String) { GAMES("Games"), TABLES("Tables"), PICKS("Your picks") }

/** Which game or race is open: "nba:401909305", "f1:21". */
data class Opened(val league: League, val id: String)

/**
 * Sports: results the Lobster brings, your prices and factors, the models, and picks that go to
 * Forecasts and mark themselves when the score comes in.
 */
@HiltViewModel
class SportsViewModel @Inject constructor(
    private val store: SportsStore,
    private val forecasts: ForecastBook,
    private val mailbox: LobsterMailbox
) : ViewModel() {

    val results: StateFlow<SportsBundle?> = store.results
    val yours: StateFlow<SportsYours> = store.yours
    val calls: StateFlow<List<Call>> = forecasts.calls

    private val _tab = MutableStateFlow(SportsTab.GAMES)
    val tab: StateFlow<SportsTab> = _tab.asStateFlow()

    private val _league = MutableStateFlow<League?>(null)
    /** The league shown; null until one is picked (then the first you follow). */
    val league: StateFlow<League?> = _league.asStateFlow()

    private val _opened = MutableStateFlow<Opened?>(null)
    val opened: StateFlow<Opened?> = _opened.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            store.load()
            forecasts.load()
            refresh()
        }
    }

    fun showTab(t: SportsTab) {
        _tab.value = t
    }

    fun showLeague(l: League) {
        _league.value = l
        _opened.value = null
    }

    fun open(o: Opened?) {
        _opened.value = o
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Takes any results the Lobster brought, then marks the picks they settle. */
    fun refresh() = viewModelScope.launch {
        if (mailbox.state.value.enabled) {
            runCatching { mailbox.refresh() }
            for (ci in mailbox.state.value.catch) {
                val c = ci.item ?: continue
                if (c.kind != "sports") continue
                val b64 = c.fileBase64 ?: continue
                try {
                    val bundle = withContext(Dispatchers.Default) { SportsFile.parse(String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)) }
                    store.take(bundle)
                    _message.value = "New results from the Lobster 🦞"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _message.value = "The Lobster's results couldn't be read: ${e.message ?: "unknown problem"}"
                }
                runCatching { mailbox.remove(ci) }
            }
        }
        settle()
    }

    /** Picks whose game is over get marked: it happened, it didn't, or (a line landed exactly) it doesn't count. */
    private suspend fun settle() {
        val bundle = store.results.value ?: return
        forecasts.load()
        for (call in forecasts.calls.value.filter { it.open && it.source.startsWith("sports:") }) {
            val pick = SportsLines.pickOf(call.source) ?: continue
            val data = bundle.leagues[pick.league.id] ?: continue
            val game = data.games.firstOrNull { it.id == pick.id }
            val race = pick.id.toIntOrNull()?.let { r -> data.races.firstOrNull { it.round == r } }
            val result = SportsLines.outcome(pick, game, race)
            when {
                result == true -> forecasts.mark(call.id, 0)
                result == false -> forecasts.mark(call.id, 1)
                game?.played == true -> forecasts.mark(call.id, null, void = true)
            }
        }
    }

    // --- asking the Lobster ---

    fun follow(ids: List<String>) = viewModelScope.launch { store.update { it.copy(follow = ids) } }

    /** The season so far once, then every morning: only the leagues you follow. */
    fun askLobster(daily: Boolean) = viewModelScope.launch {
        val y = store.yours.value
        val names = y.follow.joinToString(" ")
        if (names.isBlank()) {
            _message.value = "Pick a league to follow first."
            return@launch
        }
        try {
            if (!y.backfillSent || store.results.value == null) {
                mailbox.send(OrderKind.SPORTS, "Sports: $names from ${LocalDate.now().minusDays(120)}", deliver = "sports")
            } else {
                mailbox.send(OrderKind.SPORTS, "Sports: $names", deliver = "sports")
            }
            if (daily && !y.dailySent) mailbox.send(OrderKind.SPORTS, "Sports: $names every day at 06:15", deliver = "sports")
            store.update { it.copy(backfillSent = true, dailySent = it.dailySent || daily, lastOrderAt = System.currentTimeMillis()) }
            _message.value = "Asked the Lobster 🦞 Results arrive in a few minutes, if it's awake (it needs SlothLobster 1.9)."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _message.value = "Couldn't reach the Lobster's mailbox: ${e.message ?: "unknown problem"}"
        }
    }

    // --- your notes ---

    fun key(league: League, id: String) = "${league.id}:$id"

    fun saveNotes(league: League, id: String, notes: GameNotes) = viewModelScope.launch {
        store.update { it.copy(notes = it.notes + (key(league, id) to notes)) }
    }

    fun saveRaceFactors(round: Int, factors: List<Factor>) = viewModelScope.launch {
        store.update { it.copy(raceFactors = it.raceFactors + ("f1:$round" to factors)) }
    }

    /**
     * A pick, as a call in Forecasts: your chance (the model's, unless you moved it), the market's
     * if there's a price, checked on the day of the game. It marks itself from the score.
     */
    fun pick(league: League, id: String, day: LocalDate, market: Market, arg: String, question: String, yours: Double, marketChance: Double?, marketFrom: String, why: String) =
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toString()
            val source = SportsLines.source(league, id, market, arg)
            if (forecasts.calls.value.any { it.source == source && it.open }) {
                _message.value = "That pick is already in Forecasts."
                return@launch
            }
            val call = Call(
                id = UUID.randomUUID().toString(),
                question = question.take(Forecasts.MAX_QUESTION),
                area = Area.SPORTS,
                kind = CallKind.YES_NO,
                checkOn = day.toString(),
                createdAt = now,
                estimates = listOf(Chance(today, now, Forecasts.binary(yours), why.take(400))),
                market = marketChance?.let { listOf(Chance(today, now, Forecasts.binary(it), marketFrom.take(80))) } ?: emptyList(),
                source = source
            )
            forecasts.save(call)
            _message.value = "Picked: in Forecasts, and marked when the result comes in."
        }

    companion object {
        /** The local day a game starts on. */
        fun dayOf(g: GameRow, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            runCatching { Instant.parse(g.date.let { if (it.length == 17) it.replace("Z", ":00Z") else it }).atZone(zone).toLocalDate() }
                .getOrElse { runCatching { LocalDate.parse(g.date.take(10)) }.getOrDefault(LocalDate.now()) }
    }
}
