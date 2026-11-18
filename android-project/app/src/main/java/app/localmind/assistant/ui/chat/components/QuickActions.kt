package app.localmind.assistant.ui.chat.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.theme.SlothPalette as P

/** A one-tap follow-up: a short label, its icon, and the message it sends (or a local action). */
data class QuickAction(
    val label: String,
    val prompt: String,
    val icon: ImageVector,
    val kind: Kind = Kind.SEND
) {
    enum class Kind {
        /** Sends [prompt] as the next message. */
        SEND,
        /** Saves the last reply to Notes; nothing is sent. */
        SAVE_NOTE
    }
}

/**
 * The chips above the message box: what fits the persona (tutors get language-specific ones:
 * Pinyin, furigana, German cases, tu/vous…) and the last reply (code, a long answer).
 */
object QuickActions {

    private val SAVE_NOTE = QuickAction("Save to Notes", "", Icons.Outlined.StickyNote2, QuickAction.Kind.SAVE_NOTE)

    fun forPersona(persona: Persona, lastReply: String? = null): List<QuickAction> {
        val reply = lastReply.orEmpty()
        val language = persona.speech
        return if (language != null) tutor(language, reply) else general(reply)
    }

    private fun tutor(language: SpeechLanguage, reply: String): List<QuickAction> {
        val long = reply.length > 700
        val common = listOf(
            QuickAction("Correct me", "Please check my last message: correct any mistakes and explain each one briefly.", Icons.Outlined.Spellcheck),
            QuickAction("Say it naturally", "How would a native speaker say what I just wrote? Give the natural version and explain the differences briefly.", Icons.Outlined.RecordVoiceOver),
            QuickAction("Translate", "Translate your last reply into English, line by line.", Icons.Outlined.Translate),
            QuickAction("Simpler", "That was a bit hard for me. Please say it again with simpler words.", Icons.Outlined.Lightbulb)
        )
        val practice = listOf(
            QuickAction("Quiz me", "Quiz me on the words and phrases we've used so far. One question at a time.", Icons.Outlined.Quiz),
            QuickAction("Your turn", "Ask me a question I can answer at my level, then wait for my answer.", Icons.Outlined.QuestionAnswer),
            QuickAction("Grammar", "Explain the grammar in your last reply, with two more examples.", Icons.Outlined.School),
            QuickAction("New words", "Teach me three useful new words related to what we're talking about.", Icons.Outlined.Abc),
            QuickAction("Role-play", "Let's role-play a real situation, like ordering food at a restaurant. You start.", Icons.Outlined.TheaterComedy)
        )
        val special = when (language) {
            SpeechLanguage.MANDARIN, SpeechLanguage.CANTONESE -> {
                val romanization = if (language == SpeechLanguage.CANTONESE) "Jyutping" else "Pinyin"
                listOf(
                    QuickAction(romanization, "Write your last reply again with the $romanization under every Chinese line.", Icons.Outlined.TextFields),
                    QuickAction("Characters", "Break down the key characters in your last reply: what each means, and one more word that uses it.", Icons.Outlined.Style)
                )
            }
            SpeechLanguage.JAPANESE -> listOf(
                QuickAction("Furigana", "Write your last reply again with the kana reading after each kanji word, like 漢字(かんじ).", Icons.Outlined.TextFields),
                QuickAction("Polite / casual", "Show your last reply in both polite (です・ます) and casual Japanese.", Icons.Outlined.Forum)
            )
            SpeechLanguage.GERMAN -> listOf(
                QuickAction("Cases", "Explain which cases (Nominativ, Akkusativ, Dativ, Genitiv) appear in your last reply, and why.", Icons.Outlined.Balance),
                formality("du", "Sie")
            )
            SpeechLanguage.FRENCH -> listOf(verbs(), formality("tu", "vous"))
            SpeechLanguage.SPANISH -> listOf(verbs(), formality("tú", "usted"))
            SpeechLanguage.ITALIAN -> listOf(verbs(), formality("tu", "Lei"))
        }
        // A long reply: make it easier first; otherwise lead with the language's own helpers.
        val first = if (long) common.take(4).sortedByDescending { it.label == "Simpler" || it.label == "Translate" } else common
        return first.take(2) + special + first.drop(2) + practice + SAVE_NOTE
    }

    private fun verbs() = QuickAction(
        "Verbs", "Take the main verbs in your last reply and show their present, past and future forms.", Icons.Outlined.Bolt
    )

    private fun formality(informal: String, formal: String) = QuickAction(
        "$informal / $formal", "Say your last reply both informally ($informal) and formally ($formal).", Icons.Outlined.Forum
    )

    private fun general(reply: String): List<QuickAction> {
        val list = mutableListOf(
            QuickAction("Simpler", "Explain that more simply.", Icons.Outlined.Lightbulb),
            QuickAction("Shorter", "Summarize that in three short bullet points.", Icons.Outlined.Compress),
            QuickAction("Examples", "Give me a few concrete examples.", Icons.Outlined.AutoAwesome),
            QuickAction("Step by step", "Walk me through that step by step.", Icons.Outlined.Checklist),
            QuickAction("Pros & cons", "What are the pros and cons? Be balanced and concrete.", Icons.Outlined.Balance),
            QuickAction("Go deeper", "Go deeper: what are the less obvious points, and what would an expert add?", Icons.Outlined.Psychology),
            QuickAction("Table", "Put that in a table.", Icons.Outlined.TableChart),
            QuickAction("As an email", "Turn that into a short, friendly email I can send.", Icons.Outlined.Email),
            QuickAction("Ask me", "Ask me a few questions so you can tailor that to my situation.", Icons.Outlined.QuestionAnswer),
            QuickAction("In Chinese", "Say your last reply in simple Mandarin Chinese, with Pinyin.", Icons.Outlined.Translate)
        )
        if (reply.contains("```")) {
            list.add(0, QuickAction("Explain the code", "Explain that code line by line, and point out anything that could go wrong.", Icons.Outlined.Code))
        }
        if (reply.length > 1500) {
            // A long answer: offer the short version first.
            list.sortByDescending { it.label == "Shorter" }
        }
        return list + SAVE_NOTE
    }
}

/** Shown above the message box once a chat is under way; scrolls sideways. */
@Composable
fun QuickActionRow(actions: List<QuickAction>, onAction: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .heightIn(min = 34.dp)
                    .clip(shape)
                    .border(1.dp, P.Border, shape)
                    .clickable { onAction(action) }
                    .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(action.icon, contentDescription = null, tint = P.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(action.label, color = P.Muted, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
