package app.localmind.assistant.ui.desk

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.desk.DeskState
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.desk.JournalEntry
import app.localmind.assistant.data.desk.NearbyVerdict
import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.MailboxState
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.domain.desk.Costs
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.DeskMath
import app.localmind.assistant.domain.desk.DeskReports
import app.localmind.assistant.domain.desk.DeskStats
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Params
import app.localmind.assistant.domain.desk.StrategyReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** What the SlothDesk page shows. */
data class DeskUi(
    val ready: Boolean = false,
    val working: String? = null,
    val hasData: Boolean = false,
    val lastDate: LocalDate? = null,
    /** Business days since the latest close (stale above 4). */
    val ageDays: Int = 0,
    val series: List<String> = emptyList(),
    val reports: List<StrategyReport> = emptyList(),
    /** Strategies that can't run yet, and the series they're missing. */
    val waiting: Map<String, List<String>> = emptyMap(),
    val state: DeskState = DeskState()
)

/**
 * SlothDesk: the four starter strategies run on the tablet, on prices the Lobster fetched or a
 * file from Colab. Everything is worked out here, in code; a chat can explain a report but never
 * computes it. Nothing connects to a broker: tickets are for paper trading, or for you to act on
 * yourself.
 */
@HiltViewModel
class DeskViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DeskStore,
    private val mailbox: LobsterMailbox,
    private val attachments: AttachmentStore,
    private val askInbox: AskInbox
) : ViewModel() {

    private val _ui = MutableStateFlow(DeskUi())
    val ui: StateFlow<DeskUi> = _ui.asStateFlow()

    val lobster: StateFlow<MailboxState> = mailbox.state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val costs = Costs()
    private val work = Mutex()
    private var data: DeskData? = null

    init {
        viewModelScope.launch {
            store.load()
            takeFromMailbox()
            recompute()
        }
    }

    /** The page is shown: look for new prices from the Lobster. */
    fun refresh() = act(quiet = true) {
        if (takeFromMailbox()) recompute()
        null
    }

    /** Takes any prices the Lobster brought back, then removes them from the catch. */
    private suspend fun takeFromMailbox(): Boolean {
        if (!mailbox.state.value.enabled) return false
        runCatching { mailbox.refresh() }
        var took = false
        for (ci in mailbox.state.value.catch) {
            val c = ci.item ?: continue
            if (c.kind != "prices") continue
            val b64 = c.fileBase64 ?: continue
            try {
                val text = withContext(Dispatchers.Default) { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }
                val problems = store.takePrices(text, "the Lobster")
                took = true
                _message.value = if (problems.isEmpty()) "New prices from the Lobster 🦞" else "New prices, with ${problems.size} note(s)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = "The Lobster's prices couldn't be read: ${e.message ?: "unknown problem"}"
            }
            runCatching { mailbox.remove(ci) }
        }
        return took
    }

    // --- running the strategies ---

    private fun paramsFor(key: String, s: DeskState): Params {
        val t = DeskStrategies.template(key)
        val own = s.params[key].orEmpty()
        return Params(t.defaults.values + own, t.defaults.texts)
    }

    private suspend fun recompute() = work.withLock {
        _ui.update { it.copy(working = "Working out the strategies…") }
        try {
            val bundle = store.prices.value
            val d = withContext(Dispatchers.Default) { bundle?.let { PriceBundle.toDeskData(it) } }
            data = d
            var s = store.state.value
            if (d == null) {
                _ui.value = DeskUi(ready = true, state = s)
                return@withLock
            }
            val reports = ArrayList<StrategyReport>()
            val waiting = LinkedHashMap<String, List<String>>()
            val newTrials = HashMap<String, Map<String, Double?>>()
            val newJournal = ArrayList<JournalEntry>()
            for (t in DeskStrategies.templates) {
                if (t.key in s.hidden) continue
                val missing = DeskStrategies.missing(t.key, d)
                if (missing.isNotEmpty()) { waiting[t.key] = missing; continue }
                val p = paramsFor(t.key, s)
                val rep = withContext(Dispatchers.Default) {
                    val run = DeskStrategies.run(t.key, d, p, costs)
                    val hash = p.hash(costs)
                    val trials = s.trials[t.key].orEmpty().toMutableMap()
                    if (hash !in trials) {
                        trials[hash] = DeskReports.inSampleSharpe(run, d).takeIf { !it.isNaN() }
                        newTrials[t.key] = trials
                    }
                    val verdict = s.nearby[t.key]?.takeIf { it.paramsHash == hash }?.verdict
                    DeskReports.build(t.key, d, p, trials.values.toList(), verdict, paper(t.key, s), costs, run)
                }
                reports += rep
                val date = rep.ticket.dataDate.toString()
                if (s.journal.none { it.strategy == t.key && it.dataDate == date }) {
                    newJournal += JournalEntry(date, t.key, rep.ticket.nextOpen, rep.ticket.trade, at = System.currentTimeMillis())
                }
            }
            if (newTrials.isNotEmpty() || newJournal.isNotEmpty()) {
                store.update { st -> st.copy(trials = st.trials + newTrials, journal = (newJournal + st.journal).take(MAX_JOURNAL)) }
                s = store.state.value
            }
            val last = d.lastDate
            _ui.value = DeskUi(
                ready = true,
                hasData = true,
                lastDate = last,
                ageDays = DeskMath.businessDaysBetween(last, LocalDate.now()),
                series = bundle?.series?.keys?.toList().orEmpty(),
                reports = reports,
                waiting = waiting,
                state = s
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _ui.update { it.copy(ready = true, working = null) }
            _message.value = "SlothDesk couldn't run: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun paper(key: String, s: DeskState): DeskStats.Paper? {
        val mine = s.journal.filter { it.strategy == key }
        if (mine.isEmpty()) return null
        val tagged = mine.filter { it.followed == "yes" || it.followed == "no" }
        val share = if (tagged.isEmpty()) 0.0 else tagged.count { it.followed == "yes" }.toDouble() / tagged.size
        return DeskStats.Paper(mine.size, tagged.size, share)
    }

    // --- prices ---

    /** Asks the Lobster for prices once. */
    fun updateNow() = act {
        mailbox.send(OrderKind.PRICES, ORDER_TEXT, deliver = "desk")
        store.update { it.copy(lastOrderAt = System.currentTimeMillis()) }
        "Asked the Lobster for prices 🦞 They arrive in a few minutes, if it's awake."
    }

    /** Asks the Lobster for prices every day at [time] ("18:30"); sent once. */
    fun daily(time: String) = act {
        mailbox.send(OrderKind.PRICES, "$ORDER_TEXT every day at $time", deliver = "desk")
        store.update { it.copy(dailyOrderSent = true, lastOrderAt = System.currentTimeMillis()) }
        "The Lobster will fetch prices every day at $time. To stop, switch the repeat off in the Lobster."
    }

    /** A prices file from Colab (export_prices) or anywhere else that writes the format. */
    fun importFile(uri: Uri) = act {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                require(bytes.size <= MAX_FILE_BYTES) { "That file is too big for a prices file" }
                String(bytes, Charsets.UTF_8)
            } ?: error("Couldn't open that file")
        }
        val problems = store.takePrices(text, "a file")
        recompute()
        if (problems.isEmpty()) "Prices imported" else "Prices imported, with ${problems.size} note(s)"
    }

    // --- settings, trials and the nearby check ---

    /** Changes one setting (each new setting counts as a trial for the deflated Sharpe). */
    fun set(key: String, setting: String, value: Double) = act(quiet = true) {
        val t = DeskStrategies.template(key)
        val knob = t.knobs.first { it.key == setting }
        val v = value.coerceIn(knob.min, knob.max)
        store.update { s ->
            val own = s.params[key].orEmpty() + (setting to v)
            val trimmed = own.filter { (k, x) -> t.defaults.values[k] != x }
            s.copy(params = if (trimmed.isEmpty()) s.params - key else s.params + (key to trimmed))
        }
        recompute()
        null
    }

    fun reset(key: String) = act(quiet = true) {
        store.update { it.copy(params = it.params - key) }
        recompute()
        null
    }

    /** Runs the nearby grid on the in-sample years only; every cell is logged as a trial. */
    fun checkNearby(key: String) = act {
        val d = data ?: return@act null
        _ui.update { it.copy(working = "Trying nearby settings…") }
        val p = paramsFor(key, store.state.value)
        val (verdict, cells) = withContext(Dispatchers.Default) { DeskReports.nearby(key, d, p, costs) }
        store.update { s ->
            val trials = s.trials[key].orEmpty().toMutableMap()
            cells.forEach { (cp, sr) -> if (cp.hash(costs) !in trials) trials[cp.hash(costs)] = sr.takeIf { !it.isNaN() } }
            s.copy(trials = s.trials + (key to trials),
                nearby = s.nearby + (key to NearbyVerdict(p.hash(costs), verdict, System.currentTimeMillis())))
        }
        recompute()
        "Nearby settings: " + verdict.substringBefore(":")
    }

    fun hide(key: String) = act(quiet = true) {
        store.update { it.copy(hidden = it.hidden + key) }
        recompute()
        null
    }

    fun showAll() = act(quiet = true) {
        store.update { it.copy(hidden = emptySet()) }
        recompute()
        null
    }

    // --- the paper journal ---

    fun mark(key: String, dataDate: String, followed: Boolean, mistake: String = "none", note: String = "") = act(quiet = true) {
        store.update { s ->
            s.copy(journal = s.journal.map {
                if (it.strategy == key && it.dataDate == dataDate)
                    it.copy(followed = if (followed) "yes" else "no", mistake = if (followed) "none" else mistake, note = note.take(500))
                else it
            })
        }
        recompute()
        null
    }

    // --- asking a chat ---

    /** A new chat with the report attached. The chat explains; the numbers stay the Desk's. */
    fun askAbout(rep: StrategyReport) {
        val text = DeskText.report(rep, _ui.value.lastDate)
        askInbox.post(
            LaunchRequest.Shared(
                prompt = "Explain this SlothDesk report in plain words: what the strategy does, what the gates say, " +
                    "and what to watch. Use only the numbers in it; don't work out new ones.",
                document = attachments.textDocument("SlothDesk: ${rep.label}", text)
            )
        )
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun act(quiet: Boolean = false, block: suspend () -> String?) {
        viewModelScope.launch {
            try {
                block()?.let { _message.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.message?.takeIf { it.isNotBlank() } ?: if (quiet) "Something went wrong." else "That didn't work."
            } finally {
                _ui.update { it.copy(working = null) }
            }
        }
    }

    companion object {
        const val ORDER_TEXT = "Prices for SlothDesk: GLD SLV USDJPY DTB3 JP3M GVZ from 2008-01-01"
        private const val MAX_JOURNAL = 3_000
        private const val MAX_FILE_BYTES = 30 * 1024 * 1024
    }
}
