package app.localmind.assistant.ui.world

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.forecasts.ForecastBook
import app.localmind.assistant.data.lobster.CaughtItem
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.world.WorldStore
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.world.WorldData
import app.localmind.assistant.domain.world.WorldMarket
import app.localmind.assistant.domain.world.WorldYours
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class WorldTab(val label: String) { FOLLOWING("Following"), CALLS("Your calls"), SETTLED("Settled") }

/** News on one of your calls: a Lobster alert from the watch tied to it. */
data class News(val catchId: String, val callId: String, val title: String, val text: String, val at: Long)

/**
 * World (1.42): events you follow on Polymarket and Kalshi, the crowd's chance as the reference your
 * calls are measured against, settlements proposed for you to confirm, and news on a call from a
 * Lobster watch. The Lobster only reads public prices; nothing is traded.
 */
@HiltViewModel
class WorldViewModel @Inject constructor(
    private val store: WorldStore,
    private val forecasts: ForecastBook,
    private val mailbox: LobsterMailbox
) : ViewModel() {

    val yours: StateFlow<WorldYours> = store.yours

    /** Your World calls (on a market) and the briefing's watch items, newest first. */
    val calls: StateFlow<List<Call>> = forecasts.calls
        .map { list -> list.filter { it.source.startsWith("world:") || it.source == "briefing" }.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tab = MutableStateFlow(WorldTab.FOLLOWING)
    val tab: StateFlow<WorldTab> = _tab.asStateFlow()

    /** The market open ("platform:id"), or a briefing watch item's call id. */
    private val _opened = MutableStateFlow<String?>(null)
    val opened: StateFlow<String?> = _opened.asStateFlow()

    private val _news = MutableStateFlow<List<News>>(emptyList())
    val news: StateFlow<List<News>> = _news.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // Loading only: the World page itself takes new prices and asks the Lobster (refresh), so
        // Home can read what's kept without sending anything.
        viewModelScope.launch {
            store.load()
            forecasts.load()
        }
    }

    fun showTab(t: WorldTab) {
        _tab.value = t
    }

    fun open(key: String?) {
        _opened.value = key
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Takes any prices the Lobster brought, reads news on your calls, and asks for fresh prices when
     * the last are over six hours old (only for what you follow, and only if the Lobster is on).
     */
    fun refresh() = viewModelScope.launch {
        if (!mailbox.state.value.enabled) return@launch
        runCatching { mailbox.refresh() }
        for (ci in mailbox.state.value.catch) {
            val c = ci.item ?: continue
            if (c.kind != "world") continue
            val b64 = c.fileBase64 ?: continue
            try {
                val bundle = withContext(Dispatchers.Default) { WorldData.parse(String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)) }
                store.take(bundle)
                appendCrowd()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = "The Lobster's prices couldn't be read: ${e.message ?: "unknown problem"}"
            }
            runCatching { mailbox.remove(ci) }
        }
        readNews(mailbox.state.value.catch)
        val y = store.yours.value
        if (WorldData.stale(y, System.currentTimeMillis())) ask(quiet = true)
    }

    /** Asks the Lobster for the crowd's prices on everything you follow. */
    fun ask(quiet: Boolean = false) = viewModelScope.launch {
        val y = store.yours.value
        if (y.follow.isEmpty()) return@launch
        try {
            mailbox.send(OrderKind.WORLD, "World: " + y.follow.joinToString(" "), deliver = "world")
            store.update { it.copy(lastAskedAt = System.currentTimeMillis()) }
            if (!quiet) _message.value = "Asked the Lobster for the crowd's prices 🦞 (it needs SlothLobster 1.10)."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!quiet) _message.value = "Couldn't reach the Lobster's mailbox: ${e.message ?: "unknown problem"}"
        }
    }

    /** Follows a market from a pasted link or ref; false if it can't be read. */
    fun follow(text: String): Boolean {
        val ref = WorldData.refOf(text) ?: return false
        viewModelScope.launch {
            if (ref in store.yours.value.follow) {
                _message.value = "Already followed."
                return@launch
            }
            if (store.yours.value.follow.size >= WorldData.MAX_FOLLOW) {
                _message.value = "That's ${WorldData.MAX_FOLLOW} already: unfollow one first."
                return@launch
            }
            store.update { it.copy(follow = it.follow + ref) }
            try {
                mailbox.send(OrderKind.WORLD, "World: $ref", deliver = "world")
                store.update { it.copy(lastAskedAt = System.currentTimeMillis()) }
                _message.value = "Following. The Lobster brings its prices in a few minutes 🦞"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = "Following; the Lobster's mailbox couldn't be reached, so prices come later."
            }
        }
        return true
    }

    fun unfollow(ref: String) = viewModelScope.launch {
        store.update { y -> y.copy(follow = y.follow - ref, markets = y.markets.filterValues { it.ref != ref }) }
        if (_opened.value?.let { k -> store.yours.value.markets[k] == null } == true) _opened.value = null
    }

    /**
     * A call on a market: your chance, with the crowd's chance at the time as its reference,
     * checked when trading ends. It stays open until you confirm the settlement.
     */
    fun call(m: WorldMarket, chance: Double, checkOn: String, why: String) = viewModelScope.launch {
        val source = WorldData.source(m)
        if (forecasts.calls.value.any { it.source == source && it.open }) {
            _message.value = "You've a call on this one already: change your chance on it instead."
            return@launch
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        forecasts.save(
            Call(
                id = UUID.randomUUID().toString(),
                question = m.question.take(Forecasts.MAX_QUESTION),
                area = Area.POLITICS,
                kind = CallKind.YES_NO,
                checkOn = checkOn,
                createdAt = now,
                estimates = listOf(Chance(today, now, Forecasts.binary(chance), why.trim().take(400))),
                market = m.chance?.let { listOf(Chance(today, now, Forecasts.binary(it), "the crowd · ${m.platform}")) } ?: emptyList(),
                source = source
            )
        )
        _message.value = "Called. Measured against the crowd's ${m.chance?.let { "${Forecasts.percent(it)}%" } ?: "price"} when you called."
    }

    /** A question of your own in World, with no market: counted in World's record, marked by you. */
    fun own(question: String, chance: Double, checkOn: String, why: String) = viewModelScope.launch {
        val q = question.trim().take(Forecasts.MAX_QUESTION)
        if (q.isEmpty() || Forecasts.date(checkOn) == null) return@launch
        val now = System.currentTimeMillis()
        forecasts.save(
            Call(
                id = UUID.randomUUID().toString(), question = q, area = Area.POLITICS, kind = CallKind.YES_NO,
                checkOn = checkOn, createdAt = now,
                estimates = listOf(Chance(LocalDate.now().toString(), now, Forecasts.binary(chance), why.trim().take(400))),
                source = "world:own"
            )
        )
        _message.value = "Called. It's in Your calls, to mark when its day comes."
    }

    /** Your chance moved (by a news prompt's −5/+5, or anew). */
    fun changeChance(c: Call, chance: Double, why: String) = viewModelScope.launch {
        forecasts.addChance(c.id, Forecasts.binary(chance), why)
    }

    /** You confirm what the market's settlement says; its source stays on the call. */
    fun confirm(c: Call, m: WorldMarket, happened: Int, reasoning: String) = viewModelScope.launch {
        forecasts.settle(c.id, happened, false, "settled ${m.result} on ${platformName(m.platform)}", reasoning)
    }

    fun mark(c: Call, happened: Int?, void: Boolean, reasoning: String) = viewModelScope.launch {
        forecasts.settle(c.id, happened, void, if (void || c.source == "world:own" || c.source == "briefing") "" else "marked by hand", reasoning)
    }

    /** Has the Lobster watch the news on a call's question; its alerts come back here as news on the call. */
    fun watchNews(c: Call, topic: String) = viewModelScope.launch {
        val t = topic.trim().take(120)
        if (t.isEmpty()) return@launch
        try {
            val order = mailbox.send(OrderKind.WATCH, t)
            store.update { it.copy(watches = it.watches + (order.id to c.id)) }
            _message.value = "The Lobster will watch the news on it; alerts show here, on the call 🦞"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _message.value = "Couldn't reach the Lobster's mailbox: ${e.message ?: "unknown problem"}"
        }
    }

    /** News shown on its call; it goes once you've kept or changed your chance. */
    fun seen(n: News) = viewModelScope.launch {
        store.update { it.copy(seenNews = (it.seenNews + n.catchId).takeLast(200)) }
        _news.value = _news.value.filterNot { it.catchId == n.catchId }
    }

    private fun readNews(catch: List<CaughtItem>) {
        val y = store.yours.value
        if (y.watches.isEmpty()) return
        val open = forecasts.calls.value.filter { it.open }.map { it.id }.toSet()
        _news.value = catch.mapNotNull { ci ->
            val c = ci.item ?: return@mapNotNull null
            if (c.kind != "alert" || c.id in y.seenNews) return@mapNotNull null
            val callId = c.orderId?.let { y.watches[it] }?.takeIf { it in open } ?: return@mapNotNull null
            News(c.id, callId, c.title, c.text, c.createdAt)
        }.sortedByDescending { it.at }
    }

    /** Each day the prices come in, the crowd's chance joins the trail of your open calls on that market. */
    private suspend fun appendCrowd() {
        val y = store.yours.value
        val today = LocalDate.now().toString()
        for (c in forecasts.calls.value.filter { it.open && it.source.startsWith("world:") }) {
            val m = WorldData.keyOf(c)?.let { y.markets[it] } ?: continue
            val p = m.chance ?: continue
            val last = c.market.lastOrNull()
            if (last != null && last.on == today) continue
            if (last != null && Forecasts.percent(last.chances[0]) == Forecasts.percent(p)) continue
            forecasts.addMarket(c.id, Forecasts.binary(p), "the crowd · ${m.platform}")
        }
    }

    companion object {
        fun platformName(p: String): String = when (p) {
            "polymarket" -> "Polymarket"
            "kalshi" -> "Kalshi"
            else -> p
        }
    }
}
