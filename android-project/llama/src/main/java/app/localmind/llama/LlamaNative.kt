package app.localmind.llama

/**
 * Thin JNI binding to llama.cpp (see src/main/cpp/llama_jni.cpp).
 *
 * The native side holds ONE model, context and chat at a time and is not thread-safe:
 * every call must come from the same single thread (the app uses a single-threaded dispatcher).
 */
object LlamaNative {

    @Volatile
    private var initialized = false

    /**
     * Loads the native libraries and backends (CPU, and the GPU one when the tablet has a usable
     * OpenCL driver) once. [nativeLibDir] = applicationInfo.nativeLibraryDir; compiled GPU kernels
     * are cached in [kernelCacheDir].
     */
    @Synchronized
    fun ensureInitialized(nativeLibDir: String, kernelCacheDir: String) {
        if (initialized) return
        System.loadLibrary("llama-jni")
        init(nativeLibDir, kernelCacheDir)
        initialized = true
    }

    private external fun init(nativeLibDir: String, kernelCacheDir: String)

    /** The GPU llama.cpp can use (e.g. "QUALCOMM Adreno(TM) 750"), or "" when there is none. */
    external fun gpuName(): String

    /**
     * Returns 0 on success, non-zero if the file can't be loaded (unsupported architecture, damaged,
     * or not enough memory). [gpuLayers]: -1 = every layer on the GPU, 0 = CPU only.
     */
    external fun load(modelPath: String, gpuLayers: Int): Int

    /**
     * Creates the context ([nCtx] tokens, capped at the model's trained size). With [warmup], runs
     * one tiny decode so a GPU sets itself up now rather than during the first reply.
     * 0 on success, 1 = no memory for the context, 2 = the warm-up failed.
     */
    external fun prepare(nCtx: Int, warmup: Boolean): Int

    /** The context size actually in use after [prepare]. */
    external fun contextSize(): Int

    external fun systemInfo(): String

    external fun setSampling(temperature: Float, topP: Float, topK: Int)

    /**
     * Resets the chat and prefills [roles]/[contents] ("system" | "user" | "assistant") in one pass.
     * 0 = ok, 1 = too long for the context, 2 = decode failed.
     */
    external fun loadConversation(roles: Array<String>, contents: Array<String>): Int

    /** Adds the user turn and prepares generation of up to [predictLength] tokens. 0 on success. */
    external fun processUserPrompt(userPrompt: String, predictLength: Int): Int

    /**
     * Loads the vision projector (mmproj) for the loaded model so it can see photos.
     * 0 = ready, 1 = couldn't load it (wrong file or made for another model), 2 = not a vision projector.
     */
    external fun loadProjector(path: String, useGpu: Boolean): Int

    /** True once [loadProjector] has succeeded for the current model. */
    external fun hasProjector(): Boolean

    /**
     * [processUserPrompt] with one photo before the text. [rgb] = [width] x [height] pixels,
     * 3 bytes each. 0 = ok, 1 = too long for the context, 2 = decode failed, 3 = no projector,
     * 4 = the photo couldn't be prepared.
     */
    external fun processUserPromptWithImage(userPrompt: String, rgb: ByteArray, width: Int, height: Int, predictLength: Int): Int

    /** Next piece of text; "" while a multi-byte character is incomplete; null when generation ends. */
    external fun generateNextToken(): String?

    /** Tokens currently held in the model's context (prompt + history + reply so far). */
    external fun usedTokens(): Int

    /** True if the loaded model is a reasoning model (its chat template uses <think>). */
    external fun supportsThinking(): Boolean

    /**
     * For reasoning models: let the next reply think first ("<think>" is pre-filled, so the reply
     * starts inside the thinking block), or skip thinking with an empty think block.
     */
    external fun setThinking(enabled: Boolean)

    /** True if the last generation ended on the model's own end-of-turn token (chat history is in sync). */
    external fun lastStopWasEog(): Boolean

    external fun unload()

    // --- Search by meaning: a separate embedding model, always on the CPU ---------------------
    // Its calls are locked on the native side, so they may run on another thread than the chat.

    /** 0 = ready, 1 = couldn't load the file, 2 = not an embedding model, 3 = no memory for it. */
    external fun embedLoad(path: String): Int

    /** Length of the vectors [embed] returns (0 when no embedding model is loaded). */
    external fun embedDimensions(): Int

    /** [text]'s vector, unit length, or null when no embedding model is loaded or it failed. */
    external fun embed(text: String): FloatArray?

    external fun embedUnload()

    external fun shutdown()
}
