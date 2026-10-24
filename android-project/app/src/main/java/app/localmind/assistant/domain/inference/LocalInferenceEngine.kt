package app.localmind.assistant.domain.inference

import kotlinx.coroutines.flow.Flow

/**
 * Core inference abstraction allowing LocalMind to support LiteRT-LM (Google AI Edge),
 * deterministic test fakes, and future validated on-device runtimes without loading
 * untrusted executable code.
 */
interface LocalInferenceEngine {
    suspend fun inspectModel(source: ModelSource): ModelCompatibilityReport
    suspend fun loadModel(modelId: String): Result<Unit>
    suspend fun unloadModel()
    fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun cancelGeneration()
    fun capabilities(): EngineCapabilities
}

sealed interface ModelSource {
    data class CatalogRegistryEntry(
        val modelId: String,
        val format: String,
        val quantization: String,
        val downloadSizeBytes: Long,
        val estimatedRamBytes: Long,
        val sha256Checksum: String,
        val tokenizerIncluded: Boolean,
        val licenseAccepted: Boolean
    ) : ModelSource

    data class SafUriSource(
        val uriString: String,
        val displayName: String,
        val sizeBytes: Long
    ) : ModelSource
}

enum class CompatibilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    POSSIBLY_SUPPORTED,
    MISSING_TOKENIZER,
    UNSUPPORTED_QUANTIZATION,
    INSUFFICIENT_STORAGE,
    ESTIMATED_INSUFFICIENT_MEMORY,
    INCOMPATIBLE_RUNTIME,
    INVALID_MANIFEST,
    LICENSE_NOT_ACCEPTED
}

data class ModelCompatibilityReport(
    val status: CompatibilityStatus,
    val isCompatible: Boolean,
    val detailedReason: String,
    val availableStorageBytes: Long,
    val requiredStorageBytes: Long,
    val availableMemoryBytes: Long,
    val estimatedMemoryBytes: Long,
    val recommendedAccelerators: List<HardwareAccelerator>,
    val warnings: List<String>
)

enum class HardwareAccelerator {
    CPU, GPU, NPU
}

data class EngineCapabilities(
    val engineId: String,
    val engineName: String,
    val supportedFormats: Set<String>,
    val supportedQuantizations: Set<String>,
    val maxContextWindow: Int,
    val availableAccelerators: Set<HardwareAccelerator>,
    val supportsStreaming: Boolean
)

data class GenerationRequest(
    val conversationId: String,
    val modelId: String,
    val personaSystemInstruction: String,
    val conversationSummary: String?,
    val recentMessages: List<PromptTurn>,
    val retrievedPassages: List<RetrievedPassage>,
    val userMemories: List<String>,
    val userPrompt: String,
    val temperature: Float = 0.4f,
    val maxOutputTokens: Int = 1024,
    val totalContextBudgetTokens: Int = 4096
)

data class PromptTurn(
    val role: String,
    val content: String
)

data class RetrievedPassage(
    val chunkId: String,
    val sourceId: String,
    val sourceTitle: String,
    val heading: String,
    val collectionName: String,
    val originalUrl: String?,
    val retrievedAtIso: String,
    val excerpt: String,
    val cosineSimilarity: Float
)

sealed interface GenerationEvent {
    data class Started(val modelId: String, val promptTokensUsed: Int) : GenerationEvent
    data class TokenDelta(val textDelta: String) : GenerationEvent
    data class ToolInvoked(val toolName: String, val input: String, val deterministicResult: String) : GenerationEvent
    data class Completed(
        val fullText: String,
        val citationsUsed: List<RetrievedPassage>,
        val tokensGenerated: Int,
        val tokensPerSecond: Float,
        val peakMemoryMb: Int,
        val isCloudGenerated: Boolean = false
    ) : GenerationEvent
    data class Canceled(val partialText: String) : GenerationEvent
    data class Error(
        val code: String,
        val userMessage: String,
        val isLocalDataSafe: Boolean = true,
        val recommendedAction: String,
        val technicalDetails: String
    ) : GenerationEvent
}
