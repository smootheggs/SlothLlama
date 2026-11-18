package app.slothlobster.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The Lobster's jobs, watches, log and settings, in one JSON file in the app's private storage
 * (never backed up). Every change goes through [update], which saves at once.
 */
class Store(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _saved = MutableStateFlow(load())
    val saved: StateFlow<Saved> = _saved.asStateFlow()

    val value: Saved get() = _saved.value

    @Synchronized
    fun update(change: (Saved) -> Saved): Saved {
        val next = trim(change(_saved.value))
        _saved.value = next
        save(next)
        return next
    }

    fun updateJob(id: String, change: (Job) -> Job) = update { s ->
        s.copy(jobs = s.jobs.map { if (it.id == id) change(it) else it })
    }

    fun updateWatch(id: String, change: (Watch) -> Watch) = update { s ->
        s.copy(watches = s.watches.map { if (it.id == id) change(it) else it })
    }

    fun log(kind: String, what: String, detail: String = "") = update { s ->
        s.copy(log = listOf(LogEntry(System.currentTimeMillis(), kind, what, detail.take(300))) + s.log)
    }

    /** Keeps the file small: 30 days of log, the last 60 jobs, the last 100 deliveries. */
    private fun trim(s: Saved): Saved {
        val cutoff = System.currentTimeMillis() - LOG_DAYS * DAY_MS
        val log = s.log.filter { it.at >= cutoff }.take(MAX_LOG)
        val (open, closed) = s.jobs.partition { it.open }
        val jobs = open + closed.sortedByDescending { it.finishedAt ?: it.createdAt }.take(MAX_CLOSED_JOBS)
        return s.copy(
            log = log,
            jobs = jobs.sortedByDescending { it.createdAt },
            delivered = s.delivered.take(MAX_DELIVERED)
        )
    }

    private fun load(): Saved = runCatching {
        if (file.isFile) json.decodeFromString(Saved.serializer(), file.readText()) else Saved()
    }.getOrElse {
        Log.w(TAG, "Couldn't read the saved state; starting fresh", it)
        Saved()
    }

    private fun save(s: Saved) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), s))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "Couldn't save", it) }
    }

    companion object {
        private const val TAG = "Store"
        const val DAY_MS = 24L * 60 * 60 * 1000
        private const val LOG_DAYS = 30
        private const val MAX_LOG = 800
        private const val MAX_CLOSED_JOBS = 60
        private const val MAX_DELIVERED = 100
    }
}
