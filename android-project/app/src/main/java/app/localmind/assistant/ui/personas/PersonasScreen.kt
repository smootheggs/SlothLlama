package app.localmind.assistant.ui.personas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.models.LocalModel
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.TutorLevel
import app.localmind.assistant.ui.home.practisedAgo
import java.time.LocalDate
import java.time.ZoneId
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.persona.ChineseRules
import app.localmind.assistant.ui.models.SlothCard
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlin.math.roundToInt

@Composable
fun PersonasScreen(
    viewModel: PersonasViewModel,
    onChatWith: (personaId: String) -> Unit,
    onBack: () -> Unit,
    /** The model every persona uses unless it has its own. */
    defaultModelName: String = "",
    onModels: () -> Unit = {},
    script: ChineseScript = ChineseScript.SIMPLIFIED
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val modelAssignments by viewModel.modelAssignments.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val lastChat by viewModel.lastChat.collectAsStateWithLifecycle()
    var choosingModel by remember { mutableStateOf<Persona?>(null) }
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(personas) { personaSections(personas) }

    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        SlothTopBar(
            title = "Personas",
            subtitle = "Tap one to chat",
            onBack = onBack,
            actions = {
                TextButton(onClick = viewModel::createNew) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = P.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", color = P.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Two columns from about 600 dp, three in landscape.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.widthIn(max = 1280.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "model") {
                    ModelStrip(defaultModelName, onModels)
                }
                sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-${section.key}") {
                        SectionHeader(section.title, section.language?.let { nativeName(it, script) })
                    }
                    items(section.personas, key = { it.id }) { persona ->
                        val assigned = modelAssignments[persona.id]
                        PersonaTile(
                            persona = persona,
                            role = section.language?.let { roleName(persona, it) } ?: persona.name,
                            mark = personaMark(persona, section.language, script),
                            status = statusLine(persona, levels, lastChat[persona.id], today, zone),
                            ownModel = assigned?.let { a -> models.firstOrNull { it.file.name == a }?.displayName ?: "$a (missing)" },
                            onChat = { onChatWith(persona.id) },
                            onEdit = { viewModel.edit(persona) },
                            onModel = { choosingModel = persona },
                            onDelete = { viewModel.requestDelete(persona) }
                        )
                    }
                    if (section.key == ASSISTANTS) {
                        item(key = "new") { NewPersonaTile(viewModel::createNew) }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "note") {
                    Text(
                        "Customize sets a persona's prompt, creativity and reply length. ⋯ on a tile gives it its own model; " +
                            "a tile names its model only when it isn't the usual one.",
                        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    choosingModel?.let { persona ->
        ModelDialog(
            persona = persona,
            assigned = modelAssignments[persona.id],
            models = models,
            onPick = { model ->
                viewModel.setModel(persona, model)
                choosingModel = null
            },
            onDismiss = { choosingModel = null }
        )
    }

    draft?.let { current ->
        PersonaEditor(
            draft = current,
            onChange = viewModel::updateDraft,
            onCancel = viewModel::closeEditor,
            onSave = { viewModel.save() },
            onSaveAndChat = { viewModel.save(onSaved = onChatWith) }
        )
    }

    pendingDelete?.let { persona ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            containerColor = P.Elevated,
            title = { Text("Delete ${persona.name}?", color = P.Text) },
            text = { Text("Chats that used it will continue with the General Assistant.", color = P.Muted) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Delete", color = P.Danger) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancel", color = P.Muted) } }
        )
    }
}

/** A group of tiles: one language's tutors, or the assistants. */
data class PersonaSection(val key: String, val title: String, val language: SpeechLanguage?, val personas: List<Persona>)

private const val ASSISTANTS = "assistants"

/** Tutors by language (partner first), then the assistants (built-in first). */
fun personaSections(personas: List<Persona>): List<PersonaSection> {
    val tutors = personas.filter { it.speech != null }
        .groupBy { it.speech!! }
        .toSortedMap(compareBy { it.ordinal })
        .map { (language, list) ->
            PersonaSection(language.name, language.label, language, list.sortedBy { if (roleName(it, language) == "Partner") 0 else 1 })
        }
    val assistants = personas.filter { it.speech == null }.sortedBy { if (it.isBuiltIn) 0 else 1 }
    return tutors + PersonaSection(ASSISTANTS, "Assistants", null, assistants)
}

/** "Mandarin Teacher" → "Teacher"; a tutor named just for its language is the partner. */
fun roleName(persona: Persona, language: SpeechLanguage): String {
    val rest = persona.name.removePrefix(language.label).trim()
    return if (rest.isEmpty()) "Partner" else rest
}

/** The language's own name, beside its heading. */
fun nativeName(language: SpeechLanguage, script: ChineseScript): String = when (language) {
    SpeechLanguage.MANDARIN -> if (script == ChineseScript.TRADITIONAL) "普通話" else "普通话"
    SpeechLanguage.CANTONESE -> if (script == ChineseScript.TRADITIONAL) "廣東話" else "广东话"
    SpeechLanguage.JAPANESE -> "日本語"
    SpeechLanguage.FRENCH -> "Français"
    SpeechLanguage.GERMAN -> "Deutsch"
    SpeechLanguage.SPANISH -> "Español"
    SpeechLanguage.ITALIAN -> "Italiano"
}

/**
 * The square mark on a tile: 普 or 粤 for a Chinese partner, 师 for a teacher, the language's code
 * for the others, and a custom persona's own badge.
 */
fun personaMark(persona: Persona, language: SpeechLanguage?, script: ChineseScript): String {
    val traditional = script == ChineseScript.TRADITIONAL
    if (language == null || !persona.isBuiltIn) return persona.badge.ifBlank { "✦" }
    val teacher = roleName(persona, language) == "Teacher"
    return when (language) {
        SpeechLanguage.MANDARIN -> if (teacher) (if (traditional) "師" else "师") else "普"
        SpeechLanguage.CANTONESE -> if (teacher) (if (traditional) "師" else "师") else (if (traditional) "粵" else "粤")
        SpeechLanguage.JAPANESE -> if (teacher) "先" else "日"
        SpeechLanguage.FRENCH -> "Fr"
        SpeechLanguage.GERMAN -> "De"
        SpeechLanguage.SPANISH -> "Es"
        SpeechLanguage.ITALIAN -> "It"
    }
}

/** "Level A2 · last chat 2 days ago", "Beginner · not started yet", "last chat today". */
private fun statusLine(persona: Persona, levels: Map<SpeechLanguage, TutorLevel>, lastAt: Long?, today: LocalDate, zone: ZoneId): String {
    val ago = practisedAgo(lastAt, today, zone)
    val language = persona.speech
    val level = language?.let { levels[it] ?: TutorLevel.defaultFor(it) }?.let { l ->
        if (l.label.any { it.isDigit() }) "Level ${l.label}" else l.label
    }
    val chat = when {
        ago != null -> "last chat $ago"
        language != null -> "not started yet"
        else -> null
    }
    return listOfNotNull(level, chat).joinToString(" · ")
}

/** Which model the personas run on; a tap goes to Models. */
@Composable
private fun ModelStrip(name: String, onModels: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Memory, contentDescription = null, tint = P.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("All personas use", color = P.Faint, fontSize = 12.sp)
            Text(name.ifBlank { "the model chosen on Models" }, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onModels) { Text("Change", color = P.Accent) }
    }
}

@Composable
private fun SectionHeader(title: String, native: String?) {
    Row(Modifier.padding(top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() }
        )
        native?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, color = P.Faint, fontSize = 13.sp)
        }
    }
}

/** One persona: a tap starts a chat; ⋯ holds the rest. */
@Composable
private fun PersonaTile(
    persona: Persona,
    role: String,
    mark: String,
    status: String,
    ownModel: String?,
    onChat: () -> Unit,
    onEdit: () -> Unit,
    onModel: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 196.dp)
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .clickable(role = Role.Button, onClickLabel = "Chat with ${persona.name}", onClick = onChat)
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(P.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(mark, color = P.Accent, fontSize = if (mark.length > 1) 18.sp else 26.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "More for ${persona.name}", tint = P.Muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = P.Muted) },
                        text = { Text(if (persona.isBuiltIn) "Customize a copy" else "Edit", color = P.Text) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Memory, contentDescription = null, tint = P.Muted) },
                        text = {
                            Column {
                                Text("Model", color = P.Text)
                                Text(ownModel ?: "The usual one", color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        onClick = { menu = false; onModel() }
                    )
                    if (!persona.isBuiltIn) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = P.Danger) },
                            text = { Text("Delete", color = P.Danger) },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(role, color = P.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (persona.tagline.isNotBlank()) {
            Text(
                persona.tagline, color = P.Muted, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, end = 12.dp)
            )
        }
        if (status.isNotBlank()) Text(status, color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (persona.speech != null) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = P.Accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voice on", color = P.Accent, fontSize = 12.sp)
            }
            if (ownModel != null) {
                if (persona.speech != null) Text("  ·  ", color = P.Faint, fontSize = 12.sp)
                Text("Uses $ownModel", color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A dashed tile that makes a new persona. */
@Composable
private fun NewPersonaTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val dash = P.BorderStrong
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 196.dp)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = P.Muted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text("Your own persona", color = P.Muted, fontSize = 15.sp)
        Text("A prompt, a voice, a few starters", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The usual model, or one of this persona's own. */
@Composable
private fun ModelDialog(persona: Persona, assigned: String?, models: List<LocalModel>, onPick: (LocalModel?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Model for ${persona.name}", color = P.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ModelChoice("The usual model", "Whichever is the default on Models", assigned == null) { onPick(null) }
                models.forEach { model ->
                    ModelChoice(
                        model.displayName,
                        "${model.format.runtime} · ${ModelLibrary.formatBytes(model.sizeBytes)}",
                        assigned == model.file.name
                    ) { onPick(model) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = P.Accent) } }
    )
}

@Composable
private fun ModelChoice(title: String, detail: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (chosen) P.Accent else P.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (chosen) Box(Modifier.size(8.dp).clip(CircleShape).background(P.Accent))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = P.Faint, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaEditor(
    draft: PersonaDraft,
    onChange: ((PersonaDraft) -> PersonaDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onSaveAndChat: () -> Unit
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(P.Canvas).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    when {
                        draft.isCopy -> "Customize a copy"
                        draft.id.isBlank() -> "Create Specialist Persona"
                        else -> "Edit persona"
                    },
                    color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                if (draft.isCopy) {
                    Text("Built-in personas stay as they are; you're editing your own copy.", color = P.Muted, fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SlothField("Badge", draft.badge, { v -> onChange { it.copy(badge = v) } }, Modifier.width(90.dp))
                    SlothField("Persona Name", draft.name, { v -> onChange { it.copy(name = v) } }, Modifier.weight(1f))
                }
                SlothField("Short Description", draft.tagline, { v -> onChange { it.copy(tagline = v) } })
                SlothField(
                    "System prompt — who the persona is and how it answers",
                    draft.systemInstruction,
                    { v -> onChange { it.copy(systemInstruction = v) } },
                    singleLine = false,
                    minLines = 8
                )
                Text(
                    "With a Mandarin or Cantonese voice, the characters and Pinyin / Jyutping follow " +
                        "Settings → Chinese: they replace ${ChineseRules.PLACEHOLDER} in the prompt, or are added at the end.",
                    color = P.Faint, fontSize = 11.sp, lineHeight = 15.sp
                )

                LabeledSlider(
                    label = "Creativity (temperature)",
                    valueText = "%.2f".format(draft.temperature),
                    hint = "Lower = focused and consistent · higher = varied and playful",
                    value = draft.temperature,
                    range = 0f..1.5f,
                    steps = 14,
                    onValue = { v -> onChange { it.copy(temperature = (v * 20).roundToInt() / 20f) } }
                )
                LabeledSlider(
                    label = "Maximum reply length",
                    valueText = "${draft.maxOutputTokens} tokens",
                    hint = "About ${(draft.maxOutputTokens * 0.75).roundToInt()} English words",
                    value = draft.maxOutputTokens.toFloat(),
                    range = 128f..2048f,
                    steps = 14,
                    onValue = { v -> onChange { it.copy(maxOutputTokens = ((v / 128).roundToInt() * 128).coerceIn(128, 2048)) } }
                )

                Column {
                    Text("Read replies aloud", color = P.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf<SpeechLanguage?>(null) + SpeechLanguage.entries
                        options.forEach { option ->
                            FilterChip(
                                selected = draft.speech == option,
                                onClick = { onChange { it.copy(speech = option) } },
                                label = { Text(option?.label ?: "Off") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = P.Card,
                                    labelColor = P.Muted,
                                    selectedContainerColor = P.AccentSoft,
                                    selectedLabelColor = P.Accent
                                )
                            )
                        }
                    }
                }

                SlothField(
                    "Starter prompts — one per line (optional)",
                    draft.starterPromptsText,
                    { v -> onChange { it.copy(starterPromptsText = v) } },
                    singleLine = false,
                    minLines = 3
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = P.Muted) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSave, enabled = draft.canSave) {
                        Text("Save", color = if (draft.canSave) P.Text else P.Faint)
                    }
                    Button(
                        onClick = onSaveAndChat,
                        enabled = draft.canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Save & chat", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SlothField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = P.Text,
            unfocusedTextColor = P.Text,
            focusedContainerColor = P.Card,
            unfocusedContainerColor = P.Card,
            focusedBorderColor = P.Accent,
            unfocusedBorderColor = P.Border,
            focusedLabelColor = P.Accent,
            unfocusedLabelColor = P.Muted,
            cursorColor = P.Accent
        )
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValue: (Float) -> Unit
) {
    Column {
        Row {
            Text(label, color = P.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = P.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = P.Accent,
                activeTrackColor = P.Accent,
                inactiveTrackColor = P.Border
            )
        )
        Text(hint, color = P.Faint, fontSize = 11.sp)
    }
}
