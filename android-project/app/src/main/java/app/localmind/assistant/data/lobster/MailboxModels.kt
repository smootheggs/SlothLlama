package app.localmind.assistant.data.lobster

import kotlinx.serialization.Serializable

/*
 * The SlothLobster mailbox, version 1 (docs/LOBSTER_MAILBOX.md has the full format).
 * Every file is AES-256-GCM encrypted with the key both apps share; these are the JSON bodies.
 */

/** A job SlothLlama gives the Lobster. */
@Serializable
data class LobsterOrder(
    val v: Int = 1,
    val id: String,
    /** research, fetch, watch, brief or ask (see [OrderKind]). */
    val kind: String,
    /** What to do, in the user's words (a question, a link, what to watch). */
    val text: String,
    /** quick, or deep (may run overnight). */
    val depth: String = "quick",
    /** Where the catch should go: library, notes or chat. */
    val deliver: String = "library",
    val createdAt: Long,
    /** Optional (1.1): the searches to run for a research order. */
    val queries: List<String> = emptyList()
)

@Serializable
data class LobsterSource(val title: String = "", val url: String = "")

/** Something the Lobster brings back. */
@Serializable
data class LobsterCatch(
    val v: Int = 1,
    val id: String,
    /** The order it answers (none for something the Lobster sends on its own, like a brief). */
    val orderId: String? = null,
    /** document, note, message or alert. */
    val kind: String = "document",
    val title: String,
    /** Markdown text: the brief, the article, the message. */
    val text: String = "",
    val sources: List<LobsterSource> = emptyList(),
    val createdAt: Long = 0,
    /** An attached file (a PDF or EPUB it fetched), base64, at most a few MB. */
    val fileName: String? = null,
    val fileBase64: String? = null
)

/** The Lobster's heartbeat, rewritten every minute or so while it runs. */
@Serializable
data class LobsterStatus(
    val v: Int = 1,
    /** awake, busy, paused or asleep. */
    val state: String = "awake",
    val at: Long,
    val version: String = "",
    /** What it's doing now ("Researching: …"). */
    val working: String? = null,
    /** Orders waiting in its own queue. */
    val queue: Int = 0
)

enum class OrderKind(val id: String, val label: String, val hint: String) {
    RESEARCH("research", "Research", "A question to look into, with sources"),
    FETCH("fetch", "Fetch", "A link to bring back for offline reading"),
    WATCH("watch", "Watch", "A page or topic to keep an eye on"),
    BRIEF("brief", "Brief", "A short daily round-up"),
    ASK("ask", "Ask", "Anything else");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: ASK
    }
}

/** An order SlothLlama has sent, kept on the tablet to show its progress. */
@Serializable
data class SentOrder(val order: LobsterOrder, val fileName: String)
