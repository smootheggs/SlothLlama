package app.localmind.assistant

import app.localmind.assistant.data.inference.ContextBudgetManager
import app.localmind.assistant.domain.inference.PromptTurn
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.domain.model.MessageStatus
import app.localmind.assistant.ui.chat.components.MarkdownBlock
import app.localmind.assistant.ui.chat.components.parseMarkdownBlocks
import org.junit.Assert.*
import org.junit.Test

class ChatFeatureUnitTests {

    private val budgetManager = ContextBudgetManager()

    @Test
    fun `context budget prioritizes system prompt and current message over old history`() {
        val turns = (1..50).map { i ->
            PromptTurn(role = if (i % 2 == 1) "USER" else "ASSISTANT", content = "Message turn $i with detailed explanation.")
        }

        val result = budgetManager.planContext(
            systemInstruction = "You are SlothLlama, a helpful assistant.",
            userMemories = listOf("Prefers concise bullet points"),
            summary = null,
            historyTurns = turns,
            currentPrompt = "What is the capital of France?",
            totalContextBudgetTokens = 200,
            reservedOutputTokens = 50
        )

        // Result turns must be non-empty and must include the most recent turn
        assertTrue(result.includedTurns.isNotEmpty())
        assertEquals(turns.last().content, result.includedTurns.last().content)
        assertTrue(result.truncatedTurnsCount > 0)
        assertTrue(result.estimatedTokensUsed <= result.availableBudgetTokens)
    }

    @Test
    fun `markdown parser extracts fenced code blocks and languages correctly`() {
        val markdown = """
            Here is a Kotlin function:
            ```kotlin
            fun greet(name: String): String {
                return "Hello, " + name
            }
            ```
            And here is a bullet list:
            - Item one
            - Item two
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertTrue(blocks.any { it is MarkdownBlock.Paragraph })
        
        val codeBlock = blocks.filterIsInstance<MarkdownBlock.CodeFence>().firstOrNull()
        assertNotNull(codeBlock)
        assertEquals("kotlin", codeBlock?.language)
        assertTrue(codeBlock?.code?.contains("fun greet") == true)

        val listItems = blocks.filterIsInstance<MarkdownBlock.ListItem>()
        assertEquals(2, listItems.size)
        assertEquals("Item one", listItems[0].text)
        assertEquals("Item two", listItems[1].text)
    }

    @Test
    fun `message role and status string parsing handles lowercase and unknown strings safely`() {
        assertEquals(MessageRole.USER, MessageRole.fromString("user"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("ASSISTANT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.fromString("System"))
        assertEquals(MessageRole.TOOL, MessageRole.fromString("tool"))
        assertEquals(MessageRole.USER, MessageRole.fromString("UNKNOWN_ROLE"))

        assertEquals(MessageStatus.PENDING, MessageStatus.fromString("pending"))
        assertEquals(MessageStatus.STREAMING, MessageStatus.fromString("STREAMING"))
        assertEquals(MessageStatus.COMPLETE, MessageStatus.fromString("complete"))
        assertEquals(MessageStatus.FAILED, MessageStatus.fromString("failed"))
        assertEquals(MessageStatus.CANCELLED, MessageStatus.fromString("cancelled"))
        assertEquals(MessageStatus.COMPLETE, MessageStatus.fromString("INVALID_STATUS"))
    }

    @Test
    fun `token estimator scales proportionally with character count`() {
        val shortEstimate = budgetManager.estimateTokens("Hello")
        val longEstimate = budgetManager.estimateTokens("Hello world, this is a much longer sentence containing significantly more characters for token estimation.")
        assertTrue(longEstimate > shortEstimate)
        assertEquals(2, budgetManager.estimateTokens("Hello")) // (5 + 3) / 4 = 2
    }
}
