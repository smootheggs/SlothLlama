package app.localmind.assistant.data.repository

import app.localmind.assistant.data.local.ConversationEntity
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.MessageEntity
import app.localmind.assistant.data.persona.ChineseStyleStore
import app.localmind.assistant.data.persona.PersonaModelStore
import app.localmind.assistant.data.persona.PersonaRepository
import app.localmind.assistant.data.settings.AssistantSettingsStore
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.inference.GenerationEvent
import app.localmind.assistant.domain.inference.GenerationRequest
import app.localmind.assistant.domain.inference.LocalInferenceEngine
import app.localmind.assistant.domain.inference.PromptTurn
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.MessageNotes
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.persona.ChineseRules
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.domain.model.MessageStatus
import app.localmind.assistant.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: LocalMindDao,
    private val engine: LocalInferenceEngine,
    private val personaRepository: PersonaRepository,
    private val chineseStyle: ChineseStyleStore,
    private val assistantSettings: AssistantSettingsStore,
    private val personaModels: PersonaModelStore
) : ChatRepository {

    override val engineState: StateFlow<EngineRuntimeState>
        get() = engine.runtimeState

    override suspend fun warmUpModel() {
        val modelId = withContext(Dispatchers.IO) {
            dao.getMostRecentConversationModelId()
        } ?: DEFAULT_MODEL_ID
        engine.loadModel(modelId)
    }

    override suspend fun searchConversationIds(query: String): Set<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptySet()
        val pattern = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.conversationIdsMatching(pattern).toSet()
    }

    override suspend fun warmUpForPersona(personaId: String) {
        engine.loadModel(personaModels.fileFor(personaId) ?: DEFAULT_MODEL_ID)
    }

    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeActiveConversations().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)

    override fun observeArchivedConversations(): Flow<List<Conversation>> =
        dao.observeArchivedConversations().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)

    override suspend fun getConversation(conversationId: String): Conversation? =
        withContext(Dispatchers.IO) {
            dao.getConversationById(conversationId)?.toDomain()
        }

    override suspend fun createConversation(
        title: String,
        personaId: String,
        modelId: String
    ): Conversation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "New Conversation" },
            personaId = personaId.ifBlank { BuiltInPersonas.default.id },
            modelId = modelId.ifBlank { DEFAULT_MODEL_ID },
            rollingSummary = null,
            parentConversationId = null,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertConversation(conv)
        conv.toDomain()
    }

    override suspend fun renameConversation(conversationId: String, newTitle: String) =
        withContext(Dispatchers.IO) {
            dao.updateConversationTitle(conversationId, newTitle.trim(), System.currentTimeMillis())
        }

    override suspend fun archiveConversation(conversationId: String, isArchived: Boolean) =
        withContext(Dispatchers.IO) {
            dao.setConversationArchived(conversationId, isArchived, System.currentTimeMillis())
        }

    override suspend fun deleteConversation(conversationId: String) =
        withContext(Dispatchers.IO) {
            dao.deleteConversation(conversationId)
        }

    override suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        dao.deleteAllConversations()
    }

    override suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    override suspend fun stopGeneration() {
        engine.cancelGeneration()
    }

    override fun sendMessage(conversationId: String, content: String): Flow<ChatMessage> = flow {
        val now = System.currentTimeMillis()
        val conversation = dao.getConversationById(conversationId)
            ?: createConversation("New Conversation", BuiltInPersonas.default.id, DEFAULT_MODEL_ID).let {
                dao.getConversationById(it.id)!!
            }

        // 1. Auto-title if it's the first message and still has default title
        if (conversation.title == "New Conversation" || conversation.title.isBlank()) {
            val autoTitle = deriveAutoTitle(content)
            dao.updateConversationTitle(conversation.id, autoTitle, now)
        } else {
            dao.upsertConversation(conversation.copy(updatedAt = now))
        }

        // 2. Insert User Message
        val userMsgId = UUID.randomUUID().toString()
        val userEntity = MessageEntity(
            id = userMsgId,
            conversationId = conversation.id,
            role = MessageRole.USER.name,
            content = content.trim(),
            status = MessageStatus.COMPLETE.name,
            errorCode = null,
            parentMessageId = null,
            modelIdUsed = conversation.modelId,
            personaIdUsed = conversation.personaId,
            isCloudResponse = false,
            promptTokens = 0,
            completionTokens = 0,
            generationSpeedTps = null,
            createdAt = now,
            updatedAt = now
        )
        dao.insertMessage(userEntity)
        emit(userEntity.toDomain())

        // 3. Create Assistant Message placeholder with PENDING
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = MessageEntity(
            id = assistantMsgId,
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT.name,
            content = "",
            status = MessageStatus.PENDING.name,
            errorCode = null,
            parentMessageId = userMsgId,
            modelIdUsed = conversation.modelId,
            personaIdUsed = conversation.personaId,
            isCloudResponse = false,
            promptTokens = 0,
            completionTokens = 0,
            generationSpeedTps = null,
            createdAt = now + 1,
            updatedAt = now + 1
        )
        dao.insertMessage(assistantPlaceholder)
        emit(assistantPlaceholder.toDomain())

        // 4. Retrieve conversation history for context assembly
        val existingMessages = dao.getMessagesForConversation(conversation.id)
            // The new user message goes in <user_request>, so leave it out of the history.
            .filter { it.id != assistantMsgId && it.id != userMsgId && it.status == MessageStatus.COMPLETE.name }
            .map { PromptTurn(role = it.role, content = it.content) }

        val persona = personaRepository.byId(conversation.personaId)
        val request = GenerationRequest(
            conversationId = conversation.id,
            // The persona's own model if it has one, otherwise the default model.
            modelId = personaModels.fileFor(persona.id) ?: conversation.modelId,
            // Chinese tutors get their character / romanization rules from Settings → Chinese.
            personaSystemInstruction = ChineseRules.systemInstructionFor(persona, chineseStyle.style.value),
            conversationSummary = conversation.rollingSummary,
            recentMessages = existingMessages,
            retrievedPassages = emptyList(),
            userMemories = emptyList(),
            userPrompt = content.trim(),
            temperature = persona.temperature,
            maxOutputTokens = persona.maxOutputTokens,
            allowThinking = assistantSettings.thinking.value,
            speechLanguage = persona.speech
        )

        // 5. Update assistant status to STREAMING
        dao.updateMessageContentAndStatus(
            id = assistantMsgId,
            content = "",
            status = MessageStatus.STREAMING.name,
            updatedAt = System.currentTimeMillis()
        )

        val textAccumulator = StringBuilder()
        var lastDbFlushTime = System.currentTimeMillis()
        var lastTokenCount = 0

        engine.generate(request).collect { event ->
            when (event) {
                is GenerationEvent.Started -> {
                    emit(
                        assistantPlaceholder.copy(
                            status = MessageStatus.STREAMING.name,
                            promptTokens = event.promptTokensUsed
                        ).toDomain()
                    )
                }

                is GenerationEvent.TokenDelta -> {
                    textAccumulator.append(event.textDelta)
                    lastTokenCount++
                    val currentText = textAccumulator.toString()
                    val currentTime = System.currentTimeMillis()

                    // Throttle SQLite disk writes to every 150ms or every 10 tokens to avoid I/O thrashing
                    if (currentTime - lastDbFlushTime > 150 || lastTokenCount % 10 == 0) {
                        dao.updateMessageContentAndStatus(
                            id = assistantMsgId,
                            content = currentText,
                            status = MessageStatus.STREAMING.name,
                            updatedAt = currentTime
                        )
                        lastDbFlushTime = currentTime
                    }

                    emit(
                        assistantPlaceholder.copy(
                            content = currentText,
                            status = MessageStatus.STREAMING.name,
                            updatedAt = currentTime
                        ).toDomain()
                    )
                }

                is GenerationEvent.Completed -> {
                    val finalContent = if (event.fullText.isNotBlank()) event.fullText else textAccumulator.toString()
                    val completionTime = System.currentTimeMillis()
                    // A finished reply carries notes in errorCode (no schema change needed):
                    // LENGTH = stopped at the length limit (offer Continue), TRIMMED = older
                    // messages no longer fit the context. promptTokens holds the context in use.
                    val notes = listOfNotNull(
                        MessageNotes.LENGTH.takeIf { event.stoppedAtLimit },
                        MessageNotes.TRIMMED.takeIf { event.historyTrimmed }
                    ).joinToString(",").ifEmpty { null }
                    dao.updateMessageContentAndStatus(
                        id = assistantMsgId,
                        content = finalContent,
                        status = MessageStatus.COMPLETE.name,
                        errorCode = notes,
                        promptTokens = event.contextUsed,
                        updatedAt = completionTime,
                        completionTokens = event.tokensGenerated,
                        speedTps = event.tokensPerSecond
                    )
                    emit(
                        assistantPlaceholder.copy(
                            content = finalContent,
                            status = MessageStatus.COMPLETE.name,
                            errorCode = notes,
                            promptTokens = event.contextUsed,
                            completionTokens = event.tokensGenerated,
                            generationSpeedTps = event.tokensPerSecond,
                            updatedAt = completionTime
                        ).toDomain()
                    )
                }

                is GenerationEvent.Canceled -> {
                    val partial = if (event.partialText.isNotBlank()) event.partialText else textAccumulator.toString()
                    val cancelTime = System.currentTimeMillis()
                    dao.updateMessageContentAndStatus(
                        id = assistantMsgId,
                        content = partial,
                        status = MessageStatus.CANCELLED.name,
                        updatedAt = cancelTime
                    )
                    emit(
                        assistantPlaceholder.copy(
                            content = partial,
                            status = MessageStatus.CANCELLED.name,
                            updatedAt = cancelTime
                        ).toDomain()
                    )
                }

                is GenerationEvent.Error -> {
                    val errTime = System.currentTimeMillis()
                    val errorContent = if (textAccumulator.isNotEmpty()) textAccumulator.toString() else event.userMessage
                    dao.updateMessageContentAndStatus(
                        id = assistantMsgId,
                        content = errorContent,
                        status = MessageStatus.FAILED.name,
                        errorCode = event.code,
                        updatedAt = errTime
                    )
                    emit(
                        assistantPlaceholder.copy(
                            content = errorContent,
                            status = MessageStatus.FAILED.name,
                            errorCode = event.code,
                            updatedAt = errTime
                        ).toDomain()
                    )
                }

                is GenerationEvent.ToolInvoked -> {
                    // Phase 1 preserves tool invocation event without interrupting stream
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun regenerateLastResponse(conversationId: String): Flow<ChatMessage> = flow {
        val messages = dao.getMessagesForConversation(conversationId)
        val lastUser = messages.lastOrNull { it.role == MessageRole.USER.name } ?: return@flow

        // Remove the last question and its answer, then ask again: sendMessage re-adds the question,
        // so keeping the old copy would show the model the same question twice.
        dao.deleteMessagesFromTimestamp(conversationId, lastUser.createdAt)
        sendMessage(conversationId, lastUser.content).collect {
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    override fun editAndResend(
        conversationId: String,
        messageId: String,
        newContent: String
    ): Flow<ChatMessage> = flow {
        val targetMessage = dao.getMessageById(messageId) ?: return@flow
        // Remove target message and all subsequent messages to branch cleanly
        dao.deleteMessagesFromTimestamp(conversationId, targetMessage.createdAt)
        sendMessage(conversationId, newContent).collect {
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    private fun deriveAutoTitle(prompt: String): String {
        // Photos and documents are carried in the text; the title uses what was typed.
        val clean = app.localmind.assistant.domain.attachments.Attachments.summary(prompt)
            .trim().lines().firstOrNull { it.isNotBlank() } ?: "New Chat"
        return if (clean.length > 32) {
            clean.take(30).trimEnd() + "…"
        } else {
            clean
        }
    }

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        personaId = personaId,
        modelId = modelId,
        rollingSummary = rollingSummary,
        parentConversationId = parentConversationId,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = MessageRole.fromString(role),
        content = content,
        status = MessageStatus.fromString(status),
        errorCode = errorCode,
        parentMessageId = parentMessageId,
        modelIdUsed = modelIdUsed,
        personaIdUsed = personaIdUsed,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        generationSpeedTps = generationSpeedTps,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

const val DEFAULT_MODEL_ID = "gemma-2-2b-it-litert-int4"
