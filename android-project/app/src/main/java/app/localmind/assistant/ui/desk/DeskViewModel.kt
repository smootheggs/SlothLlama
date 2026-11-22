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
import app.localmind.assistant.data.desk.PaperRun
import app.localmind.assistant.data.desk.SavedRule
import app.localmind.assistant.data.desk.WeekReview
import app.localmind.assistant.data.forecasts.ForecastBook
import app.localmind.assistant.data.trading.IdeaBook
import app.localmind.assistant.domain.desk.DeskPath
import app.localmind.assistant.domain.desk.RuleCard
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.trading.Idea
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

/**
 * One idea on the path (1.49), or a rule of yours with no idea written for it: where it is
 * ([stage]), a line on how it's going, and the rule it's tested with.
 */
data class PathItem(
    val ideaId: String?,
    val ruleId: String?,
    val title: String,
    val stage: DeskPath.Stage,
    val line: String,
    /** The rule's key ("my:…"), once it has one. */
    val key: String?
)

/** A ticket for tonight, for a rule on paper: what to do at the next open, and why. */
data class Tonight(
    val key: String,
    val name: String,
    val report: StrategyReport,
    val entry: JournalEntry?,
    val why: List<RuleEngine.Today>,
    val progress: DeskPath.Progress
)

/** One rule on paper in the week a Sunday review looks back on. */
data class WeekRule(
    val key: String,
    val name: String,
    val idea: Idea?,
    val tickets: Int,
    val marked: Int,
    val followed: Int,
    val slips: Map<String, Int>,
    val ruleChange: Double?,
    val benchChange: Double?,
    val bench: String,
    val trades: Int,
    val progress: DeskPath.Progress
)

/** What a Sunday review looks back on (1.49). */
data class WeekFacts(
    val sunday: LocalDate,
    val rules: List<WeekRule>,
    /** Your other open ideas on the path. */
    val ideas: List<Idea>,
    /** Trade ideas whose review date has come. */
    val tradeIdeasDue: List<Idea>,
    /** Price calls checked this week, and those still open. */
    val callsSettled: List<Call>,
    val callsOpen: List<Call>,
    val done: WeekReview?
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
    val otherSeries: List<String> = emptyList(),
    /** The path (1.49): your ideas where they are, tonight's tickets, the Sunday review. */
    val path: List<PathItem> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val tonight: List<Tonight> = emptyList(),
    /** The review waiting (its Sunday), if any; days to the next one; last week's line. */
    val reviewWaiting: LocalDate? = null,
    val daysToSunday: Int = 0,
    val lastLine: String = ""
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
    private val askInbox: AskInbox,
    private val book: IdeaBook,
    private val forecasts: ForecastBook
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

    /** The idea open over the Desk, the rule whose paper days are open (its key), and the Sunday review. */
    private val _idea = MutableStateFlow<String?>(null)
    val idea: StateFlow<String?> = _idea.asStateFlow()
    private val _paperKey = MutableStateFlow<String?>(null)
    val paperKey: StateFlow<String?> = _paperKey.asStateFlow()
    private val _reviewing = MutableStateFlow(false)
    val reviewing: StateFlow<Boolean> = _reviewing.asStateFlow()

    /** Today's conditions per key on paper, from the last run. */
    private var why: Map<String, List<RuleEngine.Today>> = emptyMap()

    private val costs = Costs()
    private val work = Mutex()
    private var data: DeskData? = null

    init {
        viewModelScope.launch {
            store.load()
            book.load()
            runCatching { forecasts.load() }
            takeFromMailbox()
            recompute()
        }
        // The path follows the idea book: a saved idea, a closed one, a draft come back.
        viewModelScope.launch {
            book.ideas.collect { list -> _ui.update { it.copy(ideas = list.filter { i -> i.onPath && i.isOpen }, path = pathOf(list, store.state.value, it.reports)) } }
        }
        viewModelScope.launch {
            book.focus.collect { id ->
                if (id != null) {
                    _idea.value = id
                    book.setFocus(null)
                }
            }
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
            if (!store.state.value.paperSince149) migratePaper()
            var s = store.state.value
            if (d == null) {
                _ui.value = DeskUi(ready = true, state = s, rules = myRules(store.state.value)).withPath(book.ideas.value, s)
                return@withLock
            }
            val reports = ArrayList<StrategyReport>()
            val waiting = LinkedHashMap<String, List<String>>()
            val newTrials = HashMap<String, Map<String, Double?>>()
            val newJournal = ArrayList<JournalEntry>()
            val newPaper = HashMap<String, PaperRun>()
            val restarted = ArrayList<String>()
            val whyNow = HashMap<String, List<RuleEngine.Today>>()
            /** The rule's paper run as of this version: the same, filled in, or started again. */
            fun runFor(key: String, version: String, lastDay: String, name: String): PaperRun? {
                val run = s.paper[key] ?: return null
                val next = when {
                    run.version.isBlank() -> run.copy(version = version)
                    run.version != version -> { restarted += name; PaperRun(lastDay, version, System.currentTimeMillis()) }
                    else -> run
                }
                if (next != run) newPaper[key] = next
                return next
            }
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
                    val onPaper = runFor(t.key, hash, run.result.dates.last().toString(), t.label)
                    if (onPaper != null) whyNow[t.key] = RuleEngine.today(DeskStrategies.rule(t.key, p), d)
                    DeskReports.build(t.key, d, p, trials.values.toList(), verdict, paper(t.key, s, onPaper), costs, run)
                }
                reports += rep
                val date = rep.ticket.dataDate.toString()
                if ((newPaper[t.key] ?: s.paper[t.key]) != null && s.journal.none { it.strategy == t.key && it.dataDate == date }) {
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
                    val onPaper = runFor(key, hash, run.result.dates.last().toString(), rule.name)
                    if (onPaper != null) whyNow[key] = RuleEngine.today(rule, d)
                    DeskReports.buildFor(key, rule.name, rule, d, trials.values.toList(), verdict, paper(key, s, onPaper), costs, run)
                }
                reports += rep
                val date = rep.ticket.dataDate.toString()
                if ((newPaper[key] ?: s.paper[key]) != null && s.journal.none { it.strategy == key && it.dataDate == date }) {
                    newJournal += JournalEntry(date, key, rep.ticket.nextOpen, rep.ticket.trade, at = System.currentTimeMillis())
                }
            }
            if (newTrials.isNotEmpty() || newJournal.isNotEmpty() || newPaper.isNotEmpty()) {
                store.update { st ->
                    st.copy(trials = st.trials + newTrials, journal = (newJournal + st.journal).take(MAX_JOURNAL),
                        paper = st.paper + newPaper.filterKeys { it in st.paper })
                }
                s = store.state.value
            }
            if (restarted.isNotEmpty()) {
                _message.value = "A new version of ${restarted.joinToString(", ")}: its $PAPER_DAYS paper days start again."
            }
            why = whyNow
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
            ).withPath(book.ideas.value, s)
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

    /** The paper days of a rule on paper, for the sixth question (null: not on paper). */
    private fun paper(key: String, s: DeskState, run: PaperRun? = s.paper[key]): DeskStats.Paper? {
        run ?: return null
        val p = progressOf(key, s, run)
        return DeskStats.Paper(p.days, p.marked, p.share ?: 0.0)
    }

    private fun progressOf(key: String, s: DeskState, run: PaperRun? = s.paper[key]): DeskPath.Progress =
        DeskPath.progress(s.journal.filter { it.strategy == key }.map { DeskPath.Mark(it.dataDate, it.followed, it.mistake) }, run?.since ?: "9999")

    /**
     * Before 1.49 every strategy on the Desk wrote a ticket into the journal each night, followed
     * or not. Those whose tickets you marked were being paper-traded: they stay on paper from
     * their first day, so none of that record is lost (the version is filled in at the next run).
     * The rest weren't chosen, so they don't start sending tickets now.
     */
    private suspend fun migratePaper() {
        store.update { st ->
            val first = DeskPath.followedBefore(st.journal.groupBy { it.strategy }
                .mapValues { (_, l) -> l.map { DeskPath.Mark(it.dataDate, it.followed, it.mistake) } })
            st.copy(paper = first.mapValues { (_, d) -> PaperRun(d, "", System.currentTimeMillis()) } + st.paper, paperSince149 = true)
        }
    }

    // --- the path ---

    private fun DeskUi.withPath(ideas: List<Idea>, s: DeskState): DeskUi {
        val today = LocalDate.now()
        val tonight = s.paper.keys.mapNotNull { key ->
            val r = reports.firstOrNull { it.key == key } ?: return@mapNotNull null
            Tonight(key, r.label, r, s.journal.firstOrNull { it.strategy == key && it.dataDate == r.ticket.dataDate.toString() },
                why[key].orEmpty(), progressOf(key, s))
        }.sortedBy { it.name }
        val done = s.reviews.map { it.sunday }.toSet()
        val hasSomething = s.paper.isNotEmpty() || ideas.any { it.onPath && it.isOpen }
        return copy(
            ideas = ideas.filter { it.onPath && it.isOpen },
            path = pathOf(ideas, s, reports),
            tonight = tonight,
            reviewWaiting = if (hasSomething) DeskPath.waiting(today, done) else null,
            daysToSunday = DeskPath.daysToSunday(today),
            lastLine = s.reviews.firstOrNull()?.line.orEmpty()
        )
    }

    /** Your ideas where they are on the path, then your rules with no idea written for them. */
    private fun pathOf(ideas: List<Idea>, s: DeskState, reports: List<StrategyReport>): List<PathItem> {
        val open = ideas.filter { it.onPath && it.isOpen }
        val rules = s.rules.associateBy { it.id }
        fun ruleLine(ruleId: String): Pair<DeskPath.Stage, String> {
            val key = MINE + ruleId
            val saved = rules[ruleId] ?: return DeskPath.Stage.IDEA to "Its rule was deleted: write another"
            val run = s.paper[key]
            val prog = progressOf(key, s, run)
            val stage = DeskPath.stage(true, run != null, prog.days)
            val tries = s.trials[key].orEmpty().size
            return stage to when (stage) {
                DeskPath.Stage.IDEA -> ""
                DeskPath.Stage.TEST -> if (!saved.tested) "Test · not tested yet" else {
                    val r = reports.firstOrNull { it.key == key }
                    "Test · " + (if (tries == 1) "try 1" else "try $tries") + (r?.let { " · ${it.verdict.first.lowercase()}" } ?: "")
                }
                DeskPath.Stage.PAPER -> "Paper · ${prog.days} of $PAPER_DAYS days"
                DeskPath.Stage.REVIEW -> "Review · its $PAPER_DAYS paper days are in"
            }
        }
        val fromIdeas = open.map { i ->
            val linked = i.ruleId.takeIf { it.isNotBlank() && it in rules }
            if (linked == null) {
                val draft = if (i.draftCard.isNotBlank()) " · the Trader's draft is in" else ""
                PathItem(i.id, null, i.thesis.lineSequence().first().take(140), DeskPath.Stage.IDEA,
                    "Idea · written ${java.time.Instant.ofEpochMilli(i.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}$draft", null)
            } else {
                val (stage, line) = ruleLine(linked)
                PathItem(i.id, linked, i.thesis.lineSequence().first().take(140), stage, line, MINE + linked)
            }
        }
        val linkedIds = open.map { it.ruleId }.toSet()
        val loose = s.rules.filter { it.id !in linkedIds }.map { r ->
            val (stage, line) = ruleLine(r.id)
            val name = RuleFiles.parse(r.text).getOrNull()?.name ?: "A rule that can't be read"
            PathItem(null, r.id, name, stage, line, MINE + r.id)
        }
        // Shelf strategies on paper (kept from before 1.49, or papered from the shelf).
        val shelf = s.paper.keys.filter { !it.startsWith(MINE) }.mapNotNull { k ->
            val t = DeskStrategies.templates.firstOrNull { it.key == k } ?: return@mapNotNull null
            val prog = progressOf(k, s)
            PathItem(null, null, t.label, DeskPath.stage(true, true, prog.days),
                if (prog.done) "Review · its $PAPER_DAYS paper days are in" else "Paper · ${prog.days} of $PAPER_DAYS days · from the shelf", k)
        }
        // Furthest along first: what's on paper tonight, then what's being tested, then ideas.
        return (fromIdeas + loose + shelf).sortedByDescending { it.stage.ordinal }
    }

    fun openIdea(id: String) { _idea.value = id }
    fun closeIdea() { _idea.value = null }
    fun openPaper(key: String) { _paperKey.value = key }
    fun closePaper() { _paperKey.value = null }
    fun openReview() { _reviewing.value = true }
    fun closeReview() { _reviewing.value = false }

    /** Puts a rule on paper from tonight's ticket: its 60 days start now, for this version. */
    fun paperIt(key: String) = act {
        val r = _ui.value.reports.firstOrNull { it.key == key } ?: return@act "Test it first"
        val version = versionOf(key) ?: return@act "Test it first"
        store.update { st -> st.copy(paper = st.paper + (key to PaperRun(r.ticket.dataDate.toString(), version, System.currentTimeMillis())), hidden = st.hidden - key) }
        recompute()
        _paperKey.value = key
        "On paper: tonight's ticket is day 1 of $PAPER_DAYS. Mark each one, followed or not."
    }

    /** Takes a rule off paper; its journal stays in your record. */
    fun stopPaper(key: String) = act {
        store.update { st -> st.copy(paper = st.paper - key) }
        if (_paperKey.value == key) _paperKey.value = null
        recompute()
        "Off paper. Its days stay in the journal."
    }

    private fun versionOf(key: String): String? {
        val s = store.state.value
        return if (key.startsWith(MINE)) {
            s.rules.firstOrNull { MINE + it.id == key }?.tries?.lastOrNull()?.let { RuleFiles.parse(it).getOrNull() }?.let { RuleTweaks.hash(it, costs) }
        } else DeskStrategies.templates.firstOrNull { it.key == key }?.let { paramsFor(key, s).hash(costs) }
    }

    /**
     * Asks the Trader to draft a rule for an idea, as a card in the forms the Desk reads. The
     * card comes back under the reply; nothing is made until you open it.
     */
    fun askForDraft(ideaId: String) {
        val idea = book.ideas.value.firstOrNull { it.id == ideaId } ?: return
        book.setAsking(ideaId)
        val u = _ui.value
        val have = buildString {
            appendLine("Prices SlothDesk has: ${u.tickers.joinToString(", ").ifBlank { "none yet" }}.")
            appendLine("Other series it has: ${u.otherSeries.joinToString(", ").ifBlank { "none yet" }}.")
            appendLine("A rule can name others; the Desk then says what it needs fetched.")
        }
        askInbox.post(
            LaunchRequest.Shared(
                prompt = "Draft a first rule to test this idea, as simply as the idea allows. " +
                    "Say in one line how the rule tests the idea, then write the card. Use only the forms below; don't work out any numbers.\n\n" +
                    RuleCard.FORMS,
                document = attachments.textDocument("Idea: ${idea.thesis.take(60)}", idea.asText() + "\n\n" + have),
                personaId = BuiltInPersonas.TRADER.id
            )
        )
    }

    /** The Trader's draft for an idea, read and checked: the rule, or what couldn't be read. */
    fun draftFor(idea: Idea): RuleCard.Read? =
        idea.draftCard.takeIf { it.isNotBlank() }?.let { RuleCard.parse(it, data?.prices?.keys.orEmpty()) }

    /** Opens the Trader's draft in the rule editor, as this idea's rule. */
    fun useDraft(ideaId: String) = act(quiet = true) {
        val idea = book.ideas.value.firstOrNull { it.id == ideaId } ?: return@act null
        val rule = draftFor(idea)?.rule ?: return@act "The draft can't be read: write the rule yourself, or ask again"
        val id = add(rule, from = "trader", carried = emptyMap()) ?: return@act null
        book.setRule(ideaId, id)
        // The idea stays open under the editor: Back returns to it.
        _editing.value = id
        null
    }

    /** A rule for an idea, written yourself: a starter, or a copy from the shelf. */
    fun writeRuleFor(ideaId: String, shelf: String? = null) = act(quiet = true) {
        val rule = if (shelf != null) shelfRule(shelf) else starter()
        val carried = if (shelf != null) carriedFrom(shelf, rule) else emptyMap()
        val id = add(rule, from = if (shelf != null) "shelf:$shelf" else "", carried = carried) ?: return@act null
        book.setRule(ideaId, id)
        // The idea stays open under the editor: Back returns to it.
        _editing.value = id
        null
    }

    /** Closes an idea with its review; a rule on paper for it comes off paper (its journal stays). */
    fun reviewIdea(ideaId: String, outcome: String, reason: String, whatHappened: String, lesson: String) = act {
        val idea = book.ideas.value.firstOrNull { it.id == ideaId } ?: return@act null
        book.close(ideaId, outcome, reason, null, whatHappened, lesson)
        if (idea.ruleId.isNotBlank()) store.update { st -> st.copy(paper = st.paper - (MINE + idea.ruleId)) }
        _idea.value = null
        recompute()
        "Reviewed. It's in your record with how sure you were."
    }

    fun noteIdea(ideaId: String, text: String) = act(quiet = true) {
        book.addNote(ideaId, text.take(IdeaBook.MAX_TEXT))
        null
    }

    fun deleteIdea(ideaId: String) = act {
        book.delete(ideaId)
        _idea.value = null
        "Idea deleted (its rule stays with your rules)"
    }

    // --- the Sunday review ---

    /** What the review of the week ending [sunday] looks back on. */
    fun weekFacts(sunday: LocalDate): WeekFacts {
        val s = store.state.value
        val week = DeskPath.week(sunday)
        val ideas = book.ideas.value
        val d = data
        val rules = s.paper.keys.mapNotNull { key ->
            val r = _ui.value.reports.firstOrNull { it.key == key } ?: return@mapNotNull null
            val inWeek = s.journal.filter { it.strategy == key && LocalDate.parse(it.dataDate) in week }
            val marked = inWeek.filter { it.followed == "yes" || it.followed == "no" }
            val bench = d?.prices?.get(r.benchLabel)
            WeekRule(
                key = key, name = r.label,
                idea = ideas.firstOrNull { it.onPath && it.isOpen && it.ruleId.isNotBlank() && MINE + it.ruleId == key },
                tickets = inWeek.size, marked = marked.size, followed = marked.count { it.followed == "yes" },
                slips = inWeek.filter { it.followed == "no" }.groupingBy { it.mistake.ifBlank { "unsaid" } }.eachCount(),
                ruleChange = DeskPath.change(r.recentDates, r.recentEquity, week.start, week.endInclusive),
                benchChange = bench?.let { DeskPath.change(it.dates, it.closes, week.start, week.endInclusive) },
                bench = r.benchLabel,
                trades = inWeek.count { it.trade },
                progress = progressOf(key, s)
            )
        }.sortedBy { it.name }
        val onPaper = rules.mapNotNull { it.idea?.id }.toSet()
        val calls = forecasts.calls.value.filter { it.price != null }
        return WeekFacts(
            sunday = sunday,
            rules = rules,
            ideas = ideas.filter { it.onPath && it.isOpen && it.id !in onPaper },
            tradeIdeasDue = ideas.filter { !it.onPath && it.isDue(sunday) },
            callsSettled = calls.filter { !it.open && it.markedOn.isNotBlank() && LocalDate.parse(it.markedOn) in week },
            callsOpen = calls.filter { it.open },
            done = s.reviews.firstOrNull { it.sunday == sunday.toString() }
        )
    }

    /** Saves the week's review: your call on each rule on paper, and one line for the week ahead. */
    fun saveReview(sunday: LocalDate, calls: Map<String, String>, line: String) = act {
        store.update { st ->
            val r = WeekReview(sunday.toString(), calls.entries.take(40).associate { (k, v) -> k to v }, line.trim().take(300), System.currentTimeMillis())
            st.copy(reviews = (listOf(r) + st.reviews.filter { it.sunday != r.sunday }).take(DeskStore.MAX_REVIEWS))
        }
        calls.filterValues { it == "retire" }.keys.forEach { key -> store.update { st -> st.copy(paper = st.paper - key) } }
        _reviewing.value = false
        recompute()
        "Reviewed. See you next Sunday."
    }

    /** The week's facts for the Trader to talk through (the numbers are the Desk's). */
    fun askAboutWeek(f: WeekFacts) {
        val text = buildString {
            appendLine("SlothDesk, week of ${DeskPath.week(f.sunday).start} to ${f.sunday}")
            f.rules.forEach { w ->
                appendLine()
                appendLine("${w.name}: ${w.tickets} tickets, ${w.trades} with a trade; followed ${w.followed} of ${w.marked} marked" +
                    (if (w.slips.isNotEmpty()) "; slips: " + w.slips.entries.joinToString(", ") { (k, n) -> "${DeskPath.slip(k).ifBlank { k }} $n" } else "") + ".")
                appendLine("The rule's week: ${w.ruleChange?.let { DeskText.pct(it) } ?: "–"}; ${w.bench}: ${w.benchChange?.let { DeskText.pct(it) } ?: "–"}.")
                w.idea?.let { appendLine("The idea behind it: \"${it.thesis}\"" + if (it.why.isNotBlank()) " Why: ${it.why}" else "") }
                appendLine("Paper days: ${w.progress.days} of $PAPER_DAYS.")
            }
            f.ideas.forEach { appendLine("Another idea, not on paper yet: \"${it.thesis}\"") }
            f.callsSettled.forEach { appendLine("Price call settled: ${it.question}") }
        }
        askInbox.post(
            LaunchRequest.Shared(
                prompt = "Help me review my SlothDesk week in five minutes: does each idea still fit what happened, what did my slips cost " +
                    "in habits, and what's one thing to do differently next week? Use only the numbers here; don't work out new ones.",
                document = attachments.textDocument("SlothDesk: the week to ${f.sunday}", text),
                personaId = BuiltInPersonas.TRADER.id
            )
        )
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
        _editing.value = add(starter(), from = "", carried = emptyMap())
        null
    }

    /**
     * Copies a shelf strategy, at its current settings, into your rules. The settings already
     * tried on the shelf come with it, so the luck question doesn't start over.
     */
    fun copyShelf(key: String) = act(quiet = true) {
        val rule = shelfRule(key)
        val id = add(rule, from = "shelf:$key", carried = carriedFrom(key, rule)) ?: return@act null
        // The copy becomes an idea on the path, in the shelf's words (1.49).
        val idea = book.add(app.localmind.assistant.domain.trading.IdeaDraft(
            thesis = SHELF_IDEAS[key] ?: rule.name, kind = "rule", source = "shelf",
            reviewOn = LocalDate.now().plusMonths(3).toString()))
        book.setRule(idea.id, id)
        _editing.value = id
        null
    }

    private fun shelfRule(key: String): Rule {
        val t = DeskStrategies.template(key)
        return DeskStrategies.rule(key, paramsFor(key, store.state.value)).let { it.copy(name = "${shortName(t.label)}, my copy".take(80)) }
    }

    private fun carriedFrom(key: String, rule: Rule): Map<String, Double?> {
        val shelf = store.state.value.trials[key].orEmpty()
        val here = paramsFor(key, store.state.value).hash(costs)
        return shelf.filterKeys { it != here }.mapKeys { "shelf-" + it.key } +
            (if (here in shelf) mapOf(RuleTweaks.hash(rule, costs) to shelf[here]) else emptyMap())
    }

    private fun starter(): Rule {
        val t = data?.prices?.keys?.let { k -> if ("GLD" in k) "GLD" else k.sorted().firstOrNull() } ?: "GLD"
        return Rule(name = "My rule", benchmark = t, branches = listOf(Branch("trend",
            listOf(Condition.Compare(Signal.Price(t), Op.ABOVE, Against.Of(Signal.Average(t, 200)))), Action.Hold(mapOf(t to 1.0)))))
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
                hidden = s.hidden - key, journal = s.journal.filter { it.strategy != key }, paper = s.paper - key)
        }
        book.ideas.value.filter { it.ruleId == id }.forEach { book.setRule(it.id, "") }
        if (_editing.value == id) _editing.value = null
        if (_report.value == key) _report.value = null
        recompute()
        "Rule deleted"
    }

    // --- the paper journal ---

    /** Marks a ticket: followed, or not and what happened instead (a [DeskPath.SLIPS] code). */
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
        const val PAPER_DAYS = DeskPath.PAPER_DAYS

        /** The idea behind each shelf strategy, in words, for the idea a copy becomes. */
        val SHELF_IDEAS = mapOf(
            "gold_trend" to "Gold holds up when the trend is up and markets are calm",
            "gold_silver_ratio" to "The gold/silver ratio comes back when it's stretched",
            "usdjpy_carry" to "Holding dollars against yen pays while the trend is up and the rate gap is wide",
            "gld_covered_call" to "Selling calls on gold earns more than it gives up"
        )
        const val ORDER_TEXT = "Prices for SlothDesk: GLD SLV USDJPY DTB3 JP3M GVZ DEXJPUS from 2008-01-01"
        private const val MAX_JOURNAL = 3_000
        private const val MAX_FILE_BYTES = 30 * 1024 * 1024
    }
}
