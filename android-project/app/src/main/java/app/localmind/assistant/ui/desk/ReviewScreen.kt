package app.localmind.assistant.ui.desk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.desk.DeskPath
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sunday's review (1.49), about five minutes: the week's tickets and slips, each rule's week
 * beside what plain holding did, each idea beside the week (you judge whether they fit), the
 * price calls, then your call for each rule and one line for the week ahead. The numbers are
 * the Desk's; the Trader can talk them through but never makes new ones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(ui: DeskUi, vm: DeskViewModel, onBack: () -> Unit) {
    val sunday = ui.reviewWaiting ?: DeskPath.sundayOf(LocalDate.now()).minusDays(7)
    val f = remember(sunday, ui.reports, ui.state.journal, ui.ideas) { vm.weekFacts(sunday) }
    val calls = remember(sunday) { mutableStateOf(f.done?.calls ?: emptyMap()) }
    var line by remember(sunday) { mutableStateOf(f.done?.line.orEmpty()) }
    val monday = DeskPath.week(sunday).start
    val fmt = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(title = "Sunday's review", subtitle = "SlothDesk · Review · week of ${monday.format(fmt)}", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PathChips(DeskPath.Stage.REVIEW)

                if (f.rules.isEmpty()) {
                    Label("THE TICKETS")
                    Card { Text("Nothing was on paper this week.", color = P.Muted, fontSize = 14.sp) }
                }
                f.rules.forEach { w ->
                    Label(w.name.uppercase(Locale.ENGLISH))
                    Card(padding = 20.dp) {
                        Row2("The tickets",
                            "${w.tickets} ticket${if (w.tickets == 1) "" else "s"}" + (if (w.trades > 0) ", ${w.trades} with a trade" else ", no trades") +
                                ". You followed ${w.followed} of ${w.marked} marked" +
                                (if (w.slips.isNotEmpty()) "; " + w.slips.entries.joinToString(", ") { (k, n) -> "${DeskPath.slip(k).ifBlank { "unsaid" }.lowercase()} $n" } else "") + ".")
                        Row2("Its week",
                            "The rule ${w.ruleChange?.let { DeskText.pct(it) } ?: "–"}; holding ${w.bench} ${w.benchChange?.let { DeskText.pct(it) } ?: "–"}. " +
                                "Paper day ${w.progress.days.coerceAtMost(DeskPath.PAPER_DAYS)} of ${DeskPath.PAPER_DAYS}" +
                                (w.progress.share?.let { ", ${(it * 100).toInt()}% followed" } ?: "") + ".")
                        w.idea?.let { i ->
                            Row2("The idea", "“${i.thesis}”" + (if (i.wrongIf.isNotBlank()) " Wrong if: ${i.wrongIf}" else "") +
                                " Does the week fit it? One week is little to go on; note it if it doesn't.")
                        }
                        Text("Your call for next week", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        Choices(listOf("keep" to "Keep paper-trading it", "change" to "Change a setting (a new try)", "retire" to "Take it off paper"),
                            calls.value[w.key].orEmpty()) { c ->
                            calls.value = calls.value + (w.key to c)
                            if (c == "change") {
                                if (w.key.startsWith(DeskViewModel.MINE)) vm.openRule(w.key.removePrefix(DeskViewModel.MINE)) else vm.copyShelf(w.key)
                            }
                        }
                    }
                }

                if (f.ideas.isNotEmpty()) {
                    Label("YOUR OTHER IDEAS")
                    Card {
                        f.ideas.forEach { i ->
                            val item = ui.path.firstOrNull { it.ideaId == i.id }
                            Text("“${i.thesis.lineSequence().first().take(160)}”", color = P.Text, fontSize = 14.sp, lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 6.dp))
                            Text((item?.line ?: "Idea") + if (i.isDue(sunday)) " · its review date has come" else "",
                                color = P.Faint, fontSize = 12.sp)
                        }
                    }
                }
                if (f.tradeIdeasDue.isNotEmpty()) {
                    Label("TRADE IDEAS TO REVIEW")
                    Card {
                        f.tradeIdeasDue.forEach { i ->
                            Text("${i.instrument} ${i.direction}: ${i.thesis.lineSequence().first().take(140)}", color = P.Text, fontSize = 14.sp,
                                lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Text("On the Trade ideas tab.", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (f.callsSettled.isNotEmpty() || f.callsOpen.isNotEmpty()) {
                    Label("PRICE CALLS")
                    Card {
                        f.callsSettled.forEach { c ->
                            val happened = c.happened?.let { c.outcomes.getOrNull(it) } ?: "–"
                            Text("${c.question}: $happened", color = P.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (f.callsOpen.isNotEmpty()) {
                            Text("${f.callsOpen.size} still open", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }

                Label("ONE LINE FOR NEXT WEEK")
                TextInput("What you'll do differently, or keep doing", line, { line = it.take(300) }, hint = "Mark tickets the same evening")
                FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(if (f.done != null) "Save the review again" else "Done") { vm.saveReview(sunday, calls.value, line) }
                    OutlineButton("Talk it through with the Trader") { vm.askAboutWeek(f) }
                }
                Text("About five minutes. It's the step that turns tickets into a record you can learn from.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Row2(label: String, text: String) {
    Text(label, color = P.Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
    Text(text, color = P.Text, fontSize = 14.sp, lineHeight = 20.sp)
}
