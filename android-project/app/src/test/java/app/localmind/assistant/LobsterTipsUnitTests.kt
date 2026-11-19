package app.localmind.assistant

import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.ui.lobster.LobsterTips
import app.localmind.assistant.ui.lobster.plainPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Lobster page: clean previews of what it brought back, and the tips for asking it. */
class LobsterTipsUnitTests {

    @Test
    fun `a brief's preview drops its repeated title and the Markdown marks`() {
        val text = "# Brief · Gold\n\n_Thu 19 Nov 2026_\n\n## Gold - Google News\n\n- [Gold steadies](https://example.com/a) — **calm** day"
        val p = plainPreview(text, "Brief · Gold")
        assertEquals("Thu 19 Nov 2026\nGold - Google News\n· Gold steadies — calm day", p)
    }

    @Test
    fun `snake_case words keep their underscores`() {
        assertEquals("see snake_case here", plainPreview("see snake_case here", "x"))
    }

    @Test
    fun `the order box offers no Ask or Prices, and every kind it offers has a tip`() {
        assertFalse(OrderKind.ASK in OrderKind.forOrderBox)
        assertFalse(OrderKind.PRICES in OrderKind.forOrderBox)
        OrderKind.forOrderBox.forEach { k ->
            assertTrue(k.label, LobsterTips.forKind(k).examples.isNotEmpty())
            assertTrue(LobsterTips.draftPrompt(k, "my idea").endsWith("What I want: my idea"))
        }
    }
}
