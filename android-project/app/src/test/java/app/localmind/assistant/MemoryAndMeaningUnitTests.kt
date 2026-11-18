package app.localmind.assistant

import app.localmind.assistant.data.meaning.Vectors
import app.localmind.assistant.data.memory.Memory
import app.localmind.assistant.data.memory.MemoryRules
import app.localmind.assistant.domain.tools.ToolRequest
import app.localmind.assistant.domain.tools.ToolRouter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Memory of you and search by meaning: the parts that run without a tablet. */
class MemoryAndMeaningUnitTests {

    private val now = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun memory(id: String, text: String) = Memory(id, text, 0)

    @Test
    fun `remember forget and list are recognised`() {
        assertEquals(listOf(ToolRequest.Remember("I'm vegetarian")), ToolRouter.route("remember that I'm vegetarian", now))
        assertEquals(listOf(ToolRequest.Remember("my sister lives in Berlin")), ToolRouter.route("Remember: my sister lives in Berlin", now))
        assertEquals(listOf(ToolRequest.Remember("I prefer short answers")), ToolRouter.route("Please keep in mind that I prefer short answers", now))
        assertEquals(listOf(ToolRequest.Forget("I'm vegetarian")), ToolRouter.route("forget that I'm vegetarian", now))
        assertEquals(listOf(ToolRequest.ListMemories), ToolRouter.route("What do you remember about me?", now))
    }

    @Test
    fun `reminders and questions aren't memories`() {
        assertFalse(ToolRouter.route("remember to buy oat milk", now).any { it is ToolRequest.Remember })
        assertFalse(ToolRouter.route("do you remember the capital of Peru?", now).any { it is ToolRequest.Remember })
        assertFalse(ToolRouter.route("remember when we talked about Rome", now).any { it is ToolRequest.Remember })
        assertFalse(ToolRouter.route("forget it", now).any { it is ToolRequest.Forget })
    }

    @Test
    fun `memories are tidied and matched by their words`() {
        assertEquals("I'm vegetarian", MemoryRules.clean("  \"I'm   vegetarian.\" "))
        val all = listOf(memory("a", "My sister lives in Berlin"), memory("b", "I am vegetarian"), memory("c", "I play guitar"))
        assertEquals("b", MemoryRules.closest("vegetarian", all)?.id)
        assertEquals("a", MemoryRules.closest("sister Berlin", all)?.id)
        assertNull(MemoryRules.closest("football", all))
        assertEquals("c", MemoryRules.rankByWords("Which guitar strings should I buy?", all).first().id)
    }

    @Test
    fun `memories stay within their share of the prompt`() {
        val many = (1..200).map { memory("$it", "Fact number $it about the user, written out at some length to take room") }
        val picked = MemoryRules.withinBudget(many)
        assertTrue(picked.isNotEmpty())
        assertTrue(picked.size < many.size)
        assertEquals("1", picked.first().id)
    }

    @Test
    fun `vectors are stored and compared exactly`() {
        val v = floatArrayOf(0.6f, -0.8f, 0f)
        assertArrayEquals(v, Vectors.fromBytes(Vectors.toBytes(v)), 0f)
        assertEquals(1f, Vectors.similarity(v, v), 1e-6f)
        val matrix = floatArrayOf(1f, 0f, 0f, 0.6f, -0.8f, 0f)
        assertEquals(1f, Vectors.similarity(v, matrix, 1, 3), 1e-6f)
        assertEquals(0.6f, Vectors.similarity(v, matrix, 0, 3), 1e-6f)
    }

    @Test
    fun `meaning only adds passages that are close enough`() {
        val fused = Vectors.fuse(byWords = listOf(1L, 2L), byMeaning = listOf(3L to 0.9f, 2L to 0.8f, 4L to 0.1f), floor = 0.42f)
        assertEquals(2L, fused.first())
        assertEquals(setOf(1L, 2L, 3L), fused.toSet())
        // Without word matches, weak meaning matches find nothing.
        assertTrue(Vectors.fuse(emptyList(), listOf(5L to 0.2f), floor = 0.42f).isEmpty())
    }
}
