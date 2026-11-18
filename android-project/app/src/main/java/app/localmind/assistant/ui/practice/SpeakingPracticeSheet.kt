package app.localmind.assistant.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.speech.TutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * Repeat after me: the sentence, its reading, Listen (read slowly), then the mic. After speaking,
 * each character turns green (heard) or red (missed), with a score.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeakingPracticeSheet(
    target: String,
    language: SpeechLanguage,
    speaker: TutorSpeaker,
    onDismiss: () -> Unit
) {
    val vm: SpeakingPracticeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(target, language) { vm.open(target, language) }
    DisposableEffect(Unit) {
        onDispose {
            vm.close()
            speaker.stop()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.listen()
    }
    val listen = {
        speaker.stop()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = P.Card) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("REPEAT AFTER ME", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(12.dp))

            // The sentence; after a try, coloured by what was heard.
            val score = state.score
            if (score != null && state.phase == PracticePhase.RESULT) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(if (score.spaced) 8.dp else 0.dp)) {
                    score.units.forEach { u ->
                        Text(
                            u.text,
                            color = if (u.heard) P.Success else P.Danger,
                            fontSize = if (score.spaced) 26.sp else 30.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(state.target, color = P.Text, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
            }
            if (state.reading.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(state.reading, color = P.Muted, fontSize = 16.sp, lineHeight = 22.sp)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeButton(Icons.AutoMirrored.Filled.VolumeUp, "Listen", filled = false) {
                    speaker.speak(state.target, language)
                }
                when (state.phase) {
                    PracticePhase.LISTENING -> PracticeButton(Icons.Default.Stop, "Done", filled = true, onClick = vm::finishListening)
                    PracticePhase.CHECKING -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = P.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                    else -> PracticeButton(
                        Icons.Outlined.Mic,
                        if (state.phase == PracticePhase.RESULT) "Try again" else "Say it",
                        filled = true,
                        enabled = state.canListen,
                        onClick = listen
                    )
                }
                if (state.phase == PracticePhase.LISTENING) {
                    // Swells with the voice, so it's clear the mic is hearing you.
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size((10 + 18 * level).dp)
                                .clip(CircleShape)
                                .background(P.Danger.copy(alpha = 0.25f + 0.5f * level))
                        )
                        Box(Modifier.size(10.dp).clip(CircleShape).background(P.Danger))
                    }
                    Text("Listening… pause when you're done", color = P.Muted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            when {
                !state.canListen -> Text(
                    "To check your speaking, import the voice model first: Models → Voice input → Import. " +
                        "Listen works already.",
                    color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
                score != null && state.phase == PracticePhase.RESULT -> {
                    Text(
                        "${score.percent}% · ${score.heardCount} of ${score.units.size} ${if (score.spaced) "words" else "characters"} heard",
                        color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            score.percent >= 90 -> "Excellent. That came through clearly."
                            score.percent >= 70 -> "Nearly there. Listen again and try the red ones."
                            else -> "Keep going. Listen once more, then say it slowly."
                        },
                        color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    if (state.heard.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Heard: ${state.heard}", color = P.Faint, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                state.message != null -> Text(state.message!!, color = P.Warning, fontSize = 13.sp, lineHeight = 19.sp)
                else -> Text(
                    "Tap Listen, then Say it. It stops listening by itself when you pause.",
                    color = P.Faint, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
    }
}

/** Pick which sentence of a reply to practise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSentencePicker(sentences: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = P.Card) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "WHICH SENTENCE?", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            sentences.forEach { s ->
                Text(
                    s,
                    color = P.Text, fontSize = 20.sp, lineHeight = 28.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(s) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PracticeButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(if (enabled) P.Accent else P.Elevated) else Modifier.border(1.dp, P.BorderStrong, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = when {
            !enabled -> P.Faint
            filled -> P.OnAccent
            else -> P.Text
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
