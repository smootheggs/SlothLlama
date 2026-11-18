package app.localmind.assistant.ui.dictionary

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.dictionary.DictionaryEntry
import app.localmind.assistant.data.dictionary.Pinyin
import app.localmind.assistant.data.dictionary.WordLookup
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * A word from the dictionary: characters, Pinyin and Jyutping, meanings, and Listen / Practise /
 * Save. [language] decides which reading leads and which voice reads it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordLookupSheet(
    lookup: WordLookup,
    script: ChineseScript,
    language: SpeechLanguage,
    onSpeak: (String) -> Unit,
    onPractise: (String) -> Unit,
    onSave: (DictionaryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = P.Card) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            val spoken = lookup.entries.firstOrNull()?.let { if (script == ChineseScript.TRADITIONAL) it.traditional else it.simplified }
                ?: lookup.word
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(Icons.AutoMirrored.Filled.VolumeUp, "Listen") { onSpeak(spoken) }
                SheetAction(Icons.Outlined.RecordVoiceOver, "Practise saying it") { onPractise(spoken) }
            }
            Spacer(Modifier.height(12.dp))

            if (lookup.entries.isEmpty()) {
                // A Cantonese word CC-CEDICT doesn't have: its reading, then each character.
                Text(lookup.word, color = P.Text, fontSize = 36.sp, fontWeight = FontWeight.Medium)
                lookup.cantoneseReading?.let { Text("Jyutping  $it", color = P.Accent, fontSize = 16.sp) }
                Text("Cantonese word · meanings of each character:", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                lookup.characters.forEach { entry ->
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 12.dp))
                    EntryBlock(entry, script, language, compact = true, onSave = onSave)
                }
            } else {
                lookup.entries.take(MAX_ENTRIES).forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 14.dp))
                    EntryBlock(entry, script, language, compact = i > 0, onSave = onSave)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("CC-CEDICT · CC BY-SA 4.0", color = P.Faint, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EntryBlock(
    entry: DictionaryEntry,
    script: ChineseScript,
    language: SpeechLanguage,
    compact: Boolean,
    onSave: (DictionaryEntry) -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.headword(script, language),
                color = P.Text, fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.Medium
            )
            val pinyin = Pinyin.marked(entry.pinyin)
            val lines = if (language == SpeechLanguage.CANTONESE) {
                listOfNotNull(entry.jyutping.takeIf { it.isNotBlank() }?.let { "Jyutping  $it" }, "Pinyin  $pinyin")
            } else {
                listOfNotNull("Pinyin  $pinyin", entry.jyutping.takeIf { it.isNotBlank() }?.let { "Jyutping  $it" })
            }
            lines.forEachIndexed { i, line ->
                Text(line, color = if (i == 0) P.Accent else P.Muted, fontSize = if (i == 0) 16.sp else 13.sp)
            }
        }
        SheetAction(Icons.Outlined.StarOutline, "Save to flashcards", showLabel = false) { onSave(entry) }
    }
    Spacer(Modifier.height(8.dp))
    entry.definitions.take(if (compact) 4 else 10).forEach { d ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text("•", color = P.Faint, fontSize = 15.sp, modifier = Modifier.width(14.dp))
            Text(d, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, showLabel: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, P.BorderStrong, shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = if (showLabel) null else label, tint = P.Text, modifier = Modifier.size(18.dp))
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = P.Text, fontSize = 14.sp)
        }
    }
}

private const val MAX_ENTRIES = 6
