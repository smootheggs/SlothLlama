package app.localmind.assistant.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.projects.Brief
import app.localmind.assistant.domain.projects.Decision
import app.localmind.assistant.domain.projects.Glance
import app.localmind.assistant.domain.projects.Milestone
import app.localmind.assistant.domain.projects.Phase
import app.localmind.assistant.domain.projects.Project
import app.localmind.assistant.domain.projects.Projects
import app.localmind.assistant.domain.projects.Risk
import app.localmind.assistant.domain.projects.Stakeholder
import app.localmind.assistant.domain.projects.StatusUpdate
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.ConfirmDialog
import app.localmind.assistant.ui.common.EmptyLine
import app.localmind.assistant.ui.common.ItemRow
import app.localmind.assistant.ui.common.NameDialog
import app.localmind.assistant.ui.common.Section
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate

/** What's open over the page. */
private sealed interface Editing {
    data object Rename : Editing
    data object DeleteProject : Editing
    data object EditBrief : Editing
    data object WriteUp : Editing
    data object NewUpdate : Editing
    data class OneMilestone(val m: Milestone) : Editing
    data class OneRisk(val r: Risk) : Editing
    data class OnePerson(val s: Stakeholder) : Editing
    data class OneDecision(val d: Decision) : Editing
    data class OneUpdate(val u: StatusUpdate) : Editing
}

/**
 * One project: its phase, what the code counted, the three things you can do (write it up, grill
 * the plan, write an update), then the record in sections. Two columns on a wide screen.
 */
@Composable
internal fun ProjectPage(p: Project, vm: ProjectsViewModel, today: LocalDate, onBack: () -> Unit) {
    val g = remember(p, today) { Projects.glance(p, today) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var closedShown by remember { mutableStateOf(false) }
    var allUpdates by remember { mutableStateOf(false) }

    val left: @Composable () -> Unit = {
        BriefSection(p.brief) { editing = Editing.EditBrief }
        MilestonesSection(p, g, today, onAdd = { editing = Editing.OneMilestone(Milestone("", "")) }, onEdit = { editing = Editing.OneMilestone(it) }, onToggle = vm::toggleMilestone)
        PeopleSection(p.stakeholders, onAdd = { editing = Editing.OnePerson(Stakeholder("", "")) }, onEdit = { editing = Editing.OnePerson(it) })
    }
    val right: @Composable () -> Unit = {
        UpdatesSection(p.updates, allUpdates, onMore = { allUpdates = !allUpdates }, onAdd = { editing = Editing.NewUpdate }, onOpen = { editing = Editing.OneUpdate(it) })
        RisksSection(p, closedShown, onShowClosed = { closedShown = !closedShown }, onAdd = { editing = Editing.OneRisk(Risk("", "")) }, onEdit = { editing = Editing.OneRisk(it) })
        DecisionsSection(p.decisions, onAdd = { editing = Editing.OneDecision(Decision("", "", "")) }, onEdit = { editing = Editing.OneDecision(it) })
        DocsSection(p, onWriteUp = { editing = Editing.WriteUp }, onOpen = vm::openDoc)
    }

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = p.name,
            subtitle = p.phase.label + " · " + (p.status?.status?.label ?: "no status yet") + if (p.archived) " · archived" else "",
            onBack = onBack
        ) {
            IconButton(onClick = { editing = Editing.Rename }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = P.Text) }
            IconButton(onClick = {
                vm.setArchived(p.id, !p.archived)
                if (!p.archived) onBack()
            }) {
                Icon(if (p.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = if (p.archived) "Bring back" else "Archive", tint = P.Text)
            }
            IconButton(onClick = { editing = Editing.DeleteProject }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete the project", tint = P.Text) }
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 900.dp
            Column(
                Modifier.widthIn(max = 1280.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhaseStrip(p.phase, vm::setPhase)
                GlanceRow(g, today)
                ActionRow(onWriteUp = { editing = Editing.WriteUp }, onGrill = vm::grill, onUpdate = { editing = Editing.NewUpdate })
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { left() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { right() }
                    }
                } else {
                    left()
                    right()
                }
                Text(
                    "On this tablet and in backups. Counts, scores and dates are worked out here; a write-up only puts them into words.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }

    val close = { editing = null }
    when (val e = editing) {
        Editing.Rename -> NameDialog("Rename the project", p.name, onDismiss = close, onSave = vm::rename, maxLength = Projects.MAX_NAME)
        Editing.DeleteProject -> ConfirmDialog(
            "Delete ${p.name}?", "Its record and documents go too. A backup made before keeps a copy.", "Delete",
            onDismiss = close, onConfirm = { vm.delete(p.id) }
        )
        Editing.EditBrief -> BriefDialog(p.brief, onDismiss = close, onSave = vm::saveBrief)
        Editing.WriteUp -> WriteUpDialog(p.phase, onDismiss = close, onPick = vm::writeUp)
        Editing.NewUpdate -> UpdateDialog(p.status?.status, onDismiss = close, onSave = vm::addUpdate)
        is Editing.OneMilestone -> MilestoneDialog(e.m, onDismiss = close, onSave = vm::saveMilestone, onDelete = if (e.m.id.isBlank()) null else ({ vm.deleteMilestone(e.m.id) }))
        is Editing.OneRisk -> RiskDialog(
            e.r, onDismiss = close, onSave = vm::saveRisk,
            onDelete = if (e.r.id.isBlank()) null else ({ vm.deleteRisk(e.r.id) }),
            onClose = if (e.r.id.isBlank()) null else ({ closing: Boolean -> vm.closeRisk(e.r.id, closing) })
        )
        is Editing.OnePerson -> StakeholderDialog(e.s, onDismiss = close, onSave = vm::saveStakeholder, onDelete = if (e.s.id.isBlank()) null else ({ vm.deleteStakeholder(e.s.id) }))
        is Editing.OneDecision -> DecisionDialog(e.d, onDismiss = close, onSave = vm::saveDecision, onDelete = if (e.d.id.isBlank()) null else ({ vm.deleteDecision(e.d.id) }))
        is Editing.OneUpdate -> ConfirmDialog(
            "Delete this update?", "${Projects.day(e.u.on)} · ${e.u.status.label}: ${e.u.text}", "Delete",
            onDismiss = close, onConfirm = { vm.deleteUpdate(e.u.id) }
        )
        null -> Unit
    }
}

/** The five phases as one line: done ones filled, yours marked; tap to move. */
@Composable
private fun PhaseStrip(phase: Phase, onPick: (Phase) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Phase.entries.forEach { ph ->
                val reached = ph.ordinal <= phase.ordinal
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onPick(ph) }.padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (reached) P.Accent else P.Elevated))
                    Text(
                        ph.label, color = if (ph == phase) P.Accent else if (reached) P.Muted else P.Faint,
                        fontSize = 13.sp, fontWeight = if (ph == phase) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(phase.what, color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun Fact(value: String, label: String, color: Color = P.Text) {
    Column(
        Modifier.widthIn(min = 150.dp, max = 260.dp).clip(RoundedCornerShape(12.dp)).background(P.Card).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = P.Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** What the code counted, at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlanceRow(g: Glance, today: LocalDate) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Fact(if (g.total == 0) "—" else "${g.done} of ${g.total}", if (g.total == 0) "no milestones yet" else "milestones done · ${g.percent}%")
        val nextDue = g.next?.let { Projects.date(it.due) }
        Fact(nextDue?.let { Projects.relative(it, today) } ?: if (g.next != null) "No date" else "—", g.next?.let { "next: ${it.title}" } ?: "no milestone ahead")
        if (g.overdue.isNotEmpty()) Fact("${g.overdue.size}", if (g.overdue.size == 1) "milestone late" else "milestones late", P.Warning)
        Fact("${g.openRisks}", "open risks" + if (g.highRisks > 0) " · ${g.highRisks} high" else "", if (g.highRisks > 0) P.Warning else P.Text)
        g.deadlineIn?.let { d -> Fact(if (d >= 0) "$d days" else "${-d} days ago", if (d >= 0) "to the deadline" else "the deadline passed", if (d < 0) P.Warning else P.Text) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(onWriteUp: () -> Unit, onGrill: () -> Unit, onUpdate: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onWriteUp, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Write it up")
        }
        OutlinedButton(onClick = onGrill) {
            Icon(Icons.Outlined.QuestionAnswer, contentDescription = null, tint = P.Text, modifier = Modifier.size(18.dp))
            Text("  Grill the plan", color = P.Text)
        }
        OutlinedButton(onClick = onUpdate) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = P.Text, modifier = Modifier.size(18.dp))
            Text("  Write an update", color = P.Text)
        }
    }
}

@Composable
private fun BriefSection(b: Brief, onEdit: () -> Unit) {
    val fields = listOf(
        "Goal" to b.goal, "Sponsor" to b.sponsor, "In scope" to b.scope, "Out of scope" to b.outOfScope,
        "Constraints" to b.constraints, "Success measures" to b.success, "Deadline" to Projects.day(b.deadline)
    )
    val written = fields.filter { it.second.isNotBlank() }
    val missing = fields.filter { it.second.isBlank() }.map { it.first.lowercase() }
    Section("Brief", onAdd = onEdit, addLabel = "Edit") {
        if (written.isEmpty()) EmptyLine("The goal, who it's for, what's in and out, the limits, how you'll know it worked, and the deadline.")
        written.forEach { (label, value) ->
            Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = P.Faint, fontSize = 12.sp)
                Text(value, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        if (written.isNotEmpty() && missing.isNotEmpty()) {
            Text("Not written yet: " + missing.joinToString(", "), color = P.Faint, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 2.dp))
        }
    }
}

@Composable
private fun MilestonesSection(p: Project, g: Glance, today: LocalDate, onAdd: () -> Unit, onEdit: (Milestone) -> Unit, onToggle: (String) -> Unit) {
    Section("Milestones", note = if (g.total > 0) "${g.done} of ${g.total} done · ${g.percent}%" else null, onAdd = onAdd) {
        if (p.milestones.isEmpty()) EmptyLine("The points that show it's moving: each one done or not, with a date.")
        val late = g.overdue.map { it.id }.toSet()
        Projects.ordered(p.milestones).forEach { m ->
            val due = Projects.date(m.due)
            ItemRow(
                title = m.title,
                line = when {
                    m.done -> "Done ${Projects.day(m.doneOn)}"
                    due == null -> "No date"
                    else -> "${Projects.day(due)} · ${Projects.relative(due, today)}"
                },
                lineColor = if (m.id in late) P.Warning else P.Muted,
                struck = m.done,
                onClick = { onEdit(m) },
                lead = {
                    IconButton(onClick = { onToggle(m.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (m.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (m.done) "Mark not done" else "Mark done",
                            tint = if (m.done) P.Success else P.Muted
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ScoreMark(score: Int) {
    val band = Projects.band(score)
    val color = when (band) {
        "High" -> P.Warning
        "Medium" -> P.Muted
        else -> P.Faint
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(band, color = color, fontSize = 12.sp)
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("$score", color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RisksSection(p: Project, closedShown: Boolean, onShowClosed: () -> Unit, onAdd: () -> Unit, onEdit: (Risk) -> Unit) {
    val live = Projects.openRisks(p)
    val closed = p.risks.filter { !it.open }
    Section("Risks", note = if (p.risks.isEmpty()) null else "Score = likelihood × impact, 1 to 9, worked out here", onAdd = onAdd) {
        if (p.risks.isEmpty()) EmptyLine("What might go wrong, how likely, how bad, and what you'll do about it.")
        live.forEach { r ->
            ItemRow(
                title = r.title,
                line = listOf(r.owner.ifBlank { "No owner" }, r.response).filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { onEdit(r) },
                trail = { ScoreMark(r.score) }
            )
        }
        if (closed.isNotEmpty()) {
            TextButton(onClick = onShowClosed) {
                Text(if (closedShown) "Hide closed" else "${closed.size} closed", color = P.Muted, fontSize = 13.sp)
            }
            if (closedShown) closed.forEach { r -> ItemRow(r.title, "Closed ${Projects.day(r.closedOn)}", onClick = { onEdit(r) }, struck = true) }
        }
    }
}

@Composable
private fun PeopleSection(people: List<Stakeholder>, onAdd: () -> Unit, onEdit: (Stakeholder) -> Unit) {
    Section("Stakeholders", onAdd = onAdd) {
        if (people.isEmpty()) EmptyLine("Who has a say or a stake; the grid sorts them into who to manage closely and who to keep informed.")
        Projects.APPROACHES.forEach { a ->
            val these = people.filter { it.approach == a }
            if (these.isNotEmpty()) {
                Text(a, color = P.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                these.forEach { s -> ItemRow(s.name, s.role, onClick = { onEdit(s) }) }
            }
        }
    }
}

@Composable
private fun DecisionsSection(decisions: List<Decision>, onAdd: () -> Unit, onEdit: (Decision) -> Unit) {
    Section("Decisions", onAdd = onAdd) {
        if (decisions.isEmpty()) EmptyLine("What was decided, when and why, so nobody has to remember.")
        decisions.sortedByDescending { it.on }.forEach { d ->
            ItemRow(d.title, listOf(Projects.day(d.on), d.why).filter { it.isNotBlank() }.joinToString(" · "), onClick = { onEdit(d) })
        }
    }
}

@Composable
private fun UpdatesSection(updates: List<StatusUpdate>, all: Boolean, onMore: () -> Unit, onAdd: () -> Unit, onOpen: (StatusUpdate) -> Unit) {
    Section("Updates", note = if (updates.isEmpty()) null else "Newest first; the latest sets the status", onAdd = onAdd, addLabel = "Write") {
        if (updates.isEmpty()) EmptyLine("A few lines each week and your call on how it's going. Status reports are made from these.")
        (if (all) updates else updates.take(3)).forEach { u ->
            ItemRow(
                title = u.text,
                line = "${Projects.day(u.on)} · ${u.status.label}",
                lineColor = ragColor(u.status),
                onClick = { onOpen(u) },
                lead = { Box(Modifier.padding(horizontal = 6.dp).size(10.dp).clip(CircleShape).background(ragColor(u.status))) }
            )
        }
        if (updates.size > 3) TextButton(onClick = onMore) { Text(if (all) "Fewer" else "All ${updates.size}", color = P.Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun DocsSection(p: Project, onWriteUp: () -> Unit, onOpen: (String) -> Unit) {
    Section("Documents", note = if (p.docs.isEmpty()) null else "Built here, or kept from a chat", onAdd = onWriteUp, addLabel = "Write it up") {
        if (p.docs.isEmpty()) EmptyLine("Charters, status reports, registers and decision records you make land here.")
        p.docs.forEach { d ->
            ItemRow(
                title = d.title,
                line = d.kind.label + if (d.kind.byCode) " · built here" else "",
                onClick = { onOpen(d.id) },
                lead = { Icon(Icons.Outlined.Description, contentDescription = null, tint = P.Muted, modifier = Modifier.padding(horizontal = 4.dp).size(22.dp)) }
            )
        }
    }
}
