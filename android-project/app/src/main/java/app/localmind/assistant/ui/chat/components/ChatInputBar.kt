package app.localmind.assistant.ui.chat.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.ui.chat.VoiceInputState
import app.localmind.assistant.ui.theme.SlothPalette as P

/** Reading/typing column width on tablets; phones simply use the full width. */
val ChatColumnMaxWidth = 760.dp

/**
 * The composer: one rounded container holding the text field, the persona selector
 * and the send / stop button — the layout Claude and most modern chat apps use.
 */
@Composable
fun ChatInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    editingMessage: ChatMessage?,
    onCancelEdit: () -> Unit,
    onSubmitEdit: () -> Unit,
    activePersona: Persona,
    personas: List<Persona>,
    onSelectPersona: (Persona) -> Unit,
    voiceState: VoiceInputState,
    voiceLevel: Float,
    onStartVoice: () -> Unit,
    onFinishVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    quickActions: List<QuickAction> = emptyList(),
    onQuickAction: (QuickAction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStartVoice()
    }
    val startVoice = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onStartVoice()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val canSubmit = inputText.isNotBlank()
    val submit = {
        if (canSubmit) {
            if (editingMessage != null) onSubmitEdit() else onSendMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(P.Canvas)
            // Sit above the gesture bar, and above the keyboard when it's open.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(Modifier.widthIn(max = ChatColumnMaxWidth).fillMaxWidth()) {
        // One-tap follow-ups, only when there's nothing else going on in the composer.
        val showQuick = quickActions.isNotEmpty() && inputText.isEmpty() && !isGenerating &&
            editingMessage == null && voiceState == VoiceInputState.IDLE
        AnimatedVisibility(visible = showQuick) {
            QuickActionRow(quickActions, onQuickAction, Modifier.padding(bottom = 8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(P.Elevated)
                .border(1.dp, P.Border, RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            AnimatedVisibility(visible = editingMessage != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Editing your message",
                        color = P.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancelEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = P.Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (voiceState != VoiceInputState.IDLE) {
                VoiceBar(voiceState, voiceLevel, onFinishVoice, onCancelVoice)
            } else {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    textStyle = TextStyle(color = P.Text, fontSize = 16.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(P.Accent),
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .heightIn(min = 24.dp),
                    decorationBox = { inner ->
                        Box {
                            if (inputText.isEmpty()) {
                                Text(
                                    if (editingMessage != null) "Edit your message" else "Message ${activePersona.name}",
                                    color = P.Faint, fontSize = 16.sp, lineHeight = 24.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )

                Spacer(Modifier.size(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonaChip(
                        active = activePersona,
                        personas = personas,
                        enabled = !isGenerating && editingMessage == null,
                        onSelect = onSelectPersona
                    )
                    Spacer(Modifier.weight(1f))
                    if (isGenerating) {
                        RoundAction(
                            onClick = onStopGeneration,
                            filled = true,
                            contentDescription = "Stop generating"
                        ) { tint -> Icon(Icons.Default.Stop, null, tint = tint, modifier = Modifier.size(18.dp)) }
                    } else {
                        if (editingMessage == null) {
                            RoundAction(
                                onClick = startVoice,
                                filled = false,
                                contentDescription = "Speak"
                            ) { _ -> Icon(Icons.Outlined.Mic, null, tint = P.Muted, modifier = Modifier.size(19.dp)) }
                            Spacer(Modifier.width(8.dp))
                        }
                        RoundAction(
                            onClick = submit,
                            filled = canSubmit,
                            enabled = canSubmit,
                            contentDescription = "Send"
                        ) { tint -> Icon(Icons.Default.ArrowUpward, null, tint = tint, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        }
    }
}

/** Shown in place of the text field while listening or transcribing. */
@Composable
private fun VoiceBar(state: VoiceInputState, level: Float, onFinish: () -> Unit, onCancel: () -> Unit) {
    val listening = state == VoiceInputState.LISTENING
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (listening) {
            // A dot that swells with the voice: simple feedback that the mic is hearing you.
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size((10 + 18 * level).dp)
                        .clip(CircleShape)
                        .background(P.Danger.copy(alpha = 0.25f + 0.5f * level))
                )
                Box(Modifier.size(10.dp).clip(CircleShape).background(P.Danger))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Listening…", color = P.Text, fontSize = 15.sp)
                Text("Tap ✓ when you're done", color = P.Faint, fontSize = 12.sp)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Discard recording", tint = P.Muted)
            }
            RoundAction(onClick = onFinish, filled = true, contentDescription = "Done speaking") { tint ->
                Icon(Icons.Default.Check, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        } else {
            CircularProgressIndicator(color = P.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Turning speech into text…", color = P.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = P.Muted)
            }
        }
    }
}

@Composable
private fun RoundAction(
    onClick: () -> Unit,
    filled: Boolean,
    contentDescription: String,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (filled) P.Accent else P.Border)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(if (filled) P.OnAccent else P.Faint)
    }
}

@Composable
private fun PersonaChip(
    active: Persona,
    personas: List<Persona>,
    enabled: Boolean,
    onSelect: (Persona) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { menuOpen = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(active.badge, color = P.Accent, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                active.name, color = P.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose persona", tint = P.Faint, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            personas.forEach { persona ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "${persona.badge}  ${persona.name}",
                                color = if (persona.id == active.id) P.Accent else P.Text,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                            if (persona.tagline.isNotBlank()) {
                                Text(persona.tagline, color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onSelect(persona)
                    }
                )
            }
        }
    }
}
