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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.dictionary.ReplyPolisher
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.domain.bench.Bench
import app.localmind.assistant.domain.bench.BenchTiming
import app.localmind.assistant.domain.attachments.Attachments
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
    private val gpu: GpuSettings,
    private val library: ModelLibrary,
    private val attachments: AttachmentStore,
    private val polisher: ReplyPolisher
) {
    /** Every native call happens on this one thread. */
    private val nativeThread = Dispatchers.IO.limitedParallelism(1)

    @Volatile private var loadedFile: File? = null
    @Volatile private var loadedContext: Int = 0
    /** Whether the loaded model was asked to use the GPU (so a change of setting reloads it). */
    @Volatile private var loadedWantsGpu: Boolean = false
    /** The loaded model really is on the GPU (it may have fallen back to the CPU). */
    @Volatile private var loadedOnGpu: Boolean = false
    /** The first reply after a GPU load is still part of the crash-guarded attempt. */
    private var gpuAttemptOpen = false
    /** (role, content) pairs the native chat currently holds, or null if unknown. */
    private var nativeChat: List<Pair<String, String>>? = null

    @Volatile private var cancelRequested = false
    /** A reply is being written: its tokens come between suspensions, so nothing may unload it. */
    @Volatile private var replying = false

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
            loadedOnGpu = onGpu
            loadedContext = LlamaNative.contextSize()
            nativeChat = null
            val gpuName = if (onGpu) gpu.gpuName.value?.let(::shortGpuName) else null
            status.set(
                EngineRuntimeState.Ready(
                    file.name, loadedContext, canThink = LlamaNative.supportsThinking(), gpu = gpuName,
                    canSee = library.projectorFor(file) != null
                )
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

    /**
     * Times [file] for the benchmark (1.52): it's loaded on the GPU when [onGpu] and the GPU can
     * take it (else on the CPU), then reads [Bench.PROMPT] and writes [Bench.WRITE_TOKENS] tokens,
     * [Bench.RUNS] times, with [threads] CPU threads. Runs on the native thread, so never alongside
     * a reply. The model stays loaded afterwards, with the usual threads, and the chat is replayed
     * on its next reply. [stopBenchmark] ends it early.
     */
    suspend fun benchmark(file: File, onGpu: Boolean, threads: Int, onStage: (String) -> Unit): BenchTiming = withContext(nativeThread) {
        // A reply in progress suspends between tokens; unloading its model then would break it.
        check(!replying) { "A reply is still being written. Try again when it's finished." }
        cancelRequested = false
        initNative()
        unloadOnThread()
        try {
            ModelLibrary.ggufProblem(file)?.let { error("${file.name} can't be loaded: $it.") }
            onStage("Loading ${file.nameWithoutExtension}")
            val loadStart = SystemClock.elapsedRealtime()
            // The same rules as a chat: no GPU, or a model that took the app down on it, means the CPU.
            val gpuOk = onGpu && !gpu.gpuName.value.isNullOrEmpty() && file.name !in gpu.cpuOnlyModels.value && loadOnGpu(file)
            if (!gpuOk) loadOnCpu(file)
            val loadMillis = SystemClock.elapsedRealtime() - loadStart
            loadedFile = file
            loadedWantsGpu = onGpu
            loadedOnGpu = gpuOk
            loadedContext = LlamaNative.contextSize()
            nativeChat = null
            status.set(
                EngineRuntimeState.Ready(
                    file.name, loadedContext, canThink = LlamaNative.supportsThinking(),
                    gpu = if (gpuOk) gpu.gpuName.value?.let(::shortGpuName) else null,
                    canSee = library.projectorFor(file) != null
                )
            )
            val used = LlamaNative.setThreads(threads)
            LlamaNative.setThinking(false)
            val reads = ArrayList<Double>()
            val writes = ArrayList<Double>()
            var promptTokens = 0
            for (run in 1..Bench.RUNS) {
                if (cancelRequested) error("Stopped.")
                onStage("Run $run of ${Bench.RUNS}")
                check(LlamaNative.loadConversation(arrayOf("system"), arrayOf(Bench.SYSTEM)) == 0) { "The model couldn't start the test." }
                LlamaNative.setSampling(BENCH_TEMPERATURE, TOP_P, TOP_K)
                val before = LlamaNative.usedTokens()
                // The first reading on the GPU is guarded like a chat's first reply.
                if (gpuAttemptOpen) gpu.beginAttempt(file.name)
                val t0 = SystemClock.elapsedRealtime()
                check(LlamaNative.processUserPrompt(Bench.PROMPT, Bench.WRITE_TOKENS) == 0) { "The model couldn't read the test prompt." }
                val t1 = SystemClock.elapsedRealtime()
                promptTokens = LlamaNative.usedTokens() - before
                var written = 0
                while (written < Bench.WRITE_TOKENS && !cancelRequested) {
                    // An empty piece is still a token (half of a character), so it counts.
                    LlamaNative.generateNextToken() ?: break
                    written++
                }
                val t2 = SystemClock.elapsedRealtime()
                if (gpuAttemptOpen) {
                    gpu.endAttempt()
                    gpuAttemptOpen = false
                }
                if (cancelRequested) error("Stopped.")
                reads += Bench.tps(promptTokens, t1 - t0)
                writes += Bench.tps(written, t2 - t1)
            }
            val memoryMb = (android.os.Debug.getPss() / 1024).toInt()
            BenchTiming(gpuOk, used, promptTokens, reads, writes, loadMillis, memoryMb)
        } catch (e: Throwable) {
            gpu.endAttempt()
            gpuAttemptOpen = false
            unloadOnThread()
            status.set(EngineRuntimeState.Idle)
            throw e
        } finally {
            // Replies always use the usual threads, and the chat is replayed before the next one.
            if (loadedFile != null) runCatching { LlamaNative.setThreads(0) }
            nativeChat = null
            cancelRequested = false
        }
    }

    fun stopBenchmark() {
        cancelRequested = true
    }

    suspend fun unload() = withContext(nativeThread) { unloadOnThread() }

    private fun unloadOnThread() {
        if (loadedFile != null) {
            runCatching { LlamaNative.unload() }
        }
        loadedFile = null
        loadedContext = 0
        loadedWantsGpu = false
        loadedOnGpu = false
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

        // A photo in this message (only the first counts) takes several hundred positions.
        val photo = Attachments.photosIn(request.userPrompt).firstOrNull()

        // Budget against the real context; keep room for the reply (and the photo).
        val effective = request.copy(
            totalContextBudgetTokens = loadedContext - if (photo != null) PHOTO_TOKENS else 0,
            maxOutputTokens = if (thinks) minOf(request.maxOutputTokens + THINKING_TOKENS, loadedContext / 2)
            else minOf(request.maxOutputTokens, loadedContext / 3)
        )
        val plan = promptAssembler.planChat(effective)
        // Earlier photos are only mentioned (the model saw them when they were sent and, while
        // the chat stays loaded, still remembers them); documents are marked as reference data.
        val desiredChat = buildList {
            add("system" to plan.systemText)
            plan.history.forEach { add(it.role.lowercase() to forModel(it.content, earlier = true)) }
        }
        val userText = forModel(plan.userPrompt, earlier = false)
        emit(GenerationEvent.Started(request.modelId, plan.estimatedTokenCount))

        val projector = if (photo != null) library.projectorFor(file) else null
        if (photo != null && projector == null) {
            emit(
                GenerationEvent.Error(
                    code = "NO_VISION",
                    userMessage = "${file.nameWithoutExtension} can't see photos yet.",
                    recommendedAction = "Models → this model → Photos: add its vision file (mmproj) from the same Hugging Face page.",
                    technicalDetails = "no mmproj for ${file.name}"
                )
            )
            return@flow
        }
        val image = photo?.let { attachments.rgbFor(it) }
        if (photo != null && image == null) {
            emit(
                GenerationEvent.Error(
                    code = "PHOTO_MISSING",
                    userMessage = "That photo isn't on the tablet any more.",
                    recommendedAction = "Attach it again.",
                    technicalDetails = "missing $photo"
                )
            )
            return@flow
        }

        // First reply after a GPU load, or the first photo on the GPU: guarded, so a driver crash
        // puts that model (or its photos) on the CPU next time.
        val visionKey = file.name + GpuSettings.VISION_SUFFIX
        val visionOnGpu = photo != null && loadedOnGpu && gpu.shouldUseGpu(visionKey)
        val guardKey = when {
            visionOnGpu && !LlamaNative.hasProjector() -> visionKey
            gpuAttemptOpen -> file.name
            else -> null
        }
        if (guardKey != null) gpu.beginAttempt(guardKey)
        val guarded = guardKey != null
        try {
            if (projector != null && !LlamaNative.hasProjector()) {
                status.set(EngineRuntimeState.Loading(file.name, "Getting ready to see photos"))
                val rc = LlamaNative.loadProjector(projector.absolutePath, visionOnGpu)
                status.set(
                    EngineRuntimeState.Ready(
                        file.name, loadedContext, canThink = LlamaNative.supportsThinking(),
                        gpu = if (loadedOnGpu) gpu.gpuName.value?.let(::shortGpuName) else null, canSee = rc == 0
                    )
                )
                if (rc != 0) {
                    emit(
                        GenerationEvent.Error(
                            code = "VISION_LOAD_FAILED",
                            userMessage = if (rc == 2) "The vision file added to this model isn't a vision projector."
                            else "The vision file couldn't be loaded with this model; it may be for a different model.",
                            recommendedAction = "Models → this model → Photos: remove it and add the mmproj from the model's own Hugging Face page.",
                            technicalDetails = "loadProjector returned $rc"
                        )
                    )
                    return@flow
                }
            }
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
            val promptRc = if (image != null) {
                LlamaNative.processUserPromptWithImage(
                    userText.ifBlank { "Describe this photo." }, image.rgb, image.width, image.height, effective.maxOutputTokens
                )
            } else {
                LlamaNative.processUserPrompt(userText, effective.maxOutputTokens)
            }
            if (promptRc != 0) {
                emit(
                    GenerationEvent.Error(
                        code = "PROMPT_FAILED",
                        userMessage = when {
                            image != null && promptRc == 1 -> "The photo doesn't fit in the model's memory with this chat."
                            image != null && promptRc == 4 -> "The model couldn't read that photo."
                            else -> "The model couldn't process that message."
                        },
                        recommendedAction = if (image != null && promptRc == 1) "Start a new chat and attach the photo there."
                        else "Try again, or start a new chat.",
                        technicalDetails = "processUserPrompt failed ($promptRc)"
                    )
                )
                return@flow
            }

            // When thinking, the prompt already opened "<think>", so the reply starts inside it.
            val filter = ReplyFilter(startsThinking = thinks)
            var pieces = 0
            var firstTokenAt = 0L
            // Small models sometimes get stuck repeating a line; stop as soon as that happens.
            val shown = StringBuilder()
            var looped = false
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
                    if (visible.isNotEmpty()) {
                        emit(GenerationEvent.TokenDelta(visible))
                        shown.append(visible)
                        if (RepeatGuard.period(shown) > 0) {
                            looped = true
                            Log.w(TAG, "Reply started repeating itself; stopped")
                            break
                        }
                    }
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

            val (tail, rawReply) = filter.finish()
            if (tail.isNotEmpty()) emit(GenerationEvent.TokenDelta(tail))
            // Keep one copy of a repeated block, then check the readings against the dictionary.
            val unrepeated = if (looped) RepeatGuard.trim(rawReply) else rawReply
            val visibleReply = if (cancelRequested) unrepeated else polisher.polish(unrepeated, request.speechLanguage)

            if (cancelRequested) {
                emit(GenerationEvent.Canceled(visibleReply))
                return@flow
            }

            // Clean end-of-turn: the native chat now equals history + this exchange.
            val endedCleanly = LlamaNative.lastStopWasEog()
            if (endedCleanly) {
                // Recorded as the next turn's history will describe it, so no replay is needed
                // (and the model keeps the photo it actually saw).
                nativeChat = desiredChat + listOf("user" to forModel(plan.userPrompt, earlier = true), "assistant" to visibleReply)
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
                    stoppedAtLimit = !endedCleanly && !filter.stopped && !looped && pieces > 0,
                    historyTrimmed = plan.history.size < effective.recentMessages.count {
                        it.role.equals("USER", ignoreCase = true) || it.role.equals("ASSISTANT", ignoreCase = true)
                    }
                )
            )
        } finally {
            // Any ending we get to see (done, error, cancel) wasn't a crash.
            if (guarded) gpu.endAttempt()
        }
    }
        // While a reply is being written the benchmark won't start (see [benchmark]).
        .onStart { replying = true }
        .onCompletion { replying = false }
        .flowOn(nativeThread)

    private fun usedMemoryMb(): Int =
        (android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()

    /**
     * Message text as the model reads it (see [Attachments.forModel]). The message being sent
     * has its photo as pixels and its library passages in full; [earlier] ones only mention them.
     */
    private fun forModel(content: String, earlier: Boolean): String =
        Attachments.forModel(content, if (earlier) EARLIER_PHOTO else "", earlier) { text ->
            promptAssembler.sanitizeUntrustedPassage(text) {}
        }

    /** "QUALCOMM Adreno(TM) 750" → "Adreno 750". */
    private fun shortGpuName(raw: String): String =
        raw.replace("(TM)", "").replace("QUALCOMM", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ").trim().ifEmpty { "GPU" }

    private companion object {
        const val TAG = "LlamaCppEngine"
        /** n_gpu_layers = -1: every layer on the GPU. */
        const val ALL_LAYERS = -1
        /** Room kept for a photo (a ~900 px photo is a few hundred tokens for Qwen-VL style models). */
        const val PHOTO_TOKENS = 900
        const val EARLIER_PHOTO = "[The learner shared a photo here.]"
        /**
         * Tokens of context. 6144 leaves room for longer tutoring sessions and for thinking, while a
         * 7B Q4 model plus its cache still fits comfortably in a 12 GB tablet.
         */
        const val CONTEXT_TOKENS = 6144
        /** Extra reply budget for hidden reasoning when thinking is on. */
        const val THINKING_TOKENS = 1536
        const val TOP_P = 0.95f
        const val TOP_K = 40
        /** Sampling for the benchmark: an ordinary temperature, so it writes like a real reply. */
        const val BENCH_TEMPERATURE = 0.7f
    }
}
