package app.localmind.assistant.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.projects.Brief
import app.localmind.assistant.domain.projects.Decision
import app.localmind.assistant.domain.projects.Milestone
import app.localmind.assistant.domain.projects.Projects
import app.localmind.assistant.domain.projects.Rag
import app.localmind.assistant.domain.projects.Risk
import app.localmind.assistant.domain.projects.Stakeholder
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.DateField
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.common.Toggle
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.Instant
import java.time.ZoneOffset
/** Low / medium / high, as 1 to 3. */
@Composable
private fun LevelPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = P.Muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Low", 2 to "Medium", 3 to "High").forEach { (n, name) -> ChoicePill(name, value == n, { onChange(n) }) }
        }
    }
}

@Composable
internal fun BriefDialog(brief: Brief, onDismiss: () -> Unit, onSave: (Brief) -> Unit) {
    var b by remember { mutableStateOf(brief) }
    EditDialog("The brief", onDismiss, { onSave(b); onDismiss() }) {
        Text("Leave out what you don't know yet: write-ups keep it as a [bracket].", color = P.Faint, fontSize = 13.sp, lineHeight = 18.sp)
        FormField("Goal", b.goal, { b = b.copy(goal = it) }, lines = 2, hint = "What will be true when it's done?")
        FormField("Sponsor", b.sponsor, { b = b.copy(sponsor = it) }, hint = "Who it's for, who signs it off")
        FormField("In scope", b.scope, { b = b.copy(scope = it) }, lines = 2)
        FormField("Out of scope", b.outOfScope, { b = b.copy(outOfScope = it) }, lines = 2)
        FormField("Constraints", b.constraints, { b = b.copy(constraints = it) }, lines = 2, hint = "Budget, people, rules, dates you can't move")
        FormField("Success measures", b.success, { b = b.copy(success = it) }, lines = 2, hint = "How you'll know it worked")
        DateField("Deadline", b.deadline) { b = b.copy(deadline = it) }
    }
}

@Composable
internal fun MilestoneDialog(m: Milestone, onDismiss: () -> Unit, onSave: (Milestone) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(m.title) }
    var due by remember { mutableStateOf(m.due) }
    EditDialog(
        if (m.id.isBlank()) "New milestone" else "Milestone", onDismiss, { onSave(m.copy(title = title, due = due)); onDismiss() },
        canSave = title.isNotBlank(), onDelete = onDelete?.let { d -> { d(); onDismiss() } }
    ) {
        FormField("What's done when it's reached", title, { title = it })
        DateField("Due", due) { due = it }
        if (m.done) Text("Done ${Projects.day(m.doneOn)}", color = P.Success, fontSize = 13.sp)
    }
}

@Composable
internal fun RiskDialog(r: Risk, onDismiss: () -> Unit, onSave: (Risk) -> Unit, onDelete: (() -> Unit)?, onClose: ((Boolean) -> Unit)?) {
    var risk by remember { mutableStateOf(r) }
    EditDialog(
        if (r.id.isBlank()) "New risk" else "Risk", onDismiss, { onSave(risk); onDismiss() },
        canSave = risk.title.isNotBlank(), onDelete = onDelete?.let { d -> { d(); onDismiss() } }
    ) {
        FormField("What might happen", risk.title, { risk = risk.copy(title = it) }, lines = 2)
        LevelPicker("How likely", risk.likelihood) { risk = risk.copy(likelihood = it) }
        LevelPicker("How bad if it does", risk.impact) { risk = risk.copy(impact = it) }
        Text(
            "Score ${risk.score} of 9 · ${Projects.band(risk.score).lowercase()}",
            color = if (risk.score >= 6) P.Warning else P.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
        FormField("Owner", risk.owner, { risk = risk.copy(owner = it) })
        FormField("Response", risk.response, { risk = risk.copy(response = it) }, lines = 2, hint = "What you'll do to make it less likely or less bad")
        if (onClose != null) {
            TextButton(onClick = { onClose(r.open); onDismiss() }) {
                Text(if (r.open) "Close this risk" else "Reopen this risk", color = P.Accent)
            }
        }
    }
}

@Composable
internal fun StakeholderDialog(s: Stakeholder, onDismiss: () -> Unit, onSave: (Stakeholder) -> Unit, onDelete: (() -> Unit)?) {
    var who by remember { mutableStateOf(s) }
    EditDialog(
        if (s.id.isBlank()) "New stakeholder" else "Stakeholder", onDismiss, { onSave(who); onDismiss() },
        canSave = who.name.isNotBlank(), onDelete = onDelete?.let { d -> { d(); onDismiss() } }
    ) {
        FormField("Name or group", who.name, { who = who.copy(name = it) })
        FormField("Role", who.role, { who = who.copy(role = it) }, hint = "Sponsor, user, supplier…")
        Toggle("Can change the outcome a lot", who.highInfluence) { who = who.copy(highInfluence = it) }
        Toggle("Cares a lot how it goes", who.highInterest) { who = who.copy(highInterest = it) }
        Text(who.approach, color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DecisionDialog(d: Decision, onDismiss: () -> Unit, onSave: (Decision) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(d.title) }
    var why by remember { mutableStateOf(d.why) }
    var on by remember { mutableStateOf(d.on) }
    EditDialog(
        if (d.id.isBlank()) "New decision" else "Decision", onDismiss, { onSave(d.copy(title = title, why = why, on = on)); onDismiss() },
        canSave = title.isNotBlank(), onDelete = onDelete?.let { x -> { x(); onDismiss() } }
    ) {
        FormField("What was decided", title, { title = it }, lines = 2)
        FormField("Why", why, { why = it }, lines = 3)
        DateField("Decided on", on) { on = it }
    }
}

@Composable
internal fun UpdateDialog(last: Rag?, onDismiss: () -> Unit, onSave: (Rag, String) -> Unit) {
    var status by remember { mutableStateOf(last ?: Rag.ON_TRACK) }
    var text by remember { mutableStateOf("") }
    EditDialog("Update", onDismiss, { onSave(status, text); onDismiss() }, canSave = text.isNotBlank()) {
        Text("How is it going? Your call: the app never sets this.", color = P.Faint, fontSize = 13.sp, lineHeight = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rag.entries.forEach { r -> ChoicePill(r.label, status == r, { status = r }, color = ragColor(r)) }
        }
        FormField("What happened, what's next", text, { text = it }, lines = 4)
    }
}
