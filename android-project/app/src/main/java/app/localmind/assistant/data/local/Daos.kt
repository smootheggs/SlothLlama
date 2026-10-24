package app.localmind.assistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMindDao {
    // Installed Models & Downloads
    @Query("SELECT * FROM installed_models ORDER BY installedAt DESC")
    fun observeInstalledModels(): Flow<List<InstalledModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstalledModel(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE modelId = :modelId")
    suspend fun deleteInstalledModel(modelId: String)

    @Query("SELECT * FROM model_downloads ORDER BY updatedAt DESC")
    fun observeDownloads(): Flow<List<ModelDownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: ModelDownloadEntity)

    // Personas
    @Query("SELECT * FROM personas WHERE isArchived = 0 ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeActivePersonas(): Flow<List<PersonaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(persona: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :personaId AND isBuiltIn = 0")
    suspend fun deleteCustomPersona(personaId: String)

    // Conversations & Messages
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    // Knowledge Collections, Sources & Chunks
    @Query("SELECT * FROM knowledge_collections ORDER BY updatedAt DESC")
    fun observeCollections(): Flow<List<KnowledgeCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: KnowledgeCollectionEntity)

    @Query("DELETE FROM knowledge_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("SELECT * FROM knowledge_sources WHERE collectionId = :collectionId ORDER BY retrievedAt DESC")
    fun observeSourcesForCollection(collectionId: String): Flow<List<KnowledgeSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: KnowledgeSourceEntity)

    @Query("DELETE FROM knowledge_sources WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: String)

    @Query("SELECT * FROM knowledge_chunks WHERE collectionId IN (:collectionIds)")
    suspend fun getChunksForCollections(collectionIds: List<String>): List<KnowledgeChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    // Citations
    @Query("SELECT * FROM citations WHERE messageId = :messageId")
    suspend fun getCitationsForMessage(messageId: String): List<CitationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCitations(citations: List<CitationEntity>)

    // User Memories
    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC")
    fun observeMemories(): Flow<List<UserMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memories WHERE id = :memoryId")
    suspend fun deleteMemory(memoryId: String)

    @Query("DELETE FROM user_memories")
    suspend fun deleteAllMemories()

    // Tutor Progress & Vocabulary
    @Query("SELECT * FROM tutor_progress ORDER BY lastPracticedAt DESC")
    fun observeTutorProgress(): Flow<List<TutorProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTutorProgress(progress: TutorProgressEntity)

    @Query("SELECT * FROM vocabulary_items ORDER BY nextReviewAt ASC")
    fun observeVocabularyQueue(): Flow<List<VocabularyItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVocabularyItem(item: VocabularyItemEntity)

    // App Events
    @Query("SELECT * FROM app_events ORDER BY createdAt DESC LIMIT 200")
    fun observeAppEvents(): Flow<List<AppEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppEvent(event: AppEventEntity)

    // Privacy Wipe
    @Transaction
    suspend fun deleteAllLocalData() {
        deleteAllMemories()
    }
}

@Database(
    entities = [
        InstalledModelEntity::class,
        ModelDownloadEntity::class,
        PersonaEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        KnowledgeCollectionEntity::class,
        KnowledgeSourceEntity::class,
        KnowledgeChunkEntity::class,
        CitationEntity::class,
        UserMemoryEntity::class,
        TutorProgressEntity::class,
        VocabularyItemEntity::class,
        AppEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LocalMindDatabase : RoomDatabase() {
    abstract fun dao(): LocalMindDao
}
