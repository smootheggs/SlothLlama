package app.localmind.assistant.ui.desk

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.desk.DeskState
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.desk.JournalEntry
import app.localmind.assistant.data.desk.NearbyVerdict
import app.localmind.assistant.data.desk.PriceBundle
import app.localmind.assistant.data.desk.SavedRule
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
import app.localmind.assistant.domain.desk.Action
import app.localmind.assistant.domain.desk.Against
import app.localmind.assistant.domain.desk.Branch
import app.localmind.assistant.domain.desk.Condition
import app.localmind.assistant.domain.desk.Op
import app.localmind.assistant.domain.desk.Rule
import app.localmind.assistant.domain.desk.RuleCheck
import app.localmind.assistant.domain.desk.RuleEngine
import app.localmind.assistant.domain.desk.RuleFiles
import app.localmind.assistant.domain.desk.RuleTweaks
import app.localmind.assistant.domain.desk.Signal
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
import java.security.SecureRandom
import java.time.LocalDate
import javax.inject.Inject

/**
 * One of your rules as the Desk lists it. [rule] is the version being edited (null when it can't
 * be read, with [problem] saying why); [tries] counts every tested version, shelf ones included
 * for a copy; [changed]: edited since the last test.
 */
data class MyRule(
    val id: String,
    val key: String,
    val name: String,
    val from: String,
    val rule: Rule?,
    val problem: String?,
    val tries: Int,
    val tested: Boolean,
    val changed: Boolean,
    /** The version tested before the last one, for "Undo to try N". */
    val canUndo: Boolean
)

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
    /** Strategies that can't run yet (by name), and the series they're missing. */
    val waiting: Map<String, List<String>> = emptyMap(),
    val state: DeskState = DeskState(),
    val rules: List<MyRule> = emptyList(),
    /** Price series a rule can trade, and the other series it can read. */
    val tickers: List<String> = emptyList(),
    val otherSeries: List<String> = emptyList()
)

/**
 * SlothDesk: the four shelf strategies and your own rules run on the tablet, on prices the
 * Lobster fetched or a file from Colab. Everything is worked out here, in code; a chat can explain a report but never
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

    /** The rule open in the editor (its id), and the report open over the Desk (its key). */
    private val _editing = MutableStateFlow<String?>(null)
    val editing: StateFlow<String?> = _editing.asStateFlow()
    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report.asStateFlow()

    /**
     * Counts changes made to a rule from outside the editor (undo, rename). While a rule is
     * open, the editor's own copy is what's being edited; it takes the saved one again only then.
     */
    private val _reload = MutableStateFlow(0)
    val reload: StateFlow<Int> = _reload.asStateFlow()

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
                _ui.value = DeskUi(ready = true, state = s, rules = myRules(store.state.value))
                return@withLock
            }
            val reports = ArrayList<StrategyReport>()
            val waiting = LinkedHashMap<String, List<String>>()
            val newTrials = HashMap<String, Map<String, Double?>>()
            val newJournal = ArrayList<JournalEntry>()
            for (t in DeskStrategies.templates) {
                if (t.key in s.hidden) continue
                val missing = DeskStrategies.missing(t.key, d)
                if (missing.isNotEmpty()) { waiting[t.label] = missing; continue }
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
            // Your rules, as last tested (an edit counts once it's tested).
            for (saved in s.rules) {
                val key = MINE + saved.id
                if (!saved.tested || key in s.hidden) continue
                val rule = RuleFiles.parse(saved.tries.last()).getOrNull() ?: continue
                val missing = RuleEngine.missing(rule, d)
                if (missing.isNotEmpty()) { waiting[rule.name] = missing; continue }
                val rep = withContext(Dispatchers.Default) {
                    val run = RuleEngine.run(rule, d, costs)
                    val hash = RuleTweaks.hash(rule, costs)
                    val trials = s.trials[key].orEmpty().toMutableMap()
                    if (hash !in trials) {
                        trials[hash] = DeskReports.inSampleSharpe(run, d).takeIf { !it.isNaN() }
                        newTrials[key] = trials
                    }
                    val verdict = s.nearby[key]?.takeIf { it.paramsHash == hash }?.verdict
                    DeskReports.buildFor(key, rule.name, rule, d, trials.values.toList(), verdict, paper(key, s), costs, run)
                }
                reports += rep
                val date = rep.ticket.dataDate.toString()
                if (s.journal.none { it.strategy == key && it.dataDate == date }) {
                    newJournal += JournalEntry(date, key, rep.ticket.nextOpen, rep.ticket.trade, at = System.currentTimeMillis())
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
                state = s,
                rules = myRules(store.state.value),
                tickers = d.prices.keys.sorted(),
                otherSeries = (d.raw.keys + listOf("DTB3", "JP3M", "GVZ").filter { d.knownLine(it).size > 0 }).distinct().sorted()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _ui.update { it.copy(ready = true, working = null) }
            _message.value = "SlothDesk couldn't run: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun myRules(s: DeskState): List<MyRule> = s.rules.map { r ->
        val key = MINE + r.id
        val parsed = RuleFiles.parse(r.text)
        val rule = parsed.getOrNull()
        MyRule(
            id = r.id, key = key,
            name = rule?.name ?: r.tries.lastOrNull()?.let { RuleFiles.parse(it).getOrNull()?.name } ?: "A rule that can't be read",
            from = r.from, rule = rule, problem = parsed.exceptionOrNull()?.message,
            tries = s.trials[key].orEmpty().size, tested = r.tested,
            changed = r.tries.lastOrNull() != r.text, canUndo = r.tries.size >= 2
        )
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
        if (key.startsWith(MINE)) {
            val saved = store.state.value.rules.firstOrNull { MINE + it.id == key } ?: return@act null
            val rule = saved.tries.lastOrNull()?.let { RuleFiles.parse(it).getOrNull() } ?: return@act "Test the rule first"
            val (verdict, cells) = withContext(Dispatchers.Default) { DeskReports.nearbyRule(rule, d, costs) }
            store.update { s ->
                val trials = s.trials[key].orEmpty().toMutableMap()
                cells.forEach { (h, sr) -> if (h !in trials) trials[h] = sr.takeIf { !it.isNaN() } }
                s.copy(trials = s.trials + (key to trials),
                    nearby = s.nearby + (key to NearbyVerdict(RuleTweaks.hash(rule, costs), verdict, System.currentTimeMillis())))
            }
            recompute()
            return@act "Nearby settings: " + verdict.substringBefore(":")
        }
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

    // --- your rules ---

    /** A new rule to start from: hold the first asset there are prices for above its 200-day average. */
    fun newRule() = act(quiet = true) {
        val t = data?.prices?.keys?.let { k -> if ("GLD" in k) "GLD" else k.sorted().firstOrNull() } ?: "GLD"
        val rule = Rule(name = "My rule", benchmark = t, branches = listOf(Branch("trend",
            listOf(Condition.Compare(Signal.Price(t), Op.ABOVE, Against.Of(Signal.Average(t, 200)))), Action.Hold(mapOf(t to 1.0)))))
        _editing.value = add(rule, from = "", carried = emptyMap())
        null
    }

    /**
     * Copies a shelf strategy, at its current settings, into your rules. The settings already
     * tried on the shelf come with it, so the luck question doesn't start over.
     */
    fun copyShelf(key: String) = act(quiet = true) {
        val t = DeskStrategies.template(key)
        val p = paramsFor(key, store.state.value)
        val rule = DeskStrategies.rule(key, p).let { it.copy(name = "${shortName(t.label)}, my copy".take(80)) }
        val shelf = store.state.value.trials[key].orEmpty()
        val here = p.hash(costs)
        val carried = shelf.filterKeys { it != here }.mapKeys { "shelf-" + it.key } +
            (if (here in shelf) mapOf(RuleTweaks.hash(rule, costs) to shelf[here]) else emptyMap())
        _editing.value = add(rule, from = "shelf:$key", carried = carried)
        null
    }

    private fun shortName(label: String) = label.substringBefore(" with ").substringBefore(" (")

    private suspend fun add(rule: Rule, from: String, carried: Map<String, Double?>): String? {
        if (store.state.value.rules.size >= SavedRule.MAX_RULES) {
            _message.value = "That's ${SavedRule.MAX_RULES} rules, the most the Desk keeps: delete one first."
            return null
        }
        val id = newId()
        val now = System.currentTimeMillis()
        store.update { s ->
            s.copy(rules = s.rules + SavedRule(id, RuleFiles.encode(rule), from, created = now, updated = now),
                trials = if (carried.isEmpty()) s.trials else s.trials + (MINE + id to carried))
        }
        _ui.update { it.copy(rules = myRules(store.state.value)) }
        return id
    }

    private fun newId(): String {
        val b = ByteArray(5)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun openRule(id: String) { _editing.value = id }
    fun closeRule() { _editing.value = null }
    fun openReport(key: String) { _report.value = key }
    fun closeReport() { _report.value = null }

    /**
     * Saves an edit (not a try: only testing counts). Only a rule within its limits is saved, so
     * what's kept can always be read back; the editor shows what to fix meanwhile.
     */
    fun edit(id: String, rule: Rule) = act(quiet = true) {
        if (RuleCheck.problems(rule).isNotEmpty()) return@act null
        val text = RuleFiles.encode(rule)
        require(text.length <= RuleFiles.MAX_BYTES) { "That rule is too long to keep" }
        store.update { s -> s.copy(rules = s.rules.map { if (it.id == id) it.copy(text = text, updated = System.currentTimeMillis()) else it }) }
        _ui.update { it.copy(rules = myRules(store.state.value)) }
        null
    }

    /** What a rule needs that the prices don't have yet (empty: it can run). */
    fun missingFor(rule: Rule): List<String> = RuleEngine.missing(rule, data)

    /**
     * Tests [edited], the rule as it stands in the editor: it's saved, it counts as one more try
     * in the trial log (unless this exact version was tested before), and the six questions open.
     */
    fun test(id: String, edited: Rule) = act {
        val d = data ?: return@act "No prices yet: get prices first"
        if (store.state.value.rules.none { it.id == id }) return@act null
        val text = RuleFiles.encode(edited)
        val rule = RuleFiles.parse(text).getOrElse { return@act it.message ?: "That rule can't be tested" }
        RuleEngine.missing(rule, d).takeIf { it.isNotEmpty() }?.let { return@act "Needs prices for ${it.joinToString(", ")} first" }
        _ui.update { it.copy(working = "Testing ${rule.name}…") }
        val key = MINE + id
        val hash = RuleTweaks.hash(rule, costs)
        val sharpe = withContext(Dispatchers.Default) { DeskReports.inSampleSharpe(RuleEngine.run(rule, d, costs), d) }
        val before = store.state.value.trials[key]?.containsKey(hash) == true
        store.update { s ->
            val trials = s.trials[key].orEmpty().toMutableMap()
            if (hash !in trials) trials[hash] = sharpe.takeIf { !it.isNaN() }
            s.copy(
                trials = s.trials + (key to trials),
                hidden = s.hidden - key,
                rules = s.rules.map {
                    if (it.id != id) it
                    else it.copy(text = text, updated = System.currentTimeMillis(),
                        tries = (if (it.tries.lastOrNull() == text) it.tries else it.tries + text).takeLast(SavedRule.MAX_TRIES_KEPT))
                }
            )
        }
        recompute()
        _report.value = key
        val n = store.state.value.trials[key].orEmpty().size
        if (before) "Tested again: this version was tried before, so still $n tries" else "Try $n tested"
    }

    /** Back to the last tested version (dropping untested edits), or else to the one before it. */
    fun undo(id: String) = act(quiet = true) {
        store.update { s ->
            s.copy(rules = s.rules.map { r ->
                when {
                    r.id != id || r.tries.isEmpty() -> r
                    r.text != r.tries.last() -> r.copy(text = r.tries.last())
                    r.tries.size >= 2 -> r.copy(text = r.tries[r.tries.size - 2], tries = r.tries.dropLast(1))
                    else -> r
                }
            })
        }
        recompute()
        _reload.update { it + 1 }
        null
    }

    fun rename(id: String, name: String) = act(quiet = true) {
        val n = name.trim().take(80)
        if (n.isBlank()) return@act null
        val saved = store.state.value.rules.firstOrNull { it.id == id } ?: return@act null
        val rule = RuleFiles.parse(saved.text).getOrNull() ?: return@act "That rule can't be read"
        val renamed = RuleFiles.encode(rule.copy(name = n))
        store.update { s ->
            s.copy(rules = s.rules.map { r ->
                if (r.id != id) r
                else r.copy(text = renamed, tries = r.tries.map { t -> RuleFiles.parse(t).getOrNull()?.let { RuleFiles.encode(it.copy(name = n)) } ?: t })
            })
        }
        recompute()
        _reload.update { it + 1 }
        null
    }

    /** Deletes a rule with its tries, nearby check and paper days. */
    fun delete(id: String) = act {
        val key = MINE + id
        store.update { s ->
            s.copy(rules = s.rules.filter { it.id != id }, trials = s.trials - key, nearby = s.nearby - key,
                hidden = s.hidden - key, journal = s.journal.filter { it.strategy != key })
        }
        if (_editing.value == id) _editing.value = null
        if (_report.value == key) _report.value = null
        recompute()
        "Rule deleted"
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
                prompt = "Explain this SlothDesk report in plain words: what the rule does, what the six questions say, " +
                    "and what to watch. Use only the numbers in it; don't work out new ones.",
                document = attachments.textDocument("SlothDesk: ${rep.label}", text),
                personaId = BuiltInPersonas.TRADER.id
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
        const val MINE = DeskReports.MINE
        const val ORDER_TEXT = "Prices for SlothDesk: GLD SLV USDJPY DTB3 JP3M GVZ DEXJPUS from 2008-01-01"
        private const val MAX_JOURNAL = 3_000
        private const val MAX_FILE_BYTES = 30 * 1024 * 1024
    }
}
