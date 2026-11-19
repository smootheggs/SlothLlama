package app.localmind.assistant

import app.localmind.assistant.data.lobster.LobsterCatch
import app.localmind.assistant.data.lobster.LobsterSource
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.lobster.catchLook
import app.localmind.assistant.ui.personas.nativeName
import app.localmind.assistant.ui.personas.personaMark
import app.localmind.assistant.ui.personas.personaSections
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Personas grid's grouping and marks, and how the Lobster page labels a catch. */
class RedesignUnitTests {

    private fun persona(id: String, name: String, speech: SpeechLanguage?, builtIn: Boolean = true, badge: String = "✦") =
        Persona(id, name, badge, "", "", 0.7f, 512, speech, emptyList(), isBuiltIn = builtIn)

    @Test
    fun `personas group by language, partner first, assistants last`() {
        val sections = personaSections(
            listOf(
                persona("mine", "Recipe helper", null, builtIn = false, badge = "🍜"),
                persona("t", "Cantonese Teacher", SpeechLanguage.CANTONESE),
                persona("g", "General Assistant", null),
                persona("p", "Cantonese", SpeechLanguage.CANTONESE),
                persona("m", "Mandarin", SpeechLanguage.MANDARIN)
            )
        )
        assertEquals(listOf("MANDARIN", "CANTONESE", "assistants"), sections.map { it.key })
        assertEquals(listOf("p", "t"), sections[1].personas.map { it.id })
        assertEquals(listOf("g", "mine"), sections[2].personas.map { it.id })
    }

    @Test
    fun `tiles are marked by script and role`() {
        val partner = persona("p", "Cantonese", SpeechLanguage.CANTONESE)
        val teacher = persona("t", "Mandarin Teacher", SpeechLanguage.MANDARIN)
        assertEquals("粵", personaMark(partner, SpeechLanguage.CANTONESE, ChineseScript.TRADITIONAL))
        assertEquals("师", personaMark(teacher, SpeechLanguage.MANDARIN, ChineseScript.SIMPLIFIED))
        assertEquals("🍜", personaMark(persona("x", "Recipe helper", null, builtIn = false, badge = "🍜"), null, ChineseScript.SIMPLIFIED))
        assertEquals("廣東話", nativeName(SpeechLanguage.CANTONESE, ChineseScript.TRADITIONAL))
    }

    @Test
    fun `a catch is labelled by what it is`() {
        val research = LobsterCatch(id = "1", orderId = "o", title = "Research · Why gold rose", sources = List(3) { LobsterSource("s$it", "https://example.org/$it") })
        val look = catchLook(research)
        assertEquals("Research", look.kind)
        assertEquals("Why gold rose", look.title)
        assertEquals("3 sources · ready to read or ask about offline", look.detail)
        assertEquals("Brief", catchLook(LobsterCatch(id = "2", title = "Morning brief · 19 Nov")).kind)
        assertEquals("Prices", catchLook(LobsterCatch(id = "3", kind = "prices", title = "Prices to 2026-11-19")).kind)
        assertEquals("Message", catchLook(LobsterCatch(id = "4", kind = "message", title = "Update the SlothLobster")).kind)
        assertEquals("Page", catchLook(LobsterCatch(id = "5", orderId = "o", title = "A long read", text = "First line\n\nMore")).kind)
        assertEquals("First line", catchLook(LobsterCatch(id = "5", orderId = "o", title = "A long read", text = "First line\n\nMore")).detail)
    }
}
