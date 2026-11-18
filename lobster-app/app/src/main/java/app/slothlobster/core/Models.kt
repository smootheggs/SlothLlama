package app.slothlobster.core

import kotlinx.serialization.Serializable

/** Where an order came from. */
object From {
    const val TABLET = "tablet"
    const val MINE = "mine"
}

/** A job's progress. */
object JobState {
    const val QUEUED = "queued"
    const val WORKING = "working"
    const val DONE = "done"
    const val STOPPED = "stopped"
}

/** One source a job reads, shown with a tick as it goes. */
@Serializable
data class SourceState(
    val title: String,
    val url: String,
    /** Where it came from, shown under the title: "Wikipedia", a site's name, a feed. */
    val where: String = "",
    /** waiting, reading, done or failed. */
    val state: String = "waiting"
)

/** A job: an order from SlothLlama or one given here, and what became of it. */
@Serializable
data class Job(
    val id: String,
    /** The SlothLlama order it answers (null for orders given in the Lobster). */
    val orderId: String? = null,
    val from: String = From.MINE,
    /** research, fetch, watch, brief or ask. */
    val kind: String,
    val text: String,
    val deep: Boolean = false,
    val deliver: String = "library",
    val createdAt: Long,
    /** Extra searches for a research job (the question itself is searched when there are none). */
    val queries: List<String> = emptyList(),
    /** The repeating order that made this job, if any. */
    val repeatId: String? = null,
    val state: String = JobState.QUEUED,
    val sources: List<SourceState> = emptyList(),
    /** Asked to stop gathering and send what it has. */
    val sendNow: Boolean = false,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    /** What it brought back: a title and the start of the text. */
    val resultTitle: String? = null,
    val resultPreview: String? = null,
    /** Why it stopped, or a note about the result. */
    val note: String? = null
) {
    val open: Boolean get() = state == JobState.QUEUED || state == JobState.WORKING
    val doneCount: Int get() = sources.count { it.state == "done" }
}

/** A page or a news topic the Lobster checks for changes. */
@Serializable
data class Watch(
    val id: String,
    /** A link, or words for a news search. */
    val target: String,
    val title: String,
    val everyHours: Int = 6,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
    val lastChecked: Long = 0,
    val lastHash: String = "",
    /** The page's lines (or headlines) last time, to tell what changed. */
    val lastLines: List<String> = emptyList(),
    val lastChange: Long = 0,
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val failures: Int = 0,
    /** Only this part of the page: the heading it sits under ("" = the whole page). */
    val section: String = "",
    /** Only lines with one of these words (empty = every line). */
    val keywords: List<String> = emptyList(),
    /** The page's headings at the last look, to choose a part from. */
    val headings: List<String> = emptyList()
) {
    val isTopic: Boolean get() = !target.startsWith("https://") && !target.startsWith("http://")
}

/** An order that runs again every day or every week. */
@Serializable
data class Repeat(
    val id: String,
    val kind: String,
    val text: String,
    val deep: Boolean = false,
    val from: String = From.MINE,
    /** 1 = every day, 7 = every week (on [weekday]). */
    val everyDays: Int = 1,
    /** Calendar.MONDAY … Calendar.SUNDAY, for weekly ones. */
    val weekday: Int = 2,
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
    /** The day it last ran ("2026-11-18"), so it runs once a day at most. */
    val lastRunDay: String? = null
)

/** How a search engine has been answering, so a broken one is noticed and tried last. */
@Serializable
data class EngineHealth(
    val lastOk: Long = 0,
    val lastFailed: Long = 0,
    val failsInRow: Int = 0
)

/** One line in the Log: everything the Lobster did online. */
@Serializable
data class LogEntry(
    val at: Long,
    /** search, page, wiki, feed, mailbox, watch, brief or problem. */
    val kind: String,
    val what: String,
    val detail: String = ""
)

/** Something sent to SlothLlama (or kept here when there's no mailbox yet). */
@Serializable
data class Delivered(
    val id: String,
    val title: String,
    /** document, note, message or alert. */
    val kind: String,
    val at: Long,
    /** The catch file's name in the mailbox; null if it couldn't be sent. */
    val fileName: String? = null,
    val jobId: String? = null,
    val subtitle: String = ""
)

@Serializable
data class Settings(
    val briefOn: Boolean = true,
    val briefHour: Int = 6,
    val briefMinute: Int = 30,
    val feeds: List<String> = DEFAULT_FEEDS,
    /** A group name for each feed link ("News", "F1"), used to arrange the brief. */
    val feedGroups: Map<String, String> = emptyMap(),
    val notify: Boolean = true,
    val wifiOnly: Boolean = false,
    /** Stay awake with a quiet notification (checks every half minute) instead of every 15 minutes. */
    val stayAwake: Boolean = true,
    /** Paused here while there's no mailbox (with one, the STOP file is the switch). */
    val pausedHere: Boolean = false,
    val lastBriefDay: String? = null,
    /** Settings → Look: "oled", "white" or "system". */
    val theme: String = "oled"
) {
    companion object {
        val DEFAULT_FEEDS = listOf(
            "https://feeds.bbci.co.uk/news/world/rss.xml",
            "https://feeds.npr.org/1001/rss.xml",
            "https://hnrss.org/frontpage"
        )
    }
}

/** Everything the Lobster keeps, saved as one file in its private storage. */
@Serializable
data class Saved(
    val jobs: List<Job> = emptyList(),
    val watches: List<Watch> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val delivered: List<Delivered> = emptyList(),
    val settings: Settings = Settings(),
    /** Ids of orders already taken, so a copy of an old order file can't make it run again. */
    val seenOrders: List<String> = emptyList(),
    val repeats: List<Repeat> = emptyList(),
    /** Headlines already sent in a brief (their links), so a brief never repeats one. */
    val sentHeadlines: List<String> = emptyList(),
    val engines: Map<String, EngineHealth> = emptyMap()
)
