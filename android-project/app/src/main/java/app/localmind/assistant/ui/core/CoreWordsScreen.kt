package app.localmind.assistant.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.core.CoreBand
import app.localmind.assistant.domain.core.CoreLanguage
import app.localmind.assistant.domain.core.CoreMark
import app.localmind.assistant.domain.core.CorePos
import app.localmind.assistant.domain.core.CoreWord
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.chat.components.LocalChineseScale
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.practice.SpeakingPracticeSheet
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P

/** The voice that reads a list's words. */
fun CoreLanguage.speech(): SpeechLanguage = when (this) {
    CoreLanguage.MANDARIN -> SpeechLanguage.MANDARIN
    CoreLanguage.CANTONESE -> SpeechLanguage.CANTONESE
    CoreLanguage.FRENCH -> SpeechLanguage.FRENCH
}

/**
 * Core words: each language's most-used words, by part of speech and frequency, with a card
 * for each (reading, gloss, notes, conjugation). On a wide screen the card sits beside the list.
 */
@Composable
fun CoreWordsScreen(
    viewModel: CoreWordsViewModel,
    onBack: () -> Unit,
    /** Opens the language's tutor with [String] in the chat box (nothing is sent). */
    onAsk: (SpeechLanguage, String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val card by viewModel.card.collectAsStateWithLifecycle()
    val style by viewModel.style.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val speaker = rememberTutorSpeaker()
    val snackbar = remember { SnackbarHostState() }
    var practise by remember { mutableStateOf<String?>(null) }
    val language = state.filters.language

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val cardActions = CardActions(
        onSpeak = { speaker.speak(it, language.speech()) },
        onPractise = { practise = it },
        onMark = viewModel::setMark,
        onFlashcard = viewModel::addToFlashcards,
        onAsk = { word -> onAsk(language.speech(), viewModel.askPrompt(word)) }
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(P.Canvas)) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(title = "Core words", subtitle = language.source, onBack = onBack)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    ListPane(
                        state = state,
                        script = style.script,
                        selected = card,
                        onLanguage = viewModel::setLanguage,
                        onQuery = viewModel::setQuery,
                        onPos = viewModel::setPos,
                        onBand = viewModel::setBand,
                        onMarks = viewModel::setMarkFilter,
                        onOpen = viewModel::openWord
                    )
                }
                val shown = card
                if (wide) {
                    Box(
                        Modifier
                            .width(420.dp)
                            .fillMaxHeight()
                            .padding(top = 12.dp, end = 20.dp, bottom = 20.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(P.Card)
                    ) {
                        if (shown != null) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                                WordCard(shown, language, style.script, state.marks, cardActions)
                            }
                        } else {
                            Text(
                                "Tap a word to see its card.",
                                color = P.Faint, fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))

        val shown = card
        if (!wide && shown != null) {
            WordSheet(shown, language, style.script, state.marks, cardActions, onDismiss = viewModel::closeWord)
        }
    }

    practise?.let { target ->
        SpeakingPracticeSheet(target = target, language = language.speech(), speaker = speaker, onDismiss = { practise = null })
    }
}

class CardActions(
    val onSpeak: (String) -> Unit,
    val onPractise: (String) -> Unit,
    val onMark: (CoreWord, CoreMark) -> Unit,
    val onFlashcard: (CoreWord) -> Unit,
    val onAsk: (CoreWord) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordSheet(
    word: CoreWord,
    language: CoreLanguage,
    script: ChineseScript,
    marks: Map<String, CoreMark>,
    actions: CardActions,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = P.Card) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            WordCard(word, language, script, marks, actions)
        }
    }
}

@Composable
private fun ListPane(
    state: CoreUiState,
    script: ChineseScript,
    selected: CoreWord?,
    onLanguage: (CoreLanguage) -> Unit,
    onQuery: (String) -> Unit,
    onPos: (CorePos?) -> Unit,
    onBand: (CoreBand) -> Unit,
    onMarks: (MarkFilter) -> Unit,
    onOpen: (CoreWord) -> Unit
) {
    val f = state.filters
    val listState = rememberLazyListState()
    // A new filter starts the list from the top.
    LaunchedEffect(f.language, f.pos, f.band, f.marks, f.query) { listState.scrollToItem(0) }

    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoreLanguage.entries.forEach { l -> Chip(l.label, selected = l == f.language) { onLanguage(l) } }
        }
        SearchBox(f.query, f.language, onQuery)
        if (f.query.isBlank()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip("All words", selected = f.pos == null, small = true) { onPos(null) }
                state.partsOfSpeech.forEach { p -> Chip(p.plural, selected = f.pos == p, small = true) { onPos(p) } }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoreBand.entries.forEach { b -> Chip(b.label, selected = f.band == b, small = true) { onBand(b) } }
                Spacer(Modifier.width(10.dp))
                MarkFilter.entries.forEach { m -> Chip(m.label, selected = f.marks == m, small = true, quiet = true) { onMarks(m) } }
            }
        }
        if (!state.loading && !state.failed) Gauge(state)
        when {
            state.failed -> Hint("This build doesn't include the word lists.")
            state.loading -> Hint("Loading…")
            state.rows.isEmpty() && f.query.isNotBlank() -> Hint("No word in the ${f.language.label} list matches \"${f.query}\".")
            state.rows.isEmpty() -> Hint(
                when (f.marks) {
                    MarkFilter.KNOWN -> "No words marked known here yet. Open a word's card to mark it."
                    MarkFilter.LEARNING -> "No words marked learning here yet."
                    else -> "Nothing here."
                }
            )
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)) {
                items(state.rows, key = { it.key + "#" + it.rank }) { w ->
                    val mark = state.marks[f.language.code + ":" + w.key] ?: CoreMark.NEW
                    WordRow(w, f.language, script, mark, selected = selected != null && selected.key == w.key && selected.rank == w.rank) { onOpen(w) }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, language: CoreLanguage, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(P.Elevated)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = P.Faint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = P.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(P.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            when (language) {
                                CoreLanguage.MANDARIN -> "汉字, pinyin or English"
                                CoreLanguage.CANTONESE -> "漢字, jyutping or English"
                                CoreLanguage.FRENCH -> "A word in any form (allons, yeux) or English"
                            },
                            color = P.Faint, fontSize = 16.sp
                        )
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close, contentDescription = "Clear", tint = P.Faint,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable { onQuery("") }
            )
        }
    }
}

/** Known words and how much of everyday text they cover, as a quiet line and a thin bar. */
@Composable
private fun Gauge(state: CoreUiState) {
    val percent = (state.coverage * 100).toInt()
    val line = if (state.knownCount == 0) "Mark the words you know; this shows how much of everyday ${state.filters.language.label} they cover."
    else "${state.knownCount} known" + (if (state.learningCount > 0) " · ${state.learningCount} learning" else "") +
        " · about $percent% of everyday text"
    Column(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
        Text(line, color = P.Muted, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
        if (state.knownCount > 0) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(P.Border)) {
                Box(Modifier.fillMaxWidth(state.coverage.toFloat().coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(P.Accent))
            }
        }
    }
}

@Composable
private fun WordRow(
    word: CoreWord,
    language: CoreLanguage,
    script: ChineseScript,
    mark: CoreMark,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale = LocalChineseScale.current
    val chinese = language != CoreLanguage.FRENCH
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(P.AccentSoft) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            word.rank.toString(), color = P.Faint, fontSize = 12.sp,
            style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.width(44.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    CoreText.headword(word, language, script, withArticle = false),
                    color = P.Text, fontSize = if (chinese) (20 * scale).sp else 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val reading = CoreText.reading(word, language)
                if (reading.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(reading, color = P.Accent, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(posLine(word), color = P.Faint, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(word.gloss, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MarkDot(mark)
    }
}

/** "verb", "noun · m", "measure word". */
private fun posLine(word: CoreWord): String {
    val p = word.partsOfSpeech.firstOrNull()?.label.orEmpty()
    return if (word.gender != null) "$p · ${word.gender}" else p
}

/** Known: a small green dot. Learning: a moss ring. New: nothing. Said in words for TalkBack. */
@Composable
internal fun MarkDot(mark: CoreMark) {
    Box(
        Modifier.size(28.dp).semantics { contentDescription = mark.label },
        contentAlignment = Alignment.Center
    ) {
        when (mark) {
            CoreMark.KNOWN -> Box(Modifier.size(8.dp).clip(CircleShape).background(P.Success))
            CoreMark.LEARNING -> Box(Modifier.size(9.dp).clip(CircleShape).border(1.5.dp, P.Accent, CircleShape))
            CoreMark.NEW -> Unit
        }
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, small: Boolean = false, quiet: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        selected && quiet -> Modifier.background(P.AccentSoft)
        selected -> Modifier.background(P.Accent)
        else -> Modifier.border(1.dp, P.Border, shape)
    }
    Text(
        label,
        color = when {
            selected && !quiet -> P.OnAccent
            selected -> P.Text
            else -> P.Muted
        },
        fontSize = if (small) 12.sp else 13.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .then(background)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 9.dp else 10.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
}
