package app.localmind.assistant.ui.edition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.edition.CornerLanguage
import app.localmind.assistant.domain.edition.KeyWord
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.dictionary.WordLookupSheet
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton

/**
 * A language corner's story: Pinyin or Jyutping over the words (简 or 繁), a tap on a word to look
 * it up, the source's English line by line when it gives one, the story's key words, and its
 * tutor to talk it over with. The key words come from the dictionary and the word lists, not a model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CornerStoryScreen(view: StoryView, vm: EditionViewModel, onBack: () -> Unit) {
    val readings by vm.readings.collectAsStateWithLifecycle()
    val traditional by vm.traditional.collectAsStateWithLifecycle()
    val lookup by vm.lookup.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var english by rememberSaveable(view.story.url) { mutableStateOf(false) }
    val speaker = rememberTutorSpeaker()
    val lang = view.language
    val speech = when (lang) {
        CornerLanguage.MANDARIN -> SpeechLanguage.MANDARIN
        CornerLanguage.CANTONESE -> SpeechLanguage.CANTONESE
        CornerLanguage.JAPANESE -> SpeechLanguage.JAPANESE
        CornerLanguage.FRENCH -> SpeechLanguage.FRENCH
        CornerLanguage.GERMAN -> SpeechLanguage.GERMAN
        CornerLanguage.SPANISH -> SpeechLanguage.SPANISH
        CornerLanguage.ITALIAN -> SpeechLanguage.ITALIAN
        null -> null
    }
    val chinese = lang?.isChinese == true

    BackHandler(onBack = onBack)
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(
                title = "${lang?.label ?: view.corner.name} corner",
                subtitle = listOf(view.corner.source, "in today's edition").filter { it.isNotBlank() }.joinToString(" · "),
                onBack = onBack,
                actions = {
                    if (chinese) {
                        Pill(if (lang == CornerLanguage.MANDARIN) "Pinyin" else "Jyutping", selected = readings, quiet = true, small = true, onClick = vm::toggleReadings)
                        Spacer(Modifier.width(6.dp))
                        Pill("简", selected = !traditional, quiet = true, small = true) { vm.setTraditional(false) }
                        Pill("繁", selected = traditional, quiet = true, small = true) { vm.setTraditional(true) }
                    }
                    if (speech != null) {
                        IconButton(onClick = {
                            speaker.speak((listOf(view.story.title) + view.story.paras.map { it.t }).joinToString("\n"), speech)
                        }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen to the story", tint = P.Text) }
                    }
                }
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pad = if (maxWidth > 700.dp) 40.dp else 20.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(Modifier.widthIn(max = 820.dp)) {
                        if (chinese) {
                            RubyText(view.title, readings, traditional, 32.sp, view.hard) { vm.lookUp(it) }
                        } else {
                            Text(view.story.title, color = P.Text, fontSize = 28.sp, lineHeight = 34.sp, fontFamily = SERIF)
                        }
                        if (view.story.summary.isNotBlank() && view.story.paras.isNotEmpty()) {
                            Text(view.story.summary, color = P.Muted, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Text(
                            listOf(view.story.outlet, "read on the tablet").filter { it.isNotBlank() }.joinToString(" · "),
                            color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (view.paras.isEmpty()) {
                        Text(
                            "Only the headline came back for this one. The Lobster can fetch the whole story to Later.",
                            color = P.Faint, fontSize = 14.sp
                        )
                    }
                    view.paras.forEachIndexed { i, para ->
                        Column(Modifier.widthIn(max = 820.dp)) {
                            if (chinese) RubyText(para, readings, traditional, 22.sp, view.hard) { vm.lookUp(it) }
                            else Text(para.firstOrNull()?.text.orEmpty(), color = P.Text, fontSize = 18.sp, lineHeight = 27.sp, fontFamily = SERIF)
                            val en = view.english.getOrNull(i).orEmpty()
                            if (english && en.isNotBlank()) Text(en, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (view.english.any { it.isNotBlank() }) {
                            QuietButton(if (english) "Hide the English" else "English, sentence by sentence", muted = true) { english = !english }
                        }
                        if (speech != null && view.story.paras.isNotEmpty()) {
                            QuietButton("Listen slower", muted = true) {
                                speaker.speak(view.story.paras.joinToString("\n") { it.t }, speech)
                            }
                        }
                    }

                    if (view.keyWords.isNotEmpty() || view.idiom != null) {
                        SectionHead("Key words") {
                            QuietButton("Add the new ones to Flashcards") { vm.saveKeyWords(view) }
                        }
                        view.keyWords.forEach { KeyWordRow(it, chinese) }
                        view.idiom?.let { idiom ->
                            SectionHead("Idiom")
                            KeyWordRow(idiom, chinese)
                        }
                    }
                    if (view.levelNote.isNotBlank()) Text(view.levelNote, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp)

                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (lang != null) FilledButton("Talk it over with ${lang.badge}") { vm.talkOver(view) }
                        if (view.story.url.isNotBlank()) QuietButton("Fetch the original to Later", muted = true) { vm.keepForLater(view.story) }
                        val stories = view.corner.stories
                        val at = stories.indexOfFirst { it.url == view.story.url && it.title == view.story.title }
                        stories.getOrNull(at + 1)?.let { next -> QuietButton("Next story ›", muted = true) { vm.openStory(view.corner, next) } }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    lookup?.let { found ->
        WordLookupSheet(
            lookup = found,
            script = if (traditional) ChineseScript.TRADITIONAL else ChineseScript.SIMPLIFIED,
            language = speech ?: SpeechLanguage.MANDARIN,
            onSpeak = { word -> speaker.speak(word, speech ?: SpeechLanguage.MANDARIN) },
            onPractise = null,
            onSave = { entry -> vm.saveEntry(entry, cantonese = lang == CornerLanguage.CANTONESE) },
            onDismiss = vm::dismissLookup
        )
    }
}

@Composable
private fun KeyWordRow(k: KeyWord, chinese: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k.word, color = P.Text, fontSize = if (chinese) 20.sp else 16.sp, fontFamily = SERIF, modifier = Modifier.widthIn(min = 96.dp).padding(end = 12.dp))
        if (k.reading.isNotBlank()) Text(k.reading, color = P.Muted, fontSize = 14.sp, modifier = Modifier.widthIn(min = 110.dp).padding(end = 12.dp))
        Text(k.meaning, color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2)
        Text(
            listOf(k.note, if (k.known) "in your cards" else "new").filter { it.isNotBlank() }.joinToString(" · "),
            color = if (k.known) P.Faint else P.Accent, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)
        )
    }
    HorizontalDivider(color = P.Border)
}
