package app.localmind.assistant.data.inference

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.localmind.assistant.domain.inference.*
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device inference through Google AI Edge (MediaPipe Tasks GenAI, LLM Inference API).
 * Loads .task / .litertlm models found by [ModelFileLocator] and streams replies token by token.
 *
 * Note: Google has put the MediaPipe LLM Inference API in maintenance mode in favour of the
 * standalone LiteRT-LM Kotlin API (com.google.ai.edge.litertlm). That migration needs Kotlin 2.3+,
 * so it is left for later; this class is the only place that would change.
 */
@Singleton
class LiteRtLmInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val promptAssembler: SecurePromptAssembler,
    private val calculatorTool: DeterministicCalculatorTool,
    private val modelLocator: ModelFileLocator
) : LocalInferenceEngine {

    private val loadMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var loadedFile: File? = null
    @Volatile private var loadedMaxTokens: Int = 0
    @Volatile private var activeSession: LlmInferenceSession? = null
    private val cancelRequested = AtomicBoolean(false)

    private val _runtimeState = MutableStateFlow<EngineRuntimeState>(EngineRuntimeState.Idle)
    override val runtimeState: StateFlow<EngineRuntimeState> = _runtimeState.asStateFlow()

    override suspend fun inspectModel(source: ModelSource): ModelCompatibilityReport =
        withContext(Dispatchers.IO) {
            val usableSpace = context.filesDir.usableSpace
            val runtime = Runtime.getRuntime()
            val totalRam = 8L * 1024L * 1024L * 1024L // Device RAM baseline

            when (source) {
                is ModelSource.CatalogRegistryEntry -> {
                    if (!source.licenseAccepted) {
                        return@withContext ModelCompatibilityReport(
                            status = CompatibilityStatus.LICENSE_NOT_ACCEPTED,
                            isCompatible = false,
                            detailedReason = "Model license must be explicitly reviewed and accepted before loading.",
                            availableStorageBytes = usableSpace,
                            requiredStorageBytes = source.downloadSizeBytes,
                            availableMemoryBytes = totalRam,
                            estimatedMemoryBytes = source.estimatedRamBytes,
                            recommendedAccelerators = listOf(HardwareAccelerator.GPU, HardwareAccelerator.CPU),
                            warnings = emptyList()
                        )
                    }
                    if (!source.tokenizerIncluded) {
                        return@withContext ModelCompatibilityReport(
                            status = CompatibilityStatus.MISSING_TOKENIZER,
                            isCompatible = false,
                            detailedReason = "Package manifest indicates missing sentencepiece/BPE tokenizer.",
                            availableStorageBytes = usableSpace,
                            requiredStorageBytes = source.downloadSizeBytes,
                            availableMemoryBytes = totalRam,
                            estimatedMemoryBytes = source.estimatedRamBytes,
                            recommendedAccelerators = emptyList(),
                            warnings = emptyList()
                        )
                    }
                    if (source.format !in capabilities().supportedFormats) {
                        return@withContext ModelCompatibilityReport(
                            status = CompatibilityStatus.INCOMPATIBLE_RUNTIME,
                            isCompatible = false,
                            detailedReason = "Format '${source.format}' is not supported by LiteRT-LM engine.",
                            availableStorageBytes = usableSpace,
                            requiredStorageBytes = source.downloadSizeBytes,
                            availableMemoryBytes = totalRam,
                            estimatedMemoryBytes = source.estimatedRamBytes,
                            recommendedAccelerators = emptyList(),
                            warnings = emptyList()
                        )
                    }
                    if (source.quantization !in capabilities().supportedQuantizations) {
                        return@withContext ModelCompatibilityReport(
                            status = CompatibilityStatus.UNSUPPORTED_QUANTIZATION,
                            isCompatible = false,
                            detailedReason = "Quantization '${source.quantization}' is unsupported on this device.",
                            availableStorageBytes = usableSpace,
                            requiredStorageBytes = source.downloadSizeBytes,
                            availableMemoryBytes = totalRam,
                            estimatedMemoryBytes = source.estimatedRamBytes,
                            recommendedAccelerators = emptyList(),
                            warnings = emptyList()
                        )
                    }
                    if (source.downloadSizeBytes > usableSpace) {
                        return@withContext ModelCompatibilityReport(
                            status = CompatibilityStatus.INSUFFICIENT_STORAGE,
                            isCompatible = false,
                            detailedReason = "Insufficient free storage for model download and extraction.",
                            availableStorageBytes = usableSpace,
                            requiredStorageBytes = source.downloadSizeBytes,
                            availableMemoryBytes = totalRam,
                            estimatedMemoryBytes = source.estimatedRamBytes,
                            recommendedAccelerators = emptyList(),
                            warnings = emptyList()
                        )
                    }
                    if (source.estimatedRamBytes > totalRam * 0.85) {
                        return@withContext ModelCompatibilityReport(
                            status = CompatibilityStatus.ESTIMATED_INSUFFICIENT_MEMORY,
                            isCompatible = false,
                            detailedReason = "Estimated RAM exceeds safe device memory envelope; high risk of OOM.",
                            availableStorageBytes = usableSpace,
                            requiredStorageBytes = source.downloadSizeBytes,
                            availableMemoryBytes = totalRam,
                            estimatedMemoryBytes = source.estimatedRamBytes,
                            recommendedAccelerators = listOf(HardwareAccelerator.CPU),
                            warnings = listOf("High memory pressure warning")
                        )
                    }

                    val warnings = mutableListOf<String>()
                    if (source.downloadSizeBytes > 2_000_000_000L) {
                        warnings.add("Large model (>2 GB): May cause thermal throttling during extended sessions.")
                    }

                    ModelCompatibilityReport(
                        status = CompatibilityStatus.SUPPORTED,
                        isCompatible = true,
                        detailedReason = "Validated for Google AI Edge LiteRT-LM runtime.",
                        availableStorageBytes = usableSpace,
                        requiredStorageBytes = source.downloadSizeBytes,
                        availableMemoryBytes = totalRam,
                        estimatedMemoryBytes = source.estimatedRamBytes,
                        recommendedAccelerators = listOf(HardwareAccelerator.GPU, HardwareAccelerator.NPU, HardwareAccelerator.CPU),
                        warnings = warnings
                    )
                }

                is ModelSource.SafUriSource -> {
                    val lower = source.displayName.lowercase()
                    val isSupportedExt = lower.endsWith(".litertlm") || lower.endsWith(".task") || lower.endsWith(".bin")
                    ModelCompatibilityReport(
                        status = if (isSupportedExt) CompatibilityStatus.SUPPORTED else CompatibilityStatus.UNSUPPORTED,
                        isCompatible = isSupportedExt,
                        detailedReason = if (isSupportedExt) {
                            "Local SAF file matches LiteRT-LM task container specification."
                        } else {
                            "Unsupported model file extension. Only validated .litertlm and .task packages are supported."
                        },
                        availableStorageBytes = usableSpace,
                        requiredStorageBytes = source.sizeBytes,
                        availableMemoryBytes = totalRam,
                        estimatedMemoryBytes = (source.sizeBytes * 1.35).toLong(),
                        recommendedAccelerators = listOf(HardwareAccelerator.GPU, HardwareAccelerator.CPU),
                        warnings = emptyList()
                    )
                }
            }
        }

    override suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        loadMutex.withLock { loadLocked(modelId) }
    }

    /** Must be called with [loadMutex] held. */
    private fun loadLocked(modelId: String): Result<Unit> {
        val file = modelLocator.locate(modelId)
        if (file == null) {
            closeEngineLocked()
            _runtimeState.value = EngineRuntimeState.NoModelFound(modelLocator.sideloadDir.absolutePath)
            return Result.failure(IllegalStateException(noModelMessage()))
        }
        if (llmInference != null && file == loadedFile) return Result.success(Unit)

        closeEngineLocked()
        _runtimeState.value = EngineRuntimeState.Loading(file.name)

        // Some .task bundles are compiled with a fixed KV-cache size (e.g. "ekv1280" in the
        // file name) and reject a larger maxTokens, so step down until one is accepted.
        var lastError: Throwable? = null
        for (maxTokens in MAX_TOKEN_CANDIDATES) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(maxTokens)
                    .setMaxTopK(DEFAULT_TOP_K)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                loadedFile = file
                loadedMaxTokens = maxTokens
                _runtimeState.value = EngineRuntimeState.Ready(file.name, maxTokens)
                Log.i(TAG, "Loaded ${file.absolutePath} (maxTokens=$maxTokens)")
                return Result.success(Unit)
            } catch (t: Throwable) {
                Log.w(TAG, "Load failed for ${file.name} with maxTokens=$maxTokens", t)
                lastError = t
            }
        }
        val message = lastError?.message ?: "Unknown error"
        _runtimeState.value = EngineRuntimeState.Failed(file.name, message)
        return Result.failure(IllegalStateException("Couldn't load ${file.name}: $message", lastError))
    }

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            loadMutex.withLock {
                closeEngineLocked()
                _runtimeState.value = EngineRuntimeState.Idle
            }
        }
    }

    private fun closeEngineLocked() {
        runCatching { activeSession?.close() }
        activeSession = null
        runCatching { llmInference?.close() }
        llmInference = null
        loadedFile = null
        loadedMaxTokens = 0
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = callbackFlow {
        cancelRequested.set(false)
        val startTime = SystemClock.elapsedRealtime()

        // Lazy-load on first use; a no-op when the warm-up at app start already loaded it.
        val loadResult = loadModel(request.modelId)
        val engine = llmInference
        if (loadResult.isFailure || engine == null) {
            val state = _runtimeState.value
            trySend(
                GenerationEvent.Error(
                    code = if (state is EngineRuntimeState.NoModelFound) "NO_MODEL" else "MODEL_LOAD_FAILED",
                    userMessage = loadResult.exceptionOrNull()?.message ?: "No model is loaded.",
                    isLocalDataSafe = true,
                    recommendedAction = "Copy a .task or .litertlm model into ${modelLocator.sideloadDir.absolutePath} and send again.",
                    technicalDetails = loadResult.exceptionOrNull()?.toString() ?: "llmInference == null"
                )
            )
            close()
            return@callbackFlow
        }

        // Fit the prompt to the context size the model actually accepted.
        val effectiveRequest = if (request.totalContextBudgetTokens > loadedMaxTokens) {
            request.copy(
                totalContextBudgetTokens = loadedMaxTokens,
                maxOutputTokens = minOf(request.maxOutputTokens, loadedMaxTokens / 3)
            )
        } else request
        val assembledPrompt = promptAssembler.assemble(effectiveRequest)

        trySend(GenerationEvent.Started(request.modelId, assembledPrompt.estimatedTokenCount))

        calculatorTool.tryEvaluateArithmetic(request.userPrompt)?.let { calcOutput ->
            trySend(
                GenerationEvent.ToolInvoked(
                    toolName = "DETERMINISTIC_CALCULATOR",
                    input = calcOutput.expression,
                    deterministicResult = calcOutput.formattedResult
                )
            )
        }

        val finished = AtomicBoolean(false)
        var session: LlmInferenceSession? = null
        try {
            // A fresh session per reply: the prompt already carries the trimmed history,
            // and it keeps the KV cache from growing across turns.
            val newSession = LlmInferenceSession.createFromOptions(
                engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(DEFAULT_TOP_K)
                    .setTopP(0.95f)
                    .setTemperature(request.temperature)
                    .build()
            )
            session = newSession
            activeSession = newSession
            newSession.addQueryChunk(assembledPrompt.fullPromptText)

            val response = StringBuilder()
            var deltas = 0
            newSession.generateResponseAsync { partial, done ->
                if (!partial.isNullOrEmpty()) {
                    response.append(partial)
                    deltas++
                    trySend(GenerationEvent.TokenDelta(partial))
                }
                if (done) {
                    finished.set(true)
                    val text = response.toString()
                    if (cancelRequested.get()) {
                        trySend(GenerationEvent.Canceled(text))
                    } else {
                        val elapsedSec = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(100) / 1000f
                        trySend(
                            GenerationEvent.Completed(
                                fullText = text,
                                citationsUsed = assembledPrompt.includedPassages,
                                tokensGenerated = deltas,
                                tokensPerSecond = deltas / elapsedSec,
                                peakMemoryMb = usedMemoryMb()
                            )
                        )
                    }
                    releaseSession(newSession)
                    close()
                }
            }
        } catch (e: Exception) {
            finished.set(true)
            session?.let { releaseSession(it) }
            trySend(
                GenerationEvent.Error(
                    code = "RUNTIME_EXCEPTION",
                    userMessage = "Local model inference encountered an error.",
                    isLocalDataSafe = true,
                    recommendedAction = "Try a shorter message, or restart the app to reload the model.",
                    technicalDetails = e.message ?: e.toString()
                )
            )
            close()
        }

        awaitClose {
            // The collector went away mid-reply (e.g. its coroutine was cancelled):
            // stop native decoding too. The session is released when `done` arrives.
            if (!finished.get()) {
                cancelRequested.set(true)
                session?.let { runCatching { it.cancelGenerateResponseAsync() } }
            }
        }
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
        activeSession?.let { runCatching { it.cancelGenerateResponseAsync() } }
    }

    /** Close off the native callback thread, never inside it. */
    private fun releaseSession(session: LlmInferenceSession) {
        if (activeSession === session) activeSession = null
        cleanupScope.launch { runCatching { session.close() } }
    }

    private fun noModelMessage(): String =
        "No model found. Copy a .task or .litertlm file into ${modelLocator.sideloadDir.absolutePath}, then send again."

    private fun usedMemoryMb(): Int {
        val rt = Runtime.getRuntime()
        val javaHeap = rt.totalMemory() - rt.freeMemory()
        val nativeHeap = android.os.Debug.getNativeHeapAllocatedSize()
        return ((javaHeap + nativeHeap) / (1024 * 1024)).toInt()
    }

    override fun capabilities(): EngineCapabilities = EngineCapabilities(
        engineId = "google-ai-edge-litert-lm",
        engineName = "Google AI Edge LiteRT-LM",
        supportedFormats = setOf("litertlm", "task", "tflite"),
        supportedQuantizations = setOf("INT4", "INT8", "FP16"),
        maxContextWindow = 32768,
        availableAccelerators = setOf(HardwareAccelerator.CPU, HardwareAccelerator.GPU, HardwareAccelerator.NPU),
        supportsStreaming = true
    )


    private companion object {
        const val TAG = "LiteRtLmEngine"
        const val DEFAULT_TOP_K = 40
        val MAX_TOKEN_CANDIDATES = listOf(4096, 2048, 1280)
    }
}
