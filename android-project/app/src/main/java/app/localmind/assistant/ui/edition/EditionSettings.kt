package app.localmind.assistant.ui.edition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.edition.CornerLanguage
import app.localmind.assistant.domain.edition.EditionCorner
import app.localmind.assistant.domain.edition.EditionSection
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.EditionTime
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.ui.settings.ToggleRow
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.Field
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton
import java.text.DateFormat
import java.util.Date

/** What's being edited in a dialog. */
private sealed interface Editing {
    data class Time(val index: Int) : Editing
    data class Section(val index: Int) : Editing
    data class Corner(val index: Int) : Editing
}

/**
 * The edition's settings: when it comes, how long it is, its sections (tap one, then another, to
 * swap them), words to lift or mute, the language corners and who sits on the desk. Changes stay
 * on the tablet until they're sent to the Lobster.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditionSettings(setup: EditionSetup, personas: List<Persona>, vm: EditionViewModel) {
    val plan = setup.plan
    var editing by remember { mutableStateOf<Editing?>(null) }
    var swapFrom by rememberSaveable { mutableStateOf(-1) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val pad = if (wide) 40.dp else 20.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = 48.dp)) {
            SendCard(setup, onSend = { vm.send(now = false) }, onSendNow = { vm.send(now = true) })

            val left: @Composable () -> Unit = {
                ToggleRow("The edition", "Off: the Lobster goes back to its daily brief.", plan.on) { on -> vm.edit { it.copy(plan = it.plan.copy(on = on)) } }

                SectionHead("Editions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.times.forEachIndexed { i, t -> Pill("${t.name} %02d:%02d".format(t.h, t.m), selected = true, quiet = true) { editing = Editing.Time(i) } }
                    if (plan.times.size < 4) {
                        Pill("+ Edition", selected = false) {
                            val name = listOf("Evening", "Midday", "Night", "Edition").first { n -> plan.times.none { it.name == n } }
                            vm.edit { it.copy(plan = it.plan.copy(times = it.plan.times + EditionTime(name, 18, 30))) }
                        }
                    }
                }
                ToggleRow("Weekends too", "Off: weekdays only.", plan.weekends) { on -> vm.edit { it.copy(plan = it.plan.copy(weekends = on)) } }

                SectionHead("Length")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("quick" to "Quick", "standard" to "Standard", "long" to "Long").forEach { (key, label) ->
                        Pill("$label · ${Editions.sizes(key)} a section", selected = setup.length == key, quiet = true, small = true) {
                            vm.edit { it.copy(length = key, plan = Editions.withLength(it.plan, key)) }
                        }
                    }
                }

                SectionHead("Sections", if (swapFrom >= 0) "now tap where it goes" else "tap one, then another, to swap")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Top stories", color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("carried by two or more feeds", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    Stepper(plan.top, 0, 6) { n -> vm.edit { it.copy(plan = it.plan.copy(top = n)) } }
                }
                plan.sections.forEachIndexed { i, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (swapFrom == i) Modifier.background(P.AccentSoft) else Modifier)
                            .clickable(onClickLabel = if (swapFrom < 0) "Choose to move" else "Swap with this") {
                                if (swapFrom < 0) swapFrom = i
                                else {
                                    val from = swapFrom
                                    swapFrom = -1
                                    vm.edit { it.copy(plan = it.plan.copy(sections = Editions.swap(it.plan.sections, from, i))) }
                                }
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = P.Text, fontSize = 15.sp)
                            Text(s.sources.joinToString(", ", transform = Editions::sourceLabel), color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        QuietButton("Edit", muted = true) { editing = Editing.Section(i) }
                        Stepper(s.count, 1, 12) { n -> vm.edit { st -> st.copy(plan = st.plan.copy(sections = st.plan.sections.mapIndexed { j, x -> if (j == i) x.copy(count = n) else x })) } }
                    }
                }
                if (plan.sections.size < 10) {
                    QuietButton("+ Add a section") {
                        vm.edit { it.copy(plan = it.plan.copy(sections = it.plan.sections + EditionSection("New section", emptyList(), Editions.sizes(it.length)))) }
                        editing = Editing.Section(plan.sections.size)
                    }
                }

                SectionHead("Filters")
                WordChips("Lift", "Stories with these words come first in their section", plan.lift) { words -> vm.edit { it.copy(plan = it.plan.copy(lift = words)) } }
                WordChips("Mute", "Stories with these words are left out", plan.mute) { words -> vm.edit { it.copy(plan = it.plan.copy(mute = words)) } }
            }

            val right: @Composable () -> Unit = {
                SectionHead("Language corners", "stories each")
                plan.corners.forEachIndexed { i, c ->
                    val lang = CornerLanguage.of(c.lang)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "Edit the corner") { editing = Editing.Corner(i) }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(lang?.badge ?: c.lang, 28.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(lang?.label ?: c.name, color = if (c.on) P.Text else P.Faint, fontSize = 15.sp)
                            Text(
                                (c.sources.take(1).map(Editions::sourceLabel) + listOfNotNull(c.topic.takeIf { it.isNotBlank() }?.let { "topic: $it" }, lang?.help)).joinToString(" · "),
                                color = P.Faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Stepper(c.count, 1, 4) { n -> vm.edit { st -> st.copy(plan = st.plan.copy(corners = st.plan.corners.mapIndexed { j, x -> if (j == i) x.copy(count = n) else x })) } }
                        Spacer(Modifier.width(8.dp))
                        OnSwitch(c.on, "${lang?.label ?: c.name} corner") { on ->
                            vm.edit { st -> st.copy(plan = st.plan.copy(corners = st.plan.corners.mapIndexed { j, x -> if (j == i) x.copy(on = on) else x })) }
                        }
                    }
                }
                val missing = CornerLanguage.entries.filter { l -> plan.corners.none { it.lang == l.code } }
                if (missing.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        missing.forEach { l ->
                            Pill("+ ${l.badge} ${l.label}", selected = false, small = true) {
                                val default = Editions.defaultCorners().firstOrNull { it.lang == l.code } ?: EditionCorner(l.code, l.label, listOf("top"))
                                vm.edit { it.copy(plan = it.plan.copy(corners = it.plan.corners + default.copy(on = true))) }
                            }
                        }
                    }
                }
                Text(
                    "Mandarin key words are those above your HSK level, set in Settings → Tutors.",
                    color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )

                SectionHead("The desk")
                val candidates = personas.filter { it.speech == null && it.id !in NOT_DESK }
                candidates.forEach { p ->
                    val seated = p.id in setup.desk
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge(p.badge, 28.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, color = P.Text, fontSize = 15.sp)
                            Text(p.tagline, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OnSwitch(seated, "${p.name} on the desk") { on ->
                            vm.edit { st -> st.copy(desk = if (on) (st.desk + p.id).distinct() else st.desk - p.id) }
                        }
                    }
                }
                Text(
                    "A crypto or equities desk: make the persona in Personas, then seat it here. Its notes are written in a chat, from the edition's headlines and SlothDesk's prices.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (wide) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { left() }
                    Spacer(Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) { right() }
                }
            } else {
                left()
                right()
            }
        }
    }

    when (val e = editing) {
        is Editing.Time -> plan.times.getOrNull(e.index)?.let { t ->
            TimeDialog(
                t, canRemove = plan.times.size > 1,
                onDone = { next -> vm.edit { st -> st.copy(plan = st.plan.copy(times = st.plan.times.mapIndexed { j, x -> if (j == e.index) next else x })) }; editing = null },
                onRemove = { vm.edit { st -> st.copy(plan = st.plan.copy(times = st.plan.times.filterIndexed { j, _ -> j != e.index })) }; editing = null },
                onDismiss = { editing = null }
            )
        }
        is Editing.Section -> plan.sections.getOrNull(e.index)?.let { s ->
            SourcesDialog(
                title = "Section", name = s.name, sources = s.sources, topic = null,
                onDone = { name, sources, _ ->
                    vm.edit { st -> st.copy(plan = st.plan.copy(sections = st.plan.sections.mapIndexed { j, x -> if (j == e.index) x.copy(name = name, sources = sources) else x })) }
                    editing = null
                },
                onRemove = { vm.edit { st -> st.copy(plan = st.plan.copy(sections = st.plan.sections.filterIndexed { j, _ -> j != e.index })) }; editing = null },
                onDismiss = { editing = null }
            )
        }
        is Editing.Corner -> plan.corners.getOrNull(e.index)?.let { c ->
            SourcesDialog(
                title = "${CornerLanguage.of(c.lang)?.label ?: c.name} corner", name = null, sources = c.sources, topic = c.topic,
                onDone = { _, sources, topic ->
                    vm.edit { st -> st.copy(plan = st.plan.copy(corners = st.plan.corners.mapIndexed { j, x -> if (j == e.index) x.copy(sources = sources, topic = topic.orEmpty()) else x })) }
                    editing = null
                },
                onRemove = { vm.edit { st -> st.copy(plan = st.plan.copy(corners = st.plan.corners.filterIndexed { j, _ -> j != e.index })) }; editing = null },
                onDismiss = { editing = null }
            )
        }
        null -> Unit
    }
}

@Composable
private fun SendCard(setup: EditionSetup, onSend: () -> Unit, onSendNow: () -> Unit) {
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val status = when {
        setup.sentAt == 0L -> "Not sent yet: the Lobster makes its daily brief until it has these."
        setup.unsent -> "Changed since you sent them on ${fmt.format(Date(setup.sentAt))}."
        setup.acceptedAt >= setup.sentAt -> "The Lobster has these (sent ${fmt.format(Date(setup.sentAt))})."
        else -> "In the mailbox since ${fmt.format(Date(setup.sentAt))}: the Lobster takes them at its next check."
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(status, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledButton("Send to the Lobster", onClick = onSend)
            QuietButton("Send and make one now", onClick = onSendNow)
        }
        Text("Needs SlothLobster 1.8 or newer.", color = P.Faint, fontSize = 12.sp)
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.height(36.dp).border(1.dp, P.Border, RoundedCornerShape(18.dp)).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton("−", "Fewer", value > min) { onChange(value - 1) }
        Text("$value", color = P.Text, fontSize = 14.sp, modifier = Modifier.width(22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", "More", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) P.Muted else P.Border, fontSize = 16.sp)
    }
}

@Composable
private fun OnSwitch(checked: Boolean, description: String, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = description },
        colors = SwitchDefaults.colors(
            checkedThumbColor = P.OnAccent,
            checkedTrackColor = P.Accent,
            uncheckedThumbColor = P.Muted,
            uncheckedTrackColor = P.Elevated,
            uncheckedBorderColor = P.BorderStrong
        )
    )
}

/** Words as chips (a tap takes one off) and a field to add one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(label: String, detail: String, words: List<String>, onChange: (List<String>) -> Unit) {
    var adding by remember { mutableStateOf("") }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("$label · $detail", color = P.Faint, fontSize = 12.sp)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            words.forEach { w -> Pill("$w  ×", selected = true, quiet = true, small = true) { onChange(words - w) } }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Field("Add a word", adding, "gold, HKMA…") { adding = it.take(40) } }
            QuietButton("Add") {
                val add = adding.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (add.isNotEmpty()) onChange((words + add).distinct().take(30))
                adding = ""
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(t: EditionTime, canRemove: Boolean, onDone: (EditionTime) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = t.h, initialMinute = t.m, is24Hour = true)
    var name by remember { mutableStateOf(t.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text("Edition time", color = P.Text) },
        text = {
            Column {
                Field("Name", name, "Morning") { name = it.take(20) }
                Spacer(Modifier.height(12.dp))
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = P.Elevated,
                        selectorColor = P.Accent,
                        timeSelectorSelectedContainerColor = P.AccentSoft,
                        timeSelectorUnselectedContainerColor = P.Elevated,
                        timeSelectorSelectedContentColor = P.Text,
                        timeSelectorUnselectedContentColor = P.Muted
                    )
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(EditionTime(name.trim().ifBlank { t.name }, state.hour, state.minute)) }) { Text("Done", color = P.Accent) } },
        dismissButton = {
            Row {
                if (canRemove) TextButton(onClick = onRemove) { Text("Remove", color = P.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) }
            }
        }
    )
}

/**
 * A section's or corner's sources, one a line: a feed or page link, "search: words", or "top".
 * Plain words are taken as a news search.
 */
@Composable
private fun SourcesDialog(
    title: String,
    name: String?,
    sources: List<String>,
    topic: String?,
    onDone: (String, List<String>, String?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(name.orEmpty()) }
    var lines by remember { mutableStateOf(sources.joinToString("\n")) }
    var newTopic by remember { mutableStateOf(topic.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text(title, color = P.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (name != null) Field("Name", newName, "World") { newName = it.take(40) }
                Field("Sources, one a line", lines, "https://…/rss.xml\nsearch: gold OR silver\ntop", lines = 5) { lines = it.take(2_000) }
                Text(
                    "A feed or a page that lists stories (sites without a feed), \"search:\" and words for a news search, or \"top\" for the region's top stories. At most six.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)
                )
                if (topic != null) Field("Topic (words to prefer)", newTopic, "economy, trade") { newTopic = it.take(80) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val list = lines.lines().mapNotNull(Editions::source).distinct().take(6)
                onDone(newName.trim().ifBlank { name ?: "" }.ifBlank { "News" }, list, if (topic != null) newTopic.trim() else null)
            }) { Text("Done", color = P.Accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove", color = P.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) }
            }
        }
    )
}

/** Built-in personas that aren't desks: the general assistant, the language partner, the Librarian. */
private val NOT_DESK = setOf("persona-general-assistant", "persona-language-partner", "persona-librarian")
