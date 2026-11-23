package app.localmind.assistant

import app.localmind.assistant.domain.bench.Bench
import app.localmind.assistant.domain.bench.BenchBackend
import app.localmind.assistant.domain.bench.BenchRun
import app.localmind.assistant.domain.bench.BenchTiming
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The benchmark's sums (1.52): speeds, medians, spread and the text it copies. */
class BenchUnitTests {

    @Test
    fun `speeds, medians and spread`() {
        assertEquals(64.0, Bench.tps(128, 2_000), 1e-9)
        assertEquals(0.0, Bench.tps(0, 2_000), 1e-9)
        assertEquals(0.0, Bench.tps(128, 0), 1e-9)
        assertEquals(10.0, Bench.median(listOf(12.0, 8.0, 10.0)), 1e-9)
        assertEquals(9.0, Bench.median(listOf(8.0, 10.0)), 1e-9)
        assertEquals(40.0, Bench.spreadPct(listOf(12.0, 8.0, 10.0)), 1e-9)
        assertEquals(0.0, Bench.spreadPct(listOf(10.0)), 1e-9)
    }

    @Test
    fun `threads follow the native rule, with more to try where there are cores`() {
        assertEquals(4, Bench.usualThreads(8))
        assertEquals(2, Bench.usualThreads(4))
        assertEquals(listOf(4, 6, 8), Bench.threadChoices(8))
        assertEquals(listOf(2, 4), Bench.threadChoices(4))
    }

    @Test
    fun `a run is kept as medians, and says when the GPU wasn't used`() {
        val t = BenchTiming(onGpu = false, threads = 4, promptTokens = 600, readTps = listOf(90.0, 100.0, 110.0),
            writeTps = listOf(9.0, 10.0, 11.0), loadMillis = 2_500, memoryMb = 3_072)
        val r = Bench.run("a", 0, "q.gguf", "Qwen", BenchBackend.GPU, t, "Galaxy S25+")
        assertEquals(100.0, r.readTps, 1e-9)
        assertEquals(10.0, r.writeTps, 1e-9)
        assertEquals("CPU", r.backend)
        assertTrue(r.fellBack)
        assertEquals("CPU · 4", Bench.where(r))
        assertFalse(Bench.run("b", 0, "q.gguf", "Qwen", BenchBackend.CPU, t, "x").fellBack)
        // Kept as JSON; an older file without a field still reads.
        val json = Json { ignoreUnknownKeys = true }
        val back = json.decodeFromString(ListSerializer(BenchRun.serializer()), json.encodeToString(ListSerializer(BenchRun.serializer()), listOf(r)))
        assertEquals(r, back.single())
    }

    @Test
    fun `copied results read plainly`() {
        val r = BenchRun("a", 0, "q.gguf", "Qwen3.5 4B", "GPU", 4, 600, 210.4, 18.25, 3.0, 2_560, 4.0, "Lenovo Y700")
        val text = Bench.asText(listOf(r), "Lenovo Y700 · Qualcomm") { "22 Nov" }
        assertTrue(text.startsWith("SlothLlama benchmark · Lenovo Y700 · Qualcomm"))
        assertTrue(text.contains("22 Nov · Qwen3.5 4B · GPU · reading 210 · writing 18.3 · load 3.0 s · memory 2.5 GB"))
        assertEquals("–", Bench.speed(0.0))
    }

    @Test
    fun `the prompt is long enough to time and asks for a long reply`() {
        val words = Bench.PROMPT.split(Regex("\\s+")).count { it.isNotBlank() }
        assertTrue("$words words", words in 380..520)
        assertTrue(Bench.PROMPT.startsWith("Continue this story"))
    }
}
