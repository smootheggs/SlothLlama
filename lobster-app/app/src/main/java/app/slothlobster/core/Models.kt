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
    val failures: Int = 0
) {
    val isTopic: Boolean get() = !target.startsWith("https://") && !target.startsWith("http://")
}

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
    val notify: Boolean = true,
    val wifiOnly: Boolean = false,
    /** Stay awake with a quiet notification (checks every half minute) instead of every 15 minutes. */
    val stayAwake: Boolean = true,
    /** Paused here while there's no mailbox (with one, the STOP file is the switch). */
    val pausedHere: Boolean = false,
    val lastBriefDay: String? = null
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
    val settings: Settings = Settings()
)
