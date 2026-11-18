package app.localmind.assistant.ui.study

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.data.study.Scheduler
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.models.SlothCard
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P

@Composable
fun FlashcardsScreen(viewModel: FlashcardsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val speaker = rememberTutorSpeaker()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        SlothTopBar(
            title = "Flashcards",
            subtitle = "${state.dueCount} due · ${state.cards.size} cards",
            onBack = onBack
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val current = state.current
                    when {
                        state.cards.isEmpty() -> EmptyCard()
                        current == null -> CaughtUpCard(state.cards, state.now)
                        else -> ReviewCard(
                            card = current,
                            revealed = state.revealed,
                            now = state.now,
                            onReveal = viewModel::reveal,
                            onGrade = viewModel::grade,
                            onSpeak = { speaker.speak(current.term, current.language.speech) }
                        )
                    }
                }
                if (state.cards.isNotEmpty()) {
                    item {
                        Text(
                            "ALL CARDS · ${state.cards.size}",
                            color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                    items(state.cards.sortedBy { it.term }, key = { it.id }) { card ->
                        CardRow(card, state.now, onDelete = { viewModel.delete(card) })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun ReviewCard(
    card: Flashcard,
    revealed: Boolean,
    now: Long,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onSpeak: () -> Unit
) {
    SlothCard {
        Text(card.language.label.uppercase(), color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(card.term, color = P.Text, fontSize = 40.sp, lineHeight = 48.sp, textAlign = TextAlign.Center)
            IconButton(onClick = onSpeak) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Say it", tint = P.Muted)
            }
            if (revealed) {
                if (card.pronunciation.isNotBlank()) {
                    Text(card.pronunciation, color = P.Accent, fontSize = 20.sp, textAlign = TextAlign.Center)
                }
                if (card.meaning.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(card.meaning, color = P.Text, fontSize = 17.sp, textAlign = TextAlign.Center)
                }
                if (card.example.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(card.example, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                }
            }
        }
        if (!revealed) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(P.Accent)
                    .clickable(onClick = onReveal)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Show answer", color = P.OnAccent, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Grade.entries.forEach { grade ->
                    val tint = when (grade) {
                        Grade.AGAIN -> P.Danger
                        Grade.HARD -> P.Warning
                        Grade.GOOD -> P.Success
                        Grade.EASY -> P.Accent
                    }
                    GradeButton(
                        label = grade.label,
                        hint = Scheduler.label(Scheduler.next(card, grade, now), now),
                        tint = tint,
                        modifier = Modifier.weight(1f),
                        onClick = { onGrade(grade) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, hint: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = P.Faint, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyCard() {
    SlothCard {
        Text("No flashcards yet", color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "In a chat with a tutor, tap ☆ under a reply to save its new words (the ⭐ words and word lists). " +
                "They come back here for review just before you'd forget them.",
            color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
        )
    }
}

@Composable
private fun CaughtUpCard(cards: List<Flashcard>, now: Long) {
    val next = cards.minOfOrNull { it.dueAt }
    SlothCard {
        Text("All caught up", color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (next != null) "Next review ${dueText(next, now)}." else "Nothing scheduled.",
            color = P.Muted, fontSize = 13.sp
        )
    }
}

@Composable
private fun CardRow(card: Flashcard, now: Long, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(P.Card)
            .border(1.dp, P.Border, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(card.term, color = P.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOf(card.pronunciation, card.meaning).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${card.language.label} · ${if (card.isDue(now)) "due now" else "due " + dueText(card.dueAt, now)}",
                color = P.Faint, fontSize = 11.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete card", tint = P.Faint, modifier = Modifier.size(18.dp))
        }
    }
}

private fun dueText(dueAt: Long, now: Long): String {
    val minutes = (dueAt - now) / 60_000
    return when {
        minutes <= 0 -> "now"
        minutes < 60 -> "in $minutes min"
        minutes < 24 * 60 -> "in ${minutes / 60} h"
        else -> "in ${minutes / (24 * 60)} days"
    }
}
