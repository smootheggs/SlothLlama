package app.localmind.assistant

import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.home.languageMark
import app.localmind.assistant.ui.home.roleOf
import app.localmind.assistant.ui.home.tutorGroups
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home page's tutor chips: one per language, partner first. */
class HomeUnitTests {

    private fun tutor(id: String, name: String, speech: SpeechLanguage?) =
        Persona(id, name, "", "", "", 0.7f, 512, speech, emptyList())

    @Test
    fun `tutors are grouped by language with the partner first`() {
        val personas = listOf(
            tutor("a", "General Assistant", null),
            tutor("t", "Mandarin Teacher", SpeechLanguage.MANDARIN),
            tutor("g", "German Partner", SpeechLanguage.GERMAN),
            tutor("m", "Mandarin Partner", SpeechLanguage.MANDARIN),
            tutor("h", "Hans", SpeechLanguage.GERMAN)
        )
        val groups = tutorGroups(personas)
        assertEquals(listOf(SpeechLanguage.MANDARIN, SpeechLanguage.GERMAN), groups.map { it.language })
        assertEquals(listOf("m", "t"), groups[0].tutors.map { it.id })
        assertEquals(listOf("Partner", "Teacher"), groups[0].tutors.map { roleOf(it, SpeechLanguage.MANDARIN) })
        assertEquals("Hans", roleOf(groups[1].tutors[1], SpeechLanguage.GERMAN))
    }

    @Test
    fun `languages are marked by script or code, not flags`() {
        assertEquals("粵", languageMark(SpeechLanguage.CANTONESE, ChineseScript.TRADITIONAL))
        assertEquals("粤", languageMark(SpeechLanguage.CANTONESE, ChineseScript.SIMPLIFIED))
        assertEquals("FR", languageMark(SpeechLanguage.FRENCH, ChineseScript.SIMPLIFIED))
    }
}
