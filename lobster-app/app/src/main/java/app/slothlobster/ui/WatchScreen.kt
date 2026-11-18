package app.slothlobster.ui

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slothlobster.core.Saved
import app.slothlobster.core.Watch

private val EVERY = listOf(1 to "Hourly", 6 to "Every 6 h", 24 to "Daily")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchScreen(
    saved: Saved,
    now: Long,
    onAdd: (String, Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEvery: (String, Int) -> Unit,
    onCheckNow: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val watches = saved.watches

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader("Watch", "Pages and topics the Lobster checks for you")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Watching ${watches.count { it.enabled }} of ${watches.size}") {
                Panel {
                    if (watches.isEmpty()) {
                        Text(
                            "Nothing watched yet. Add a page (a careers page, a release list) or a news topic, " +
                                "and the Lobster sends an alert to the mailbox when it changes.",
                            modifier = Modifier.padding(16.dp),
                            color = Sea.Muted,
                            fontSize = 14.sp
                        )
                    }
                    watches.forEachIndexed { i, w ->
                        val open = openId == w.id
                        ListRow(
                            w.title,
                            watchLine(w, now),
                            onClick = { openId = if (open) null else w.id },
                            divider = !open && i < watches.size - 1
                        ) {
                            if (w.lastChange > 0 && now - w.lastChange < 24 * 60 * 60 * 1000L) Pill("Changed", hot = true)
                            CoralSwitch(w.enabled, { onToggle(w.id, it) }, "Watch ${w.title}")
                        }
                        if (open) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(w.target, color = Sea.Faint, fontSize = 12.sp, maxLines = 2)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EVERY.forEach { (h, label) -> Chip(label, selected = w.everyHours == h, onClick = { onEvery(w.id, h) }) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GhostButton("Check now", { onCheckNow(w.id) }, icon = LobsterIcons.Refresh)
                                    GhostButton("Stop watching", { onRemove(w.id); openId = null }, icon = LobsterIcons.Trash)
                                }
                            }
                            if (i < watches.size - 1) Divider()
                        }
                    }
                }
            }

            val latest = watches.filter { it.lastChange > 0 }.maxByOrNull { it.lastChange }
            if (latest != null) {
                Section("Latest change") { ChangeCard(latest) }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Sea.Off, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { adding = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LobsterIcons.Plus, null, tint = Sea.Text, modifier = Modifier.size(18.dp))
                    Box(Modifier.width(8.dp))
                    Text("Watch a page or topic", color = Sea.Text, fontSize = 15.sp)
                }
            }
        }
    }

    if (adding) AddWatchDialog(onDismiss = { adding = false }, onAdd = { t, h -> onAdd(t, h); adding = false })
}

private fun watchLine(w: Watch, now: Long): String = when {
    !w.enabled -> "Paused by you"
    w.lastChecked == 0L -> "First look soon"
    w.failures >= 3 -> "Couldn't open it the last ${w.failures} times"
    w.lastChange > 0 -> "${every(w.everyHours)} · changed ${ago(w.lastChange, now)}"
    else -> "${every(w.everyHours)} · no change since ${ago(w.createdAt.takeIf { it > 0 } ?: w.lastChecked, now)}"
}

private fun every(h: Int) = when (h) {
    1 -> "Checks hourly"
    24 -> "Checks daily"
    else -> "Checks every $h h"
}

@Composable
private fun ChangeCard(w: Watch) {
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("What changed · ${w.title}", color = Sea.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(clockTime(w.lastChange), color = Sea.Muted, fontSize = 13.sp)
            }
            if (w.added.isNotEmpty()) DiffBlock(w.added.take(6), "+", Sea.Text, Sea.Coral, Sea.CoralSoft)
            if (w.removed.isNotEmpty()) DiffBlock(w.removed.take(6), "−", Sea.Faint, Sea.Off, Color.Transparent)
            Text("Sent to the mailbox. SlothLlama shows it as a catch.", color = Sea.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DiffBlock(lines: List<String>, sign: String, text: Color, bar: Color, fill: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text("$sign ${it.removePrefix("- ").removePrefix("#").trim()}", color = text, fontSize = 13.sp, maxLines = 2) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddWatchDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var target by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf(6) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sea.Card,
        title = { Text("Watch a page or topic", color = Sea.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("A link, or words for the news") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sea.Coral, focusedLabelColor = Sea.Coral, cursorColor = Sea.Coral),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EVERY.forEach { (h, label) -> Chip(label, selected = hours == h, onClick = { hours = h }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(target, hours) }, enabled = target.isNotBlank()) { Text("Watch", color = if (target.isNotBlank()) Sea.Coral else Sea.Faint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sea.Muted) } }
    )
}
