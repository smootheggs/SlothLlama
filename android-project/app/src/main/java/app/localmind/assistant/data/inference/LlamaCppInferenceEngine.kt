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
    private val status: EngineStatus
) {
    /** Every native call happens on this one thread. */
    private val nativeThread = Dispatchers.IO.limitedParallelism(1)

    @Volatile private var loadedFile: File? = null
    @Volatile private var loadedContext: Int = 0
    /** (role, content) pairs the native chat currently holds, or null if unknown. */
    private var nativeChat: List<Pair<String, String>>? = null

    @Volatile private var cancelRequested = false

    val isLoaded: Boolean get() = loadedFile != null

    suspend fun loadFile(file: File): Result<Unit> = withContext(nativeThread) {
        if (loadedFile == file) return@withContext Result.success(Unit)
        unloadOnThread()
        status.set(EngineRuntimeState.Loading(file.name))
        runCatching {
            LlamaNative.ensureInitialized(context.applicationInfo.nativeLibraryDir)
            check(LlamaNative.load(file.absolutePath) == 0) {
                "llama.cpp couldn't open ${file.name} — the architecture may be unsupported or the file damaged."
            }
            if (LlamaNative.prepare(CONTEXT_TOKENS) != 0) {
                LlamaNative.unload()
                error("Not enough memory to set up ${file.name}. Try a smaller model or quantization (e.g. Q4_K_M).")
            }
            loadedFile = file
            loadedContext = LlamaNative.contextSize()
            nativeChat = null
            status.set(EngineRuntimeState.Ready(file.name, loadedContext))
            Log.i(TAG, "Loaded ${file.name}, context=$loadedContext\n${LlamaNative.systemInfo()}")
            Unit
        }.onFailure { e ->
            loadedFile = null
            status.set(EngineRuntimeState.Failed(file.name, e.message ?: e.toString()))
            Log.e(TAG, "Load failed for ${file.absolutePath}", e)
        }
    }

    suspend fun unload() = withContext(nativeThread) { unloadOnThread() }

    private fun unloadOnThread() {
        if (loadedFile != null) {
            runCatching { LlamaNative.unload() }
        }
        loadedFile = null
        loadedContext = 0
        nativeChat = null
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

        // Budget against the real context; keep room for the reply.
        val effective = request.copy(
            totalContextBudgetTokens = loadedContext,
            maxOutputTokens = minOf(request.maxOutputTokens, loadedContext / 3)
        )
        val plan = promptAssembler.planChat(effective)
        val desiredChat = buildList {
            add("system" to plan.systemText)
            plan.history.forEach { add(it.role.lowercase() to it.content) }
        }
        emit(GenerationEvent.Started(request.modelId, plan.estimatedTokenCount))

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

        val filter = ReplyFilter()
        var pieces = 0
        try {
            while (!cancelRequested && !filter.stopped) {
                val piece = LlamaNative.generateNextToken() ?: break
                if (piece.isEmpty()) continue
                pieces++
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
        if (LlamaNative.lastStopWasEog()) {
            nativeChat = desiredChat + listOf("user" to plan.userPrompt, "assistant" to visibleReply)
        }
        val elapsedSec = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(100) / 1000f
        emit(
            GenerationEvent.Completed(
                fullText = visibleReply,
                citationsUsed = plan.includedPassages,
                tokensGenerated = pieces,
                tokensPerSecond = pieces / elapsedSec,
                peakMemoryMb = usedMemoryMb()
            )
        )
    }.flowOn(nativeThread)

    private fun usedMemoryMb(): Int =
        (android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()

    private companion object {
        const val TAG = "LlamaCppEngine"
        /** Tokens of context. 4096 keeps a 7B Q4 model comfortably inside a 12 GB tablet. */
        const val CONTEXT_TOKENS = 4096
        const val TOP_P = 0.95f
        const val TOP_K = 40
    }
}
