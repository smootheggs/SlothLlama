package app.slothlobster.core

import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import app.slothlobster.jobs.Engine
import app.slothlobster.jobs.RepeatRule
import app.slothlobster.jobs.Repeats
import app.slothlobster.web.Feeds
import app.slothlobster.mailbox.KeyVault
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
import java.io.InputStream
import java.util.Calendar
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
 * The SlothLobster: its mailbox, its saved state and its engine, one per app. The screens,
 * the awake service and the 15-minute check all go through here.
 */
class Lobster private constructor(val context: Context) {

    // Not backed up (the manifest turns backups off): the pairing key stays on this device.
    private val prefs = context.getSharedPreferences("lobster", Context.MODE_PRIVATE)
    val mailbox = Mailbox(context, prefs)
    val store = Store(File(context.filesDir, "lobster.json"))
    /** The full text of what it brought back, to read here. */
    val shelf = Shelf(File(context.filesDir, "catch"))
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

    /** A new order given here. With [repeat], it runs now and then again on that schedule. */
    fun newOrder(kind: OrderKind, text: String, deep: Boolean, repeat: RepeatRule? = null) {
        val now = System.currentTimeMillis()
        val repeatId = repeat?.let { UUID.randomUUID().toString() }
        val job = Job(
            id = UUID.randomUUID().toString(),
            from = From.MINE,
            kind = kind.id,
            text = text.trim().take(4_000),
            deep = deep,
            createdAt = now,
            repeatId = repeatId
        )
        store.update { s ->
            val repeats = if (repeat == null || repeatId == null) s.repeats else s.repeats + Repeat(
                id = repeatId, kind = kind.id, text = job.text, deep = deep, from = From.MINE,
                everyDays = repeat.everyDays, weekday = repeat.weekday, hour = repeat.hour, minute = repeat.minute,
                createdAt = now, lastRunDay = Repeats.dayKey(Calendar.getInstance())
            )
            s.copy(jobs = listOf(job) + s.jobs, repeats = repeats)
        }
        if (repeat != null) store.log("order", "Set to repeat", "${kind.label} · ${repeat.describe()}")
        engine.poke()
    }

    fun setRepeatOn(id: String, on: Boolean) {
        store.updateRepeat(id) { it.copy(enabled = on) }
    }

    fun removeRepeat(id: String) {
        store.update { s -> s.copy(repeats = s.repeats.filterNot { it.id == id }) }
    }

    fun cancel(jobId: String) {
        val job = store.value.jobs.find { it.id == jobId } ?: return
        if (!job.open) return
        store.updateJob(jobId) { it.copy(state = JobState.STOPPED, note = "Cancelled", finishedAt = System.currentTimeMillis()) }
        if (job.from == From.TABLET) scope.launch { engine.tellSlothLlama(job, "Cancelled in the Lobster", "You cancelled this order in the SlothLobster app.") }
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

    fun addWatch(target: String, everyHours: Int, keywords: List<String> = emptyList()) {
        val link = Web.firstLink(target)?.let(Web::safeUrl)
        val goal = link ?: target.trim().take(120)
        if (goal.isBlank()) return
        val watch = Watch(
            id = UUID.randomUUID().toString(),
            target = goal,
            title = if (link != null) Web.site(link) else "News · $goal",
            everyHours = everyHours,
            createdAt = System.currentTimeMillis(),
            keywords = keywords
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

    /** Watch only one part of the page and/or lines with some words. Starts a fresh comparison. */
    fun setWatchFilter(id: String, section: String, keywords: List<String>) {
        store.updateWatch(id) {
            it.copy(section = section.trim(), keywords = keywords, lastHash = "", lastLines = emptyList(), lastChecked = 0)
        }
        store.log("watch", "Narrowed a watch", listOfNotNull(section.takeIf { it.isNotBlank() }?.let { "Part: $it" },
            keywords.takeIf { it.isNotEmpty() }?.let { "Words: ${it.joinToString(", ")}" }).joinToString(" · ").ifBlank { "Whole page" })
        engine.poke()
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
            // The code is the mailbox key: don't leave it on the clipboard for other apps.
            runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
            store.log("mailbox", "Paired with SlothLlama", "Code ending ${mailbox.codeHint()}")
            scope.launch { refreshLive(); engine.poke() }
        }
        return ok
    }

    // --- market data key (SlothDesk's prices) ---

    /** Wrapped by its own Android Keystore key, like the pairing code; never shown again once saved. */
    private val dataKeys = KeyVault(prefs, alias = "slothlobster-data-tiingo", name = "tiingo_wrapped")

    fun tiingoKey(): String? = runCatching { dataKeys.loadText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun hasTiingoKey(): Boolean = tiingoKey() != null

    /** Saves (or with null, removes) the Tiingo key. False if it doesn't look like one. */
    fun setTiingoKey(key: String?): Boolean {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) {
            dataKeys.clear()
            store.log("settings", "Removed the market data key")
            return true
        }
        if (!Regex("^[A-Za-z0-9]{20,64}$").matches(k)) return false
        dataKeys.saveText(k)
        runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
        store.log("settings", "Saved a market data key", "Tiingo, ending …${k.takeLast(4)}")
        return true
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

    /** The feeds list as typed in Settings, with "# Group" lines. */
    fun setFeeds(text: String) {
        val list = Feeds.parseList(text)
        settings {
            it.copy(
                feeds = list.map { p -> p.second },
                feedGroups = list.filter { p -> p.first.isNotBlank() }.associate { p -> p.second to p.first }
            )
        }
    }

    /** Adds the feeds in an OPML file exported from a news reader, keeping its folders as groups. */
    fun importOpml(uri: Uri) {
        scope.launch {
            val found = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val buf = input.readNBytesCompat(2 * 1024 * 1024 + 1)
                    require(buf.size <= 2 * 1024 * 1024) { "That file is too big for a feeds list" }
                    buf
                } ?: error("Couldn't open the file")
                Feeds.parseOpml(String(bytes))
            }.getOrElse {
                store.log("problem", "Couldn't import feeds", it.message.orEmpty())
                return@launch
            }
            if (found.isEmpty()) {
                store.log("problem", "No feeds in that file", "It should be an OPML file from a news reader")
                return@launch
            }
            settings { s ->
                val feeds = (s.feeds + found.map { it.second }).distinct()
                val groups = s.feedGroups + found.filter { it.first.isNotBlank() }.associate { it.second to it.first }
                s.copy(feeds = feeds, feedGroups = groups)
            }
            store.log("feed", "Imported feeds", "${found.size} from a news reader file")
        }
    }

    fun loadCatch(id: String): KeptCatch? = shelf.load(id)

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

/** Reads up to [max] bytes (InputStream.readNBytes needs Android 13). */
private fun InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
