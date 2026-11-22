package app.localmind.assistant

import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.speech.VoiceChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Which voice reads which language, and the trader persona's ground rules. */
class VoiceChoiceUnitTests {

    private fun voice(name: String, tag: String, network: Boolean = false, missing: Boolean = false, quality: Int = 300) =
        VoiceChoice.Candidate(name, Locale.forLanguageTag(tag), network, missing, quality)

    private val mandarinOnly = listOf(voice("cmn-cn-x-ccc-local", "zh-CN"), voice("cmn-tw-x-ctc-local", "zh-TW"))

    @Test
    fun `Cantonese is never read by a Mandarin voice`() {
        assertNull(VoiceChoice.pick(SpeechLanguage.CANTONESE, mandarinOnly))
        val withCantonese = mandarinOnly + voice("yue-hk-x-yuc-local", "yue-HK")
        assertEquals("yue-hk-x-yuc-local", VoiceChoice.pick(SpeechLanguage.CANTONESE, withCantonese))
        assertTrue(VoiceChoice.isCantonese(Locale.forLanguageTag("zh-HK"), null))
        assertTrue(VoiceChoice.isCantonese(null, "yue-hk-x-jar-network"))
        assertFalse(VoiceChoice.isCantonese(Locale.forLanguageTag("zh-CN"), "cmn-cn-x-ccc-local"))
    }

    @Test
    fun `Mandarin isn't read by a Cantonese voice, and mainland voices come first`() {
        val all = listOf(voice("yue-hk-x-yuc-local", "zh-HK")) + mandarinOnly.reversed()
        assertEquals("cmn-cn-x-ccc-local", VoiceChoice.pick(SpeechLanguage.MANDARIN, all))
    }

    @Test
    fun `Google's cmn voices count as Mandarin, never its yue ones`() {
        val google = listOf(voice("yue-hk-x-jar-local", "yue-HK"), voice("cmn-tw-x-ctc-local", "cmn-TW"), voice("cmn-cn-x-ccc-local", "cmn-CN"))
        assertEquals("cmn-cn-x-ccc-local", VoiceChoice.pick(SpeechLanguage.MANDARIN, google))
        assertEquals("yue-hk-x-jar-local", VoiceChoice.pick(SpeechLanguage.CANTONESE, google))
    }

    @Test
    fun `only installed voices on the tablet are picked`() {
        val voices = listOf(
            voice("fr-fr-x-network", "fr-FR", network = true, quality = 500),
            voice("fr-fr-x-missing", "fr-FR", missing = true),
            voice("fr-ca-x-local", "fr-CA")
        )
        assertEquals("fr-ca-x-local", VoiceChoice.pick(SpeechLanguage.FRENCH, voices))
        assertNull(VoiceChoice.pick(SpeechLanguage.CANTONESE, listOf(voice("yue-hk-x-net", "yue-HK", network = true))))
    }

    @Test
    fun `the trader is built in and leaves the sums to code`() {
        val trader = BuiltInPersonas.byId(BuiltInPersonas.TRADER.id)
        assertEquals("Trader", trader.name)
        assertNull(trader.speech)
        assertTrue(trader.systemInstruction.contains("never work out prices"))
        assertTrue(trader.systemInstruction.contains("Wrong if"))
    }

    @Test
    fun `the trader carries the rule card forms, so a card from plain chat reads too`() {
        val trader = BuiltInPersonas.TRADER.systemInstruction
        assertTrue(trader.contains(app.localmind.assistant.domain.desk.RuleCard.FORMS))
        assertTrue(trader.contains("never options"))
        // Its own example card reads, with no problems.
        val example = trader.substringAfter("Example:\n")
        val read = app.localmind.assistant.domain.desk.RuleCard.parse(example, setOf("GLD"))
        assertNotNull(read.rule)
        assertTrue(read.problems.toString(), read.problems.isEmpty())
    }
}
