package app.localmind.assistant.data.inference

import app.localmind.assistant.domain.inference.PromptTurn

/**
 * Native chat formats for known model families. Small models answer far better when
 * the prompt uses the exact turn markers they were trained on.
 *
 * When a template is known, the engine renders the full multi-turn prompt itself and
 * turns off MediaPipe's own prompt wrapping, so nothing is wrapped twice.
 */
enum class ChatTemplate(val stopMarkers: List<String>) {

    /** Qwen 2.5 / Qwen 3, SmolLM and other ChatML models. */
    CHATML(listOf("<|im_end|>", "<|im_start|>", "<|endoftext|>")) {
        override fun render(system: String, history: List<PromptTurn>, userPrompt: String) = buildString {
            append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            for (turn in history) {
                append("<|im_start|>").append(if (turn.isUser()) "user" else "assistant").append('\n')
                append(turn.content).append("<|im_end|>\n")
            }
            append("<|im_start|>user\n").append(userPrompt).append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    },

    /** Gemma has no system role, so the system text goes at the top of the first user turn. */
    GEMMA(listOf("<end_of_turn>", "<start_of_turn>")) {
        override fun render(system: String, history: List<PromptTurn>, userPrompt: String) = buildString {
            var systemPending = true
            for (turn in history + PromptTurn(role = "USER", content = userPrompt)) {
                val role = if (turn.isUser()) "user" else "model"
                val content = if (systemPending && turn.isUser()) {
                    systemPending = false
                    "$system\n\n${turn.content}"
                } else turn.content
                append("<start_of_turn>").append(role).append('\n')
                append(content).append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
    },

    LLAMA3(listOf("<|eot_id|>", "<|start_header_id|>")) {
        override fun render(system: String, history: List<PromptTurn>, userPrompt: String) = buildString {
            fun turn(role: String, content: String) {
                append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                append(content).append("<|eot_id|>")
            }
            turn("system", system)
            for (t in history) turn(if (t.isUser()) "user" else "assistant", t.content)
            turn("user", userPrompt)
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    };

    abstract fun render(system: String, history: List<PromptTurn>, userPrompt: String): String

    companion object {
        /** Guesses the family from the model file name; null = unknown, use the bundle's defaults. */
        fun detect(fileName: String): ChatTemplate? {
            val name = fileName.lowercase()
            return when {
                "qwen" in name || "smollm" in name -> CHATML
                "gemma" in name -> GEMMA
                "llama" in name -> LLAMA3
                else -> null
            }
        }
    }
}

private fun PromptTurn.isUser(): Boolean = role.equals("USER", ignoreCase = true)
