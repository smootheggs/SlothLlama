package app.localmind.assistant.domain.repository

import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    /** Live state of the on-device model (loading, ready, missing, failed). */
    val engineState: StateFlow<EngineRuntimeState>

    /** Loads the default model in the background so the first reply doesn't wait for it. */
    suspend fun warmUpModel()

    fun observeConversations(): Flow<List<Conversation>>
    fun observeArchivedConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun getConversation(conversationId: String): Conversation?
    suspend fun createConversation(title: String, personaId: String, modelId: String): Conversation
    suspend fun renameConversation(conversationId: String, newTitle: String)
    suspend fun archiveConversation(conversationId: String, isArchived: Boolean)
    suspend fun deleteConversation(conversationId: String)
    suspend fun deleteAllConversations()
    fun sendMessage(conversationId: String, content: String): Flow<ChatMessage>
    suspend fun stopGeneration()
    fun regenerateLastResponse(conversationId: String): Flow<ChatMessage>
    fun editAndResend(conversationId: String, messageId: String, newContent: String): Flow<ChatMessage>
    suspend fun deleteMessage(messageId: String)
}
