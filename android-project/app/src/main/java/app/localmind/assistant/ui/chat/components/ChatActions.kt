package app.localmind.assistant.ui.chat.components

import androidx.compose.runtime.compositionLocalOf

/**
 * What the reply UI can ask the chat to do, provided once by the chat screen instead of being
 * threaded through every list item.
 */
data class ChatActions(
    /** The loaded model's context size in tokens, for the "used / total" gauge (0 = unknown). */
    val contextSize: Int = 0,
    /** Continue a reply that stopped at the length limit. */
    val onContinue: (() -> Unit)? = null,
    /** Repeat-after-me with a sentence from this reply (tutors only). */
    val onPractise: ((String) -> Unit)? = null,
    /** Save the idea card in this reply to the idea book (the Trader only). */
    val onSaveIdea: ((String) -> Unit)? = null,
    /** Save the gist card in this reply to the book's card in SlothBooks (the Librarian only). */
    val onSaveGist: ((String) -> Unit)? = null,
    /** Keep this reply as the desk's notes on today's edition (the desk persona the edition asked). */
    val onKeepOnDesk: ((String) -> Unit)? = null,
    /** Keep this reply as a document in the project that asked (the Scribe or the Grill, while a project's ask is open). */
    val onKeepInProject: ((String) -> Unit)? = null,
    /** The project that asked, for the button's label. */
    val projectName: String? = null
)

val LocalChatActions = compositionLocalOf { ChatActions() }
