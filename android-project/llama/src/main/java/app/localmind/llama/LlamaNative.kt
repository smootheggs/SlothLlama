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

    /** Loads the native libraries and CPU backends once. [nativeLibDir] = applicationInfo.nativeLibraryDir */
    @Synchronized
    fun ensureInitialized(nativeLibDir: String) {
        if (initialized) return
        System.loadLibrary("llama-jni")
        init(nativeLibDir)
        initialized = true
    }

    private external fun init(nativeLibDir: String)

    /** Returns 0 on success, non-zero if the file can't be loaded (unsupported architecture or damaged). */
    external fun load(modelPath: String): Int

    /** Creates the context ([nCtx] tokens, capped at the model's trained size). 0 on success. */
    external fun prepare(nCtx: Int): Int

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

    /** Next piece of text; "" while a multi-byte character is incomplete; null when generation ends. */
    external fun generateNextToken(): String?

    /** True if the last generation ended on the model's own end-of-turn token (chat history is in sync). */
    external fun lastStopWasEog(): Boolean

    external fun unload()

    external fun shutdown()
}
