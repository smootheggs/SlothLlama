package app.slothlobster.jobs

import app.slothlobster.BuildConfig
import app.slothlobster.core.Delivered
import app.slothlobster.core.From
import app.slothlobster.core.Job
import app.slothlobster.core.JobState
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
            // Only fresh orders: an old order file put back in the folder is ignored.
            if (order.createdAt < now - MAX_ORDER_AGE_MS || order.createdAt > now + Store.DAY_MS) {
                store.log("problem", "Ignored an out-of-date order", "${OrderKind.of(order.kind).label}: ${order.text.take(80)}")
                continue
            }
            val job = Job(
                id = UUID.randomUUID().toString(),
                orderId = order.id,
                from = From.TABLET,
                kind = order.kind,
                text = order.text.take(4_000),
                deep = order.depth == "deep",
                deliver = order.deliver,
                createdAt = System.currentTimeMillis()
            )
            store.update { it.copy(jobs = listOf(job) + it.jobs) }
            store.log("mailbox", "Took an order from SlothLlama", "${OrderKind.of(order.kind).label}: ${order.text.take(80)}")
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
                OrderKind.ASK -> ask(job)
            }
            checkpoint(job.id)
            val sent = deliver(outcome.catch, job.id, outcome.subtitle)
            store.updateJob(job.id) {
                it.copy(
                    state = JobState.DONE,
                    finishedAt = System.currentTimeMillis(),
                    resultTitle = outcome.catch.title,
                    resultPreview = outcome.preview.take(4_000),
                    note = if (sent) null else "Kept here: there's no mailbox to send it to yet"
                )
            }
            notify(outcome.catch.title, outcome.subtitle)
        } catch (e: Stopped) {
            if (e.requeue) store.updateJob(job.id) { it.copy(state = JobState.QUEUED, sources = emptyList()) }
        } catch (e: CancellationException) {
            store.updateJob(job.id) { it.copy(state = JobState.QUEUED, sources = emptyList()) }
            throw e
        } catch (e: Exception) {
            val why = friendly(e)
            store.updateJob(job.id) { it.copy(state = JobState.STOPPED, note = why, finishedAt = System.currentTimeMillis()) }
            store.log("problem", "Couldn't finish: ${verb(job.kind)}", why)
            if (job.from == From.TABLET) tellSlothLlama(job, "The Lobster couldn't do this", why)
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
        val question = job.text.trim()
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

        val (engine, hits) = runCatching { Search.web(question, if (job.deep) 7 else 4) }.getOrElse {
            store.log("problem", "Web search didn't answer", friendly(it))
            "" to emptyList<Hit>()
        }
        if (engine.isNotEmpty()) store.log("search", "Searched $engine", question)
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
            append("_Gathered by the Sloth Lobster on ").append(today()).append(" from ").append(read.size)
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
        val link = Web.firstLink(job.text)?.let(Web::safeUrl)
        val target = link ?: job.text.trim().take(120)
        if (target.isBlank()) throw WebException("The order doesn't say what to watch")
        val first = Watch(
            id = UUID.randomUUID().toString(),
            target = target,
            title = if (link != null) Web.site(link) else "News · $target",
            createdAt = System.currentTimeMillis()
        )
        val (title, lines) = look(first)
        val watch = first.copy(
            title = title, lastChecked = System.currentTimeMillis(),
            lastLines = lines, lastHash = Changes.hash(lines)
        )
        store.update { it.copy(watches = listOf(watch) + it.watches) }
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "message",
            title = "Watching · $title",
            text = "The Sloth Lobster is now watching **$title** and checks it every ${watch.everyHours} hours. " +
                "When it changes, an alert comes to the mailbox.\n\nSource: ${watchUrl(watch)}",
            sources = listOf(LobsterSource(title, watchUrl(watch))),
            createdAt = System.currentTimeMillis()
        )
        return Outcome(catch, "Watching $title, every ${watch.everyHours} hours.", "Now watching")
    }

    private suspend fun briefOrder(job: Job): Outcome {
        val links = Regex("""https?://\S+""").findAll(job.text).map { it.value }.mapNotNull(Web::safeUrl).toList()
        val topic = job.text.replace(Regex("""https?://\S+"""), "").trim()
        val feeds = links.ifEmpty { listOf(Feeds.newsSearch(topic.ifBlank { "world news" })) }
        val (text, sources) = gatherBrief(feeds, perFeed = if (links.isEmpty()) 10 else 5)
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

    private fun ask(job: Job): Outcome {
        val catch = LobsterCatch(
            id = UUID.randomUUID().toString(),
            orderId = job.orderId,
            kind = "message",
            title = "The Lobster has no brain switched on",
            text = "The Sloth Lobster runs free jobs only for now, with no AI model of its own, so it can't " +
                "answer this itself. It can research (gather sources for SlothLlama to read and answer from), " +
                "fetch a link, watch a page or topic, or make a brief. Send it again as **Research** and " +
                "SlothLlama will answer from what comes back.\n\nYour order: ${job.text.take(300)}",
            createdAt = System.currentTimeMillis()
        )
        return Outcome(catch, "Needs a model, which is switched off. Try Research instead.", "Needs a model")
    }

    // --- watches ---

    private suspend fun checkWatches() {
        val now = System.currentTimeMillis()
        val due = store.value.watches.filter { it.enabled && now - it.lastChecked >= it.everyHours * HOUR_MS }
        for (watch in due) {
            if (lobster.isPaused()) return
            try {
                val (title, lines) = look(watch)
                val hash = Changes.hash(lines)
                val checkedAt = System.currentTimeMillis()
                if (watch.lastHash.isEmpty() || hash == watch.lastHash) {
                    store.updateWatch(watch.id) {
                        it.copy(title = title, lastChecked = checkedAt, lastHash = hash, lastLines = lines, failures = 0)
                    }
                    store.log("watch", "Checked a watched page", "$title · no change")
                    continue
                }
                val change = Changes.between(watch.lastLines, lines, watch.isTopic)
                store.updateWatch(watch.id) {
                    it.copy(
                        title = title, lastChecked = checkedAt, lastHash = hash, lastLines = lines, failures = 0,
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

    /** The watched thing's title and lines now: a page's text, or a topic's headlines. */
    private suspend fun look(watch: Watch): Pair<String, List<String>> {
        if (watch.isTopic) {
            val page = Web.get(Feeds.newsSearch(watch.target))
            store.log("feed", "Read news headlines", watch.target)
            val feed = Feeds.parse(page.text, watch.target)
            return "News · ${watch.target}" to Changes.normalise(feed.items.map { it.title })
        }
        val page = Web.get(watch.target)
        store.log("page", "Opened a watched page", page.url)
        if (!page.isHtml) {
            return Web.site(page.url) to Changes.normalise(page.text.lines())
        }
        val readable = Readable.extract(page.text, page.url)
        return readable.title.take(80) to Changes.normalise(readable.lines)
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
            val (text, sources) = gatherBrief(settings.feeds, perFeed = 5)
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

    /** Headlines from each feed, in Markdown, with the links as sources. */
    private suspend fun gatherBrief(feeds: List<String>, perFeed: Int): Pair<String, List<LobsterSource>> {
        val sources = ArrayList<LobsterSource>()
        val text = StringBuilder()
        for (url in feeds) {
            if (lobster.isPaused()) break
            try {
                val page = Web.get(url)
                val feed = Feeds.parse(page.text, Web.site(url))
                store.log("feed", "Read a feed", feed.title)
                val items = feed.items.take(perFeed)
                if (items.isEmpty()) continue
                text.append("## ").append(feed.title).append("\n\n")
                for (item in items) {
                    text.append("- ")
                    if (item.link.isNotBlank()) text.append('[').append(item.title).append("](").append(item.link).append(')')
                    else text.append(item.title)
                    if (item.summary.isNotBlank() && item.summary != item.title) text.append(" — ").append(item.summary)
                    text.append('\n')
                    if (item.link.isNotBlank()) sources += LobsterSource(item.title, item.link)
                }
                text.append('\n')
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.log("problem", "Couldn't read a feed", "${Web.site(url)}: ${friendly(e)}")
            }
        }
        if (sources.isEmpty() && text.isEmpty()) throw WebException("None of the feeds answered")
        return text.toString().trim() to sources
    }

    // --- delivery ---

    /** Puts the catch in the mailbox and remembers it. False if there's no mailbox to put it in. */
    private fun deliver(catch: LobsterCatch, jobId: String?, subtitle: String): Boolean {
        val name = runCatching { mailbox.deliver(catch) }.getOrElse {
            store.log("problem", "Couldn't write to the mailbox", it.message.orEmpty())
            null
        }
        if (name != null) store.log("mailbox", "Sent to the mailbox", catch.title)
        store.update {
            it.copy(delivered = listOf(Delivered(catch.id, catch.title, catch.kind, System.currentTimeMillis(), name, jobId, subtitle)) + it.delivered)
        }
        lobster.refreshLive()
        return name != null
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
        const val HOUR_MS = 60L * 60 * 1000

        fun verb(kind: String): String = when (OrderKind.of(kind)) {
            OrderKind.RESEARCH -> "Researching"
            OrderKind.FETCH -> "Fetching"
            OrderKind.WATCH -> "Setting up a watch"
            OrderKind.BRIEF -> "Making a brief"
            OrderKind.ASK -> "Reading"
        }
    }
}
