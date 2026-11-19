package app.localmind.assistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** A persona and a time, from a query (not a table). */
data class PersonaTime(val personaId: String, val at: Long)

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

    @Query("SELECT * FROM personas WHERE id = :personaId LIMIT 1")
    suspend fun getPersonaById(personaId: String): PersonaEntity?

    @Query("DELETE FROM personas WHERE id = :personaId AND isBuiltIn = 0")
    suspend fun deleteCustomPersona(personaId: String)

    // Conversations & Messages
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT modelId FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentConversationModelId(): String?

    // @Upsert updates in place. REPLACE would delete the row first, and the foreign-key
    // CASCADE would take every child row (e.g. all of a conversation's messages) with it.
    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setConversationArchived(id: String, isArchived: Boolean, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    // @Upsert updates in place. REPLACE would delete the row first, and the foreign-key
    // CASCADE would take every child row (e.g. all of a conversation's messages) with it.
    @Upsert
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status, errorCode = :errorCode, promptTokens = :promptTokens, completionTokens = :completionTokens, generationSpeedTps = :speedTps, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMessageContentAndStatus(
        id: String,
        content: String,
        status: String,
        updatedAt: Long,
        errorCode: String? = null,
        promptTokens: Int = 0,
        completionTokens: Int = 0,
        speedTps: Float? = null
    )

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    /** Chats whose title or any message contains [pattern] (already escaped for LIKE with '\\'). */
    @Query(
        "SELECT id FROM conversations WHERE title LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "UNION SELECT DISTINCT conversationId FROM messages WHERE content LIKE '%' || :pattern || '%' ESCAPE '\\'"
    )
    suspend fun conversationIdsMatching(pattern: String): List<String>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :timestamp")
    suspend fun deleteMessagesFromTimestamp(conversationId: String, timestamp: Long)

    /** For each persona, when you last wrote to it (the Home page's "last practised"). */
    @Query(
        "SELECT c.personaId AS personaId, MAX(m.createdAt) AS at FROM messages m " +
            "JOIN conversations c ON c.id = m.conversationId WHERE m.role = 'USER' GROUP BY c.personaId"
    )
    fun observeLastWrittenByPersona(): Flow<List<PersonaTime>>

    /** The hours since [since] in which you wrote to each persona (one row per persona and hour; days are counted in local time by the caller). */
    @Query(
        "SELECT DISTINCT c.personaId AS personaId, (m.createdAt / 3600000) * 3600000 AS at FROM messages m " +
            "JOIN conversations c ON c.id = m.conversationId WHERE m.role = 'USER' AND m.createdAt >= :since"
    )
    fun observeWrittenSince(since: Long): Flow<List<PersonaTime>>

    // Knowledge Collections, Sources & Chunks
    @Query("SELECT * FROM knowledge_collections ORDER BY updatedAt DESC")
    fun observeCollections(): Flow<List<KnowledgeCollectionEntity>>

    // @Upsert updates in place. REPLACE would delete the row first, and the foreign-key
    // CASCADE would take every child row (e.g. all of a conversation's messages) with it.
    @Upsert
    suspend fun upsertCollection(collection: KnowledgeCollectionEntity)

    @Query("DELETE FROM knowledge_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("SELECT * FROM knowledge_sources WHERE collectionId = :collectionId ORDER BY retrievedAt DESC")
    fun observeSourcesForCollection(collectionId: String): Flow<List<KnowledgeSourceEntity>>

    // @Upsert updates in place. REPLACE would delete the row first, and the foreign-key
    // CASCADE would take every child row (e.g. all of a conversation's messages) with it.
    @Upsert
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

    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC")
    suspend fun getAllMemories(): List<UserMemoryEntity>

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

    @Query("SELECT COUNT(*) FROM vocabulary_items WHERE nextReviewAt <= :now")
    suspend fun countDueVocabulary(now: Long): Int

    // Backup / restore: everything the learner made (models aren't included; they're re-importable).
    @Query("SELECT * FROM conversations ORDER BY createdAt ASC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("SELECT * FROM personas WHERE isBuiltIn = 0")
    suspend fun getCustomPersonas(): List<PersonaEntity>

    @Query("SELECT * FROM vocabulary_items")
    suspend fun getAllVocabulary(): List<VocabularyItemEntity>

    @Query("SELECT * FROM vocabulary_items WHERE term = :term AND targetLanguage = :language LIMIT 1")
    suspend fun findVocabularyItem(term: String, language: String): VocabularyItemEntity?

    @Query("DELETE FROM vocabulary_items WHERE id = :id")
    suspend fun deleteVocabularyItem(id: String)

    // App Events
    @Query("SELECT * FROM app_events ORDER BY createdAt DESC LIMIT 200")
    fun observeAppEvents(): Flow<List<AppEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppEvent(event: AppEventEntity)

    // Privacy Wipe
    @Transaction
    suspend fun deleteAllLocalData() {
        deleteAllConversations()
        deleteAllMemories()
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETE'")
        db.execSQL("ALTER TABLE messages ADD COLUMN errorCode TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN parentMessageId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
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
    version = 2,
    exportSchema = false
)
abstract class LocalMindDatabase : RoomDatabase() {
    abstract fun dao(): LocalMindDao
}
