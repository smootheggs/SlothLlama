package app.localmind.assistant

import app.localmind.assistant.data.desk.SavedRule
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.RuleFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Your rules as kept (1.48): only what passes its checks comes back from a backup. */
class SavedRuleUnitTests {

    private val good = RuleFiles.encode(DeskStrategies.rule("gold_trend", DeskStrategies.template("gold_trend").defaults))

    @Test
    fun `a sound rule comes back as it went`() {
        val r = SavedRule("0123456789", good, "shelf:gold_trend", tries = listOf(good))
        assertEquals(r, SavedRule.sound(r))
    }

    @Test
    fun `odd ids, unreadable rules and long tags are refused`() {
        assertNull(SavedRule.sound(SavedRule("../../etc", good)))
        assertNull(SavedRule.sound(SavedRule("0123456789", "{\"v\":2}")))
        assertNull(SavedRule.sound(SavedRule("0123456789", good, from = "x".repeat(41))))
        val outOfRange = good.replace("\"days\":200", "\"days\":5000")
        assertNull(SavedRule.sound(SavedRule("0123456789", outOfRange)))
    }

    @Test
    fun `bad tries are dropped and only the last twenty kept`() {
        val tries = List(30) { good } + "not a rule"
        val back = SavedRule.sound(SavedRule("abcdef0123", good, tries = tries))
        assertNotNull(back)
        assertEquals(SavedRule.MAX_TRIES_KEPT, back!!.tries.size)
    }
}
