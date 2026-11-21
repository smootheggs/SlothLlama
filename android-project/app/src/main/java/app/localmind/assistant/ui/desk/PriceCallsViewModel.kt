package app.localmind.assistant.ui.desk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.data.forecasts.ForecastBook
import app.localmind.assistant.domain.forecasts.Area
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.CallKind
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Closes
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.forecasts.PriceCalls
import app.localmind.assistant.domain.forecasts.PriceCheck
import app.localmind.assistant.domain.forecasts.PriceTerms
import app.localmind.assistant.domain.forecasts.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Price calls on SlothDesk (1.41): a level by a date on GLD, SLV or USD/JPY, kept in your record
 * with the other calls. When the day has passed, the closes SlothDesk keeps propose the result,
 * with a second source where there is one; nothing is marked until you confirm it.
 */
@HiltViewModel
class PriceCallsViewModel @Inject constructor(
    private val desk: DeskStore,
    private val forecasts: ForecastBook
) : ViewModel() {

    val prices: StateFlow<PriceBundle?> = desk.prices

    /** Every price call, newest first. */
    val calls: StateFlow<List<Call>> = forecasts.calls
        .map { list -> list.filter { it.price != null }.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { desk.load() }
        viewModelScope.launch { forecasts.load() }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** A new price call with your chance (and a line on why). */
    fun call(terms: PriceTerms, chance: Double, why: String) = viewModelScope.launch {
        if (Forecasts.date(terms.on) == null || terms.level <= 0 || !terms.level.isFinite()) return@launch
        val now = System.currentTimeMillis()
        forecasts.save(
            Call(
                id = UUID.randomUUID().toString(),
                question = PriceCalls.question(terms),
                area = Area.MARKETS,
                kind = CallKind.YES_NO,
                checkOn = terms.on,
                createdAt = now,
                estimates = listOf(Chance(LocalDate.now().toString(), now, Forecasts.binary(chance), why.trim().take(400))),
                source = "price:${terms.series}",
                price = terms
            )
        )
        _message.value = "Called: ${PriceCalls.question(terms)}. It's checked against the closes after the day."
    }

    /** You confirm what the prices propose; the evidence stays on the call. */
    fun confirm(c: Call, check: PriceCheck, reasoning: String) = viewModelScope.launch {
        val happened = when (check.verdict) {
            Verdict.HAPPENED -> 0
            Verdict.DIDNT -> 1
            Verdict.ASK -> return@launch
        }
        forecasts.settle(c.id, happened, false, check.evidence, reasoning)
    }

    /** Marked by hand, when the prices can't settle it or you know better. */
    fun markByHand(c: Call, happened: Int?, void: Boolean, evidence: String, reasoning: String) = viewModelScope.launch {
        forecasts.settle(c.id, happened, void, if (evidence.isBlank()) "marked by hand" else "$evidence · marked by hand", reasoning)
    }

    fun delete(c: Call) = viewModelScope.launch { forecasts.delete(c.id) }

    companion object {
        /** A series of daily closes from SlothDesk's prices, if it's there and whole. */
        fun closes(b: PriceBundle?, name: String): Closes? =
            b?.series?.get(name)?.takeIf { it.kind == "bars" && it.closes.size == it.dates.size && it.dates.isNotEmpty() }
                ?.let { Closes(it.dates, it.closes, it.source) }

        /** The check for a price call against today's prices; null while it isn't time yet. */
        fun checkOf(c: Call, b: PriceBundle?, today: LocalDate = LocalDate.now()): PriceCheck? {
            val t = c.price ?: return null
            if (!c.open) return null
            return PriceCalls.check(t, closes(b, t.series), PriceCalls.SECOND[t.series]?.let { closes(b, it) }, today)
        }
    }
}
