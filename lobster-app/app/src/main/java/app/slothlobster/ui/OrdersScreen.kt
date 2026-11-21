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
import app.slothlobster.jobs.RepeatRule
import app.slothlobster.mailbox.OrderKind
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.Role
import java.util.Calendar
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
    onSend: (OrderKind, String, Boolean, RepeatRule?) -> Unit,
    onOpenJob: (String) -> Unit,
    onRepeatOn: (String, Boolean) -> Unit,
    onRepeatRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableStateOf(OrderKind.RESEARCH.id) }
    var deep by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    // Repeat: 0 = once, 1 = every day, 7 = every week.
    var every by rememberSaveable { mutableStateOf(0) }
    var weekday by rememberSaveable { mutableStateOf(Calendar.MONDAY) }
    var hour by rememberSaveable { mutableStateOf(7) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var timing by rememberSaveable { mutableStateOf(false) }

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
                            unfocusedBorderColor = Sea.BorderStrong,
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
                    if (kind == OrderKind.RESEARCH || kind == OrderKind.BRIEF) {
                        RepeatControls(
                            every = every, weekday = weekday, hour = hour, minute = minute,
                            onEvery = { every = it }, onWeekday = { weekday = it }, onTime = { timing = true }
                        )
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
                                val repeats = every != 0 && (kind == OrderKind.RESEARCH || kind == OrderKind.BRIEF)
                                onSend(kind, text, deep && kind == OrderKind.RESEARCH, if (repeats) RepeatRule(every, weekday, hour, minute) else null)
                                text = ""
                                deep = false
                                every = 0
                            },
                            enabled = text.isNotBlank() && (kind != OrderKind.FETCH || Web.firstLink(text) != null),
                            icon = LobsterIcons.Send
                        )
                    }
                }
            }
        }
        if (saved.repeats.isNotEmpty()) {
            item {
                Section("Repeating", Modifier.padding(horizontal = 16.dp)) {
                    Panel {
                        saved.repeats.forEachIndexed { i, r ->
                            ListRow(
                                "${OrderKind.of(r.kind).label} · ${r.text.lineSequence().firstOrNull().orEmpty().take(80)}",
                                RepeatRule(r.everyDays, r.weekday, r.hour, r.minute).describe() +
                                    (if (r.from == From.TABLET) " · from SlothLlama" else ""),
                                divider = i < saved.repeats.size - 1
                            ) {
                                IconButton(onClick = { onRepeatRemove(r.id) }) {
                                    Icon(LobsterIcons.Trash, contentDescription = "Stop repeating", tint = Sea.Muted)
                                }
                                CoralSwitch(r.enabled, { onRepeatOn(r.id, it) }, "Repeat ${r.text.take(40)}")
                            }
                        }
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

    if (timing) TimeDialog(hour, minute, onDismiss = { timing = false }) { h, m ->
        hour = h
        minute = m
        timing = false
    }
}

private val WEEK = listOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatControls(
    every: Int,
    weekday: Int,
    hour: Int,
    minute: Int,
    onEvery: (Int) -> Unit,
    onWeekday: (Int) -> Unit,
    onTime: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("Once", selected = every == 0, onClick = { onEvery(0) })
            Chip("Every day", selected = every == 1, onClick = { onEvery(1) })
            Chip("Every week", selected = every == 7, onClick = { onEvery(7) })
        }
        if (every == 7) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEK.forEach { (d, label) -> Chip(label, selected = weekday == d, onClick = { onWeekday(d) }) }
            }
        }
        if (every != 0) {
            Text(
                "Runs now, then ${RepeatRule(every, weekday, hour, minute).describe().replaceFirstChar { it.lowercase() }}. Change the time",
                modifier = Modifier.clickable(role = Role.Button, onClick = onTime),
                color = Sea.Coral,
                fontSize = 13.sp
            )
        }
    }
}

private fun placeholder(kind: OrderKind) = when (kind) {
    OrderKind.RESEARCH -> "What changed in the EU AI Act this year?\n(extra searches on new lines)"
    OrderKind.FETCH -> "A link to bring back for offline reading"
    OrderKind.WATCH -> "A page's link, or a topic for the news"
    OrderKind.BRIEF -> "A topic, or feed links, for a round-up"
    OrderKind.ASK -> "Your question; it's researched like Research"
    OrderKind.PRICES -> "GLD SLV USDJPY DTB3 JP3M GVZ from 2008-01-01"
    OrderKind.COVERS -> "Sent from SlothBooks"
    OrderKind.EDITION -> "Sent from SlothLlama's edition settings"
    OrderKind.SPORTS -> "nba nfl epl f1 from 2026-10-01"
}

private fun hint(kind: OrderKind) = when (kind) {
    OrderKind.RESEARCH -> "Free job. SlothLlama writes the answer offline."
    OrderKind.FETCH -> "Pages come back as text; PDFs and EPUBs as files."
    OrderKind.WATCH -> "Checked every 6 hours; change it on the Watch tab."
    OrderKind.BRIEF -> "Two to four key words work best; \"updates\" and \"top news\" only narrow it."
    OrderKind.ASK -> "Runs as Research: SlothLlama answers from the sources."
    OrderKind.PRICES -> "Daily prices for SlothDesk; ETFs need a Tiingo key (Settings, Market data)."
    OrderKind.COVERS -> "Book covers for SlothBooks, looked up on Open Library and Google Books."
    OrderKind.EDITION -> "The edition's sections, language corners and times, set in SlothLlama."
    OrderKind.SPORTS -> "Results and fixtures for SlothLlama's Sports page, from ESPN's scoreboard and Jolpica-F1."
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
