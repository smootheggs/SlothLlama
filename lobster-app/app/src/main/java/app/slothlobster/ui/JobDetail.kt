package app.slothlobster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slothlobster.core.From
import app.slothlobster.core.Job
import app.slothlobster.core.JobState
import app.slothlobster.core.SourceState
import app.slothlobster.mailbox.OrderKind

@Composable
fun JobDetail(
    job: Job?,
    now: Long,
    onBack: (() -> Unit)?,
    onCancel: (String) -> Unit,
    onSendNow: (String) -> Unit,
    onAgain: (String) -> Unit,
    onRemove: (String) -> Unit,
    catchId: String? = null,
    onRead: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (job == null) {
        Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pick an order to see its sources and what came back.", color = Sea.Muted, fontSize = 14.sp)
        }
        return
    }
    val kind = OrderKind.of(job.kind)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = job.text.lineSequence().firstOrNull().orEmpty().take(120).ifBlank { kind.label },
            subtitle = "${kind.label} · ${if (job.from == From.TABLET) "from SlothLlama" else "yours"} · " +
                "ordered ${ago(job.createdAt, now)} · free job",
            onBack = onBack
        ) { Box(Modifier.padding(start = 8.dp)) { StatePill(job) } }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("The order") {
                Panel {
                    SelectionContainer {
                        Text(job.text, modifier = Modifier.padding(16.dp), color = Sea.Text, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
            if (job.sources.isNotEmpty()) {
                Section("Sources") {
                    Panel {
                        job.sources.forEachIndexed { i, s -> SourceRow(s, divider = i < job.sources.size - 1) }
                    }
                }
            }
            if (job.resultPreview != null) {
                Section("Brought back") {
                    Panel {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            job.resultTitle?.let { Text(it, color = Sea.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                            SelectionContainer {
                                Text(job.resultPreview, color = Sea.Soft, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                            if (catchId != null) GhostButton("Read it all", { onRead(catchId) })
                        }
                    }
                }
            }
            job.note?.let { note ->
                Panel {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(LobsterIcons.Info, null, tint = Sea.Muted, modifier = Modifier.size(20.dp))
                        Text(note, color = Sea.Muted, fontSize = 13.sp)
                    }
                }
            }
            if (kind == OrderKind.RESEARCH || kind == OrderKind.FETCH) {
                Panel {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(LobsterIcons.Info, null, tint = Sea.Muted, modifier = Modifier.size(20.dp))
                        Text(
                            "The Lobster gathers the text only. SlothLlama reads it offline and writes the answer, citing each source.",
                            color = Sea.Muted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (job.state) {
                    JobState.WORKING -> {
                        if (kind == OrderKind.RESEARCH && !job.sendNow) PrimaryButton("Send what's done now", { onSendNow(job.id) })
                        GhostButton("Cancel order", { onCancel(job.id) })
                    }
                    JobState.QUEUED -> GhostButton("Cancel order", { onCancel(job.id) })
                    else -> {
                        PrimaryButton("Run again", { onAgain(job.id) }, icon = LobsterIcons.Refresh)
                        GhostButton("Remove", { onRemove(job.id) }, icon = LobsterIcons.Trash)
                    }
                }
            }
        }
    }
}

@Composable
fun StatePill(job: Job) {
    when (job.state) {
        JobState.WORKING -> Pill("Working", hot = true)
        JobState.QUEUED -> Pill("Queued")
        JobState.DONE -> Pill("Done")
        else -> Pill(if (job.note == "Cancelled") "Cancelled" else "Stopped")
    }
}

@Composable
private fun SourceRow(s: SourceState, divider: Boolean) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when (s.state) {
                    "done" -> Icon(LobsterIcons.Check, contentDescription = "Read", tint = Sea.Coral, modifier = Modifier.size(20.dp))
                    "failed" -> Icon(LobsterIcons.Close, contentDescription = "Couldn't read", tint = Sea.Faint, modifier = Modifier.size(18.dp))
                    else -> Box(
                        Modifier
                            .size(12.dp)
                            .border(2.dp, if (s.state == "reading") Sea.Coral else Sea.Off, CircleShape)
                            .semantics { contentDescription = if (s.state == "reading") "Reading" else "Waiting" }
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(s.title, color = if (s.state == "failed") Sea.Faint else Sea.Text, fontSize = 14.sp, maxLines = 2)
                Text(s.where, color = Sea.Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (divider) Divider()
    }
}
