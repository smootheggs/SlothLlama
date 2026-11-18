package app.localmind.assistant

import app.localmind.assistant.data.library.Chunker
import app.localmind.assistant.data.library.SearchTokens
import app.localmind.assistant.data.lobster.MailboxCrypto
import app.localmind.assistant.domain.attachments.Attachments
import app.localmind.assistant.domain.attachments.LibraryPassage
import app.localmind.assistant.domain.attachments.ToolResult
import app.localmind.assistant.domain.tools.Calculator
import app.localmind.assistant.domain.tools.TimeTools
import app.localmind.assistant.domain.tools.ToolNames
import app.localmind.assistant.domain.tools.ToolRequest
import app.localmind.assistant.domain.tools.ToolRouter
import app.localmind.assistant.domain.tools.Units
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The offline tools' pattern matching, the message blocks, library search words and the
 * Lobster mailbox encryption. All plain Kotlin, so they run on GitHub without a tablet.
 */
class ToolsAndMailboxUnitTests {

    /** Wednesday 18 November 2026, 9:00 in San Francisco. */
    private val now = ZonedDateTime.of(2026, 11, 18, 9, 0, 0, 0, ZoneId.of("America/Los_Angeles"))

    // --- calculator ---

    @Test
    fun `calculator finds a percentage of a number with thousands separators`() {
        val r = Calculator.find("What's 18% of 2,450?")
        assertNotNull(r)
        assertEquals(441.0, r!!.value, 1e-9)
        assertEquals("441", r.formatted)
    }

    @Test
    fun `calculator reads words and symbols`() {
        assertEquals(90.0, Calculator.find("what's 12 times 7.5")!!.value, 1e-9)
        assertEquals(21.0, Calculator.evaluate("144/12+3^2"), 1e-9)
        assertEquals(512.0, Calculator.evaluate("2^3^2"), 1e-9)
        assertEquals(12.0, Calculator.find("square root of 144")!!.value, 1e-9)
    }

    @Test
    fun `calculator leaves ranges times and dates alone`() {
        assertNull(Calculator.find("I work 9-5 on weekdays"))
        assertNull(Calculator.find("the shop is open 24/7"))
        assertNull(Calculator.find("we meet on 2027-07-25"))
        assertNull(Calculator.find("I have 3 cats and 2 dogs"))
    }

    @Test
    fun `numbers are formatted with separators and without trailing zeros`() {
        assertEquals("1,234,567", Calculator.format(1_234_567.0))
        assertEquals("8.047", Calculator.format(8.047))
    }

    // --- units ---

    @Test
    fun `units convert distance temperature and kitchen measures`() {
        assertEquals("5 mi = 8.047 km", Units.find("5 miles in km")!!.label)
        assertEquals("350 °F = 176.7 °C", Units.find("350 F to C")!!.label)
        assertEquals("1 L = 4.227 cups", Units.find("how many cups in a liter?")!!.label)
    }

    @Test
    fun `units ignore sentences that only mention units`() {
        assertNull(Units.find("I ran 5 km in 30 minutes"))
    }

    // --- clock and dates ---

    @Test
    fun `time in a city`() {
        assertEquals("02:00 in Tokyo", TimeTools.find("what time is it in tokyo", now)!!.label)
    }

    @Test
    fun `time converted between two cities`() {
        val r = TimeTools.find("what time is 3pm in hong kong in san francisco", now)
        assertEquals("15:00 Hong Kong = 23:00 San Francisco (the day before)", r!!.label)
    }

    @Test
    fun `days until a holiday and the weekday of a date`() {
        assertTrue(TimeTools.find("how many days until christmas", now)!!.label.startsWith("37 days until Friday 25 December 2026"))
        assertEquals("2027-07-25 is a Sunday", TimeTools.find("what day is 2027-07-25", now)!!.label)
    }

    @Test
    fun `a date some days away`() {
        assertEquals("100 days from today: Friday 26 February 2027", TimeTools.find("what's the date in 100 days", now)!!.label)
    }

    // --- the router ---

    @Test
    fun `timers alarms notes and lobster orders are recognized`() {
        assertEquals(listOf(ToolRequest.Timer(600, null)), ToolRouter.route("set a timer for 10 minutes", now))
        assertEquals(listOf(ToolRequest.Timer(1200, "check the oven")), ToolRouter.route("remind me in 20 minutes to check the oven", now))
        assertEquals(listOf(ToolRequest.AddNote("buy oat milk")), ToolRouter.route("take a note: buy oat milk", now))
        assertEquals(listOf(ToolRequest.Lobster("find flights")), ToolRouter.route("ask the lobster to find flights", now))
        val alarm = ToolRouter.route("wake me up at 6:30 am", now).single() as ToolRequest.Alarm
        assertEquals(6, alarm.hour)
        assertEquals(30, alarm.minute)
    }

    @Test
    fun `sums become calculator results`() {
        val r = ToolRouter.route("what's 12 times 7.5", now).single() as ToolRequest.Computed
        assertEquals(ToolNames.CALCULATOR, r.result.tool)
        assertEquals("12 × 7.5 = 90", r.result.label)
    }

    @Test
    fun `ordinary sentences call no tools`() {
        assertTrue(ToolRouter.route("I work 9-5 on weekdays", now).isEmpty())
        assertTrue(ToolRouter.route("how do timers work", now).isEmpty())
        assertTrue(ToolRouter.route("Tell me about the history of Rome", now).isEmpty())
    }

    @Test
    fun `previews describe what would happen`() {
        assertEquals(ToolNames.TIMER to "Will set a 10 min timer", ToolRouter.describe(ToolRequest.Timer(600, null)))
        assertEquals(ToolNames.ALARM to "Will set an alarm at 06:30", ToolRouter.describe(ToolRequest.Alarm(6, 30, null)))
    }

    // --- message blocks ---

    private val passages = listOf(
        LibraryPassage("Book", "Ch 1", "para one\n\npara two"),
        LibraryPassage("Wiki", "", "text")
    )
    private val tools = listOf(ToolResult(ToolNames.UNITS, "5 mi = 8.047 km", "5 mi = 8.047 km (exact)."))

    @Test
    fun `library and tool blocks survive a round trip`() {
        val content = Attachments.compose("What is X?", null, null, passages, tools)
        val parsed = Attachments.parse(content)
        assertEquals("What is X?", parsed.text)
        assertEquals(passages, parsed.library)
        assertEquals(tools, parsed.tools)
    }

    @Test
    fun `history keeps only titles and one-line results`() {
        val content = Attachments.compose("What is X?", null, null, passages, tools)
        val note = Attachments.historyNote(content)
        assertTrue(note.contains("[Looked up in the library: Book; Wiki]"))
        assertTrue(note.contains("[Tools: 5 mi = 8.047 km]"))
        assertFalse(note.contains("para two"))
    }

    @Test
    fun `the current message gives the model numbered passages and the tool results`() {
        val content = Attachments.compose("What is X?", null, null, passages, tools)
        val forModel = Attachments.forModel(content, "", earlier = false) { it }
        assertTrue(forModel.contains("[1] Book — Ch 1"))
        assertTrue(forModel.contains("[2] Wiki\n"))
        assertTrue(forModel.contains("- 5 mi = 8.047 km (exact)."))
        assertTrue(forModel.trimEnd().endsWith("What is X?"))
    }

    // --- library search words ---

    @Test
    fun `search words fold accents and split Chinese into pairs`() {
        assertEquals(listOf("cafe", "lait"), SearchTokens.forIndex("Café au lait"))
        assertEquals(listOf("长城", "城历", "历史"), SearchTokens.forQuery("长城历史"))
        assertEquals(listOf("Ada", "Lovelace"), SearchTokens.keywords("Who was Ada Lovelace?"))
    }

    @Test
    fun `passages keep the heading they fall under`() {
        val chunks = Chunker.chunks("# Intro\n\nFirst paragraph.\n\n## Part two\n\nSecond paragraph.")
        assertEquals(listOf(Chunker.Chunk("Intro", "First paragraph."), Chunker.Chunk("Part two", "Second paragraph.")), chunks)
    }

    // --- the Lobster mailbox ---

    @Test
    fun `mailbox files open only with the right key and type`() {
        val key = MailboxCrypto.newKey()
        val sealed = MailboxCrypto.seal(key, "order", "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), MailboxCrypto.open(key, "order", sealed))
        assertNull(MailboxCrypto.open(key, "catch", sealed))
        assertNull(MailboxCrypto.open(MailboxCrypto.newKey(), "order", sealed))
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(MailboxCrypto.open(key, "order", tampered))
    }

    @Test
    fun `pairing codes round trip`() {
        val key = MailboxCrypto.newKey()
        val code = MailboxCrypto.pairingCode(key)
        assertEquals(43, code.length)
        assertArrayEquals(key, MailboxCrypto.fromPairingCode(code.chunked(4).joinToString(" ")))
    }
}
