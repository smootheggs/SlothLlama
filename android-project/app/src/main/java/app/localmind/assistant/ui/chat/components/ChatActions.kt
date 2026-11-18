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
    val onPractise: ((String) -> Unit)? = null
)

val LocalChatActions = compositionLocalOf { ChatActions() }
