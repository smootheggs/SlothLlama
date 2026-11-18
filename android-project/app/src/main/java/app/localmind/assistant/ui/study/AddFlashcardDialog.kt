package app.localmind.assistant.ui.study

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.data.study.WordCandidate
import app.localmind.assistant.data.study.WordCandidates
import app.localmind.assistant.ui.theme.SlothPalette as P

/** Provided by the chat screen: opens the "save to flashcards" dialog for a reply's text. */
val LocalSaveWordAction = compositionLocalOf<((String) -> Unit)?> { null }

/**
 * Lets the learner save words from a tutor reply. The ⭐ words and word-list entries are offered
 * as chips that fill in the fields; everything stays editable, and several can be saved in turn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFlashcardDialog(
    replyText: String,
    defaultLanguage: StudyLanguage,
    onSave: (language: StudyLanguage, term: String, pronunciation: String, meaning: String, example: String) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = remember(replyText) { WordCandidates.from(replyText, chinese = defaultLanguage.speech.isChinese) }
    val saved = remember { mutableStateListOf<String>() }
    var language by remember { mutableStateOf(defaultLanguage) }
    // Start with the first word the tutor introduced.
    val first = candidates.firstOrNull()
    var term by remember { mutableStateOf(first?.term.orEmpty()) }
    var pronunciation by remember { mutableStateOf(first?.pronunciation.orEmpty()) }
    var meaning by remember { mutableStateOf(first?.meaning.orEmpty()) }
    var example by remember { mutableStateOf(first?.example.orEmpty()) }

    fun fill(c: WordCandidate?) {
        term = c?.term.orEmpty()
        pronunciation = c?.pronunciation.orEmpty()
        meaning = c?.meaning.orEmpty()
        example = c?.example.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Save to flashcards", color = P.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isNotEmpty()) {
                    Text("From this reply", color = P.Faint, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidates.forEach { c ->
                            Chip(
                                label = if (c.term in saved) "✓ ${c.term}" else c.term,
                                selected = term == c.term,
                                onClick = { fill(c) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyLanguage.entries.forEach { lang ->
                        Chip(lang.label, selected = language == lang, onClick = { language = lang })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Field("Word", term) { term = it }
                Field(language.romanization.ifEmpty { "Pronunciation (optional)" }, pronunciation) { pronunciation = it }
                Field("Meaning", meaning) { meaning = it }
                Field("Example (optional)", example, singleLine = false) { example = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = term.isNotBlank(),
                onClick = {
                    onSave(language, term, pronunciation, meaning, example)
                    saved += term.trim()
                    // Move on to the next unsaved word, or close when there are none left.
                    val next = candidates.firstOrNull { it.term !in saved }
                    if (next != null) fill(next) else onDismiss()
                }
            ) { Text("Save", color = if (term.isNotBlank()) P.Accent else P.Faint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (saved.isEmpty()) "Cancel" else "Done", color = P.Muted) }
        }
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) P.Accent else P.Muted,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) P.Accent else P.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
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
