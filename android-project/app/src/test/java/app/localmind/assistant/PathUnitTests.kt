package app.localmind.assistant

import app.localmind.assistant.domain.desk.Action
import app.localmind.assistant.domain.desk.Against
import app.localmind.assistant.domain.desk.Bars
import app.localmind.assistant.domain.desk.Branch
import app.localmind.assistant.domain.desk.Check
import app.localmind.assistant.domain.desk.Condition
import app.localmind.assistant.domain.desk.DeskData
import app.localmind.assistant.domain.desk.DeskPath
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Exit
import app.localmind.assistant.domain.desk.Line
import app.localmind.assistant.domain.desk.Op
import app.localmind.assistant.domain.desk.Rule
import app.localmind.assistant.domain.desk.RuleCard
import app.localmind.assistant.domain.desk.RuleEngine
import app.localmind.assistant.domain.desk.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.sqrt

/** The path (1.49): stages, paper days, the Sunday week, the Trader's rule card, tonight's why. */
class PathUnitTests {

    // --- stages and paper days ---

    @Test
    fun `an idea moves along the path`() {
        assertEquals(DeskPath.Stage.IDEA, DeskPath.stage(hasRule = false, onPaper = false, paperDays = 0))
        assertEquals(DeskPath.Stage.TEST, DeskPath.stage(hasRule = true, onPaper = false, paperDays = 0))
        assertEquals(DeskPath.Stage.PAPER, DeskPath.stage(hasRule = true, onPaper = true, paperDays = 59))
        assertEquals(DeskPath.Stage.REVIEW, DeskPath.stage(hasRule = true, onPaper = true, paperDays = 60))
    }

    @Test
    fun `paper days count from the version's start, once a day`() {
        val marks = listOf(
            DeskPath.Mark("2026-10-01", "yes", "none"),
            DeskPath.Mark("2026-11-02", "yes", "none"),
            DeskPath.Mark("2026-11-03", "no", "late"),
            DeskPath.Mark("2026-11-03", "yes", "none"),
            DeskPath.Mark("2026-11-04", "", ""),
            DeskPath.Mark("2026-11-05", "no", "skipped")
        )
        val p = DeskPath.progress(marks, since = "2026-11-02")
        assertEquals(4, p.days)
        assertEquals(3, p.marked)
        assertEquals(1, p.followed)
        assertEquals(mapOf("late" to 1, "skipped" to 1), p.slips)
        assertEquals(1.0 / 3, p.share!!, 1e-12)
        assertEquals(DeskPath.PAPER_DAYS, p.cells.size)
        assertEquals(listOf(DeskPath.Day.FOLLOWED, DeskPath.Day.NOT_FOLLOWED, DeskPath.Day.UNMARKED, DeskPath.Day.NOT_FOLLOWED, DeskPath.Day.TO_COME),
            p.cells.take(5))
        assertFalse(p.done)
        assertNull(DeskPath.progress(emptyList(), "2026-11-02").share)
    }

    @Test
    fun `only strategies you marked were on paper before 1_49`() {
        val before = DeskPath.followedBefore(mapOf(
            "gold_trend" to listOf(DeskPath.Mark("2026-09-02", "", ""), DeskPath.Mark("2026-09-03", "yes", "none")),
            "usdjpy_carry" to listOf(DeskPath.Mark("2026-09-02", "", ""), DeskPath.Mark("2026-09-03", "", "")),
            "my:0123456789" to listOf(DeskPath.Mark("2026-10-01", "no", "late"))
        ))
        assertEquals(mapOf("gold_trend" to "2026-09-02", "my:0123456789" to "2026-10-01"), before)
    }

    @Test
    fun `old slip tags still read`() {
        assertEquals("Skipped it", DeskPath.slip("skipped a ticket"))
        assertEquals("Changed the size", DeskPath.slip("sizing"))
        assertEquals("Didn't trust it", DeskPath.slip("distrust"))
        assertEquals("", DeskPath.slip("none"))
    }

    @Test
    fun `the review looks back on Monday to Sunday`() {
        val sun = LocalDate.of(2026, 11, 22)
        assertEquals(DayOfWeek.SUNDAY, sun.dayOfWeek)
        assertEquals(sun, DeskPath.sundayOf(LocalDate.of(2026, 11, 16)))
        assertEquals(sun, DeskPath.sundayOf(sun))
        assertEquals(0, DeskPath.daysToSunday(sun))
        assertEquals(1, DeskPath.daysToSunday(sun.minusDays(1)))
        assertEquals(LocalDate.of(2026, 11, 16), DeskPath.week(sun).start)
        assertEquals(sun, DeskPath.waiting(sun, emptySet()))
        assertEquals(sun, DeskPath.waiting(LocalDate.of(2026, 11, 25), emptySet()))
        assertNull(DeskPath.waiting(LocalDate.of(2026, 11, 25), setOf("2026-11-22")))
        assertEquals(LocalDate.of(2026, 11, 29), DeskPath.waiting(LocalDate.of(2026, 11, 29), setOf("2026-11-22")))
    }

    @Test
    fun `a week's change runs from the last close before it`() {
        val d = listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))
        val v = doubleArrayOf(100.0, 101.0, 103.0)
        assertEquals(0.03, DeskPath.change(d, v, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22))!!, 1e-12)
        assertNull(DeskPath.change(d, v, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)))
    }

    // --- the rule card ---

    private val prices = setOf("GLD", "SLV", "USDJPY", "TLT", "SPY")

    /** Tags and names aside, the same rule. */
    private fun same(a: Rule, b: Rule) {
        fun plain(r: Rule) = r.copy(name = "-", branches = r.branches.map { br ->
            br.copy(tag = "-", conditions = br.conditions.map { c -> if (c is Condition.Compare) c.copy(tag = "") else c })
        })
        assertEquals(plain(a), plain(b))
    }

    @Test
    fun `the shelf's rules go to a card and back`() {
        for (key in listOf("gold_trend", "gold_silver_ratio", "usdjpy_carry")) {
            val r = DeskStrategies.rule(key, DeskStrategies.template(key).defaults)
            val card = RuleCard.write(r)
            assertNotNull(key, card)
            val back = RuleCard.parse(card!!, prices)
            assertTrue("$key: ${back.problems}", back.problems.isEmpty())
            same(r, back.rule!!)
        }
        assertNull(RuleCard.write(DeskStrategies.rule("gld_covered_call", DeskStrategies.template("gld_covered_call").defaults)))
    }

    @Test
    fun `a Trader's card reads, bullets and bold and all`() {
        val reply = """
            Here's a first rule for your idea.

            - **Rule:** Gold when real rates fall
            - **When:** DFII10 63-day change below 0
            - **Then:** hold GLD 100%
            - **Otherwise:** cash
            - **Exit:** after 60 days
            - **Exit:** trailing stop 10%

            Check it before you test it.
        """.trimIndent()
        assertTrue(RuleCard.has(reply))
        val read = RuleCard.parse(reply, prices)
        assertTrue(read.problems.toString(), read.problems.isEmpty())
        val r = read.rule!!
        assertEquals("Gold when real rates fall", r.name)
        assertEquals(Condition.Compare(Signal.Change("DFII10", 63), Op.BELOW, Against.Value(0.0)), r.branches[0].conditions[0])
        assertEquals(Action.Hold(mapOf("GLD" to 1.0)), r.branches[0].action)
        assertEquals(listOf(Exit.After(60), Exit.Stop(0.10, trailing = true)), r.exits)
        assertEquals("GLD", r.benchmark)
        assertEquals("change", r.branches[0].tag)
    }

    @Test
    fun `rotations, shorts, crossings and percentiles read`() {
        val r = RuleCard.parse("""
            Rule: Strongest of three
            When: SPY crosses above its 50-day high
            And: SPY 14-day RSI at most 70 for 3 days
            Then: hold the best 1 of GLD, TLT, SPY by 120-day return
            Else when: GLD/SLV 60-day stretch above 2
            Then: short GLD 50%, SLV 50%
            Held: until an exit
            Otherwise: cash
            Exit: GLD/SLV 60-day stretch back within 0.5
            Check: weekly
            Compare with: SPY
        """.trimIndent(), prices).rule
        assertNotNull(r)
        r!!
        assertEquals(Condition.Compare(Signal.Price("SPY"), Op.CROSSES_ABOVE, Against.Of(Signal.High("SPY", 50))), r.branches[0].conditions[0])
        assertEquals(3, (r.branches[0].conditions[1] as Condition.Compare).days)
        assertEquals(Action.Rotate(listOf("GLD", "TLT", "SPY"), Signal.Momentum("*", 120), 1), r.branches[0].action)
        assertEquals(Action.Hold(mapOf("GLD" to -0.5, "SLV" to 0.5)), r.branches[1].action)
        assertTrue(r.branches[1].until)
        assertEquals(Check.WEEKLY, r.check)
        val pct = RuleCard.parse("Rule: x\nWhen: GVZ at most its 80th percentile over 252 days (to enter)\nAnd: GLD above its 200-day average\nThen: hold GLD", prices).rule!!
        assertEquals(Condition.Compare(Signal.Series("GVZ"), Op.AT_MOST, Against.Percentile(0.8, 252, 126), enterOnly = true), pct.branches[0].conditions[0])
    }

    @Test
    fun `a card with a line it can't read gives no rule, and says which`() {
        val read = RuleCard.parse("Rule: Moon\nWhen: GLD feels strong\nThen: hold GLD 100%", prices)
        assertNull(read.rule)
        assertTrue(read.problems.single().contains("GLD feels strong"))
        // Within the limits too: 5,000 days is refused, not clamped.
        assertNull(RuleCard.parse("Rule: Long\nWhen: GLD above its 5000-day average\nThen: hold GLD", prices).rule)
        assertNull(RuleCard.parse("Rule: Bad ticker\nWhen: GLD above 1\nThen: hold GLD;DROP 100%", prices).rule)
        assertFalse(RuleCard.has("Just some words: about gold"))
        // A card is read only so far: a huge reply is cut, not followed.
        assertTrue(RuleCard.parse("Rule: x\n" + "When: GLD above 1\n".repeat(5000) + "Then: hold GLD", prices).rule == null)
    }

    // --- tonight, in words ---

    private class Lcg(var x: Long) {
        fun u(): Double { x = (1103515245L * x + 12345L) % 2147483648L; return x / 2147483648.0 }
        fun g(): Double = (u() + u() + u() + u() - 2.0) * sqrt(3.0)
    }

    @Test
    fun `tonight's conditions come with their numbers`() {
        val days = ArrayList<LocalDate>()
        var d = LocalDate.of(2015, 1, 2)
        while (days.size < 900) { if (d.dayOfWeek.value <= 5) days += d; d = d.plusDays(1) }
        val rng = Lcg(99L)
        var acc = 0.0
        val close = DoubleArray(days.size) { acc += 0.0004 + 0.01 * rng.g(); 100 * exp(acc) }
        val bars = Bars(days, DoubleArray(days.size) { if (it == 0) 100.0 else close[it - 1] }, close)
        val line = Line(days, DoubleArray(days.size) { 0.02 })
        val data = DeskData.assemble(linkedMapOf("GLD" to bars), line, line, Line(days, DoubleArray(days.size) { 0.15 }))
        val r = DeskStrategies.rule("gold_trend", DeskStrategies.template("gold_trend").defaults)
        val today = RuleEngine.today(r, data)
        assertEquals(2, today.size)
        assertTrue(today[0].text, today[0].text.startsWith("GLD closes above its 200-day average · now "))
        val avg = close.takeLast(200).average()
        assertEquals(close.last() > avg, today[0].met)
        assertTrue(RuleEngine.today(r.copy(branches = listOf(Branch("x", listOf(Condition.Compare(Signal.Series("NOPE"), Op.ABOVE, Against.Value(0.0))),
            Action.Hold(mapOf("GLD" to 1.0))))), data).isEmpty())
    }

    @Test
    fun `every exit, sizing and action the card forms offer reads`() {
        val forms = RuleCard.FORMS.lines()
        fun options(label: String) = forms.first { it.startsWith("$label: ") }.removePrefix("$label: ")
            .substringBefore("  (").split(" | ").map { it.substringBefore(" (").trim() }
        val base = "Rule: x\nWhen: GLD above its 200-day average\nThen: hold GLD 100%\nHeld: until an exit\nOtherwise: cash\nExit: stop 3 ATR\n"
        for (e in options("Exit")) {
            val read = RuleCard.parse(base + "Exit: $e", prices)
            assertTrue("Exit: $e ${read.problems}", read.rule != null && read.problems.isEmpty())
        }
        for (z in options("Size")) {
            val read = RuleCard.parse(base + "Size: $z", prices)
            assertTrue("Size: $z ${read.problems}", read.rule != null && read.problems.isEmpty())
        }
        val short = RuleCard.parse(base.replace("hold GLD 100%", "short GLD 100%"), prices).rule!!
        assertEquals(Action.Hold(mapOf("GLD" to -1.0)), short.branches[0].action)
    }
}
