package app.localmind.assistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_models")
data class InstalledModelEntity(
    @PrimaryKey val modelId: String,
    val displayName: String,
    val version: String,
    val architecture: String,
    val format: String,
    val quantization: String,
    val contextWindow: Int,
    val localFilePath: String,
    val sizeBytes: Long,
    val estimatedRamBytes: Long,
    val sha256Checksum: String,
    val isIntegrityVerified: Boolean,
    val licenseSpdx: String,
    val licenseAcceptedAt: Long,
    val installedAt: Long
)

@Entity(tableName = "model_downloads")
data class ModelDownloadEntity(
    @PrimaryKey val modelId: String,
    val sourceUrl: String,
    val status: String, // QUEUED, DOWNLOADING, PAUSED, VERIFYING, COMPLETED, FAILED, CANCELED
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val expectedSha256: String,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val errorMessage: String?,
    val updatedAt: Long
)

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val accentHex: String,
    val selectedModelId: String,
    val systemInstruction: String,
    val temperature: Float,
    val maxOutputTokens: Int,
    val contextWindowStrategy: String,
    val enabledCollectionIdsJson: String,
    val enabledToolsJson: String,
    val memoryEnabled: Boolean,
    val tutorConfigJson: String?,
    val isArchived: Boolean,
    val isBuiltIn: Boolean,
    val createdAt: Long
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String,
    val modelId: String,
    val rollingSummary: String?,
    val parentConversationId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // USER, ASSISTANT, SYSTEM
    val content: String,
    val modelIdUsed: String?,
    val personaIdUsed: String?,
    val isCloudResponse: Boolean,
    val promptTokens: Int,
    val completionTokens: Int,
    val generationSpeedTps: Float?,
    val createdAt: Long
)

@Entity(tableName = "knowledge_collections")
data class KnowledgeCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val license: String,
    val sourceCount: Int,
    val chunkCount: Int,
    val totalSizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "knowledge_sources",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId")]
)
data class KnowledgeSourceEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val title: String,
    val author: String?,
    val sourceType: String, // TEXT, MARKDOWN, PDF, EPUB, HTML, URL, WIKIPEDIA_DUMP
    val sourceUrl: String?,
    val license: String,
    val retrievedAt: Long,
    val language: String,
    val sha256Checksum: String,
    val indexStatus: String, // INSPECTING, INDEXING, PAUSED, READY, FAILED
    val extractedTextPreview: String,
    val sizeBytes: Long
)

@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceId"), Index("collectionId")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey val chunkId: String,
    val sourceId: String,
    val collectionId: String,
    val heading: String,
    val normalizedText: String,
    val tokenCount: Int,
    val vectorIndexOffset: Long
)

@Entity(
    tableName = "citations",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class CitationEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val chunkId: String,
    val sourceId: String,
    val sourceTitle: String,
    val sectionHeading: String,
    val collectionName: String,
    val originalUrl: String?,
    val retrievalDateIso: String,
    val supportingExcerpt: String,
    val similarityScore: Float,
    val whyUsedExplanation: String
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val id: String,
    val factText: String,
    val scope: String, // GLOBAL or PERSONA_ID
    val personaId: String?,
    val isApprovedByUser: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "tutor_progress")
data class TutorProgressEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val domain: String, // LANGUAGE or MATH
    val topicName: String,
    val cefrOrDifficultyLevel: String,
    val exercisesCompleted: Int,
    val streakDays: Int,
    val lastPracticedAt: Long
)

@Entity(tableName = "vocabulary_items")
data class VocabularyItemEntity(
    @PrimaryKey val id: String,
    val targetLanguage: String,
    val term: String,
    val transliteration: String?,
    val translation: String,
    val exampleSentence: String,
    val cefrLevel: String,
    val easeFactor: Float,
    val intervalDays: Int,
    val nextReviewAt: Long
)

@Entity(tableName = "app_events")
data class AppEventEntity(
    @PrimaryKey val id: String,
    val category: String, // SECURITY, DOWNLOAD, INDEXING, INFERENCE, POLICY
    val severity: String, // INFO, WARN, ERROR
    val message: String,
    val redactedDetails: String?,
    val createdAt: Long
)
