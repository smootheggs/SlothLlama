package app.slothlobster.jobs

import app.slothlobster.BuildConfig
import app.slothlobster.core.Delivered
import app.slothlobster.core.From
import app.slothlobster.core.Job
import app.slothlobster.core.JobState
import app.slothlobster.core.EngineHealth
import app.slothlobster.core.Repeat
import app.slothlobster.core.Lobster
import app.slothlobster.core.SourceState
import app.slothlobster.core.Store
import app.slothlobster.core.Watch
import app.slothlobster.mailbox.LobsterCatch
import app.slothlobster.mailbox.LobsterSource
import app.slothlobster.mailbox.LobsterStatus
import app.slothlobster.mailbox.OrderKind
import app.slothlobster.run.LobsterService
import app.slothlobster.run.Notifier
import app.slothlobster.web.Feeds
import app.slothlobster.web.Hit
import app.slothlobster.web.Readable
import app.slothlobster.web.Search
import app.slothlobster.web.Web
import app.slothlobster.web.WebException
import app.slothlobster.web.WikiArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** A job was cancelled, or the Lobster was paused (then it goes back in the queue). */
private class Stopped(val requeue: Boolean) : Exception()

/** What a job brings back. */
private class Outcome(val catch: LobsterCatch, val preview: String, val subtitle: String)

/** Where a page saved for later goes, and the catch kind SlothLlama shelves under Later. */
private const val LATER = "later"

/**
 * The Lobster's work: take orders from the mailbox, run them one at a time, check watched pages,
 * make the daily brief and keep the heartbeat going. Every job is one SlothLlama could name;
 * nothing in an order or on a page is ever run as a command.
 */
class Engine(private val lobster: Lobster) {

    private val store: Store get() = lobster.store
    private val mailbox get() = lobster.mailbox

    private val passLock = Mutex()
    private val statusLock = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var briefRequested = false
    @Volatile private var workingId: String? = null

    /** Something new to do: wakes the awake service, or runs a pass now if it isn't running. */
    fun poke() {
        wake.trySend(Unit)
        if (!LobsterService.running) lobster.scope.launch { pass() }
    }

    fun requestBrief() {
        briefRequested = true
        poke()
    }

    /** For the awake service: waits until poked, or [ms] at most. */
    suspend fun nap(ms: Long) {
        withTimeoutOrNull(ms) { wake.receive() }
    }

    /** One round of work. If a round is already going, this one is skipped (it'll see the news). */
    suspend fun pass() {
        if (!passLock.tryLock()) return
        try {
            takeOrders()
            lobster.refreshLive()
            heartbeat()
            if (lobster.isPaused()) return
            val offline = lobster.offlineReason()
            lobster.setLive { it.copy(waitingFor = offline) }
            if (offline != null) return
            checkRepeats()
            while (!lobster.isPaused()) {
                val next = store.value.jobs.filter { it.state == JobState.QUEUED }.minByOrNull { it.createdAt } ?: break
                work(next)
                takeOrders()
            }
            if (!lobster.isPaused()) checkWatches()
            if (!lobster.isPaused()) maybeBrief()
        } finally {
            lobster.setLive { it.copy(lastPass = System.currentTimeMillis(), workingId = null) }
            runCatching { lobster.refreshLive() }
            heartbeat()
            passLock.unlock()
        }
    }

    // --- mailbox ---

    private fun takeOrders() {
        if (!mailbox.isReady()) return
        val orders = runCatching { mailbox.takeOrders() }.getOrElse {
            store.log("problem", "Couldn't read the mailbox", it.message.orEmpty())
            return
        }
        lobster.setLive { it.copy(lastSync = System.currentTimeMillis()) }
        val known = store.value.jobs.mapNotNull { it.orderId }.toSet() + store.value.seenOrders
        val now = System.currentTimeMillis()
        for (order in orders) {
            if (order.id in known) continue
            store.update { it.copy(seenOrders = it.seenOrders + order.id) }
            // A kind this version doesn't know (sent by a newer SlothLlama): say so instead of guessing.
            if (OrderKind.entries.none { it.id == order.kind }) {
                val note = LobsterCatch(
                    id = UUID.randomUUID().toString(),
                    orderId = order.id,
                    kind = "message",
                    title = "Update the SlothLobster",
                    text = "This SlothLobster (${BuildConfig.VERSION_NAME}) doesn't know \"${order.kind.take(20)}\" orders yet. " +
                        "Update it (Obtainium), then send the order again.\n\nYour order: ${order.text.take(300)}",
                    createdAt = System.currentTimeMillis()
                )
                deliver(note, null, "Needs an update")
                store.log("problem", "An order kind this version doesn't know", order.kind.take(20))
                continue
            }
            // Only fresh orders: an old order file put back in the folder is ignored.
            if (order.createdAt < now - MAX_ORDER_AGE_MS || order.createdAt > now + Store.DAY_MS) {
                store.log("problem", "Ignored an out-of-date order", "${OrderKind.of(order.kind).label}: ${order.text.take(80)}")
                continue
            }
            // "every Monday at 7 …" makes it a repeating order: it runs now, then on schedule.
            // Watches repeat by themselves, and a covers order is a list of books ("every day" may be a title).
            val rule = if (OrderKind.of(order.kind) in setOf(OrderKind.WATCH, OrderKind.COVERS)) null else Repeats.parse(order.text)
            val text = (if (rule != null) Repeats.strip(order.text) else order.text).take(4_000)
            val repeatId = rule?.let { UUID.randomUUID().toString() }
            val job = Job(
                id = UUID.randomUUID().toString(),
                orderId = order.id,
                from = From.TABLET,
                kind = order.kind,
                text = text,
                deep = order.depth == "deep",
                deliver = order.deliver,
                createdAt = System.currentTimeMillis(),
                queries = order.queries.map { it.take(200) }.take(Plans.MAX_SEARCHES),
                repeatId = repeatId
            )
            store.update { s ->
                val repeats = if (rule == null || repeatId == null) s.repeats else s.repeats + Repeat(
                    id = repeatId, kind = order.kind, text = text, deep = job.deep, from = From.TABLET,
                    everyDays = rule.everyDays, weekday = rule.weekday, hour = rule.hour, minute = rule.minute,
                    createdAt = now, lastRunDay = Repeats.dayKey(Calendar.getInstance())
                )
                s.copy(jobs = listOf(job) + s.jobs, repeats = repeats)
            }
            store.log("mailbox", "Took an order from SlothLlama", "${OrderKind.of(order.kind).label}: ${text.take(80)}")
            if (rule != null) store.log("order", "Set to repeat", "${OrderKind.of(order.kind).label} · ${rule.describe()}")
        }
    }

    /** Queues each repeating order that's due (once a day at most). */
    private fun checkRepeats() {
        val now = Calendar.getInstance()
        val day = Repeats.dayKey(now)
        for (r in store.value.repeats) {
            if (!r.enabled || !Repeats.due(r.everyDays, r.weekday, r.hour, r.minute, r.lastRunDay, now)) continue
            val job = Job(
                id = UUID.randomUUID().toString(),
                from = r.from,
                kind = r.kind,
                text = r.text,
                deep = r.deep,
                createdAt = System.currentTimeMillis(),
                repeatId = r.id
            )
            store.update { s -> s.copy(jobs = listOf(job) + s.jobs) }
            store.updateRepeat(r.id) { it.copy(lastRunDay = day) }
            store.log("order", "Repeating order is due", "${OrderKind.of(r.kind).label}: ${r.text.take(80)}")
        }
    }

    /** Writes the heartbeat SlothLlama reads (awake, busy or paused, and the job in hand). */
    suspend fun heartbeat(state: String? = null) = statusLock.withLock {
        runCatching {
            if (!mailbox.isReady()) return@runCatching
            val s = store.value
            val working = workingId?.let { id -> s.jobs.find { it.id == id } }
            val status = LobsterStatus(
                state = state ?: when {
                    lobster.isPaused() -> "paused"
                    working != null -> "busy"
                    else -> "awake"
                },
                at = System.currentTimeMillis(),
                version = BuildConfig.VERSION_NAME,
                working = working?.let { "${verb(it.kind)}: ${it.text.take(60)}" },
                queue = s.jobs.count { it.state == JobState.QUEUED }
            )
            mailbox.writeStatus(status)
            lobster.setLive { it.copy(lastSync = status.at) }
        }
    }

    // --- jobs ---

    private suspend fun work(job: Job) {
        workingId = job.id
        lobster.setLive { it.copy(workingId = job.id) }
        store.updateJob(job.id) { it.copy(state = JobState.WORKING, startedAt = System.currentTimeMillis(), sendNow = false) }
        heartbeat()
        try {
            val outcome = when (OrderKind.of(job.kind)) {
                OrderKind.RESEARCH -> research(job)
                OrderKind.FETCH -> fetch(job)
                OrderKind.WATCH -> watchOrder(job)
                OrderKind.BRIEF -> briefOrder(job)
                // No model here: a question is researched, and SlothLlama answers from what comes back.
                OrderKind.ASK -> research(job)
                OrderKind.PRICES -> prices(job)
                OrderKind.COVERS -> covers(job)
            }
            checkpoint(job.id)
            val catch = forLater(job, outcome.catch)
            val sent = deliver(catch, job.id, outcome.subtitle)
            store.updateJob(job.id) {
                it.copy(
                    state = JobState.DONE,
                    finishedAt = System.currentTimeMillis(),
                    resultTitle = outcome.catch.title,
                    resultPreview = outcome.preview.take(4_000),
                    note = if (sent) null else "Kept here: there's no mailbox to send it to yet"
                )
            }
            // Saving for later is meant to be quiet: no notification for each article.
            if (catch.kind != LATER) notify(outcome.catch.title, outcome.subtitle)
        } catch (e: Stopped) {
            if (e.requeue) store.updateJob(job.id) { it.copy(state = JobState.QUEUED, sources = emptyList()) }
        } catch (e: CancellationException) {
            store.updateJob(job.id) { it.copy(state = JobState.QUEUED, sources = emptyList()) }
            throw e
        } catch (e: Exception) {
            val why = friendly(e)
            store.updateJob(job.id) { it.copy(state = JobState.STOPPED, note = why, finishedAt = System.currentTimeMillis()) }
            store.log("problem", "Couldn't finish: ${verb(job.kind)}", why)
            if (job.deliver == LATER) saveUnreadable(job, why)
            else if (job.from == From.TABLET) tellSlothLlama(job, "The Lobster couldn't do this", why)
        } finally {
            workingId = null
            lobster.setLive { it.copy(workingId = null) }
        }
    }

    /** Throws if the job was cancelled, or the Lobster paused (then the job waits for later). */
    private fun checkpoint(jobId: String) {
        val job = store.value.jobs.find { it.id == jobId }
        if (job == null || job.state == JobState.STOPPED) throw Stopped(requeue = false)
        if (lobster.isPaused()) throw Stopped(requeue = true)
    }

    private fun setSources(jobId: String, sources: List<SourceState>) {
        store.updateJob(jobId) { it.copy(sources = sources) }
    }

    private fun markSource(jobId: String, url: String, state: String) {
        store.updateJob(jobId) { j -> j.copy(sources = j.sources.map { if (it.url == url) it.copy(state = state) else it }) }
    }

    private suspend fun research(job: Job): Outcome {
        val (question, searches) = Plans.searches(job.text, job.queries)
        if (question.isBlank()) throw WebException("The order has no question")
        val read = ArrayList<Pair<SourceState, String>>()

        store.log("search", "Searched Wikipedia", question)
        val wiki = runCatching { Search.wikipedia(question, if (job.deep) 2 else 1) }.getOrElse {
            store.log("problem", "Wikipedia didn't answer", friendly(it))
            emptyList<WikiArticle>()
        }
        wiki.forEach { a ->
            store.log("wiki", "Read Wikipedia", a.title)
            read += SourceState(a.title, a.url, "Wikipedia", "done") to a.text
        }
        setSources(job.id, read.map { it.first })
        checkpoint(job.id)

        // Every search's results go into one pool, and the best pages across them are read
        // (see Picks): one per site, original sources first, answer farms and index pages last.
        val total = if (job.deep) 7 else 4
        val per = Plans.perSearch(total, searches.size)
        val pool = ArrayList<Picks.Candidate>()
        searches.forEachIndexed { i, q ->
            checkpoint(job.id)
            val (engine, found) = runCatching { Search.web(q, per + 4, engineOrder(), ::recordEngine) }.getOrElse {
                store.log("problem", "Web search didn't answer", friendly(it))
                "" to emptyList<Hit>()
            }
            if (engine.isNotEmpty()) store.log("search", "Searched $engine", q)
            found.forEachIndexed { position, hit -> pool += Picks.Candidate(hit, i, position) }
        }
        // Wikipedia was already read above, so its pages don't take a place here.
        val hits = Picks.rank(if (wiki.isEmpty()) pool else pool.filterNot { Picks.site(it.hit.url).endsWith("wikipedia.org") }, question, total)
        setSources(job.id, read.map { it.first } + hits.map { SourceState(it.title, it.url, Web.site(it.url)) })

        for (hit in hits) {
            checkpoint(job.id)
            if (store.value.jobs.find { it.id == job.id }?.sendNow == true) break
            markSource(job.id, hit.url, "reading")
            heartbeat()
            try {
                val page = Web.get(hit.url)
                val text = when {
                    page.isHtml -> Readable.extract(page.text, page.url).text
                    page.mime.startsWith("text/") -> page.text
                    else -> throw WebException("not a web page (${page.mime})")
                }.take(PER_SOURCE)
                if (text.length < 200) throw WebException("almost nothing readable on the page")
                store.log("page", "Opened page", page.url)
                markSource(job.id, hit.url, "done")
                read += SourceState(hit.title, page.url, Web.site(page.url), "done") to text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markSource(job.id, hit.url, "failed")
                store.log("problem", "Couldn't read a page", "${Web.site(hit.url)}: ${friendly(e)}")
            }
        }
        if (read.isEmpty()) throw WebException("Found nothing readable for this question")

        val text = buildString {
            append("# ").append(question).append("\n\n")
            if (searches.size > 1 || searches.firstOrNull() != question) {
                append("Searches: ").append(searches.joinToString(" · ")).append("\n\n")
            }
            append("_Gathered by the SlothLobster on ").append(today()).append(" from ").append(read.size)
            append(if (read.size == 1) " source" else " sources")
            append(". Not summarised yet: ask SlothLlama to read it and answer, citing the sources by number._\n\n")
            read.forEachIndexed { i, (source, body) ->
                append("## [").append(i + 1).append("] ").append(source.title).append("\n\n")
                append("Source: ").append(source.url).append("\n\n")
                append(body.trim()).append("\n\n")
            }
        }
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "document",
            title = "Research · " + question.take(80),
            text = text,
            sources = read.map { LobsterSource(it.first.title, it.first.url) },
            createdAt = System.currentTimeMillis()
        )
        val preview = read.mapIndexed { i, (s, _) -> "[${i + 1}] ${s.title} — ${s.where}" }.joinToString("\n")
        return Outcome(catch, preview, "${read.size} sources")
    }

    private suspend fun fetch(job: Job): Outcome {
        val link = Web.firstLink(job.text) ?: throw WebException("There's no link in this order")
        val url = Web.safeUrl(link) ?: throw WebException("The Lobster won't open that link: $link")
        setSources(job.id, listOf(SourceState(Web.site(url), url, Web.site(url), "reading")))
        val page = Web.get(url)
        store.log("page", "Fetched", page.url)
        markSource(job.id, url, "done")
        val isFile = page.mime == "application/pdf" || page.mime == "application/epub+zip" ||
            page.url.substringBefore('?').let { it.endsWith(".pdf", true) || it.endsWith(".epub", true) }
        val catch = when {
            isFile -> {
                val name = fileName(page.url, if (page.mime == "application/epub+zip") "epub" else "pdf")
                LobsterCatch(
                    id = UUID.randomUUID().toString(),
                    orderId = job.orderId,
                    kind = "document",
                    title = name,
                    text = "Fetched **$name** (${size(page.bytes.size)}) from ${page.url}",
                    sources = listOf(LobsterSource(name, page.url)),
                    createdAt = System.currentTimeMillis(),
                    fileName = name,
                    fileBase64 = Base64.getEncoder().encodeToString(page.bytes)
                )
            }
            page.isHtml -> {
                val readable = Readable.extract(page.text, page.url)
                LobsterCatch(
                    id = UUID.randomUUID().toString(),
                    orderId = job.orderId,
                    kind = "document",
                    title = readable.title.take(120),
                    text = "# ${readable.title}\n\nSource: ${page.url}\n\n${readable.text}",
                    sources = listOf(LobsterSource(readable.title, page.url)),
                    createdAt = System.currentTimeMillis()
                )
            }
            page.mime.startsWith("text/") -> LobsterCatch(
                id = UUID.randomUUID().toString(),
                orderId = job.orderId,
                kind = "document",
                title = Web.site(page.url),
                text = "Source: ${page.url}\n\n" + page.text.take(200_000),
                sources = listOf(LobsterSource(Web.site(page.url), page.url)),
                createdAt = System.currentTimeMillis()
            )
            else -> throw WebException("It's a ${page.mime.ifBlank { "kind of" }} file, which SlothLlama can't read")
        }
        val words = catch.text.split(Regex("\\s+")).size
        val subtitle = if (catch.fileName != null) "File · ${size(page.bytes.size)}" else "Fetched · ${"%,d".format(words)} words"
        return Outcome(catch, catch.text.take(1_500), subtitle)
    }

    private suspend fun watchOrder(job: Job): Outcome {
        val link = Regex("""https?://\S+""").find(job.text)?.value?.trimEnd('.', ',', ';', ':')?.let(Web::safeUrl)
        val target = link ?: Watches.topicFrom(job.text)
        if (target.isBlank()) throw WebException("The order doesn't say what to watch")
        val first = Watch(
            id = UUID.randomUUID().toString(),
            target = target,
            title = if (link != null) Web.site(link) else "News · $target",
            createdAt = System.currentTimeMillis(),
            keywords = if (link != null) Watches.keywordsFrom(job.text) else emptyList()
        )
        val seen = look(first)
        val watch = first.copy(
            title = seen.title, lastChecked = System.currentTimeMillis(), headings = seen.headings,
            lastLines = seen.lines, lastHash = Changes.hash(seen.lines)
        )
        val title = watch.title
        store.update { it.copy(watches = listOf(watch) + it.watches) }
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "message",
            title = "Watching · $title",
            text = "The SlothLobster is now watching **$title** and checks it every ${watch.everyHours} hours. " +
                (if (watch.keywords.isNotEmpty()) "It only looks at lines that mention ${watch.keywords.joinToString(", ")}. " else "") +
                "When it changes, an alert comes to the mailbox.\n\nSource: ${watchUrl(watch)}",
            sources = listOf(LobsterSource(title, watchUrl(watch))),
            createdAt = System.currentTimeMillis()
        )
        return Outcome(catch, "Watching $title, every ${watch.everyHours} hours.", "Now watching")
    }

    /** SlothDesk's daily prices: public data services only, the Tiingo key sent in a header. */
    private suspend fun prices(job: Job): Outcome {
        val (names, since) = Prices.request(job.text)
        if (names.isEmpty()) throw WebException("The order doesn't name any prices to fetch")
        setSources(job.id, names.map { SourceState(it, "", "prices", "reading") })
        val g = Prices.gather(names, since, lobster.tiingoKey(), BuildConfig.VERSION_NAME) { name, what ->
            store.log("prices", name, what)
        }
        setSources(job.id, names.map { SourceState(it, "", "prices", if (it in g.bundle.series) "done" else "failed") })
        val last = g.bundle.series.values.maxOf { it.dates.last() }
        val summary = Prices.summary(g)
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "prices",
            title = "Prices to $last",
            text = summary,
            createdAt = System.currentTimeMillis(),
            fileName = "slothdesk-prices.json",
            fileBase64 = Base64.getEncoder().encodeToString(Prices.encode(g.bundle).toByteArray(Charsets.UTF_8))
        )
        return Outcome(catch, summary, "${g.bundle.series.size} series for SlothDesk")
    }

    /**
     * Covers for SlothBooks: each book looked up by title and author, a second apart (Open Library
     * asks apps to go gently), sent back in batches so the shelf fills in as it goes.
     */
    private suspend fun covers(job: Job): Outcome {
        val (choices, asks) = Covers.parse(job.text)
        if (asks.isEmpty()) throw WebException("The order doesn't list any books")
        val mode = if (choices > 1) "pick" else "set"
        setSources(job.id, listOf(SourceState("Open Library, then Google Books", "https://openlibrary.org", "${asks.size} books", "reading")))
        var found = mutableListOf<CoverFound>()
        var missing = mutableListOf<String>()
        var foundAll = 0
        var missingAll = 0
        fun batch(): LobsterCatch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = COVERS_KIND,
            title = "Covers · ${found.size + missing.size} books",
            text = "Covers for SlothBooks: ${found.size} found" + if (missing.isNotEmpty()) ", ${missing.size} not found." else ".",
            createdAt = System.currentTimeMillis(),
            fileName = "slothbooks-covers.json",
            fileBase64 = Base64.getEncoder().encodeToString(Covers.encode(CoverBatch(mode = mode, covers = found, missing = missing)).toByteArray(Charsets.UTF_8))
        )
        for ((i, ask) in asks.withIndex()) {
            checkpoint(job.id)
            val f = try {
                Covers.find(ask, choices)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.log("problem", "No cover for ${ask.title.take(60)}", friendly(e))
                null
            }
            if (f != null) found += f else missing += ask.id
            if (found.size + missing.size >= COVER_BATCH && i < asks.lastIndex) {
                deliver(batch(), job.id, "covers")
                foundAll += found.size
                missingAll += missing.size
                found = mutableListOf()
                missing = mutableListOf()
            }
            kotlinx.coroutines.delay(COVER_PAUSE_MS)
        }
        foundAll += found.size
        missingAll += missing.size
        setSources(job.id, listOf(SourceState("Open Library, then Google Books", "https://openlibrary.org", "${asks.size} books", "done")))
        val summary = "$foundAll covers found" + if (missingAll > 0) ", $missingAll not found" else ""
        return Outcome(batch(), summary, "$foundAll covers for SlothBooks")
    }

    private suspend fun briefOrder(job: Job): Outcome {
        val links = Regex("""https?://\S+""").findAll(job.text).map { it.value }.mapNotNull(Web::safeUrl).toList()
        val topic = job.text.replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""^(?:a\s+|the\s+)?(?:brief|briefing|round-?up|news)\s+(?:on|about|of)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
        // "Precious metals market updates, top news, prices, etc." searches as "precious metals market":
        // filler words only narrow a news search. Recent stories only.
        val feeds = links.ifEmpty { listOf(Feeds.newsSearch(Feeds.newsQuery(topic).ifBlank { "world news" }, days = 2)) }
        val (text, sources) = gatherBrief(feeds, perFeed = if (links.isEmpty()) 10 else 5, skipSeen = job.repeatId != null)
        val title = "Brief · " + (topic.ifBlank { "your feeds" }).take(80)
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "document",
            title = title,
            text = "# $title\n\n_${today()}_\n\n$text",
            sources = sources,
            createdAt = System.currentTimeMillis()
        )
        return Outcome(catch, text.take(1_500), "${sources.size} headlines")
    }

    // --- search engines ---

    /** Engines that have been failing go last; the rest keep their usual order. */
    private fun engineOrder(): List<String> {
        val health = store.value.engines
        return Search.ENGINES.sortedBy { minOf(health[it]?.failsInRow ?: 0, 3) }
    }

    private fun recordEngine(name: String, ok: Boolean) {
        val before = store.value.engines[name] ?: EngineHealth()
        val now = System.currentTimeMillis()
        val after = if (ok) before.copy(lastOk = now, failsInRow = 0) else before.copy(lastFailed = now, failsInRow = before.failsInRow + 1)
        store.update { it.copy(engines = it.engines + (name to after)) }
        if (!ok && after.failsInRow == 3) store.log("problem", "$name has stopped answering searches", "Tried 3 times in a row; the other engines are used first")
        if (ok && before.failsInRow >= 3) store.log("search", "$name is answering again")
    }

    // --- watches ---

    private suspend fun checkWatches() {
        val now = System.currentTimeMillis()
        val due = store.value.watches.filter { it.enabled && now - it.lastChecked >= it.everyHours * HOUR_MS }
        for (watch in due) {
            if (lobster.isPaused()) return
            try {
                val seen = look(watch)
                val title = seen.title
                val lines = seen.lines
                val hash = Changes.hash(lines)
                val checkedAt = System.currentTimeMillis()
                if (watch.lastHash.isEmpty() || hash == watch.lastHash) {
                    store.updateWatch(watch.id) {
                        it.copy(title = title, lastChecked = checkedAt, lastHash = hash, lastLines = lines, failures = 0, headings = seen.headings)
                    }
                    store.log("watch", "Checked a watched page", "$title · no change")
                    continue
                }
                val change = Changes.between(watch.lastLines, lines, watch.isTopic)
                store.updateWatch(watch.id) {
                    it.copy(
                        title = title, lastChecked = checkedAt, lastHash = hash, lastLines = lines, failures = 0, headings = seen.headings,
                        lastChange = if (change.isEmpty) it.lastChange else checkedAt,
                        added = if (change.isEmpty) it.added else change.added,
                        removed = if (change.isEmpty) it.removed else change.removed
                    )
                }
                if (change.isEmpty) {
                    store.log("watch", "Checked a watched page", "$title · only small changes")
                    continue
                }
                store.log("watch", "Watch found a change", title)
                val catch = LobsterCatch(
                    id = UUID.randomUUID().toString(),
                    kind = "alert",
                    title = "$title changed",
                    text = Changes.describe(change, watchUrl(watch)),
                    sources = listOf(LobsterSource(title, watchUrl(watch))),
                    createdAt = System.currentTimeMillis()
                )
                val subtitle = when {
                    change.added.isNotEmpty() && change.removed.isNotEmpty() -> "${change.added.size} new, ${change.removed.size} gone"
                    change.added.isNotEmpty() -> "${change.added.size} new"
                    else -> "${change.removed.size} gone"
                }
                deliver(catch, null, subtitle)
                notify(catch.title, subtitle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.updateWatch(watch.id) { it.copy(lastChecked = System.currentTimeMillis(), failures = it.failures + 1) }
                store.log("problem", "Couldn't check a watched page", "${watch.title}: ${friendly(e)}")
            }
        }
    }

    /** What a look at a watched page or topic found. */
    private class Look(val title: String, val lines: List<String>, val headings: List<String>)

    /**
     * The watched thing now: a page's text (only the chosen part, if one is set), or a topic's
     * headlines; then only the lines with the chosen words, if any.
     */
    private suspend fun look(watch: Watch): Look {
        if (watch.isTopic) {
            val page = Web.get(Feeds.newsSearch(watch.target))
            store.log("feed", "Read news headlines", watch.target)
            val feed = Feeds.parse(page.text, watch.target)
            val lines = feed.items.map { it.title }.filter { Watches.matches(it, watch.keywords) }
            return Look("News · ${watch.target}", Changes.normalise(lines), emptyList())
        }
        val page = Web.get(watch.target)
        store.log("page", "Opened a watched page", page.url)
        if (!page.isHtml) {
            val lines = page.text.lines().filter { Watches.matches(it, watch.keywords) }
            return Look(Web.site(page.url), Changes.normalise(lines), emptyList())
        }
        val readable = Readable.extract(page.text, page.url)
        var lines = readable.lines
        if (watch.section.isNotBlank()) {
            lines = Watches.section(lines, watch.section) ?: run {
                store.log("problem", "A watched part wasn't found", "\"${watch.section}\" is no longer on ${Web.site(page.url)}; watching the whole page")
                lines
            }
        }
        lines = lines.filter { Watches.matches(it, watch.keywords) }
        return Look(readable.title.take(80), Changes.normalise(lines), Watches.headings(readable.lines))
    }

    private fun watchUrl(watch: Watch) = if (watch.isTopic) Feeds.newsSearch(watch.target) else watch.target

    // --- brief ---

    private suspend fun maybeBrief() {
        val settings = store.value.settings
        val now = Calendar.getInstance()
        val day = dayKey(now)
        val due = settings.briefOn && settings.feeds.isNotEmpty() && settings.lastBriefDay != day &&
            now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) >= settings.briefHour * 60 + settings.briefMinute
        if (!due && !briefRequested) return
        briefRequested = false
        // A brief asked for by hand doesn't use up the day's scheduled one.
        if (due) store.update { it.copy(settings = it.settings.copy(lastBriefDay = day)) }
        try {
            val (text, sources) = gatherBrief(settings.feeds, perFeed = 5, groups = settings.feedGroups, skipSeen = true)
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val name = when {
                hour < 12 -> "Morning brief"
                hour < 18 -> "Afternoon brief"
                else -> "Evening brief"
            }
            val catch = LobsterCatch(
                id = UUID.randomUUID().toString(),
                kind = "document",
                title = "$name · ${today()}",
                text = "# $name\n\n_${today()}_\n\n$text",
                sources = sources,
                createdAt = System.currentTimeMillis()
            )
            store.log("brief", name, "${sources.size} headlines")
            deliver(catch, null, "${sources.size} headlines")
            notify(catch.title, "${sources.size} headlines")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.log("problem", "Couldn't make the brief", friendly(e))
        }
    }

    /**
     * Headlines from each feed, in Markdown, with the links as sources; arranged under the feeds'
     * [groups] when they have them. With [skipSeen], headlines sent in an earlier brief are left out.
     */
    private suspend fun gatherBrief(
        feeds: List<String>,
        perFeed: Int,
        groups: Map<String, String> = emptyMap(),
        skipSeen: Boolean = false
    ): Pair<String, List<LobsterSource>> {
        val sources = ArrayList<LobsterSource>()
        val seen = if (skipSeen) store.value.sentHeadlines.toHashSet() else HashSet()
        val sections = LinkedHashMap<String, StringBuilder>()
        var answered = 0
        // Ungrouped feeds first, then each group in the order its first feed appears.
        val ordered = feeds.sortedBy { if (groups[it].isNullOrBlank()) 0 else 1 }
        for (url in ordered) {
            if (lobster.isPaused()) break
            try {
                val page = Web.get(url)
                val feed = Feeds.parse(page.text, Web.site(url))
                answered++
                store.log("feed", "Read a feed", feed.title)
                val items = feed.items.filter { item -> key(item.link, item.title) !in seen }.take(perFeed)
                if (items.isEmpty()) continue
                val group = groups[url].orEmpty()
                val out = sections.getOrPut(group) { StringBuilder() }
                out.append(if (group.isBlank()) "## " else "### ").append(feed.title).append("\n\n")
                for (item in items) {
                    out.append("- ")
                    if (item.link.isNotBlank()) out.append('[').append(item.title).append("](").append(item.link).append(')')
                    else out.append(item.title)
                    if (item.summary.isNotBlank() && item.summary != item.title) out.append(" — ").append(item.summary)
                    out.append('\n')
                    seen += key(item.link, item.title)
                    if (item.link.isNotBlank()) sources += LobsterSource(item.title, item.link)
                }
                out.append('\n')
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.log("problem", "Couldn't read a feed", "${Web.site(url)}: ${friendly(e)}")
            }
        }
        if (answered == 0) throw WebException("None of the feeds answered")
        if (skipSeen) {
            val keys = sources.map { key(it.url, it.title) }
            store.update { it.copy(sentHeadlines = it.sentHeadlines + keys) }
        }
        val text = buildString {
            sections.forEach { (group, body) ->
                if (group.isNotBlank()) append("## ").append(group).append("\n\n")
                append(body)
            }
        }.trim()
        return text.ifBlank { "Nothing new since the last brief." } to sources
    }

    /** A headline's identity for "already sent": its link, or its title when it has none. */
    private fun key(link: String, title: String): String = link.ifBlank { title }.trim().lowercase().take(300)

    // --- delivery ---

    /** Puts the catch in the mailbox and remembers it. False if there's no mailbox to put it in. */
    private fun deliver(catch: LobsterCatch, jobId: String?, subtitle: String): Boolean {
        // Covers are for SlothBooks only: no copy is kept on the Lobster's own shelf.
        if (catch.kind != COVERS_KIND) lobster.shelf.keep(catch)
        val name = runCatching { mailbox.deliver(catch) }.getOrElse {
            store.log("problem", "Couldn't write to the mailbox", it.message.orEmpty())
            null
        }
        if (name != null) store.log("mailbox", "Sent to the mailbox", catch.title)
        store.update {
            it.copy(delivered = listOf(Delivered(catch.id, catch.title, catch.kind, System.currentTimeMillis(), name, jobId, subtitle)) + it.delivered)
        }
        lobster.shelf.prune(store.value.delivered.map { it.id }.toSet())
        lobster.refreshLive()
        return name != null
    }

    /**
     * A page saved for later goes to SlothLlama's Later shelf rather than the catch. Files (PDFs,
     * books) stay ordinary catches: the Later shelf is for articles.
     */
    private fun forLater(job: Job, catch: LobsterCatch): LobsterCatch =
        if (job.deliver == LATER && job.kind == OrderKind.FETCH.id && catch.kind == "document" && catch.fileBase64 == null) catch.copy(kind = LATER) else catch

    /** A link saved for later that couldn't be read still reaches the shelf, with why and the link. */
    private fun saveUnreadable(job: Job, why: String) {
        val link = Web.firstLink(job.text).orEmpty()
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = LATER,
            title = Web.site(link).ifBlank { "A saved link" },
            text = "The Lobster couldn't bring back a readable copy of this page ($why). " +
                "Some sites need a sign-in or only work in a browser.\n\nThe link: $link",
            sources = if (link.isBlank()) emptyList() else listOf(LobsterSource(Web.site(link), link)),
            createdAt = System.currentTimeMillis()
        )
        deliver(catch, job.id, "Saved for later · no readable copy")
    }

    /** A short message back to SlothLlama about one of its orders (so it doesn't wait forever). */
    fun tellSlothLlama(job: Job, title: String, text: String) {
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "message",
            title = title,
            text = "$text\n\nYour order: ${job.text.take(300)}",
            createdAt = System.currentTimeMillis()
        )
        deliver(catch, job.id, "Message")
    }

    private fun notify(title: String, text: String) {
        if (store.value.settings.notify) Notifier.brought(lobster.context, title, text)
    }

    // --- words ---

    private fun friendly(e: Throwable): String = when (e) {
        is WebException -> e.message.orEmpty()
        is java.net.UnknownHostException -> e.message?.takeIf { "private" in it } ?: "Couldn't find the site (no connection?)"
        is java.net.SocketTimeoutException -> "The site took too long to answer"
        is javax.net.ssl.SSLException -> "The site's secure connection didn't check out"
        else -> e.message?.take(160) ?: e.javaClass.simpleName
    }

    private fun today(): String = SimpleDateFormat("EEE d MMM yyyy", Locale.ENGLISH).format(Date())

    private fun dayKey(c: Calendar) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    private fun fileName(url: String, ext: String): String {
        val last = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        val clean = last.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        return if (clean.endsWith(".$ext", ignoreCase = true)) clean else "${clean.ifBlank { "document" }}.$ext"
    }

    private fun size(bytes: Int): String =
        if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1024.0 / 1024.0) else "${bytes / 1024} KB"

    companion object {
        private const val PER_SOURCE = 6_000
        private const val MAX_ORDER_AGE_MS = 14 * Store.DAY_MS
        private const val COVERS_KIND = "covers"
        /** Books per covers catch: about 1 MB each. */
        private const val COVER_BATCH = 20
        private const val COVER_PAUSE_MS = 1_000L
        const val HOUR_MS = 60L * 60 * 1000

        fun verb(kind: String): String = when (OrderKind.of(kind)) {
            OrderKind.RESEARCH -> "Researching"
            OrderKind.FETCH -> "Fetching"
            OrderKind.WATCH -> "Setting up a watch"
            OrderKind.BRIEF -> "Making a brief"
            OrderKind.ASK -> "Researching"
            OrderKind.PRICES -> "Fetching prices"
            OrderKind.COVERS -> "Finding book covers"
        }
    }
}
