package app.slothlobster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slothlobster.core.LogEntry
import app.slothlobster.core.Saved

@Composable
fun LogScreen(saved: Saved, now: Long, modifier: Modifier = Modifier) {
    val today = saved.log.filter { isToday(it.at, now) }
    val days = saved.log.groupBy { dayOf(it.at) }.toList()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Log", "Everything the Lobster did online") }
        item {
            Section("Today", Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${today.count { it.kind in setOf("page", "wiki", "feed") }}", "pages opened", Modifier.weight(1f))
                        StatTile("${today.count { it.kind == "search" }}", "searches", Modifier.weight(1f))
                        StatTile("Free", "no model used", Modifier.weight(1f), accent = true)
                    }
                }
            }
        }
        if (saved.log.isEmpty()) {
            item {
                Panel(Modifier.padding(horizontal = 16.dp)) {
                    Text("Nothing yet. Every page, search and feed the Lobster opens shows up here.", Modifier.padding(16.dp), color = Sea.Muted, fontSize = 14.sp)
                }
            }
        }
        items(days, key = { it.first }) { (_, entries) ->
            Section(dayLabel(entries.first().at, now), Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    entries.take(150).forEachIndexed { i, e -> LogRow(e, divider = i < minOf(entries.size, 150) - 1) }
                }
            }
        }
        item {
            Text(
                "Kept on this device for 30 days. Nothing is sent anywhere else.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Sea.Faint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, divider: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
            Text(clockTime(e.at), color = Sea.Faint, fontSize = 13.sp, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(e.what, color = if (e.kind == "problem") Sea.Danger else Sea.Text, fontSize = 14.sp)
                if (e.detail.isNotBlank()) {
                    Text(e.detail, color = Sea.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (divider) Divider()
    }
}
