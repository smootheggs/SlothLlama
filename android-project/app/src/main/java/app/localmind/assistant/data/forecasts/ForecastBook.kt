package app.localmind.assistant.data.forecasts

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.Chance
import app.localmind.assistant.domain.forecasts.Forecasts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Your calls: one file in private storage (forecasts/calls.json), written whole through a
 * temporary file; in backups. A file that can't be read is set aside as calls.unreadable.json
 * rather than written over. SlothDesk's ideas aren't copied here: Forecasts reads them in place.
 */
@Singleton
class ForecastBook @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "forecasts").apply { mkdirs() }
    private val file = File(dir, "calls.json")
    private val lock = Mutex()
    private var loaded = false

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _calls.value = withContext(Dispatchers.IO) {
            if (!file.exists()) emptyList() else runCatching { Forecasts.parse(file.readText()) }.getOrElse {
                Log.w(TAG, "Couldn't read calls.json; set aside", it)
                runCatching { file.copyTo(File(dir, "calls.unreadable.json"), overwrite = true) }
                emptyList()
            }
        }
        loaded = true
    }

    private suspend fun change(edit: (List<Call>) -> List<Call>) {
        load()
        lock.withLock {
            val next = edit(_calls.value)
            if (next == _calls.value) return@withLock
            _calls.value = next
            withContext(Dispatchers.IO) { writeAtomic(Forecasts.encode(next)) }
        }
    }

    /** Adds [call], or replaces the one with its id. */
    suspend fun save(call: Call) = change { list ->
        if (list.any { it.id == call.id }) list.map { if (it.id == call.id) call else it } else list + call
    }

    suspend fun update(id: String, edit: (Call) -> Call) = change { list -> list.map { if (it.id == id) edit(it) else it } }

    suspend fun delete(id: String) = change { list -> list.filterNot { it.id == id } }

    suspend fun addChance(id: String, chances: List<Double>, reason: String, now: Long = System.currentTimeMillis()) =
        update(id) { it.copy(estimates = it.estimates + Chance(LocalDate.now().toString(), now, chances, reason.trim().take(400))) }

    suspend fun addMarket(id: String, chances: List<Double>, where: String, now: Long = System.currentTimeMillis()) =
        update(id) { it.copy(market = it.market + Chance(LocalDate.now().toString(), now, chances, where.trim().take(80))) }

    /** Marks what happened ([happened] = the outcome's index), or that it doesn't count; null and false reopen it. */
    suspend fun mark(id: String, happened: Int?, void: Boolean = false) = update(id) {
        it.copy(happened = happened, void = void, markedOn = if (happened == null && !void) "" else LocalDate.now().toString())
    }

    /** (1.41) Marks it with what settled it ([evidence]) and how you rated your reasoning, in one write. */
    suspend fun settle(id: String, happened: Int?, void: Boolean, evidence: String = "", reasoning: String = "") = update(id) {
        it.copy(
            happened = happened, void = void, markedOn = LocalDate.now().toString(),
            evidence = evidence.trim().take(200), reasoning = reasoning.ifBlank { it.reasoning }
        )
    }

    /** How you rate the reasoning behind a call you've marked: "sound", "partly", "no", or blank. */
    suspend fun rate(id: String, reasoning: String) = update(id) { it.copy(reasoning = reasoning) }

    suspend fun backupText(): String {
        load()
        return Forecasts.encode(_calls.value)
    }

    suspend fun mergeBackup(text: String) {
        val theirs = Forecasts.parse(text)
        change { mine -> Forecasts.merge(mine, theirs) }
    }

    private fun writeAtomic(text: String) {
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save the calls" }
        }
    }

    private companion object {
        const val TAG = "ForecastBook"
    }
}
