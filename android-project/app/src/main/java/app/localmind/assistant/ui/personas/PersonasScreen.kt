package app.localmind.assistant.ui.personas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
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
import app.localmind.assistant.domain.persona.Persona
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
    onBack: () -> Unit
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val modelAssignments by viewModel.modelAssignments.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        SlothTopBar(
            title = "Personas",
            subtitle = "Apply any persona to any model",
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
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        assignedModel = modelAssignments[persona.id],
                        models = models,
                        onPickModel = { viewModel.setModel(persona, it) },
                        onChat = { onChatWith(persona.id) },
                        onEdit = { viewModel.edit(persona) },
                        onDelete = { viewModel.requestDelete(persona) }
                    )
                }
            }
        }
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

@Composable
private fun PersonaCard(
    persona: Persona,
    assignedModel: String?,
    models: List<LocalModel>,
    onPickModel: (LocalModel?) -> Unit,
    onChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SlothCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(P.Elevated).border(1.dp, P.Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(persona.badge, color = P.Accent, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.name, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (persona.tagline.isNotBlank()) {
                    Text(persona.tagline, color = P.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(if (persona.isBuiltIn) "Built-in" else "Custom", if (persona.isBuiltIn) P.Muted else P.Accent)
                    Tag("temp ${"%.1f".format(persona.temperature)}", P.Muted)
                    persona.speech?.let { Tag("🔊 ${it.label}", P.Success) }
                }
                Spacer(Modifier.height(8.dp))
                ModelPicker(assignedModel, models, onPickModel)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onChat) { Text("Chat with Persona", color = P.Accent, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(if (persona.isBuiltIn) "Customize" else "Edit", color = P.Muted) }
            if (!persona.isBuiltIn) {
                TextButton(onClick = onDelete) { Text("Delete", color = P.Danger) }
            }
        }
    }
}

/** "Model: Default ▾": the persona runs on the default model unless one is picked here. */
@Composable
private fun ModelPicker(assignedModel: String?, models: List<LocalModel>, onPick: (LocalModel?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = when (assignedModel) {
        null -> "Default model"
        else -> models.firstOrNull { it.file.name == assignedModel }?.displayName ?: "$assignedModel (missing)"
    }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (assignedModel == null) P.Border else P.Accent, RoundedCornerShape(8.dp))
                .clickable { menuOpen = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Memory, contentDescription = null, tint = P.Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label, color = if (assignedModel == null) P.Muted else P.Text, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp)
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose model", tint = P.Muted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Default model", color = P.Text, fontSize = 14.sp)
                        Text("Whichever is the default on the Models screen", color = P.Faint, fontSize = 11.sp)
                    }
                },
                onClick = { menuOpen = false; onPick(null) }
            )
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName, color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${model.format.runtime} · ${ModelLibrary.formatBytes(model.sizeBytes)}",
                                color = P.Faint, fontSize = 11.sp
                            )
                        }
                    },
                    onClick = { menuOpen = false; onPick(model) }
                )
            }
        }
    }
}

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(P.Elevated)
            .border(1.dp, P.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
