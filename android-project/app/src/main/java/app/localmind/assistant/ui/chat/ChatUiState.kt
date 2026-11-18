package app.localmind.assistant.ui.chat

import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.persona.Persona

enum class VoiceInputState { IDLE, LISTENING, TRANSCRIBING }

enum class ModelRuntimeStatus {
    READY,
    GENERATING,
    NO_MODEL_LOADED,
    ERROR
}

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val archivedConversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingMessageId: String? = null,
    val activeModelName: String = "Starting…",
    val modelStatus: ModelRuntimeStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
    val editingMessage: ChatMessage? = null,
    val errorMessage: String? = null,
    val renameTarget: Conversation? = null,
    val deleteTarget: Conversation? = null,
    val isClearAllConfirmOpen: Boolean = false,
    val isDrawerOpen: Boolean = false,
    val personas: List<Persona> = BuiltInPersonas.all,
    /** The loaded model's context size in tokens (0 until a model is ready). */
    val contextSize: Int = 0,
    /** The active conversation's persona, or the one new chats will use. */
    val activePersona: Persona = BuiltInPersonas.default
) {
    val activeConversation: Conversation?
        get() = conversations.find { it.id == activeConversationId }
            ?: archivedConversations.find { it.id == activeConversationId }
}
