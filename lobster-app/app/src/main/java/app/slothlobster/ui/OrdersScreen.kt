package app.slothlobster.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slothlobster.core.From
import app.slothlobster.core.Job
import app.slothlobster.core.JobState
import app.slothlobster.core.Saved
import app.slothlobster.mailbox.OrderKind
import app.slothlobster.web.Web

private enum class Filter(val label: String) { ALL("All"), OPEN("Open"), DONE("Done") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersScreen(
    saved: Saved,
    now: Long,
    wide: Boolean,
    selectedJob: String?,
    shared: String?,
    onSharedUsed: () -> Unit,
    onSend: (OrderKind, String, Boolean) -> Unit,
    onOpenJob: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableStateOf(OrderKind.RESEARCH.id) }
    var deep by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    // Something shared from another app: a link becomes a Fetch, words a Research.
    LaunchedEffect(shared) {
        if (shared != null) {
            text = shared
            kindId = if (Web.firstLink(shared) != null && shared.trim().split(Regex("\\s+")).size <= 3) OrderKind.FETCH.id else OrderKind.RESEARCH.id
            onSharedUsed()
        }
    }
    val kind = OrderKind.of(kindId)
    val jobs = saved.jobs.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.OPEN -> it.open
            Filter.DONE -> !it.open
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Orders", "From SlothLlama and from you") }
        item {
            Panel(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp),
                        label = { Text("New order") },
                        placeholder = { Text(placeholder(kind), color = Sea.Faint) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Sea.Coral,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFF2A2A30),
                            focusedLabelColor = Sea.Coral,
                            cursorColor = Sea.Coral,
                            focusedContainerColor = Sea.Canvas,
                            unfocusedContainerColor = Sea.Canvas
                        )
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OrderKind.RESEARCH, OrderKind.FETCH, OrderKind.WATCH, OrderKind.BRIEF).forEach { k ->
                            Chip(k.label, selected = kind == k, onClick = { kindId = k.id })
                        }
                        Chip("Ask · needs a model", selected = false, onClick = {}, enabled = false)
                    }
                    if (kind == OrderKind.RESEARCH) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Dig deeper", color = Sea.Soft, fontSize = 14.sp)
                                Text("About 9 sources instead of 5", color = Sea.Faint, fontSize = 12.sp)
                            }
                            CoralSwitch(deep, { deep = it }, "Dig deeper")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            hint(kind),
                            color = Sea.Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            "Send",
                            onClick = {
                                onSend(kind, text, deep && kind == OrderKind.RESEARCH)
                                text = ""
                                deep = false
                            },
                            enabled = text.isNotBlank() && (kind != OrderKind.FETCH || Web.firstLink(text) != null),
                            icon = LobsterIcons.Send
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f -> Chip(f.label, selected = filter == f, onClick = { filter = f }) }
            }
        }
        if (jobs.isEmpty()) {
            item {
                Panel(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        if (saved.jobs.isEmpty()) "No orders yet. Give one above, or send one from SlothLlama's Lobster page."
                        else "Nothing here.",
                        modifier = Modifier.padding(16.dp),
                        color = Sea.Muted,
                        fontSize = 14.sp
                    )
                }
            }
        }
        items(jobs, key = { it.id }) { job ->
            Panel(Modifier.padding(horizontal = 16.dp), border = if (wide && selectedJob == job.id) Sea.Coral else Sea.Border) {
                ListRow(jobTitle(job), orderLine(job, now), onClick = { onOpenJob(job.id) }, divider = false, titleLines = 2) {
                    StatePill(job)
                }
            }
        }
    }
}

private fun placeholder(kind: OrderKind) = when (kind) {
    OrderKind.RESEARCH -> "What changed in the EU AI Act this year?"
    OrderKind.FETCH -> "A link to bring back for offline reading"
    OrderKind.WATCH -> "A page's link, or a topic for the news"
    OrderKind.BRIEF -> "A topic, or feed links, for a round-up"
    OrderKind.ASK -> ""
}

private fun hint(kind: OrderKind) = when (kind) {
    OrderKind.RESEARCH -> "Free job. SlothLlama writes the answer offline."
    OrderKind.FETCH -> "Pages come back as text; PDFs and EPUBs as files."
    OrderKind.WATCH -> "Checked every 6 hours; change it on the Watch tab."
    OrderKind.BRIEF -> "Headlines with links, sent to the mailbox."
    OrderKind.ASK -> ""
}

private fun orderLine(job: Job, now: Long): String {
    val from = if (job.from == From.TABLET) "From the tablet" else "Yours"
    val state = when (job.state) {
        JobState.WORKING -> if (job.sources.isEmpty()) "working" else "working, ${job.doneCount} of ${job.sources.size} sources"
        JobState.QUEUED -> "queued"
        JobState.DONE -> "brought back ${ago(job.finishedAt ?: job.createdAt, now)}"
        else -> (job.note ?: "stopped").replaceFirstChar { it.lowercase() }
    }
    return "$from · $state"
}
