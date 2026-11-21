package app.localmind.assistant

import app.localmind.assistant.domain.briefing.Briefing
import app.localmind.assistant.domain.briefing.Briefings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.40: a pasted briefing, read into its parts on the tablet. The text here is made up. */
class BriefingUnitTests {

    private val sample = """
        **Research date:** March 3, 2027. **Cutoff:** ~06:00 UTC.

        Stories were chosen for their effect on security and diplomacy.

        ### 1. TOP 7 GLOBAL DEVELOPMENTS

        **1. Two neighbours sign a river treaty (Northland / Southland)**
        **Verified Facts:** Both governments signed a treaty on sharing the river's water.
        It takes effect next year.
        **Global Implications:** Lowers the risk of a long-running dispute.
        **Information Gaps:** How the water will be measured.
        **Source Confidence:** High (official texts and wire reports).

        **2. A port strike enters its second week (Eastport)**
        **Verified Facts:** Dock workers are still out.
        **Global Implications:** Shipping is being rerouted.
        **Information Gaps:** Whether talks have resumed.
        **Source Confidence:** Medium (local reporting).

        ### 2. ESCALATION WATCHLIST

        **Observable facts:**
        - Talks between the port and the union were cancelled.
        - Two shipping lines announced detours.

        **Speculation (clearly labeled):** The strike could spread to other ports.
        Watch for a court order.

        This briefing prioritizes verified developments.
    """.trimIndent()

    private fun parse(text: String = sample): Briefing = Briefings.parse(text, "b1", 1_000)

    @Test
    fun `stories are read with their parts`() {
        val b = parse()
        assertEquals(2, b.stories.size)
        val first = b.stories[0]
        assertEquals(1, first.rank)
        assertEquals("Two neighbours sign a river treaty", first.headline)
        assertEquals("Northland / Southland", first.region)
        assertEquals("Both governments signed a treaty on sharing the river's water. It takes effect next year.", first.facts)
        assertEquals("Lowers the risk of a long-running dispute.", first.implications)
        assertEquals("How the water will be measured.", first.gaps)
        assertEquals(3, first.confidence)
        assertEquals("official texts and wire reports", first.confidenceNote)
        assertEquals(2, b.stories[1].confidence)
        assertTrue(b.preface.startsWith("Research date: March 3, 2027."))
    }

    @Test
    fun `the watchlist keeps what it saw apart from its guesses`() {
        val b = parse()
        assertEquals(listOf("Talks between the port and the union were cancelled.", "Two shipping lines announced detours."), b.seen)
        assertEquals("The strike could spread to other ports. Watch for a court order.", b.guess)
        assertEquals("This briefing prioritizes verified developments.", b.rest)
    }

    @Test
    fun `plain text without bold marks reads the same`() {
        val b = parse(sample.replace("**", ""))
        assertEquals(2, b.stories.size)
        assertEquals("Eastport", b.stories[1].region)
        assertEquals(2, b.seen.size)
    }

    @Test
    fun `what doesn't fit is kept, not guessed`() {
        val b = parse("Just a note with no stories in it.")
        assertTrue(b.stories.isEmpty())
        assertEquals("Just a note with no stories in it.", b.preface)
    }

    @Test
    fun `briefings are kept thirty days and round-trip`() {
        val day = 86_400_000L
        val now = 100 * day
        val list = listOf(parse().copy(id = "old", takenAt = now - 40 * day), parse().copy(id = "new", takenAt = now - day))
        assertEquals(listOf("new"), Briefings.kept(list, now).map { it.id })
        val b = parse()
        assertEquals(listOf(b), Briefings.decode(Briefings.encode(listOf(b))))
        assertTrue("not checked" in Briefings.asText(b.stories[0], "Grok"))
    }
}
