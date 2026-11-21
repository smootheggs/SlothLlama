package app.localmind.assistant

import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.projects.Brief
import app.localmind.assistant.domain.projects.Decision
import app.localmind.assistant.domain.projects.DocKind
import app.localmind.assistant.domain.projects.Milestone
import app.localmind.assistant.domain.projects.Phase
import app.localmind.assistant.domain.projects.Project
import app.localmind.assistant.domain.projects.Projects
import app.localmind.assistant.domain.projects.Projects.upsert
import app.localmind.assistant.domain.projects.Rag
import app.localmind.assistant.domain.projects.Risk
import app.localmind.assistant.domain.projects.Stakeholder
import app.localmind.assistant.domain.projects.StatusUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.36: Projects, where the app counts and dates everything and a write-up only words it. */
class ProjectsUnitTests {

    private val today = LocalDate.of(2026, 11, 20)

    private fun sample() = Project(
        id = "p1", name = "Office move", createdAt = 1, updatedAt = 1, phase = Phase.EXECUTING,
        brief = Brief(goal = "Move the team to the new floor", deadline = "2027-01-31"),
        milestones = listOf(
            Milestone("m1", "Kick-off", due = "2026-11-02", doneOn = "2026-11-02"),
            Milestone("m2", "Vendor signed", due = "2026-11-15"),
            Milestone("m3", "Furniture order", due = "2026-12-01"),
            Milestone("m4", "Wi-fi survey"),
            Milestone("m5", "Move day", due = "2027-01-24")
        ),
        risks = listOf(
            Risk("r1", "Vendor late", likelihood = 2, impact = 3, owner = "Facilities"),
            Risk("r2", "Lift booking", likelihood = 1, impact = 2),
            Risk("r3", "Lease clause", likelihood = 3, impact = 3, closedOn = "2026-11-10")
        ),
        stakeholders = listOf(
            Stakeholder("s1", "Head of operations", highInfluence = true, highInterest = true),
            Stakeholder("s2", "Team", highInterest = true),
            Stakeholder("s3", "Landlord", highInfluence = true)
        ),
        decisions = listOf(Decision("d1", "2026-11-12", "Move in January", "Lease ends in February")),
        updates = listOf(StatusUpdate("u1", "2026-11-18", Rag.AT_RISK, "Vendor hasn't signed"))
    )

    @Test
    fun `risk scores are likelihood times impact, banded`() {
        assertEquals(6, Projects.score(2, 3))
        assertEquals(9, Projects.score(5, 7)) // out of range is held to 1..3
        assertEquals("High", Projects.band(6))
        assertEquals("Medium", Projects.band(4))
        assertEquals("Medium", Projects.band(3))
        assertEquals("Low", Projects.band(2))
    }

    @Test
    fun `the grid gives the four classic approaches`() {
        assertEquals("Manage closely", Projects.approach(highInfluence = true, highInterest = true))
        assertEquals("Keep satisfied", Projects.approach(highInfluence = true, highInterest = false))
        assertEquals("Keep informed", Projects.approach(highInfluence = false, highInterest = true))
        assertEquals("Monitor", Projects.approach(highInfluence = false, highInterest = false))
    }

    @Test
    fun `the glance counts progress, lateness and the next milestone`() {
        val g = Projects.glance(sample(), today)
        assertEquals(1, g.done)
        assertEquals(5, g.total)
        assertEquals(20, g.percent)
        assertEquals(listOf("m2"), g.overdue.map { it.id })
        assertEquals("m3", g.next?.id)
        assertEquals(2, g.openRisks)
        assertEquals(1, g.highRisks)
        assertEquals(Rag.AT_RISK, g.status?.status)
        assertEquals(72L, g.deadlineIn)
    }

    @Test
    fun `an undated milestone is next only when nothing dated is ahead`() {
        val p = sample().copy(milestones = listOf(Milestone("a", "Late one", due = "2026-11-01"), Milestone("b", "Some day")))
        assertEquals("b", Projects.next(p, today)?.id)
        assertNull(Projects.next(p.copy(milestones = emptyList()), today))
    }

    @Test
    fun `days read as people say them`() {
        assertEquals("today", Projects.relative(today, today))
        assertEquals("tomorrow", Projects.relative(today.plusDays(1), today))
        assertEquals("in 11 days", Projects.relative(LocalDate.of(2026, 12, 1), today))
        assertEquals("1 day late", Projects.relative(today.minusDays(1), today))
        assertEquals("5 days late", Projects.relative(LocalDate.of(2026, 11, 15), today))
        assertEquals("20 Nov 2026", Projects.day("2026-11-20"))
        assertEquals("", Projects.day(""))
    }

    @Test
    fun `the facts sheet carries the sums and brackets the gaps`() {
        val facts = Projects.factsSheet(sample(), today)
        assertTrue("Phase: Executing (3 of 5)" in facts)
        assertTrue("Status: At risk (set 18 Nov 2026)" in facts)
        assertTrue("Milestones (1 of 5 done, 20%)" in facts)
        assertTrue("LATE, due 15 Nov 2026 (5 days late): Vendor signed" in facts)
        assertTrue("Next milestone: Furniture order, 1 Dec 2026 (in 11 days)" in facts)
        assertTrue("Deadline: 31 Jan 2027 (in 72 days)" in facts)
        assertTrue("High (6): Vendor late" in facts)
        assertTrue("Owner: Facilities" in facts)
        assertTrue("Owner: [owner]" in facts) // the lift risk has no owner
        assertTrue("Sponsor: [sponsor]" in facts)
        assertTrue("Closed risks: 1" in facts)
        assertFalse("Lease clause" in facts) // closed risks aren't listed as open
        assertTrue("Team, [role]: keep informed" in facts)
    }

    @Test
    fun `an empty project still makes a sheet of gaps`() {
        val facts = Projects.factsSheet(Project("x", "Idea", 0, 0), today)
        assertTrue("Status: [status not set]" in facts)
        assertTrue("Goal: [goal]" in facts)
        assertTrue("Deadline: [deadline]" in facts)
        assertTrue("Milestones: [none written yet]" in facts)
    }

    @Test
    fun `the risk register and the map are built in code`() {
        val reg = Projects.riskRegister(sample(), today)
        assertTrue(reg.startsWith("# Risk register · Office move"))
        assertTrue(reg.indexOf("## High") < reg.indexOf("## Low"))
        assertTrue("**Vendor late** · 6" in reg)
        assertTrue("## Closed" in reg && "Lease clause · closed 10 Nov 2026" in reg)
        val map = Projects.stakeholderMap(sample(), today)
        assertTrue("## Manage closely" in map && "**Head of operations**" in map)
        assertTrue("## Keep satisfied" in map && "**Landlord**" in map)
        assertFalse("## Monitor" in map) // nobody there
        assertTrue(DocKind.RISKS.byCode && DocKind.STAKEHOLDERS.byCode && !DocKind.STATUS.byCode)
    }

    @Test
    fun `write-up prompts keep the bracket rule and the grill asks first`() {
        val p = sample()
        DocKind.entries.filter { !it.byCode && it != DocKind.DECISION }.forEach { k ->
            val prompt = Projects.prompt(k, p, today)
            assertTrue(k.name, "[square brackets]" in prompt)
            assertTrue(k.name, "Office move" in prompt)
        }
        assertTrue("week ending 20 Nov 2026" in Projects.prompt(DocKind.STATUS, p, today))
        assertTrue("Move the team to the new floor" in Projects.grillPrompt(p))
        assertTrue("[what I'm deciding or planning]" in Projects.grillPrompt(p.copy(brief = Brief())))
        assertEquals("Status report · 20 Nov 2026", Projects.docTitle(DocKind.STATUS, today))
    }

    @Test
    fun `the phase puts its documents first`() {
        assertEquals(DocKind.STATUS, Projects.suggested(Phase.EXECUTING).first())
        assertEquals(DocKind.CLOSEOUT, Projects.suggested(Phase.CLOSING).first())
        assertFalse(DocKind.DECISION in Projects.suggested(Phase.PLANNING))
        assertEquals(DocKind.entries.size - 1, Projects.suggested(Phase.INITIATING).toSet().size)
    }

    @Test
    fun `projects round-trip, and a backup only adds what's missing`() {
        val p = sample()
        assertEquals(listOf(p), Projects.parse(Projects.encode(listOf(p))))
        val mine = listOf(p.copy(name = "Mine"))
        val merged = Projects.merge(mine, listOf(p.copy(name = "Theirs"), p.copy(id = "p2")))
        assertEquals(listOf("Mine", "Office move"), merged.map { it.name })
        // An old file without the newer fields still reads.
        val old = """{"projects":[{"id":"a","name":"A","createdAt":1,"updatedAt":2}]}"""
        assertEquals(Phase.INITIATING, Projects.parse(old).single().phase)
    }

    @Test
    fun `upsert replaces by id or adds`() {
        val list = listOf(Milestone("a", "A"), Milestone("b", "B"))
        assertEquals(listOf("A", "B2"), list.upsert(Milestone("b", "B2")) { it.id }.map { it.title })
        assertEquals(3, list.upsert(Milestone("c", "C")) { it.id }.size)
    }

    @Test
    fun `the scribe and the grill are built in, and keep the bracket rule`() {
        assertTrue(BuiltInPersonas.all.any { it.id == BuiltInPersonas.SCRIBE.id })
        assertTrue(BuiltInPersonas.all.any { it.id == BuiltInPersonas.GRILL.id })
        assertTrue("square brackets" in BuiltInPersonas.SCRIBE.systemInstruction)
        assertTrue("One question per turn" in BuiltInPersonas.GRILL.systemInstruction)
    }
}
