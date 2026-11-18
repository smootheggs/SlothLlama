package app.localmind.assistant.ui.chat.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.ui.theme.SlothPalette as P

/** A one-tap follow-up: a short label and the message it sends. */
data class QuickAction(val label: String, val prompt: String)

object QuickActions {
    private val TUTOR = listOf(
        QuickAction("Correct me", "Please check my last message: correct any mistakes and explain each one briefly."),
        QuickAction("Quiz me", "Quiz me on the words and phrases we've used so far. One question at a time."),
        QuickAction("Simpler", "That was a bit hard for me. Please say it again with simpler words."),
        QuickAction("Grammar", "Explain the grammar in your last reply, with two more examples."),
        QuickAction("Role-play", "Let's role-play a real situation, like ordering food at a restaurant. You start."),
        QuickAction("New words", "Teach me three useful new words related to what we're talking about.")
    )
    private val GENERAL = listOf(
        QuickAction("Simpler", "Explain that more simply."),
        QuickAction("Shorter", "Summarize that in three short bullet points."),
        QuickAction("Examples", "Give me a few concrete examples."),
        QuickAction("In Chinese", "Say your last reply in simple Mandarin Chinese, with Pinyin.")
    )

    fun forPersona(persona: Persona): List<QuickAction> = if (persona.speech != null) TUTOR else GENERAL
}

/** Shown above the message box once a chat is under way. */
@Composable
fun QuickActionRow(actions: List<QuickAction>, onAction: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            Text(
                action.label,
                color = P.Muted, fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, P.Border, RoundedCornerShape(16.dp))
                    .clickable { onAction(action) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
