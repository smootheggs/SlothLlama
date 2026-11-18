package app.slothlobster.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import app.slothlobster.jobs.Engine
import app.slothlobster.mailbox.Mailbox
import app.slothlobster.mailbox.OrderKind
import app.slothlobster.run.LobsterService
import app.slothlobster.web.Web
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** What the screens show besides the saved state: the mailbox and whether the Lobster is up. */
data class Live(
    val paired: Boolean = false,
    val codeHint: String? = null,
    val folderName: String? = null,
    /** A folder was chosen but no longer opens (moved, deleted, permission gone). */
    val folderMissing: Boolean = false,
    val paused: Boolean = false,
    /** Catch files SlothLlama hasn't filed yet. */
    val waitingCatch: Set<String> = emptySet(),
    /** The awake service is running (otherwise it checks every 15 minutes). */
    val awake: Boolean = false,
    val workingId: String? = null,
    val lastSync: Long = 0,
    val lastPass: Long = 0,
    /** Why it isn't working right now ("Waiting for Wi-Fi"), if it isn't. */
    val waitingFor: String? = null
) {
    val mailboxReady: Boolean get() = paired && folderName != null
}

/**
 * The Sloth Lobster: its mailbox, its saved state and its engine, one per app. The screens,
 * the awake service and the 15-minute check all go through here.
 */
class Lobster private constructor(val context: Context) {

    // Not backed up (the manifest turns backups off): the pairing key stays on this device.
    private val prefs = context.getSharedPreferences("lobster", Context.MODE_PRIVATE)
    val mailbox = Mailbox(context, prefs)
    val store = Store(File(context.filesDir, "lobster.json"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live.asStateFlow()

    val engine = Engine(this)

    init {
        // A job cut off when the app was closed starts again.
        store.update { s ->
            s.copy(jobs = s.jobs.map { if (it.state == JobState.WORKING) it.copy(state = JobState.QUEUED, sources = emptyList()) else it })
        }
        scope.launch { refreshLive() }
    }

    fun setLive(change: (Live) -> Live) {
        _live.value = change(_live.value)
    }

    /** Re-reads the mailbox's pairing, folder and pause switch. Touches files: call off the main thread. */
    fun refreshLive() {
        val ready = mailbox.isReady()
        val folderName = runCatching { mailbox.folderName() }.getOrNull()
        val paused = isPaused()
        val waiting = if (ready) runCatching { mailbox.waitingCatch() }.getOrDefault(emptySet()) else emptySet()
        setLive {
            it.copy(
                paired = mailbox.isPaired,
                codeHint = mailbox.codeHint(),
                folderName = folderName,
                folderMissing = folderName == null && mailbox.hasFolderChoice,
                paused = paused,
                waitingCatch = waiting,
                awake = LobsterService.running
            )
        }
    }

    /** With a mailbox, its STOP file is the pause switch (SlothLlama can flip it too). */
    fun isPaused(): Boolean =
        if (mailbox.isReady()) runCatching { mailbox.isStopped() }.getOrDefault(false) else store.value.settings.pausedHere

    /** Null when it can go online now, or why not. */
    fun offlineReason(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "No connection"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "No connection"
        if (store.value.settings.wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return "Waiting for Wi-Fi"
        return null
    }

    // --- orders ---

    fun newOrder(kind: OrderKind, text: String, deep: Boolean) {
        val job = Job(
            id = UUID.randomUUID().toString(),
            from = From.MINE,
            kind = kind.id,
            text = text.trim().take(4_000),
            deep = deep,
            createdAt = System.currentTimeMillis()
        )
        store.update { it.copy(jobs = listOf(job) + it.jobs) }
        engine.poke()
    }

    fun cancel(jobId: String) {
        val job = store.value.jobs.find { it.id == jobId } ?: return
        if (!job.open) return
        store.updateJob(jobId) { it.copy(state = JobState.STOPPED, note = "Cancelled", finishedAt = System.currentTimeMillis()) }
        if (job.from == From.TABLET) scope.launch { engine.tellSlothLlama(job, "Cancelled in the Lobster", "You cancelled this order in the Sloth Lobster app.") }
    }

    fun sendNow(jobId: String) {
        store.updateJob(jobId) { it.copy(sendNow = true) }
    }

    fun again(jobId: String) {
        store.updateJob(jobId) {
            it.copy(
                state = JobState.QUEUED, sources = emptyList(), sendNow = false, startedAt = null,
                finishedAt = null, resultTitle = null, resultPreview = null, note = null,
                createdAt = System.currentTimeMillis()
            )
        }
        engine.poke()
    }

    fun remove(jobId: String) {
        cancel(jobId)
        store.update { s -> s.copy(jobs = s.jobs.filterNot { it.id == jobId }) }
    }

    // --- pause ---

    fun setPaused(on: Boolean) {
        store.update { it.copy(settings = it.settings.copy(pausedHere = on)) }
        scope.launch {
            runCatching { mailbox.setStop(on) }
            refreshLive()
            engine.heartbeat()
            if (!on) engine.poke()
        }
    }

    /** Pauses and stops the job in hand at once. */
    fun stopEverything() {
        live.value.workingId?.let { id ->
            store.updateJob(id) { it.copy(state = JobState.STOPPED, note = "Stopped by you", finishedAt = System.currentTimeMillis()) }
        }
        setPaused(true)
        store.log("problem", "Stopped everything", "Paused until you resume")
    }

    // --- watches ---

    fun addWatch(target: String, everyHours: Int) {
        val link = Web.firstLink(target)?.let(Web::safeUrl)
        val goal = link ?: target.trim().take(120)
        if (goal.isBlank()) return
        val watch = Watch(
            id = UUID.randomUUID().toString(),
            target = goal,
            title = if (link != null) Web.site(link) else "News · $goal",
            everyHours = everyHours,
            createdAt = System.currentTimeMillis()
        )
        store.update { it.copy(watches = listOf(watch) + it.watches) }
        engine.poke()
    }

    fun setWatchOn(id: String, on: Boolean) {
        store.updateWatch(id) { it.copy(enabled = on) }
    }

    fun setWatchEvery(id: String, hours: Int) {
        store.updateWatch(id) { it.copy(everyHours = hours) }
    }

    fun checkWatchNow(id: String) {
        store.updateWatch(id) { it.copy(lastChecked = 0) }
        engine.poke()
    }

    fun removeWatch(id: String) {
        store.update { s -> s.copy(watches = s.watches.filterNot { it.id == id }) }
    }

    // --- setup ---

    fun pair(code: String): Boolean {
        val ok = mailbox.pair(code)
        if (ok) {
            store.log("mailbox", "Paired with SlothLlama", "Code ending ${mailbox.codeHint()}")
            scope.launch { refreshLive(); engine.poke() }
        }
        return ok
    }

    fun unpair() {
        mailbox.unpair()
        store.log("mailbox", "Unpaired from SlothLlama")
        scope.launch { refreshLive() }
    }

    fun setFolder(uri: Uri) {
        scope.launch {
            runCatching { mailbox.setFolder(uri) }
                .onSuccess { store.log("mailbox", "Mailbox folder set", mailbox.folderName().orEmpty()) }
                .onFailure { store.log("problem", "Couldn't use that folder", it.message.orEmpty()) }
            refreshLive()
            engine.poke()
        }
    }

    fun settings(change: (Settings) -> Settings) {
        val before = store.value.settings
        val after = store.update { it.copy(settings = change(it.settings)) }.settings
        if (before.stayAwake != after.stayAwake) applyAwake()
        if (before.wifiOnly && !after.wifiOnly) engine.poke()
    }

    fun briefNow() = engine.requestBrief()

    /** Starts or stops the awake service to match the setting. */
    fun applyAwake() {
        if (store.value.settings.stayAwake) LobsterService.start(context) else LobsterService.stop(context)
        scope.launch { refreshLive() }
    }

    companion object {
        @Volatile private var instance: Lobster? = null

        fun get(context: Context): Lobster =
            instance ?: synchronized(this) {
                instance ?: Lobster(context.applicationContext).also { instance = it }
            }
    }
}
