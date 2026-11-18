package app.localmind.assistant.domain.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core inference abstraction allowing LocalMind to support LiteRT-LM (Google AI Edge),
 * deterministic test fakes, and future validated on-device runtimes without loading
 * untrusted executable code.
 */
interface LocalInferenceEngine {
    /** What the runtime is doing right now — drives the model badge in the chat header. */
    val runtimeState: StateFlow<EngineRuntimeState>

    suspend fun inspectModel(source: ModelSource): ModelCompatibilityReport
    suspend fun loadModel(modelId: String): Result<Unit>
    suspend fun unloadModel()
    fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun cancelGeneration()
    fun capabilities(): EngineCapabilities
}

sealed interface EngineRuntimeState {
    data object Idle : EngineRuntimeState
    /** [note]: what's taking the time, e.g. the GPU setting itself up on first use. */
    data class Loading(val modelName: String, val note: String? = null) : EngineRuntimeState
    /**
     * [canThink]: a reasoning model whose thinking can be switched on or off.
     * [gpu]: the GPU running the model, or null when it runs on the CPU.
     */
    data class Ready(
        val modelName: String,
        val maxTokens: Int,
        val canThink: Boolean = false,
        val gpu: String? = null
    ) : EngineRuntimeState
    data class NoModelFound(val sideloadPath: String) : EngineRuntimeState
    data class Failed(val modelName: String, val message: String) : EngineRuntimeState
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
    val totalContextBudgetTokens: Int = 4096,
    /** Reasoning models only: think before answering (slower, sometimes smarter). */
    val allowThinking: Boolean = false
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
        val isCloudGenerated: Boolean = false,
        /** Tokens in the model's context after this reply, and the context's size (0 = unknown). */
        val contextUsed: Int = 0,
        val contextSize: Int = 0,
        /** The reply stopped at the length limit rather than finishing: offer "Continue". */
        val stoppedAtLimit: Boolean = false,
        /** Older messages didn't fit the context and were left out of this reply's prompt. */
        val historyTrimmed: Boolean = false
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
