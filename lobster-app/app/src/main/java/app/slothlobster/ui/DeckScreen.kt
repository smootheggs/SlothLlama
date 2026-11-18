package app.slothlobster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import app.slothlobster.jobs.Plans
import app.slothlobster.jobs.RepeatRule
import app.slothlobster.jobs.Repeats
import app.slothlobster.web.Search
import app.slothlobster.web.Web
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    onSend: (OrderKind, String) -> Unit,
    onSendNow: (String) -> Unit,
    onWatches: () -> Unit,
    onOrders: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One page: the parts spread over the height, scrolling only when they can't fit.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screen = maxHeight
        val tilesAcross = if (maxWidth >= 600.dp) 4 else 2
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = screen)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DeckHeader(showMark = !wide, onSettings = if (wide) null else onSettings)
                StatusLine(saved, live, now, onPause)
            }
            if (!live.mailboxReady) SetupCard(live, onPair)
            OrderBox(onSend)
            TodayTiles(saved, live, now, tilesAcross, onSeeAll, onWatches, onSettings)
            val working = saved.jobs.find { it.id == live.workingId } ?: saved.jobs.find { it.state == JobState.WORKING }
            Section("Now") {
                if (working == null) {
                    Panel {
                        val queued = saved.jobs.count { it.state == JobState.QUEUED }
                        Text(
                            when {
                                live.paused -> "Paused. Orders wait in the mailbox until you resume."
                                queued > 0 -> "$queued waiting. The Lobster starts on the next one in a moment."
                                else -> "Nothing in the claws. Orders from SlothLlama or the box above land here."
                            },
                            modifier = Modifier.padding(16.dp),
                            color = Sea.Muted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    NowCard(working, now, selected = wide && selectedJob == working.id, onClick = { onOpenJob(working.id) }, onSendNow = { onSendNow(working.id) })
                }
            }
            ComingUp(saved, now, onOrders, onWatches)
            BroughtBack(saved, live, now, onOpenCatch, onSeeAll)
        }
    }
}

/** "Awake · checks every 30 s · last look 1 min ago · mailbox synced just now", and Pause. */
@Composable
private fun StatusLine(saved: Saved, live: Live, now: Long, onPause: (Boolean) -> Unit) {
    val (word, on) = mood(live)
    val details = buildList {
        if (live.mailboxReady && !live.paused) {
            add(if (saved.settings.stayAwake && live.awake) "checks every 30 s" else "checks every 15 min")
            if (live.lastPass > 0) add("last look ${ago(live.lastPass, now)}")
        }
        if (live.mailboxReady) add("mailbox synced ${ago(live.lastSync, now)}")
    }.joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(on)
        Column(Modifier.weight(1f)) {
            Text(word, color = Sea.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (details.isNotEmpty()) Text(details, color = Sea.Faint, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (live.mailboxReady) {
            GhostButton(if (live.paused) "Resume" else "Pause", { onPause(!live.paused) }, icon = if (live.paused) null else LobsterIcons.Pause)
        }
    }
}

/** New orders straight from the Deck; research shows the searches it will run as you type. */
@Composable
private fun OrderBox(onSend: (OrderKind, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableStateOf(OrderKind.RESEARCH.id) }
    val kind = OrderKind.of(kindId)
    val ready = text.isNotBlank() && (kind != OrderKind.FETCH || Web.firstLink(text) != null)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Sea.Raised)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("New order", color = Sea.Faint, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).semantics { contentDescription = "New order" },
                textStyle = TextStyle(color = Sea.Text, fontSize = 17.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(Sea.Coral),
                maxLines = 5,
                decorationBox = { field ->
                    Box {
                        if (text.isEmpty()) Text(orderHint(kind), color = Sea.Faint, fontSize = 17.sp, lineHeight = 24.sp)
                        field()
                    }
                }
            )
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (ready) Sea.Coral else Sea.Off)
                    .clickable(enabled = ready, role = Role.Button, onClickLabel = "Send the order") {
                        onSend(kind, text.trim())
                        text = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(LobsterIcons.Send, contentDescription = "Send the order", tint = if (ready) Sea.OnCoral else Sea.Muted, modifier = Modifier.size(22.dp))
            }
        }
        if (kind == OrderKind.RESEARCH && text.isNotBlank()) {
            val (_, searches) = Plans.searches(text, emptyList())
            Text(
                "Will read Wikipedia, then search: " + searches.joinToString(" · ") { "“${it.take(60)}”" },
                color = Sea.Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(OrderKind.RESEARCH, OrderKind.FETCH, OrderKind.WATCH, OrderKind.BRIEF).forEach { k ->
                Chip(k.label, selected = kind == k, onClick = { kindId = k.id })
            }
        }
    }
}

private fun orderHint(kind: OrderKind) = when (kind) {
    OrderKind.RESEARCH -> "Ask the Lobster to look into something (extra searches on new lines)"
    OrderKind.FETCH -> "Paste a link to bring back for offline reading"
    OrderKind.WATCH -> "A link, or words for a news topic, to keep an eye on"
    OrderKind.BRIEF -> "A topic for a round-up of today's headlines"
    else -> "What should the Lobster do?"
}

/** What's new or next, as numbers: brought back, watches, the next brief, search engines. */
@Composable
private fun TodayTiles(
    saved: Saved,
    live: Live,
    now: Long,
    across: Int,
    onSeeAll: () -> Unit,
    onWatches: () -> Unit,
    onSettings: () -> Unit
) {
    val newCatch = saved.delivered.count { isToday(it.at, now) }
    val waiting = saved.delivered.count { it.fileName != null && it.fileName in live.waitingCatch }
    val watches = saved.watches.filter { it.enabled }
    val changed = watches.count { isToday(it.lastChange, now) }
    val checked = watches.count { isToday(it.lastChecked, now) }
    val s = saved.settings
    val brief = if (s.briefOn) Repeats.next(1, 0, s.briefHour, s.briefMinute, s.lastBriefDay, calendar(now)).timeInMillis else null
    val down = Search.ENGINES.filter { (saved.engines[it]?.failsInRow ?: 0) >= 3 }
    val tiles = listOf(
        DeckTile(LobsterIcons.Inbox, if (newCatch == 0) "None yet" else "$newCatch new", "Brought back today",
            if (waiting > 0) "$waiting waiting for SlothLlama" else "Tap to read", if (newCatch > 0) Tone.CORAL else Tone.QUIET, onSeeAll),
        DeckTile(LobsterIcons.Watch, if (watches.isEmpty()) "None" else "$changed changed", "Watches",
            if (watches.isEmpty()) "Watch a page or topic" else "$checked of ${watches.size} checked today", if (changed > 0) Tone.CORAL else Tone.QUIET, onWatches),
        DeckTile(LobsterIcons.Clock, brief?.let { clockTime(it) } ?: "Off", "Daily brief",
            brief?.let { whenLabel(it, now) + " · ${s.feeds.size} feeds" } ?: "Turn it on in Settings", Tone.PLAIN, onSettings),
        DeckTile(LobsterIcons.Globe, "${Search.ENGINES.size - down.size} of ${Search.ENGINES.size}", "Search engines answering",
            if (down.isEmpty()) "All answering" else "${down.joinToString()} not answering", if (down.isEmpty()) Tone.QUIET else Tone.PROBLEM, onSettings)
    )
    Section("Today") {
        tiles.chunked(across).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> TileView(t, Modifier.weight(1f)) }
                repeat(across - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private enum class Tone { CORAL, PLAIN, QUIET, PROBLEM }

private data class DeckTile(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String,
    val title: String,
    val detail: String,
    val tone: Tone,
    val onClick: () -> Unit
)

@Composable
private fun TileView(t: DeckTile, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(shape)
            .border(1.dp, Sea.Border, shape)
            .clickable(role = Role.Button, onClick = t.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(t.icon, contentDescription = null, tint = Sea.Faint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            Icon(LobsterIcons.Chevron, contentDescription = null, tint = Sea.Faint, modifier = Modifier.size(16.dp))
        }
        Text(
            t.value,
            color = when (t.tone) {
                Tone.CORAL -> Sea.Coral
                Tone.PLAIN -> Sea.Text
                Tone.QUIET -> Sea.Soft
                Tone.PROBLEM -> Sea.Danger
            },
            fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(t.title, color = Sea.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.detail, color = if (t.tone == Tone.PROBLEM) Sea.Danger else Sea.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The next brief, watch checks and repeating orders, soonest first. */
@Composable
private fun ComingUp(saved: Saved, now: Long, onOrders: () -> Unit, onWatches: () -> Unit) {
    val s = saved.settings
    val items = buildList {
        if (s.briefOn) {
            add(Triple(Repeats.next(1, 0, s.briefHour, s.briefMinute, s.lastBriefDay, calendar(now)).timeInMillis, "Daily brief", LobsterIcons.Clock) to onOrders)
        }
        saved.watches.filter { it.enabled }.forEach { w ->
            val at = if (w.lastChecked <= 0) now else w.lastChecked + w.everyHours * 3_600_000L
            add(Triple(maxOf(at, now), "Watch · ${w.title.ifBlank { w.target }}", LobsterIcons.Watch) to onWatches)
        }
        saved.repeats.filter { it.enabled }.forEach { r ->
            val at = Repeats.next(r.everyDays, r.weekday, r.hour, r.minute, r.lastRunDay, calendar(now)).timeInMillis
            val what = "${OrderKind.of(r.kind).label}: ${r.text.lineSequence().firstOrNull().orEmpty()}"
            add(Triple(at, "${RepeatRule(r.everyDays, r.weekday, r.hour, r.minute).describe()} · $what", LobsterIcons.Repeat) to onOrders)
        }
    }.sortedBy { it.first.first }.take(4)
    if (items.isEmpty()) return
    Section("Coming up") {
        Panel {
            items.forEachIndexed { i, (item, go) ->
                val (at, title, icon) = item
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClick = go)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Sea.Faint, modifier = Modifier.size(18.dp))
                    Text(title, color = Sea.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(whenLabel(at, now), color = Sea.Muted, fontSize = 13.sp)
                }
                if (i < items.lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun BroughtBack(saved: Saved, live: Live, now: Long, onOpenCatch: (String) -> Unit, onSeeAll: () -> Unit) {
    val today = saved.delivered.filter { isToday(it.at, now) }
    Section("Brought back today") {
        Panel {
            if (today.isEmpty()) {
                Text("Nothing yet today.", modifier = Modifier.padding(16.dp), color = Sea.Muted, fontSize = 14.sp)
            }
            today.take(3).forEach { d ->
                ListRow(
                    d.title,
                    listOf(d.subtitle, clockTime(d.at)).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { onOpenCatch(d.id) },
                    divider = true
                ) { DeliveredPill(d, live) }
            }
            if (saved.delivered.isNotEmpty()) {
                ListRow("Everything brought back", "${saved.delivered.size} kept here to read", onClick = onSeeAll, divider = false) {
                    Icon(LobsterIcons.Chevron, contentDescription = null, tint = Sea.Faint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun calendar(at: Long): Calendar = Calendar.getInstance().apply { timeInMillis = at }

/** "now", "in 40 min", "in 3 h", "tomorrow 07:00", "Mon 07:00". */
fun whenLabel(at: Long, now: Long): String {
    val minutes = (at - now) / 60_000
    return when {
        minutes <= 1 -> "now"
        minutes < 60 -> "in $minutes min"
        dayOf(at) == dayOf(now) -> "in ${(minutes + 30) / 60} h"
        dayOf(at) - dayOf(now) == 1 -> "tomorrow ${clockTime(at)}"
        else -> SimpleDateFormat("EEE HH:mm", Locale.ENGLISH).format(Date(at))
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
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showMark) {
            Image(
                painterResource(R.drawable.sloth_lobster),
                contentDescription = "A sloth riding a lobster",
                modifier = Modifier.size(width = 56.dp, height = 49.dp)
            )
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (showMark) "SlothLobster" else "Deck",
                modifier = Modifier.semantics { heading() },
                color = Sea.Text,
                fontSize = if (showMark) 24.sp else 32.sp,
                lineHeight = if (showMark) 30.sp else 38.sp,
                fontWeight = FontWeight.Medium
            )
            Text("Goes online for SlothLlama", color = Sea.Muted, fontSize = 13.sp)
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) { Icon(LobsterIcons.Settings, contentDescription = "Settings", tint = Sea.Muted) }
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
private fun NowCard(job: Job, now: Long, selected: Boolean, onClick: () -> Unit, onSendNow: () -> Unit) {
    Panel(border = if (selected) Sea.Coral else Sea.Border) {
        Column(Modifier.fillMaxWidth()) {
            ListRow(jobTitle(job), null, onClick = onClick, divider = false, titleLines = 2) {
                if (OrderKind.of(job.kind) == OrderKind.RESEARCH && !job.sendNow) {
                    GhostButton("Send now", onSendNow)
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = job.sources.size
                Progress(if (total == 0) 0.05f else job.doneCount.toFloat() / total)
                val (_, searches) = Plans.searches(job.text, job.queries)
                val line = buildList {
                    add(if (total == 0) "${Engine.verb(job.kind)}…" else "Reading ${job.doneCount} of $total pages")
                    if (OrderKind.of(job.kind) == OrderKind.RESEARCH) add(if (searches.size == 1) "1 search" else "${searches.size} searches")
                    job.startedAt?.let { add("started ${ago(it, now)}") }
                    if (job.sendNow) add("sending what it has")
                }.joinToString(" · ")
                Text(line, color = Sea.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
