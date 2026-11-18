package app.localmind.assistant.data.inference

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.inference.GenerationEvent
import app.localmind.assistant.domain.inference.GenerationRequest
import app.localmind.llama.LlamaNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import app.localmind.assistant.data.models.ModelLibrary
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs GGUF models with llama.cpp — the format almost every open model on Hugging Face ships in.
 *
 * The native side keeps one chat in memory. We mirror what it holds; when the next request
 * continues that same chat, only the new user turn is processed (fast). When it doesn't
 * (another conversation, an edit, a regenerate, a persona change), the conversation is
 * replayed once with [LlamaNative.loadConversation].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LlamaCppInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val promptAssembler: SecurePromptAssembler,
    private val status: EngineStatus,
    private val gpu: GpuSettings
) {
    /** Every native call happens on this one thread. */
    private val nativeThread = Dispatchers.IO.limitedParallelism(1)

    @Volatile private var loadedFile: File? = null
    @Volatile private var loadedContext: Int = 0
    /** Whether the loaded model was asked to use the GPU (so a change of setting reloads it). */
    @Volatile private var loadedWantsGpu: Boolean = false
    /** The first reply after a GPU load is still part of the crash-guarded attempt. */
    private var gpuAttemptOpen = false
    /** (role, content) pairs the native chat currently holds, or null if unknown. */
    private var nativeChat: List<Pair<String, String>>? = null

    @Volatile private var cancelRequested = false

    val isLoaded: Boolean get() = loadedFile != null

    /** Starts the native side if needed and reports which GPU (if any) llama.cpp can use. */
    suspend fun probeGpu(): String = withContext(nativeThread) {
        initNative()
        gpu.gpuName.value.orEmpty()
    }

    private fun initNative() {
        LlamaNative.ensureInitialized(context.applicationInfo.nativeLibraryDir, gpu.kernelCacheDir)
        if (gpu.gpuName.value == null) {
            val name = runCatching { LlamaNative.gpuName() }.getOrDefault("")
            gpu.reportGpu(name)
            Log.i(TAG, if (name.isEmpty()) "No GPU backend; models run on the CPU" else "GPU available: $name")
        }
    }

    suspend fun loadFile(file: File): Result<Unit> = withContext(nativeThread) {
        runCatching { initNative() }
        val wantGpu = gpu.shouldUseGpu(file.name)
        if (loadedFile == file && loadedWantsGpu == wantGpu) return@withContext Result.success(Unit)
        unloadOnThread()
        runCatching {
            ModelLibrary.ggufProblem(file)?.let { error("${file.name} can't be loaded: $it.") }
            initNative()
            val onGpu = if (wantGpu) loadOnGpu(file) else false
            if (!onGpu) loadOnCpu(file)
            loadedFile = file
            loadedWantsGpu = wantGpu
            loadedContext = LlamaNative.contextSize()
            nativeChat = null
            val gpuName = if (onGpu) gpu.gpuName.value?.let(::shortGpuName) else null
            status.set(
                EngineRuntimeState.Ready(file.name, loadedContext, canThink = LlamaNative.supportsThinking(), gpu = gpuName)
            )
            Log.i(TAG, "Loaded ${file.name} on ${gpuName ?: "CPU"}, context=$loadedContext\n${LlamaNative.systemInfo()}")
            Unit
        }.onFailure { e ->
            loadedFile = null
            gpu.endAttempt()
            gpuAttemptOpen = false
            status.set(EngineRuntimeState.Failed(file.name, e.message ?: e.toString()))
            Log.e(TAG, "Load failed for ${file.absolutePath}", e)
        }
    }

    /**
     * Tries the whole model on the GPU. Returns false (with nothing loaded) when the GPU can't hold
     * it, so the caller falls back to the CPU. A hard GPU crash is caught by [GpuSettings] instead.
     */
    private fun loadOnGpu(file: File): Boolean {
        val note = if (gpu.firstGpuSetup()) "Setting up the GPU (first time, about a minute)" else "Loading on the GPU"
        status.set(EngineRuntimeState.Loading(file.name, note))
        gpu.beginAttempt(file.name)
        if (LlamaNative.load(file.absolutePath, ALL_LAYERS) != 0) {
            Log.w(TAG, "GPU load failed for ${file.name}; using the CPU")
            gpu.endAttempt()
            return false
        }
        val rc = LlamaNative.prepare(CONTEXT_TOKENS, /* warmup */ true)
        if (rc != 0) {
            Log.w(TAG, "GPU context failed for ${file.name} (rc=$rc); using the CPU")
            LlamaNative.unload()
            gpu.endAttempt()
            return false
        }
        // Loaded and warmed up. The first reply is guarded again in generate(), because reading a
        // real prompt uses GPU kernels the warm-up didn't.
        gpu.endAttempt()
        gpuAttemptOpen = true
        return true
    }

    private fun loadOnCpu(file: File) {
        status.set(EngineRuntimeState.Loading(file.name))
        check(LlamaNative.load(file.absolutePath, 0) == 0) {
            "llama.cpp couldn't open ${file.name} — the architecture may be unsupported or the file damaged."
        }
        if (LlamaNative.prepare(CONTEXT_TOKENS, /* warmup */ false) != 0) {
            LlamaNative.unload()
            error("Not enough memory to set up ${file.name}. Try a smaller model or quantization (e.g. Q4_K_M).")
        }
    }

    suspend fun unload() = withContext(nativeThread) { unloadOnThread() }

    private fun unloadOnThread() {
        if (loadedFile != null) {
            runCatching { LlamaNative.unload() }
        }
        loadedFile = null
        loadedContext = 0
        loadedWantsGpu = false
        nativeChat = null
        gpuAttemptOpen = false
    }

    fun cancel() {
        cancelRequested = true
    }

    fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        cancelRequested = false
        val startTime = SystemClock.elapsedRealtime()
        val file = loadedFile
        if (file == null) {
            emit(
                GenerationEvent.Error(
                    code = "NO_MODEL",
                    userMessage = "No model is loaded.",
                    recommendedAction = "Open the Models tab and pick a model.",
                    technicalDetails = "llama.cpp engine has no model"
                )
            )
            return@flow
        }

        // Reasoning models think only when asked; thinking needs extra room for the hidden reasoning.
        val thinks = request.allowThinking && LlamaNative.supportsThinking()
        LlamaNative.setThinking(thinks)

        // Budget against the real context; keep room for the reply.
        val effective = request.copy(
            totalContextBudgetTokens = loadedContext,
            maxOutputTokens = if (thinks) minOf(request.maxOutputTokens + THINKING_TOKENS, loadedContext / 2)
            else minOf(request.maxOutputTokens, loadedContext / 3)
        )
        val plan = promptAssembler.planChat(effective)
        val desiredChat = buildList {
            add("system" to plan.systemText)
            plan.history.forEach { add(it.role.lowercase() to it.content) }
        }
        emit(GenerationEvent.Started(request.modelId, plan.estimatedTokenCount))

        // First reply after a GPU load: guarded, so a driver crash puts this model on the CPU next time.
        val guarded = gpuAttemptOpen
        if (guarded) gpu.beginAttempt(file.name)
        try {
            if (nativeChat != desiredChat) {
                val rc = LlamaNative.loadConversation(
                    desiredChat.map { it.first }.toTypedArray(),
                    desiredChat.map { it.second }.toTypedArray()
                )
                if (rc != 0) {
                    nativeChat = null
                    emit(
                        GenerationEvent.Error(
                            code = "CONTEXT_PREFILL_FAILED",
                            userMessage = if (rc == 1) "This conversation is too long for the model's memory." else "The model failed to read the conversation.",
                            recommendedAction = "Start a new chat, or use a model with a longer context.",
                            technicalDetails = "loadConversation returned $rc"
                        )
                    )
                    return@flow
                }
                Log.i(TAG, "Replayed ${desiredChat.size} messages")
            }
            // From here until a clean end-of-turn, the native chat no longer matches our mirror.
            nativeChat = null

            LlamaNative.setSampling(request.temperature, TOP_P, TOP_K)
            if (LlamaNative.processUserPrompt(plan.userPrompt, effective.maxOutputTokens) != 0) {
                emit(
                    GenerationEvent.Error(
                        code = "PROMPT_FAILED",
                        userMessage = "The model couldn't process that message.",
                        recommendedAction = "Try again, or start a new chat.",
                        technicalDetails = "processUserPrompt failed"
                    )
                )
                return@flow
            }

            // When thinking, the prompt already opened "<think>", so the reply starts inside it.
            val filter = ReplyFilter(startsThinking = thinks)
            var pieces = 0
            var firstTokenAt = 0L
            try {
                while (!cancelRequested && !filter.stopped) {
                    val piece = LlamaNative.generateNextToken() ?: break
                    if (piece.isEmpty()) continue
                    pieces++
                    if (pieces == 1) {
                        firstTokenAt = SystemClock.elapsedRealtime()
                        // The GPU has now read a real prompt and written a token: it works with this model.
                        gpuAttemptOpen = false
                    }
                    val visible = filter.push(piece)
                    if (visible.isNotEmpty()) emit(GenerationEvent.TokenDelta(visible))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(
                    GenerationEvent.Error(
                        code = "RUNTIME_EXCEPTION",
                        userMessage = "The model stopped with an error.",
                        recommendedAction = "Try again; if it repeats, reload the model from the Models tab.",
                        technicalDetails = e.message ?: e.toString()
                    )
                )
                return@flow
            }

            val (tail, visibleReply) = filter.finish()
            if (tail.isNotEmpty()) emit(GenerationEvent.TokenDelta(tail))

            if (cancelRequested) {
                emit(GenerationEvent.Canceled(visibleReply))
                return@flow
            }

            // Clean end-of-turn: the native chat now equals history + this exchange.
            val endedCleanly = LlamaNative.lastStopWasEog()
            if (endedCleanly) {
                nativeChat = desiredChat + listOf("user" to plan.userPrompt, "assistant" to visibleReply)
            }
            // Generation speed only (from the first token), not the wait while the prompt is read.
            val decodeStart = if (firstTokenAt > 0) firstTokenAt else startTime
            val elapsedSec = (SystemClock.elapsedRealtime() - decodeStart).coerceAtLeast(100) / 1000f
            emit(
                GenerationEvent.Completed(
                    fullText = visibleReply,
                    citationsUsed = plan.includedPassages,
                    tokensGenerated = pieces,
                    tokensPerSecond = if (pieces > 1) (pieces - 1) / elapsedSec else 0f,
                    peakMemoryMb = usedMemoryMb(),
                    contextUsed = LlamaNative.usedTokens(),
                    contextSize = loadedContext,
                    stoppedAtLimit = !endedCleanly && !filter.stopped && pieces > 0,
                    historyTrimmed = plan.history.size < effective.recentMessages.count {
                        it.role.equals("USER", ignoreCase = true) || it.role.equals("ASSISTANT", ignoreCase = true)
                    }
                )
            )
        } finally {
            // Any ending we get to see (done, error, cancel) wasn't a crash.
            if (guarded) gpu.endAttempt()
        }
    }.flowOn(nativeThread)

    private fun usedMemoryMb(): Int =
        (android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()

    /** "QUALCOMM Adreno(TM) 750" → "Adreno 750". */
    private fun shortGpuName(raw: String): String =
        raw.replace("(TM)", "").replace("QUALCOMM", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ").trim().ifEmpty { "GPU" }

    private companion object {
        const val TAG = "LlamaCppEngine"
        /** n_gpu_layers = -1: every layer on the GPU. */
        const val ALL_LAYERS = -1
        /**
         * Tokens of context. 6144 leaves room for longer tutoring sessions and for thinking, while a
         * 7B Q4 model plus its cache still fits comfortably in a 12 GB tablet.
         */
        const val CONTEXT_TOKENS = 6144
        /** Extra reply budget for hidden reasoning when thinking is on. */
        const val THINKING_TOKENS = 1536
        const val TOP_P = 0.95f
        const val TOP_K = 40
    }
}
