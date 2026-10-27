package app.localmind.assistant.domain.model

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    companion object {
        fun fromString(role: String): MessageRole = when (role.uppercase()) {
            "SYSTEM" -> SYSTEM
            "USER" -> USER
            "ASSISTANT" -> ASSISTANT
            "TOOL" -> TOOL
            else -> USER
        }
    }
}

enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETE,
    FAILED,
    CANCELLED;

    companion object {
        fun fromString(status: String): MessageStatus = when (status.uppercase()) {
            "PENDING" -> PENDING
            "STREAMING" -> STREAMING
            "COMPLETE" -> COMPLETE
            "FAILED" -> FAILED
            "CANCELLED" -> CANCELLED
            else -> COMPLETE
        }
    }
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val errorCode: String? = null,
    val parentMessageId: String? = null,
    val modelIdUsed: String? = null,
    val personaIdUsed: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val generationSpeedTps: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class Conversation(
    val id: String,
    val title: String,
    val personaId: String,
    val modelId: String,
    val rollingSummary: String? = null,
    val parentConversationId: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
