package app.localmind.assistant.data.inference

import app.localmind.assistant.data.models.ModelFormat
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.domain.inference.EngineCapabilities
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.inference.GenerationEvent
import app.localmind.assistant.domain.inference.GenerationRequest
import app.localmind.assistant.domain.inference.HardwareAccelerator
import app.localmind.assistant.domain.inference.LocalInferenceEngine
import app.localmind.assistant.domain.inference.ModelCompatibilityReport
import app.localmind.assistant.domain.inference.ModelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's single inference engine. Loads whatever model is active in the [ModelLibrary]
 * and hands it to the runtime that understands its format:
 *   .gguf             -> llama.cpp
 *   .task / .litertlm -> MediaPipe (Google AI Edge)
 * Only one model is in memory at a time.
 */
@Singleton
class RoutingInferenceEngine @Inject constructor(
    private val mediaPipe: LiteRtLmInferenceEngine,
    private val llama: LlamaCppInferenceEngine,
    private val library: ModelLibrary,
    private val locator: ModelFileLocator,
    private val status: EngineStatus
) : LocalInferenceEngine {

    private val switchMutex = Mutex()

    override val runtimeState: StateFlow<EngineRuntimeState> get() = status.state

    override suspend fun inspectModel(source: ModelSource): ModelCompatibilityReport =
        mediaPipe.inspectModel(source)

    /**
     * [modelId] is a model file name when a persona has its own model; anything else (or a file
     * that's gone) falls back to the default model chosen on the Models screen.
     */
    override suspend fun loadModel(modelId: String): Result<Unit> {
        return switchMutex.withLock {
            val file = library.fileNamed(modelId) ?: library.activeFile()
            if (file == null) {
                if (mediaPipe.isLoaded) mediaPipe.unloadModel()
                if (llama.isLoaded) llama.unload()
                status.set(EngineRuntimeState.NoModelFound(locator.sideloadDir.absolutePath))
                Result.failure<Unit>(IllegalStateException("No model yet. Open the Models tab to import one."))
            } else when (ModelFormat.of(file)) {
                ModelFormat.GGUF -> {
                    if (mediaPipe.isLoaded) mediaPipe.unloadModel()
                    llama.loadFile(file)
                }
                ModelFormat.LITERT -> {
                    if (llama.isLoaded) llama.unload()
                    mediaPipe.loadFile(file)
                }
                null -> Result.failure<Unit>(IllegalStateException("Unsupported model file: ${file.name}"))
            }
        }
    }

    /**
     * Times a GGUF model for the benchmark (1.52). Waits for any reply to finish, and frees a
     * LiteRT model first, so only one model is ever in memory.
     */
    suspend fun benchmark(file: java.io.File, onGpu: Boolean, threads: Int, onStage: (String) -> Unit): app.localmind.assistant.domain.bench.BenchTiming =
        switchMutex.withLock {
            if (mediaPipe.isLoaded) mediaPipe.unloadModel()
            llama.benchmark(file, onGpu, threads, onStage)
        }

    fun stopBenchmark() = llama.stopBenchmark()

    override suspend fun unloadModel() {
        switchMutex.withLock {
            if (mediaPipe.isLoaded) mediaPipe.unloadModel()
            if (llama.isLoaded) llama.unload()
            status.set(EngineRuntimeState.Idle)
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val loaded = loadModel(request.modelId)
        if (loaded.isFailure) {
            emit(
                GenerationEvent.Error(
                    code = "NO_MODEL",
                    userMessage = loaded.exceptionOrNull()?.message ?: "No model is loaded.",
                    recommendedAction = "Open the Models tab and pick or import a model.",
                    technicalDetails = loaded.exceptionOrNull()?.toString() ?: "load failed"
                )
            )
            return@flow
        }
        emitAll(if (llama.isLoaded) llama.generate(request) else mediaPipe.generate(request))
    }

    override suspend fun cancelGeneration() {
        llama.cancel()
        mediaPipe.cancelGeneration()
    }

    override fun capabilities(): EngineCapabilities = EngineCapabilities(
        engineId = "slothllama-router",
        engineName = "llama.cpp + Google AI Edge",
        supportedFormats = setOf("gguf", "litertlm", "task"),
        supportedQuantizations = setOf("Q2_K", "Q3_K", "Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0", "F16", "INT4", "INT8"),
        maxContextWindow = 32768,
        availableAccelerators = setOf(HardwareAccelerator.CPU, HardwareAccelerator.GPU),
        supportsStreaming = true
    )
}
