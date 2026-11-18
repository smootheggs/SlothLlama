package app.localmind.assistant.data.inference

import app.localmind.assistant.domain.inference.PromptTurn
import javax.inject.Inject
import javax.inject.Singleton

data class ContextBudgetResult(
    val includedTurns: List<PromptTurn>,
    val estimatedTokensUsed: Int,
    val reservedOutputTokens: Int,
    val availableBudgetTokens: Int,
    val truncatedTurnsCount: Int
)

@Singleton
class ContextBudgetManager @Inject constructor() {

    fun planContext(
        systemInstruction: String,
        userMemories: List<String>,
        summary: String?,
        historyTurns: List<PromptTurn>,
        currentPrompt: String,
        totalContextBudgetTokens: Int = 4096,
        reservedOutputTokens: Int = 1024
    ): ContextBudgetResult {
        val maxInputBudget = (totalContextBudgetTokens - reservedOutputTokens).coerceAtLeast(512)
        var usedTokens = 0

        // 1. Mandatory Core system instructions & memories
        usedTokens += estimateTokens(systemInstruction)
        if (userMemories.isNotEmpty()) {
            usedTokens += estimateTokens(userMemories.joinToString("\n"))
        }

        // 2. Mandatory current user prompt
        usedTokens += estimateTokens(currentPrompt)

        // 3. Optional conversation summary
        if (!summary.isNullOrBlank()) {
            usedTokens += estimateTokens(summary)
        }

        // 4. Fit recent history turns in reverse order (newest to oldest)
        val includedReversed = mutableListOf<PromptTurn>()
        var truncatedCount = 0

        for (i in historyTurns.indices.reversed()) {
            val turn = historyTurns[i]
            val turnTokens = estimateTokens("${turn.role}: ${turn.content}")
            if (usedTokens + turnTokens <= maxInputBudget) {
                includedReversed.add(turn)
                usedTokens += turnTokens
            } else {
                truncatedCount = i + 1
                break
            }
        }

        val finalTurns = includedReversed.reversed()

        return ContextBudgetResult(
            includedTurns = finalTurns,
            estimatedTokensUsed = usedTokens,
            reservedOutputTokens = reservedOutputTokens,
            availableBudgetTokens = maxInputBudget,
            truncatedTurnsCount = truncatedCount
        )
    }

    fun estimateTokens(text: String): Int {
        // Chinese / Japanese characters are roughly one token each; other text about four characters per token.
        val cjk = text.count { it.code in 0x3000..0x9FFF || it.code in 0xF900..0xFAFF || it.code in 0xFF00..0xFFEF }
        return (cjk + (text.length - cjk + 3) / 4).coerceAtLeast(1)
    }
}
