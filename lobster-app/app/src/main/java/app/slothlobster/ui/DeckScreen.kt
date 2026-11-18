package app.slothlobster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slothlobster.R
import app.slothlobster.core.Delivered
import app.slothlobster.core.Job
import app.slothlobster.core.JobState
import app.slothlobster.core.Live
import app.slothlobster.core.Saved
import app.slothlobster.jobs.Engine
import app.slothlobster.mailbox.OrderKind

/** The Lobster's mood, in a word and a line. */
fun mood(live: Live): Pair<String, Boolean> = when {
    !live.mailboxReady -> "Not paired yet" to false
    live.paused -> "Resting on the rocks" to false
    live.workingId != null -> "Diving" to true
    live.waitingFor != null -> (live.waitingFor ?: "") to false
    live.awake -> "Awake" to true
    else -> "Napping" to false
}

/** "Research · ECB rate decision" */
fun jobTitle(job: Job): String = "${OrderKind.of(job.kind).label} · ${job.text.lineSequence().firstOrNull().orEmpty().take(90)}"

@Composable
fun DeckScreen(
    saved: Saved,
    live: Live,
    now: Long,
    wide: Boolean,
    selectedJob: String?,
    onSettings: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenCatch: (String) -> Unit,
    onSeeAll: () -> Unit,
    onPause: (Boolean) -> Unit,
    onPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        DeckHeader(showMark = !wide, onSettings = if (wide) null else onSettings)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!live.mailboxReady) SetupCard(live, onPair)
            StatusCard(saved, live, now, wide, onPause)

            val working = saved.jobs.find { it.id == live.workingId } ?: saved.jobs.find { it.state == JobState.WORKING }
            Section("Now") {
                if (working == null) {
                    Panel {
                        Text(
                            if (live.paused) "Paused. Orders wait in the mailbox until you resume."
                            else "Nothing in the claws. Orders from SlothLlama land here.",
                            modifier = Modifier.padding(16.dp),
                            color = Sea.Muted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    NowCard(working, selected = wide && selectedJob == working.id, onClick = { onOpenJob(working.id) })
                }
            }

            val queued = saved.jobs.filter { it.state == JobState.QUEUED }.sortedBy { it.createdAt }
            if (queued.isNotEmpty()) {
                Section("Next") {
                    Panel {
                        queued.take(5).forEachIndexed { i, job ->
                            ListRow(
                                jobTitle(job),
                                (if (job.from == "tablet") "From SlothLlama" else "Yours") + if (i == 0) " · next" else "",
                                onClick = { onOpenJob(job.id) },
                                divider = i < minOf(queued.size, 5) - 1
                            ) { Pill("Queued") }
                        }
                    }
                }
            }

            val today = saved.delivered.filter { isToday(it.at, now) }
            Section("Brought back today") {
                Panel {
                    if (today.isEmpty()) {
                        Text("Nothing yet today.", modifier = Modifier.padding(16.dp), color = Sea.Muted, fontSize = 14.sp)
                    }
                    today.take(8).forEachIndexed { i, d ->
                        ListRow(
                            d.title,
                            listOf(d.subtitle, clockTime(d.at)).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { onOpenCatch(d.id) },
                            divider = true
                        ) { DeliveredPill(d, live) }
                    }
                    if (saved.delivered.isNotEmpty()) {
                        ListRow("Everything brought back", "${saved.delivered.size} kept here to read", onClick = onSeeAll, divider = false) {
                            Text("Open", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeliveredPill(d: Delivered, live: Live) {
    when {
        d.fileName == null -> Pill("Kept here")
        d.fileName in live.waitingCatch -> Pill("In the mailbox", hot = true)
        else -> Pill("Filed")
    }
}

@Composable
private fun DeckHeader(showMark: Boolean, onSettings: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showMark) {
            Image(
                painterResource(R.drawable.sloth_lobster),
                contentDescription = "A sloth riding a lobster",
                modifier = Modifier.size(width = 64.dp, height = 56.dp)
            )
            Spacer(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (showMark) 0.dp else 8.dp)) {
            Text(
                if (showMark) "SlothLobster" else "Deck",
                modifier = Modifier.semantics { heading() },
                color = Sea.Text,
                fontSize = if (showMark) 20.sp else 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Goes online for SlothLlama", color = Sea.Muted, fontSize = 13.sp)
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings) { Icon(LobsterIcons.Settings, contentDescription = "Settings", tint = Sea.Muted) }
        }
    }
}

@Composable
private fun SetupCard(live: Live, onPair: () -> Unit) {
    Panel(border = Sea.Coral) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Two steps to meet SlothLlama", color = Sea.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "In SlothLlama, open the Lobster page: copy its pairing code and note its mailbox folder. " +
                    "Then give the Lobster the same code and the same folder.",
                color = Sea.Muted,
                fontSize = 13.sp
            )
            Step(1, "Pairing code", if (live.paired) "Paired" else "Paste the code from SlothLlama", live.paired)
            Step(
                2, "Mailbox folder",
                when {
                    live.folderName != null -> live.folderName
                    live.folderMissing -> "The folder can't be opened any more: choose it again"
                    else -> "Choose the folder SlothLlama uses"
                },
                live.folderName != null
            )
            PrimaryButton("Set up", onClick = onPair, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Step(n: Int, title: String, line: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (done) Icon(LobsterIcons.Check, contentDescription = "Done", tint = Sea.Coral, modifier = Modifier.size(22.dp))
        else Text("$n", color = Sea.Coral, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.size(22.dp))
        Column {
            Text(title, color = Sea.Text, fontSize = 15.sp)
            Text(line, color = Sea.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusCard(saved: Saved, live: Live, now: Long, wide: Boolean, onPause: (Boolean) -> Unit) {
    val (word, on) = mood(live)
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(on)
                Text(word, color = Sea.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (live.mailboxReady) "Mailbox synced ${ago(live.lastSync, now)}" else "No mailbox yet",
                    color = Sea.Muted,
                    fontSize = 13.sp
                )
            }
            val pagesToday = saved.log.count { isToday(it.at, now) && it.kind in setOf("page", "wiki", "feed") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${saved.jobs.count { it.state == JobState.QUEUED }}", "in queue", Modifier.weight(1f))
                StatTile("${saved.watches.count { it.enabled }}", "watching", Modifier.weight(1f))
                if (wide) StatTile("$pagesToday", "pages today", Modifier.weight(1f))
                StatTile("\$0", "spent", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pause the Lobster", color = Sea.Soft, fontSize = 14.sp)
                    if (!live.awake && !live.paused) Text("Checks every 15 minutes", color = Sea.Faint, fontSize = 12.sp)
                }
                CoralSwitch(live.paused, onPause, "Pause the Lobster")
            }
        }
    }
}

@Composable
private fun NowCard(job: Job, selected: Boolean, onClick: () -> Unit) {
    Panel(border = if (selected) Sea.Coral else Sea.Border) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(Modifier.padding(0.dp))
        ) {
            ListRow(
                jobTitle(job),
                null,
                onClick = onClick,
                divider = false,
                titleLines = 2
            ) {
                if (job.sources.isNotEmpty()) Text("${job.doneCount} of ${job.sources.size}", color = Sea.Muted, fontSize = 13.sp)
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = job.sources.size
                Progress(if (total == 0) 0.05f else job.doneCount.toFloat() / total)
                val reading = job.sources.filter { it.state == "reading" || it.state == "done" }.takeLast(3).joinToString(", ") { it.where.ifBlank { it.title } }
                Text(
                    if (reading.isBlank()) "${Engine.verb(job.kind)}…" else "Reading: $reading",
                    color = Sea.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
