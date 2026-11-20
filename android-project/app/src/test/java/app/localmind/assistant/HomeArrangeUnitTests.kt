package app.localmind.assistant

import app.localmind.assistant.data.home.HomeSettings
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.personas.personaSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's arranging (tap to swap) and favourite personas. */
class HomeArrangeUnitTests {

    @Test
    fun `tiles follow the saved order, new ones keep their place after`() {
        val tiles = listOf("desk", "lobster", "ourjong")
        assertEquals(tiles, HomeSettings.arranged(tiles, emptyList()) { it })
        assertEquals(listOf("ourjong", "lobster", "desk"), HomeSettings.arranged(tiles, listOf("ourjong", "lobster", "desk")) { it })
        // An order saved before the Lobster was switched on still places the rest.
        assertEquals(listOf("ourjong", "desk", "lobster"), HomeSettings.arranged(tiles, listOf("ourjong", "desk")) { it })
    }

    @Test
    fun `a swap trades two places and nothing else`() {
        val keys = listOf("desk", "lobster", "ourjong")
        assertEquals(listOf("ourjong", "lobster", "desk"), HomeSettings.swapped(keys, 0, 2))
        assertEquals(keys, HomeSettings.swapped(keys, 1, 1))
        assertEquals(keys, HomeSettings.swapped(keys, 0, 5))
    }

    @Test
    fun `favourites lead the Personas page, once each, in Home's order`() {
        fun p(id: String, name: String, speech: SpeechLanguage?) = Persona(id, name, "✦", "", "", 0.7f, 512, speech, emptyList())
        val all = listOf(p("g", "General Assistant", null), p("t", "Trader", null), p("m", "Mandarin", SpeechLanguage.MANDARIN))
        val sections = personaSections(all, favourites = listOf("t", "m", "gone"))
        assertEquals("favourites", sections.first().key)
        assertEquals(listOf("t", "m"), sections.first().personas.map { it.id })
        assertEquals(listOf("g"), sections.last().personas.map { it.id })
        assertTrue(sections.none { it.key == "MANDARIN" })
    }

    @Test
    fun `the default favourites are personas that exist`() {
        assertTrue(HomeSettings.DEFAULT_FAVOURITES.all { id -> BuiltInPersonas.all.any { it.id == id } })
    }
}
