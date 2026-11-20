package app.localmind.assistant.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.core.CoreLanguage
import app.localmind.assistant.domain.core.CoreMark
import app.localmind.assistant.domain.core.CorePos
import app.localmind.assistant.domain.core.CoreWord
import app.localmind.assistant.domain.core.FrenchConjugator
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.ui.chat.components.LocalChineseScale
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * A word's card: how it's written and read, what it means, your mark, then its grammar
 * (conjugation, gender and plural, measure words, other readings) and a note with examples.
 */
@Composable
fun WordCard(
    word: CoreWord,
    language: CoreLanguage,
    script: ChineseScript,
    marks: Map<String, CoreMark>,
    actions: CardActions
) {
    val scale = LocalChineseScale.current
    val chinese = language != CoreLanguage.FRENCH
    val head = CoreText.headword(word, language, script, withArticle = false)
    val spoken = CoreText.headword(word, language, script, withArticle = true)

    Row(verticalAlignment = Alignment.Bottom) {
        Text(head, color = P.Text, fontSize = if (chinese) (38 * scale).sp else 32.sp, fontWeight = FontWeight.Medium)
        // The other script, quietly, for Mandarin words that differ (学习 / 學習).
        val other = if (language == CoreLanguage.MANDARIN) {
            if (script == ChineseScript.TRADITIONAL) word.word.takeIf { word.traditional != null } else word.traditional
        } else null
        if (other != null && other != head) {
            Spacer(Modifier.width(10.dp))
            Text(other, color = P.Faint, fontSize = (20 * scale).sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
    val reading = CoreText.reading(word, language)
    if (reading.isNotBlank()) Text(reading, color = P.Accent, fontSize = 17.sp)
    Text(tagLine(word, language), color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action(Icons.AutoMirrored.Filled.VolumeUp, "Listen") { actions.onSpeak(spoken) }
        Action(Icons.Outlined.RecordVoiceOver, "Practise saying it") { actions.onPractise(spoken) }
    }

    Text(word.gloss, color = P.Text, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 16.dp))

    // Your mark, then the card's one filled button and a quiet one.
    val mark = marks[language.code + ":" + word.key] ?: CoreMark.NEW
    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CoreMark.entries.forEach { m -> Chip(m.label, selected = m == mark, quiet = true) { actions.onMark(word, m) } }
    }
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Add to flashcards",
            color = P.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(P.Accent)
                .clickable { actions.onFlashcard(word) }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Text(
            "Ask the teacher",
            color = P.Accent, fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { actions.onAsk(word) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }

    when (language) {
        CoreLanguage.FRENCH -> FrenchGrammar(word, actions)
        CoreLanguage.MANDARIN -> MandarinGrammar(word, scale)
        CoreLanguage.CANTONESE -> Unit
    }

    if (word.note != null || word.examples.isNotEmpty()) {
        Section("How it's used")
        word.note?.let { Text(it, color = P.Text, fontSize = 15.sp, lineHeight = 22.sp) }
        word.examples.forEach { e ->
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Listen") { actions.onSpeak(e.text) }
                    .padding(vertical = 4.dp)
            ) {
                Text(e.text, color = P.Text, fontSize = if (chinese) (19 * scale).sp else 16.sp)
                if (e.roman.isNotBlank()) Text(e.roman, color = P.Accent, fontSize = 13.sp)
                Text(e.meaning, color = P.Muted, fontSize = 14.sp)
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(credit(language), color = P.Faint, fontSize = 10.sp, lineHeight = 14.sp)
}

/** "#12 · verb, adverb · HSK 2". */
private fun tagLine(word: CoreWord, language: CoreLanguage): String {
    val parts = mutableListOf("#${word.rank}")
    val pos = word.partsOfSpeech.map { it.label }
    if (language == CoreLanguage.FRENCH && word.mainPos == CorePos.NOUN && word.gender != null) {
        parts += (if (word.gender == "f") "feminine noun" else "masculine noun")
    } else if (pos.isNotEmpty()) {
        parts += pos.take(3).joinToString(", ")
    }
    word.hsk?.let { parts += "HSK $it" }
    return parts.joinToString(" · ")
}

private fun credit(language: CoreLanguage): String = when (language) {
    CoreLanguage.MANDARIN -> "Words and levels: complete-hsk-vocabulary (MIT). Meanings: CC-CEDICT (CC BY-SA 4.0). Frequencies: wordfreq (CC BY-SA 4.0)."
    CoreLanguage.CANTONESE -> "Words and counts: Hong Kong Cantonese Corpus (CC BY 4.0). Meanings: CC-CEDICT (CC BY-SA 4.0) and SlothLlama's own."
    CoreLanguage.FRENCH -> "Frequencies: wordfreq (CC BY-SA 4.0). Word list, meanings and conjugation: SlothLlama's own."
}

@Composable
private fun Section(label: String) {
    HorizontalDivider(color = P.Border, modifier = Modifier.padding(top = 24.dp, bottom = 14.dp))
    Text(label.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun MandarinGrammar(word: CoreWord, scale: Float) {
    val cls = word.classifiers.orEmpty()
    if (cls.isNotEmpty()) {
        Section("Measure words")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            cls.forEach { c -> Text("一$c${word.word}", color = P.Text, fontSize = (19 * scale).sp) }
        }
    }
    if (word.otherReadings.isNotEmpty()) {
        Section("Also read")
        word.otherReadings.forEach { r ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(r[0], color = P.Accent, fontSize = 15.sp, modifier = Modifier.width(96.dp))
                Text(r[2], color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun FrenchGrammar(word: CoreWord, actions: CardActions) {
    when (word.mainPos) {
        CorePos.VERB -> Conjugation(word.word, actions)
        CorePos.NOUN -> if (word.gender != null) {
            Section("Gender and plural")
            Text(CoreText.article(word) + word.word + "   ·   " + CoreText.plural(word), color = P.Text, fontSize = 16.sp)
        }
        CorePos.ADJECTIVE -> word.adjectiveForms?.takeIf { it.size == 3 }?.let { (f, mpl, fpl) ->
            Section("Forms")
            FormGrid(listOf("" to "Singular", "" to "Plural"), listOf(word.word to mpl, f to fpl), listOf("Masculine", "Feminine"))
            word.beforeVowel?.let {
                Text("Before a vowel: $it (un $it ami)", color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        else -> word.forms?.takeIf { it.isNotEmpty() }?.let { forms ->
            Section("Other forms")
            Text(forms.joinToString(" · "), color = P.Text, fontSize = 16.sp)
        }
    }
}

@Composable
private fun FormGrid(header: List<Pair<String, String>>, rows: List<Pair<String, String>>, labels: List<String>) {
    Row {
        Spacer(Modifier.width(96.dp))
        header.forEach { (_, h) -> Text(h, color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
    }
    rows.forEachIndexed { i, (a, b) ->
        Row(Modifier.padding(top = 6.dp)) {
            Text(labels[i], color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(96.dp).padding(top = 2.dp))
            Text(a, color = P.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(b, color = P.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        }
    }
}

private enum class Tense(val label: String) {
    PRESENT("Présent"), PASSE_COMPOSE("Passé composé"), IMPARFAIT("Imparfait"), FUTUR("Futur"),
    CONDITIONNEL("Conditionnel"), SUBJONCTIF("Subjonctif"), IMPERATIF("Impératif"),
    PLUS_QUE_PARFAIT("Plus-que-parfait"), PASSE_SIMPLE("Passé simple")
}

@Composable
private fun Conjugation(verb: String, actions: CardActions) {
    val c = FrenchConjugator.conjugate(verb) ?: return
    var tense by rememberSaveable(verb) { mutableStateOf(Tense.PRESENT) }
    Section("Conjugation")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tense.entries.forEach { t -> Chip(t.label, selected = t == tense, small = true, quiet = true) { tense = t } }
    }
    val t = c.tables
    val inf = c.infinitive
    val auxTables = if (c.aux == "être") FrenchConjugator.etre else FrenchConjugator.avoir
    val lines: List<String> = when (tense) {
        Tense.IMPERATIF -> t.imperatif.mapIndexed { i, f -> FrenchConjugator.imperative(i, f, c.reflexive) }
        Tense.SUBJONCTIF -> t.subjonctif.mapIndexed { i, f -> FrenchConjugator.withQue(i, f, inf, c.reflexive) }
        else -> {
            val forms = when (tense) {
                Tense.PRESENT -> t.present
                Tense.IMPARFAIT -> t.imparfait
                Tense.FUTUR -> t.futur
                Tense.CONDITIONNEL -> t.conditionnel
                Tense.PASSE_SIMPLE -> t.passeSimple
                Tense.PASSE_COMPOSE -> (0 until 6).map { FrenchConjugator.compound(c, it, auxTables.present) }
                Tense.PLUS_QUE_PARFAIT -> (0 until 6).map { FrenchConjugator.compound(c, it, auxTables.imparfait) }
                else -> t.present
            }
            // A compound form starts with the auxiliary, so elision follows it (j'ai, je suis).
            val elideOn = if (tense == Tense.PASSE_COMPOSE || tense == Tense.PLUS_QUE_PARFAIT) c.aux else inf
            forms.mapIndexed { i, f -> FrenchConjugator.withPronoun(i, f, elideOn, c.reflexive) }
        }
    }
    val shown = lines.filter { it != "-" }
    if (shown.isEmpty()) {
        Text("Not used in this tense.", color = P.Faint, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    } else if (tense == Tense.IMPERATIF || c.impersonal) {
        Column(Modifier.padding(top = 10.dp)) { shown.forEach { line -> FormLine(line, actions) } }
    } else {
        // Singular on the left, plural on the right, as in a grammar book.
        Row(Modifier.padding(top = 10.dp).fillMaxWidth()) {
            Column(Modifier.weight(1f)) { lines.take(3).forEach { FormLine(it, actions) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { lines.drop(3).forEach { FormLine(it, actions) } }
        }
    }
    val pps = c.participles.distinct().joinToString(", ")
    val extra = buildList {
        add("Past participle: $pps")
        if (t.ppr != "-") add("Present participle: ${t.ppr}")
        add(
            "Compound tenses take ${c.aux}" + when {
                c.both -> ", or avoir with an object (j'ai ${t.pp} la valise)"
                c.reflexive -> " (pronominal: the participle agrees)"
                c.aux == "être" -> " (the participle agrees with the subject)"
                else -> ""
            }
        )
    }
    extra.forEach { Text(it, color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp)) }
}

@Composable
private fun FormLine(line: String, actions: CardActions) {
    Text(
        line,
        color = if (line == "-") P.Faint else P.Text,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (line != "-") Modifier.clickable(onClickLabel = "Listen") { actions.onSpeak(line) } else Modifier)
            .padding(vertical = 5.dp)
    )
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, P.BorderStrong, shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = P.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = P.Text, fontSize = 14.sp)
    }
}
