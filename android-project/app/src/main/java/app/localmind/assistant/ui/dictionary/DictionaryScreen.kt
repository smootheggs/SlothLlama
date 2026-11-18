package app.localmind.assistant.ui.dictionary

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.dictionary.DictionaryEntry
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.practice.SpeakingPracticeSheet
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P

/** Offline dictionary: search by characters, pinyin (with or without tones) or English. */
@Composable
fun DictionaryScreen(viewModel: DictionaryViewModel, onBack: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val lookup by viewModel.lookup.collectAsStateWithLifecycle()
    val style by viewModel.style.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val speaker = rememberTutorSpeaker()
    val snackbar = remember { SnackbarHostState() }
    var cantonese by rememberSaveable { mutableStateOf(false) }
    var practise by remember { mutableStateOf<String?>(null) }
    val language = if (cantonese) SpeechLanguage.CANTONESE else SpeechLanguage.MANDARIN
    val focus = remember { FocusRequester() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(Unit) { if (viewModel.isAvailable) runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { viewModel.dismiss() } }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(title = "Dictionary", subtitle = "CC-CEDICT · offline", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(P.Elevated)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = P.Faint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            enabled = viewModel.isAvailable,
                            textStyle = TextStyle(color = P.Text, fontSize = 17.sp),
                            cursorBrush = SolidColor(P.Accent),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) Text("汉字, pinyin (nihao) or English", color = P.Faint, fontSize = 17.sp)
                                    inner()
                                }
                            }
                        )
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close, contentDescription = "Clear", tint = P.Faint,
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable { viewModel.onQueryChange("") }
                            )
                        }
                    }
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageChip("Mandarin", selected = !cantonese) { cantonese = false }
                        LanguageChip("Cantonese", selected = cantonese) { cantonese = true }
                    }
                    when {
                        !viewModel.isAvailable -> Hint("This build doesn't include the dictionary. It's added when GitHub builds the app.")
                        query.isBlank() -> Hint(
                            "Look up a word by its characters, by pinyin with or without tones (\"nihao\", \"ni3 hao3\"), " +
                                "or by English. In a chat, tap any Chinese character in a reply to look it up."
                        )
                        results.isEmpty() -> Hint("No matches for \"$query\".")
                        else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                            items(results, key = { "${it.traditional}|${it.simplified}|${it.pinyin}" }) { entry ->
                                ResultRow(entry, style.script, language) { viewModel.open(entry) }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    lookup?.let { found ->
        WordLookupSheet(
            lookup = found,
            script = style.script,
            language = language,
            onSpeak = { speaker.speak(it, language) },
            onPractise = {
                viewModel.dismiss()
                practise = it
            },
            onSave = { viewModel.save(it, language) },
            onDismiss = viewModel::dismiss
        )
    }
    practise?.let { target ->
        SpeakingPracticeSheet(target = target, language = language, speaker = speaker, onDismiss = { practise = null })
    }
}

@Composable
private fun ResultRow(
    entry: DictionaryEntry,
    script: app.localmind.assistant.domain.persona.ChineseScript,
    language: SpeechLanguage,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(entry.headword(script, language), color = P.Text, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(entry.reading(language), color = P.Accent, fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(
            entry.definitions.joinToString("; "),
            color = P.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        label,
        color = if (selected) P.OnAccent else P.Muted,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(P.Accent) else Modifier.border(1.dp, P.Border, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
}
