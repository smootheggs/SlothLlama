package app.localmind.assistant.ui.chat

import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.Conversation

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
    val activeModelName: String = "Gemma 2 2B (INT4)",
    val modelStatus: ModelRuntimeStatus = ModelRuntimeStatus.READY,
    val editingMessage: ChatMessage? = null,
    val errorMessage: String? = null,
    val renameTarget: Conversation? = null,
    val deleteTarget: Conversation? = null,
    val isClearAllConfirmOpen: Boolean = false,
    val isDrawerOpen: Boolean = false
) {
    val activeConversation: Conversation?
        get() = conversations.find { it.id == activeConversationId }
            ?: archivedConversations.find { it.id == activeConversationId }
}
