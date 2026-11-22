package app.localmind.assistant.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.study.Flashcard
import app.localmind.assistant.data.study.Grade
import app.localmind.assistant.domain.study.Ask
import app.localmind.assistant.domain.study.AskWith
import app.localmind.assistant.domain.study.Checked
import app.localmind.assistant.domain.study.Match
import app.localmind.assistant.domain.study.Study
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.speech.TutorSpeaker
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * Flashcards (1.51): a Review tab where nothing but the card shows until you flip it, and a Browse
 * tab where meanings stay hidden until you tap. Phone and tablet, upright or sideways: held
 * sideways on a wide screen the grades sit down the right edge. With a keyboard, Space or Enter
 * flips, 1 to 4 grade, Ctrl+Z undoes.
 */
@Composable
fun FlashcardsScreen(viewModel: FlashcardsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val speaker = rememberTutorSpeaker()
    val snackbar = remember { SnackbarHostState() }
    var browse by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(deleted) {
        val card = deleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Deleted “${card.term}”", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearDeleted()
    }
    BackHandler(enabled = state.review != null) { viewModel.end() }
    BackHandler(enabled = state.review == null && state.summary != null) { viewModel.closeSummary() }

    BoxWithConstraints(Modifier.fillMaxSize().background(P.Canvas)) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight
        val review = state.review
        if (review != null) {
            ReviewPane(review, state.settings.showReading, state.settings.typed, wide, viewModel, speaker)
        } else {
            Column(Modifier.fillMaxSize()) {
                SlothTopBar(
                    title = "Flashcards",
                    subtitle = "${state.dueCount} due · ${state.cards.size} cards",
                    onBack = onBack
                )
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Tabs(browse, state.dueCount, state.cards.size, Modifier.widthIn(max = 480.dp)) { browse = it }
                }
                when {
                    browse -> BrowsePane(state.cards, state.now, viewModel)
                    state.summary != null -> SummaryPane(state.summary!!, state.canUndo, viewModel)
                    else -> StartPane(state, viewModel)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

// --- tabs ---

@Composable
private fun Tabs(browse: Boolean, due: Int, cards: Int, modifier: Modifier, onBrowse: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).border(1.dp, P.BorderStrong, RoundedCornerShape(26.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Segment("Review · $due due", !browse, Modifier.weight(1f)) { onBrowse(false) }
        Segment("Browse · $cards cards", browse, Modifier.weight(1f)) { onBrowse(true) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) P.AccentSoft else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) P.Accent else P.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// --- start ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPane(state: FlashcardsUiState, vm: FlashcardsViewModel) {
    val plan = state.plan
    val s = state.settings
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (state.cards.isEmpty()) {
                    Text("No flashcards yet", color = P.Text, fontSize = 26.sp, fontFamily = FontFamily.Serif)
                    Text(
                        "In a chat with a tutor, tap ☆ under a reply to save its new words. They come back here for review just before you'd forget them.",
                        color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    return@Column
                }
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (plan.items.isEmpty()) "All caught up" else if (plan.items.size == 1) "1 card due" else "${plan.items.size} cards due",
                        color = P.Text, fontSize = 30.sp, fontFamily = FontFamily.Serif
                    )
                    Text(
                        if (plan.items.isEmpty()) nextDue(state.cards, state.now)
                        else listOfNotNull(
                            "About ${plan.minutes} ${if (plan.minutes == 1) "minute" else "minutes"}",
                            plan.reviews.takeIf { it > 0 }?.let { "$it to review" },
                            plan.fresh.takeIf { it > 0 }?.let { "$it new (up to ${s.newPerDay} a day)" }
                        ).joinToString(" · "),
                        color = P.Muted, fontSize = 14.sp
                    )
                }
                if (state.byLanguage.size > 1 || s.languages.isNotEmpty()) {
                    Section("LANGUAGES") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("All ${state.byLanguage.sumOf { it.second }}", s.languages.isEmpty()) { vm.setLanguages(emptyList()) }
                            state.byLanguage.forEach { (lang, n) ->
                                Pill("${lang.label} $n", s.languages == listOf(lang.code)) { vm.setLanguages(listOf(lang.code)) }
                            }
                        }
                    }
                }
                Section("ASK WITH") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).border(1.dp, P.BorderStrong, RoundedCornerShape(26.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AskWith.entries.forEach { a -> Segment(a.label, s.ask == a, Modifier.weight(1f)) { vm.setAskWith(a.name) } }
                    }
                    Text(
                        when (s.ask) {
                            AskWith.WORD -> "See the word, recall its meaning."
                            AskWith.MEANING -> "See the meaning, recall the word. A word joins once you've recalled it across a day."
                            AskWith.MIXED -> "Both ways, one way per card each day."
                        },
                        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
                Column {
                    Toggle("Hide the reading until the flip", "Pinyin or Jyutping on the front gives the word away.", !s.showReading) { vm.setShowReading(!it) }
                    Toggle("Type the answer first", "Then flip to see how close you were.", s.typed) { vm.setTyped(it) }
                }
            }
            if (state.cards.isNotEmpty()) PrimaryButton("Start review", enabled = plan.items.isNotEmpty(), height = 56) { vm.start() }
        }
    }
}

private fun nextDue(cards: List<Flashcard>, now: Long): String {
    val next = cards.filter { it.dueAt > now }.minOfOrNull { it.dueAt } ?: return "Nothing scheduled."
    return "Next review ${dueText(next, now)}."
}

@Composable
private fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) P.Accent else P.BorderStrong, RoundedCornerShape(20.dp))
            .background(if (selected) P.AccentSoft else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) P.Accent else P.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = P.Accent, checkedThumbColor = P.OnAccent, uncheckedTrackColor = P.BorderStrong)
        )
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, height: Int = 56, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp))
            .background(if (enabled) P.Accent else P.Border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) P.OnAccent else P.Faint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuietButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
            .border(1.dp, P.BorderStrong, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// --- review ---

@Composable
private fun ReviewPane(r: ReviewView, showReading: Boolean, typed: Boolean, wide: Boolean, vm: FlashcardsViewModel, speaker: TutorSpeaker) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(r.item.key, r.revealed) { if (!(typed && !r.revealed)) runCatching { focus.requestFocus() } }
    val keys = Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
            e.isCtrlPressed && e.key == Key.Z && r.canUndo -> { vm.undoLast(); true }
            !r.revealed && (e.key == Key.Enter || e.key == Key.NumPadEnter || (!typed && e.key == Key.Spacebar)) -> { vm.reveal(); true }
            r.revealed && e.key in GRADE_KEYS -> { vm.grade(Grade.entries[GRADE_KEYS.indexOf(e.key)]); true }
            r.revealed && (e.key == Key.Spacebar || e.key == Key.Enter) -> { vm.grade(r.checked?.suggested ?: Grade.GOOD); true }
            else -> false
        }
    }
    val speak = { speaker.speak(r.card.term, r.card.language.speech) }
    val label = "${r.card.language.label.uppercase()} · ${if (r.item.ask == Ask.WORD) "WORD → MEANING" else "MEANING → WORD"}" +
        if (r.practice) " · PRACTICE" else ""
    if (wide) {
        Row(keys.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
                CardFace(r, showReading, typed, big = true, modifier = Modifier.weight(1f), vm = vm, speak = speak)
            }
            Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Progress(r.done, r.total, Modifier.weight(1f))
                    IconButton(onClick = { vm.end() }) { Icon(Icons.Filled.Close, contentDescription = "End review", tint = P.Muted) }
                }
                Spacer(Modifier.weight(1f))
                if (r.revealed) {
                    Text("How well did you know it?", color = P.Muted, fontSize = 13.sp)
                    Grades(r, vertical = true, vm = vm)
                } else {
                    PrimaryButton(if (typed) "Check" else "Show answer", height = 64) { vm.reveal() }
                }
                UndoLine(r.canUndo, vm)
            }
        }
    } else {
        Column(keys.fillMaxSize().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.end() }) { Icon(Icons.Filled.Close, contentDescription = "End review", tint = P.Muted) }
                Progress(r.done, r.total, Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp).align(Alignment.CenterHorizontally).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(label, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
                CardFace(r, showReading, typed, big = false, modifier = Modifier.weight(1f), vm = vm, speak = speak)
                if (r.revealed) {
                    Text("How well did you know it?", color = P.Faint, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Grades(r, vertical = false, vm = vm)
                } else {
                    PrimaryButton(if (typed) "Check" else "Show answer", height = 60) { vm.reveal() }
                }
                UndoLine(r.canUndo, vm)
            }
        }
    }
}

private val GRADE_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four)

@Composable
private fun Progress(done: Int, total: Int, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            color = P.Accent, trackColor = P.BorderStrong,
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text("$done of $total", color = P.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun UndoLine(canUndo: Boolean, vm: FlashcardsViewModel) {
    if (!canUndo) return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { vm.undoLast() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = P.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Undo the last card", color = P.Muted, fontSize = 14.sp)
    }
}

/** The card: tap it to flip; in typed mode, the answer box is on its front. */
@Composable
private fun CardFace(r: ReviewView, showReading: Boolean, typed: Boolean, big: Boolean, modifier: Modifier, vm: FlashcardsViewModel, speak: () -> Unit) {
    val c = r.card
    val askWord = r.item.ask == Ask.WORD
    val termSize = if (big) 88.sp else 64.sp
    // Centred while it fits, scrolling when a long example doesn't.
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.Card).border(1.dp, P.BorderStrong, RoundedCornerShape(24.dp))
            .clickable(enabled = !r.revealed && !typed, role = Role.Button, onClickLabel = "Flip the card") { vm.reveal() },
        contentAlignment = Alignment.Center
    ) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The front: the word, or its meaning. The word is spoken only once it's no secret.
        if (askWord || r.revealed) {
            Text(c.term, color = P.Text, fontSize = termSize, lineHeight = termSize * 1.15f, textAlign = TextAlign.Center)
            IconButton(onClick = speak) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Say it", tint = P.Muted) }
        }
        if (!askWord && !r.revealed) {
            Text(c.meaning, color = P.Text, fontSize = 28.sp, lineHeight = 36.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        }
        if (c.pronunciation.isNotBlank() && (r.revealed || (askWord && showReading))) {
            Text(c.pronunciation, color = P.Accent, fontSize = 22.sp, textAlign = TextAlign.Center)
        }
        if (!r.revealed && typed) {
            OutlinedTextField(
                value = r.typed, onValueChange = vm::type, singleLine = true,
                label = { Text(if (askWord) "The meaning" else "The word") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.reveal() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = P.Accent, unfocusedBorderColor = P.BorderStrong, focusedTextColor = P.Text, unfocusedTextColor = P.Text),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!r.revealed && !typed) {
            Text("Tap the card to flip it", color = P.Faint, fontSize = 13.sp)
        }
        if (r.revealed) {
            r.checked?.let { CheckedLine(it, r.typed) }
            if (askWord && c.meaning.isNotBlank()) {
                Text(c.meaning, color = P.Text, fontSize = 18.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
            }
            if (c.example.isNotBlank()) {
                Box(Modifier.width(48.dp).height(1.dp).background(P.BorderStrong))
                Text(c.example, color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
            }
            if (r.leech) {
                Text(
                    "Keeps slipping: missed ${Study.lapses(c)} times. Rewrite it, add an example, or split it in two.",
                    color = P.Warning, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center
                )
            }
        }
    }
    }
}

/** How close a typed answer came, with the characters you got wrong underlined. */
@Composable
private fun CheckedLine(ch: Checked, typed: String) {
    val (title, tint) = when (ch.match) {
        Match.EXACT -> "Right" to P.Success
        Match.CLOSE -> (ch.note.ifBlank { "Nearly" }) to P.Warning
        Match.WRONG -> (if (typed.isBlank()) "No answer" else "Not quite") to P.Danger
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.Elevated).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (ch.match != Match.EXACT) {
            Text(marked(ch), color = P.Text, fontSize = 22.sp)
            if (typed.isNotBlank()) Text("You wrote: $typed", color = P.Muted, fontSize = 13.sp)
        }
    }
}

private fun marked(ch: Checked): AnnotatedString = buildAnnotatedString {
    val cps = ch.expected.codePoints().toArray()
    // Only letters count as positions in the check, so map letter positions back onto the text.
    var letter = 0
    cps.forEach { cp ->
        val s = String(Character.toChars(cp))
        if (Character.isLetterOrDigit(cp)) {
            if (letter in ch.wrongAt) withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) { append(s) }
            else append(s)
            letter++
        } else append(s)
    }
}

@Composable
private fun Grades(r: ReviewView, vertical: Boolean, vm: FlashcardsViewModel) {
    val tints = mapOf(Grade.AGAIN to P.Danger, Grade.HARD to P.Warning, Grade.GOOD to P.Success, Grade.EASY to P.Accent)
    val suggested = r.checked?.suggested
    val buttons: @Composable (Modifier) -> Unit = { m ->
        Grade.entries.forEachIndexed { i, g ->
            val tint = tints.getValue(g)
            val on = g == suggested
            Column(
                m.heightIn(min = if (vertical) 64.dp else 66.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) tint.copy(alpha = 0.14f) else Color.Transparent)
                    .border(if (on) 2.dp else 1.dp, tint.copy(alpha = if (on) 1f else 0.7f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { vm.grade(g) }
                    .semantics { contentDescription = "${g.label}, key ${i + 1}" + (r.hints[g]?.let { ", back in $it" } ?: "") }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(g.label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(r.hints[g] ?: "practice", color = P.Faint, fontSize = 11.sp)
            }
        }
    }
    if (vertical) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.fillMaxWidth()) }
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.weight(1f)) }
}

// --- the end of a session ---

@Composable
private fun SummaryPane(s: ReviewSummary, canUndo: Boolean, vm: FlashcardsViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (s.practice) "Practice done" else "Done for now", color = P.Text, fontSize = 30.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("${s.reviewed}", if (s.practice) "gone over" else "reviewed", P.Text, Modifier.weight(1f))
                    Tile("${s.right}", "right first time", P.Success, Modifier.weight(1f))
                    Tile("${s.missed.size}", "to see again", if (s.missed.isEmpty()) P.Muted else P.Danger, Modifier.weight(1f))
                }
                if (s.practice) Text("Practice doesn't change when cards come back.", color = P.Muted, fontSize = 14.sp)
                else Text(s.nextLine, color = P.Muted, fontSize = 14.sp)
                if (s.missed.isNotEmpty()) {
                    Section(if (s.missed.size == 1) "THE ONE YOU MISSED" else "THE ${s.missed.size} YOU MISSED") {
                        s.missed.forEach { (c, _) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.term, color = P.Text, fontSize = 22.sp, modifier = Modifier.widthIn(min = 72.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    if (c.pronunciation.isNotBlank()) Text(c.pronunciation, color = P.Accent, fontSize = 14.sp)
                                    Text(c.meaning, color = P.Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (Study.isLeech(c)) Text("Keeps slipping: worth rewriting", color = P.Warning, fontSize = 12.sp)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(P.Border))
                        }
                    }
                }
            }
            if (s.missed.isNotEmpty()) PrimaryButton(if (s.missed.size == 1) "Go over it again" else "Go over these ${s.missed.size} again") { vm.practiseMissed() }
            if (canUndo) UndoLine(true, vm)
            QuietButton("Close") { vm.closeSummary() }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, tint: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(P.Card).padding(14.dp)) {
        Text(value, color = tint, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = P.Faint, fontSize = 12.sp)
    }
}

// --- browse ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowsePane(cards: List<Flashcard>, now: Long, vm: FlashcardsViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var unfolded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val languages = cards.map { it.language }.distinct()
    val leeches = cards.count { Study.isLeech(it) }
    val q = query.trim().lowercase()
    val shown = cards.filter { c ->
        (q.isEmpty() || c.term.lowercase().contains(q) || c.pronunciation.lowercase().contains(q) || c.meaning.lowercase().contains(q)) &&
            when (filter) {
                "all" -> true
                "due" -> c.isDue(now)
                "leech" -> Study.isLeech(c)
                else -> c.language.code == filter
            }
    }.sortedBy { it.dueAt }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                        label = { Text("Find a card") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = P.Accent, unfocusedBorderColor = P.BorderStrong, focusedTextColor = P.Text, unfocusedTextColor = P.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("All", filter == "all") { filter = "all" }
                        if (languages.size > 1) languages.forEach { l -> Pill(l.label, filter == l.code) { filter = l.code } }
                        Pill("Due now", filter == "due") { filter = "due" }
                        if (leeches > 0) Pill("Keep slipping $leeches", filter == "leech") { filter = "leech" }
                    }
                    Text("Meanings stay hidden until you tap a card, so browsing never spoils a review.", color = P.Faint, fontSize = 12.sp)
                }
            }
            if (shown.isEmpty()) item { Text(if (cards.isEmpty()) "No flashcards yet." else "No cards match.", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp)) }
            items(shown, key = { it.id }) { c ->
                BrowseRow(c, now, c.id in unfolded, onToggle = { unfolded = if (c.id in unfolded) unfolded - c.id else unfolded + c.id }, onDelete = { vm.delete(c) })
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun BrowseRow(c: Flashcard, now: Long, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = if (expanded) "Hide the meaning" else "Show the meaning", onClick = onToggle).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(c.term, color = P.Text, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(min = 72.dp, max = 220.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (expanded) {
                        if (c.pronunciation.isNotBlank()) Text(c.pronunciation, color = P.Accent, fontSize = 14.sp)
                        Text(c.meaning, color = P.Text, fontSize = 14.sp)
                        if (c.example.isNotBlank()) Text(c.example, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    } else {
                        Text("Tap to show the meaning", color = P.Faint, fontSize = 13.sp)
                    }
                    Text(
                        "${c.language.label} · ${if (c.isDue(now)) "due now" else "due " + dueText(c.dueAt, now)}" +
                            if (Study.isLeech(c)) " · keeps slipping (${Study.lapses(c)} misses)" else "",
                        color = if (Study.isLeech(c)) P.Warning else P.Faint, fontSize = 11.sp
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete “${c.term}”", tint = P.Faint, modifier = Modifier.size(20.dp)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.Border))
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
