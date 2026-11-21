package app.localmind.assistant.ui.projects

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.projects.DocKind
import app.localmind.assistant.domain.projects.Phase
import app.localmind.assistant.domain.projects.Project
import app.localmind.assistant.domain.projects.ProjectDoc
import app.localmind.assistant.domain.projects.Projects
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/**
 * Projects: the list, one project's page, and a document from it. Back steps out one level at a
 * time (document → project → list) before leaving.
 */
@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel, onBack: () -> Unit) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val openId by viewModel.openId.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val docId by viewModel.docId.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    BackHandler(enabled = openId != null) {
        if (docId != null) viewModel.openDoc(null) else viewModel.openProject(null)
    }

    val project = open
    val doc = project?.docs?.firstOrNull { it.id == docId }
    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        when {
            project != null && doc != null -> DocPage(
                project, doc,
                onBack = { viewModel.openDoc(null) },
                onRename = { viewModel.renameDoc(doc.id, it) },
                onDelete = { viewModel.deleteDoc(doc.id) }
            )
            project != null -> ProjectPage(project, viewModel, today, onBack = { viewModel.openProject(null) })
            else -> ProjectList(projects, today, onBack, onOpen = viewModel::openProject, onCreate = { viewModel.create(it) })
        }
    }
}

@Composable
private fun ProjectList(
    projects: List<Project>,
    today: LocalDate,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    BackHandler(enabled = showArchived) { showArchived = false }
    val archived = projects.count { it.archived }
    val shown = projects.filter { it.archived == showArchived }

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = if (showArchived) "Archived projects" else "Projects",
            subtitle = "On this tablet · in backups",
            onBack = if (showArchived) ({ showArchived = false }) else onBack
        ) {
            if (archived > 0 || showArchived) {
                TextButton(onClick = { showArchived = !showArchived }) {
                    Text(if (showArchived) "Current" else "Archived · $archived", color = P.Muted, fontSize = 14.sp)
                }
            }
            if (!showArchived) {
                TextButton(onClick = { naming = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = P.Accent, modifier = Modifier.size(18.dp))
                    Text(" New project", color = P.Accent, fontSize = 14.sp)
                }
            }
        }
        if (shown.isEmpty()) {
            if (showArchived) EmptyLine("Nothing archived.") else NoProjects(onNew = { naming = true })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(340.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.id }) { p -> ProjectCard(p, today) { onOpen(p.id) } }
            }
        }
    }
    if (naming) NameDialog("New project", "", onDismiss = { naming = false }, onSave = onCreate, saveLabel = "Create")
}

@Composable
private fun NoProjects(onNew: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).padding(horizontal = 28.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Keep a project here", color = P.Text, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Text(
                "Its record lives on this tablet: the brief, milestones, risks, the people, decisions and your updates. " +
                    "The tablet does the counting and the dates.",
                color = P.Muted, fontSize = 16.sp, lineHeight = 24.sp
            )
            Text(
                "Write it up hands that record to the Scribe for a charter, a status report or a closeout, with anything " +
                    "you haven't written left as a [bracket]. The Grill interviews you about the plan, one question at a time, " +
                    "and writes the decision record. Keep the reply and it's filed with the project.",
                color = P.Muted, fontSize = 16.sp, lineHeight = 24.sp
            )
            Button(
                onClick = onNew,
                colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent),
                modifier = Modifier.padding(top = 6.dp)
            ) { Text("New project") }
        }
    }
}

@Composable
private fun ProjectCard(p: Project, today: LocalDate, onClick: () -> Unit) {
    val g = remember(p, today) { Projects.glance(p, today) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(p.name, color = P.Text, fontSize = 20.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel(p.phase.label, Modifier.weight(1f))
            StatusMark(g.status?.status)
        }
        if (g.total > 0) {
            ProgressBar(g.percent, Modifier.padding(top = 4.dp))
            Text("${g.done} of ${g.total} milestones · ${g.percent}%", color = P.Faint, fontSize = 12.sp)
        }
        g.next?.let { n ->
            val due = Projects.date(n.due)
            Text("Next: ${n.title}" + (due?.let { " · ${Projects.relative(it, today)}" } ?: ""), color = P.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val alerts = listOfNotNull(
            g.overdue.size.takeIf { it > 0 }?.let { if (it == 1) "1 milestone late" else "$it milestones late" },
            g.highRisks.takeIf { it > 0 }?.let { if (it == 1) "1 high risk" else "$it high risks" }
        )
        if (alerts.isNotEmpty()) Text(alerts.joinToString(" · "), color = P.Warning, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        g.deadlineIn?.let { d ->
            Text(deadlineText(d), color = if (d < 0) P.Warning else P.Faint, fontSize = 13.sp)
        }
    }
}

internal fun deadlineText(days: Long): String = when {
    days > 1 -> "Deadline in $days days"
    days == 1L -> "Deadline tomorrow"
    days == 0L -> "Deadline today"
    days == -1L -> "Deadline passed yesterday"
    else -> "Deadline passed ${-days} days ago"
}

/** The documents for this phase first: built ones are made here; the rest open a chat with the Scribe. */
@Composable
internal fun WriteUpDialog(phase: Phase, onDismiss: () -> Unit, onPick: (DocKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text("Write it up", color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "The Scribe gets a facts sheet made here from your record, with the counts and dates already done. " +
                        "What you haven't written stays a [bracket]. Keep its reply with the button under it.",
                    color = P.Faint, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 8.dp)
                )
                Projects.suggested(phase).forEachIndexed { i, kind ->
                    if (i == 3) SmallLabel("More", Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(kind); onDismiss() }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(kind.label, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(kind.what, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        Text(if (kind.byCode) "Built here" else "Scribe", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = P.Muted) } }
    )
}

@Composable
private fun DocPage(p: Project, doc: ProjectDoc, onBack: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = doc.title,
            subtitle = "${p.name} · " + if (doc.kind.byCode) "built on the tablet" else "kept from a chat",
            onBack = onBack
        ) {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = P.Text) }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(doc.text))
                android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = P.Text) }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, doc.title)
                    putExtra(Intent.EXTRA_TEXT, doc.text)
                }
                runCatching { context.startActivity(Intent.createChooser(send, "Share ${doc.title}")) }
            }) { Icon(Icons.Outlined.Share, contentDescription = "Share", tint = P.Text) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = P.Text) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 820.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DocText(doc.text)
                Text(
                    "Made " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(doc.made)) + ". " +
                        if (doc.kind.byCode) "Built from the record as it was then." else "Only what was in the facts sheet and the chat; check the [brackets] before you send it.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }
    }
    if (renaming) NameDialog("Rename", doc.title, onDismiss = { renaming = false }, onSave = onRename)
    if (deleting) ConfirmDialog("Delete this document?", doc.title, "Delete", onDismiss = { deleting = false }, onConfirm = onDelete)
}
