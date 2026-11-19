package app.localmind.assistant.data.desk

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A ticket as it stood on a data date, and how you marked it. */
@Serializable
data class JournalEntry(
    val dataDate: String,
    val strategy: String,
    val nextOpen: String,
    val trade: Boolean,
    /** "yes", "no" or "" (not marked yet). */
    val followed: String = "",
    /** entry, exit, management, sizing, skipped a ticket, none. */
    val mistake: String = "",
    val note: String = "",
    val at: Long = 0
)

/** A sensitivity verdict, valid only for the setting it was worked out for. */
@Serializable
data class NearbyVerdict(val paramsHash: String, val verdict: String, val at: Long)

@Serializable
data class DeskState(
    /** Your settings per strategy, where they differ from the default rule sheet. */
    val params: Map<String, Map<String, Double>> = emptyMap(),
    /** Strategies taken off the Desk. */
    val hidden: Set<String> = emptySet(),
    /** Every setting tried per strategy (by hash) and its in-sample daily Sharpe. */
    val trials: Map<String, Map<String, Double?>> = emptyMap(),
    val nearby: Map<String, NearbyVerdict> = emptyMap(),
    val journal: List<JournalEntry> = emptyList(),
    /** A repeating Prices order has been sent to the Lobster. */
    val dailyOrderSent: Boolean = false,
    val lastOrderAt: Long = 0,
    /** Where the prices came from (the Lobster, a file) and when they arrived. */
    val pricesFrom: String = "",
    val pricesAt: Long = 0,
    /** What the last check of the prices noticed. */
    val pricesNotes: List<String> = emptyList()
)

/**
 * SlothDesk's files on the tablet: the prices (desk/prices.json) and the Desk's state
 * (desk/state.json: your settings, the trial log, the paper journal). Private app storage,
 * written whole each time through a temporary file so a crash can't leave half a file.
 * Nothing here is sent anywhere.
 */
@Singleton
class DeskStore @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "desk").apply { mkdirs() }
    private val pricesFile = File(dir, "prices.json")
    private val stateFile = File(dir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    private val _state = MutableStateFlow(DeskState())
    val state: StateFlow<DeskState> = _state.asStateFlow()

    private val _prices = MutableStateFlow<PriceBundle?>(null)
    val prices: StateFlow<PriceBundle?> = _prices.asStateFlow()

    private var loaded = false

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        withContext(Dispatchers.IO) {
            _state.value = runCatching { json.decodeFromString(DeskState.serializer(), stateFile.readText()) }
                .getOrDefault(DeskState())
            _prices.value = runCatching { PriceBundle.parse(pricesFile.readText()) }.getOrNull()
        }
        loaded = true
    }

    suspend fun update(change: (DeskState) -> DeskState) = lock.withLock {
        val next = change(_state.value)
        _state.value = next
        withContext(Dispatchers.IO) { writeAtomic(stateFile, json.encodeToString(DeskState.serializer(), next)) }
    }

    /**
     * Checks a prices file and merges it into the stored prices. Returns what it noticed;
     * throws if nothing usable is in it.
     */
    suspend fun takePrices(text: String, from: String): List<String> {
        val (clean, problems) = withContext(Dispatchers.Default) { PriceBundle.validate(PriceBundle.parse(text)) }
        require(clean.series.isNotEmpty()) { "Nothing usable in that prices file" }
        lock.withLock {
            val merged = PriceBundle.merge(_prices.value, clean)
            withContext(Dispatchers.IO) { writeAtomic(pricesFile, PriceBundle.encode(merged)) }
            _prices.value = merged
        }
        update { it.copy(pricesFrom = from, pricesAt = System.currentTimeMillis(), pricesNotes = problems.take(12)) }
        return problems
    }

    /** The Desk's state for a backup (not the prices: they can be fetched again). */
    suspend fun backupText(): String? {
        load()
        return json.encodeToString(DeskState.serializer(), _state.value)
    }

    /**
     * Adds a backup's Desk to this one: its journal days, trials and nearby checks join yours
     * (yours win where both have the same day); its settings apply only where you have none.
     */
    suspend fun mergeBackup(text: String) {
        load()
        val b = json.decodeFromString(DeskState.serializer(), text)
        update { s ->
            val have = s.journal.map { it.strategy to it.dataDate }.toSet()
            s.copy(
                params = b.params + s.params,
                trials = (b.trials.keys + s.trials.keys).associateWith { k -> b.trials[k].orEmpty() + s.trials[k].orEmpty() },
                nearby = b.nearby + s.nearby,
                journal = (s.journal + b.journal.filter { (it.strategy to it.dataDate) !in have })
                    .sortedByDescending { it.dataDate }
            )
        }
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save ${file.name}" }
        }
    }

    companion object {
        val MISTAKES = listOf("entry", "exit", "management", "sizing", "skipped a ticket", "none")
    }
}
