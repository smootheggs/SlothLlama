package app.localmind.assistant.ui.edition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.core.CoreWordsRepository
import app.localmind.assistant.data.desk.DeskStore
import app.localmind.assistant.data.dictionary.ChineseDictionary
import app.localmind.assistant.data.dictionary.DictionaryEntry
import app.localmind.assistant.data.dictionary.Segment
import app.localmind.assistant.data.dictionary.WordLookup
import app.localmind.assistant.data.edition.EditionEntry
import app.localmind.assistant.data.edition.EditionStore
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.persona.ChineseStyleStore
import app.localmind.assistant.data.persona.PersonaRepository
import app.localmind.assistant.data.persona.TutorLevelStore
import app.localmind.assistant.data.study.FlashcardRepository
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.domain.core.CoreLanguage
import app.localmind.assistant.domain.core.CoreWords
import app.localmind.assistant.domain.edition.CornerLanguage
import app.localmind.assistant.domain.edition.EdCorner
import app.localmind.assistant.domain.edition.EdStory
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.edition.KeyWord
import app.localmind.assistant.domain.edition.MarketTile
import app.localmind.assistant.domain.edition.SavedEdition
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A corner story opened: the text split into words (with readings, for Chinese) and its key words. */
data class StoryView(
    val corner: EdCorner,
    val story: EdStory,
    val language: CornerLanguage?,
    /** The title and each paragraph as pieces; one piece each for a language without readings. */
    val title: List<Segment>,
    val paras: List<List<Segment>>,
    /** The source's own English for each paragraph ("" where it gives none). */
    val english: List<String>,
    val keyWords: List<KeyWord>,
    val idiom: KeyWord?,
    /** "Words above HSK 2 are underlined: your level in Settings → Tutors." */
    val levelNote: String,
    /** Words above your level, to underline faintly. */
    val hard: Set<String>
)

/** The edition: the paper, the desk, the language corners, the archive and the settings sent to the Lobster. */
@HiltViewModel
class EditionViewModel @Inject constructor(
    private val store: EditionStore,
    private val mailbox: LobsterMailbox,
    private val desk: DeskStore,
    private val dictionary: ChineseDictionary,
    private val core: CoreWordsRepository,
    private val flashcards: FlashcardRepository,
    private val levels: TutorLevelStore,
    private val style: ChineseStyleStore,
    private val askInbox: AskInbox,
    private val attachments: AttachmentStore,
    private val atlas: app.localmind.assistant.data.atlas.AtlasRepository,
    personas: PersonaRepository
) : ViewModel() {

    val setup: StateFlow<EditionSetup> = store.setup
    val entries: StateFlow<List<EditionEntry>> = store.entries
    val latest: StateFlow<SavedEdition?> = store.latest

    val personas: StateFlow<List<Persona>> = personas.personas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _chosen = MutableStateFlow<SavedEdition?>(null)
    /** The edition on the page: one picked from the archive, or the latest. */
    val shown: StateFlow<SavedEdition?> = combine(_chosen, store.latest) { c, l -> c ?: l }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The markets strip, worked out from SlothDesk's latest prices. */
    val tiles: StateFlow<List<MarketTile>> = desk.prices.map { b ->
        b?.series?.mapValues { (_, s) -> Triple(s.kind, s.dates, if (s.kind == "bars") s.closes else s.values) }
            ?.let(Editions::marketTiles).orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _story = MutableStateFlow<StoryView?>(null)
    val story: StateFlow<StoryView?> = _story.asStateFlow()

    /** Corner headlines split into words with readings, by story key (filled as they're worked out). */
    private val _places = MutableStateFlow<Map<String, app.localmind.assistant.domain.atlas.PlaceHit>>(emptyMap())
    /** The place each story names most, by story key: found on the tablet with the Atlas's names. */
    val places: StateFlow<Map<String, app.localmind.assistant.domain.atlas.PlaceHit>> = _places.asStateFlow()

    private val _heads = MutableStateFlow<Map<String, List<Segment>>>(emptyMap())
    val heads: StateFlow<Map<String, List<Segment>>> = _heads.asStateFlow()

    private val _pinyin = MutableStateFlow(style.style.value.romanization)
    /** Pinyin or Jyutping over the words. */
    val readings: StateFlow<Boolean> = _pinyin.asStateFlow()

    private val _traditional = MutableStateFlow(style.style.value.script == ChineseScript.TRADITIONAL)
    val traditional: StateFlow<Boolean> = _traditional.asStateFlow()

    private val _lookup = MutableStateFlow<WordLookup?>(null)
    val lookup: StateFlow<WordLookup?> = _lookup.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lookupJob: Job? = null

    private var started = false

    init {
        viewModelScope.launch {
            store.load()
            runCatching { desk.load() }
        }
    }

    /** The edition page is open: corner headlines get their readings (the dictionary loads only then). */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            shown.collect { s ->
                s?.let {
                    placesFor(it)
                    headsFor(it)
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // --- the paper ---

    fun openEdition(entry: EditionEntry) = viewModelScope.launch {
        _chosen.value = store.paper(entry.file)
    }

    fun showLatest() {
        _chosen.value = null
    }

    fun delete(entry: EditionEntry) = viewModelScope.launch {
        if (_chosen.value?.edition?.id == entry.id) _chosen.value = null
        store.delete(entry.file)
    }

    fun setRead(story: EdStory, read: Boolean) = viewModelScope.launch {
        val id = shown.value?.edition?.id ?: return@launch
        val next = store.setRead(id, Editions.storyKey(story), read)
        if (_chosen.value?.edition?.id == id) _chosen.value = next
    }

    /** The whole article, fetched by the Lobster to the Later shelf (SlothLlama itself never goes online). */
    fun keepForLater(story: EdStory) = viewModelScope.launch {
        if (story.url.isBlank()) {
            _message.value = "This story has no link to fetch"
            return@launch
        }
        _message.value = runCatching {
            mailbox.send(OrderKind.FETCH, story.url, deliver = "later")
            "The Lobster will fetch it to Later"
        }.getOrElse { it.message ?: "Couldn't send the order" }
    }

    private suspend fun placesFor(s: SavedEdition) {
        val finder = runCatching { atlas.finder() }.getOrNull() ?: return
        _places.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            s.stories.mapNotNull { st ->
                val text = st.title + "\n" + st.summary + "\n" + st.paras.joinToString("\n") { it.t }
                finder.find(text, 1).firstOrNull()?.let { Editions.storyKey(st) to it }
            }.toMap()
        }
    }

    private suspend fun headsFor(s: SavedEdition) {
        val out = HashMap<String, List<Segment>>()
        for (c in s.edition.corners) {
            val lang = CornerLanguage.of(c.lang) ?: continue
            if (!lang.isChinese) continue
            for (story in c.stories) {
                out[Editions.storyKey(story)] = runCatching { dictionary.segment(story.title, speech(lang)) }.getOrDefault(listOf(Segment(story.title, null, null)))
            }
        }
        _heads.value = out
    }

    private fun speech(lang: CornerLanguage): SpeechLanguage = when (lang) {
        CornerLanguage.MANDARIN -> SpeechLanguage.MANDARIN
        CornerLanguage.CANTONESE -> SpeechLanguage.CANTONESE
        CornerLanguage.JAPANESE -> SpeechLanguage.JAPANESE
        CornerLanguage.FRENCH -> SpeechLanguage.FRENCH
        CornerLanguage.GERMAN -> SpeechLanguage.GERMAN
        CornerLanguage.SPANISH -> SpeechLanguage.SPANISH
        CornerLanguage.ITALIAN -> SpeechLanguage.ITALIAN
    }

    // --- a corner story ---

    fun openStory(corner: EdCorner, story: EdStory) = viewModelScope.launch {
        val lang = CornerLanguage.of(corner.lang)
        setRead(story, true)
        _story.value = build(corner, story, lang)
    }

    fun closeStory() {
        _story.value = null
        dismissLookup()
    }

    private suspend fun known(language: StudyLanguage): Set<String> =
        runCatching { flashcards.cards.first().filter { it.language == language }.map { it.term }.toSet() }.getOrDefault(emptySet())

    private suspend fun build(corner: EdCorner, story: EdStory, lang: CornerLanguage?): StoryView {
        val paras = story.paras.ifEmpty { if (story.summary.isNotBlank()) listOf(app.localmind.assistant.domain.edition.EdPara(story.summary)) else emptyList() }
        val english = paras.map { it.en }
        val level = lang?.let { levels.levels.value[speech(it)] }
        if (lang != null && lang.isChinese) {
            val speech = speech(lang)
            val title = dictionary.segment(story.title, speech)
            val body = paras.map { dictionary.segment(it.t, speech) }
            val words = (title + body.flatten()).filter { it.reading != null }.map { Triple(it.text, it.reading.orEmpty(), it.meaning.orEmpty()) }
            val list = runCatching { core.load(if (lang == CornerLanguage.MANDARIN) CoreLanguage.MANDARIN else CoreLanguage.CANTONESE) }.getOrNull()
            val hsk = HashMap<String, Int>()
            list?.words?.forEach { w ->
                w.hsk?.let { h ->
                    hsk.putIfAbsent(w.word, h)
                    w.traditional?.let { t -> hsk.putIfAbsent(t, h) }
                }
            }
            val cutoff = Editions.hskCutoff(level?.name ?: "ELEMENTARY")
            val mine = known(if (lang == CornerLanguage.MANDARIN) StudyLanguage.MANDARIN else StudyLanguage.CANTONESE)
            val keys = if (lang == CornerLanguage.MANDARIN) Editions.chineseKeyWords(words, hsk, cutoff, mine)
            else Editions.chineseKeyWords(words, emptyMap(), 0, mine).map { it.copy(note = "") }
            val idiom = Editions.idiom(words)?.let { (w, r, m) -> KeyWord(w, r, m, "idiom", w in mine) }
            val hard = if (lang == CornerLanguage.MANDARIN) words.filter { it.first.length >= 2 && (hsk[it.first] ?: 99) > cutoff && it.second.firstOrNull()?.isUpperCase() != true }.map { it.first }.toSet() else emptySet()
            val note = if (lang == CornerLanguage.MANDARIN) "Words above HSK $cutoff are underlined faintly: your level is set in Settings → Tutors. Readings and meanings: CC-CEDICT."
            else "Jyutping and meanings: CC-CEDICT and its Cantonese readings."
            return StoryView(corner, story, lang, title, body, english, keys, idiom, note, hard)
        }
        val title = listOf(Segment(story.title, null, null))
        val body = paras.map { listOf(Segment(it.t, null, null)) }
        val keys = if (lang == CornerLanguage.FRENCH) frenchKeyWords(paras.joinToString(" ") { it.t } + " " + story.title, level?.name ?: "ELEMENTARY") else emptyList()
        val note = if (lang == CornerLanguage.FRENCH) "Key words: beyond the commonest words for your level (Settings → Tutors), from the Core words list." else ""
        return StoryView(corner, story, lang, title, body, english, keys, null, note, emptySet())
    }

    private suspend fun frenchKeyWords(text: String, level: String): List<KeyWord> {
        val list = runCatching { core.load(CoreLanguage.FRENCH) }.getOrNull() ?: return emptyList()
        val cutoff = Editions.rankCutoff(level)
        val mine = known(StudyLanguage.FRENCH)
        val counts = HashMap<Int, Int>()
        Editions.frenchTokens(text).forEach { t ->
            list.index[CoreWords.fold(t)]?.firstOrNull()?.let { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.keys
            .filter { list.words[it].rank > cutoff }
            .sortedWith(compareByDescending<Int> { counts[it] ?: 0 }.thenBy { list.words[it].rank })
            .take(6)
            .map { list.words[it] }
            .map { w ->
                val gender = when (w.gender) { "m" -> "m."; "f" -> "f."; else -> w.mainPos?.label.orEmpty() }
                val article = when (w.gender) { "m" -> "le "; "f" -> "la "; else -> "" }
                KeyWord(article + w.word, "", w.gloss, listOf(gender, "#${w.rank}").filter { it.isNotBlank() }.joinToString(" · "), w.word in mine || (article + w.word) in mine)
            }
    }

    fun toggleReadings() {
        _pinyin.value = !_pinyin.value
    }

    fun setTraditional(on: Boolean) {
        _traditional.value = on
    }

    fun lookUp(text: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            dictionary.lookup(text)?.let { _lookup.value = it }
        }
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        _lookup.value = null
    }

    fun saveEntry(entry: DictionaryEntry, cantonese: Boolean) = viewModelScope.launch {
        val language = if (cantonese) SpeechLanguage.CANTONESE else SpeechLanguage.MANDARIN
        val added = flashcards.add(
            language = StudyLanguage.from(language),
            term = entry.headword(style.style.value.script, language),
            pronunciation = entry.reading(language),
            meaning = entry.definitions.take(3).joinToString("; "),
            example = ""
        )
        _message.value = if (added) "Added to flashcards" else "Already in your flashcards"
    }

    /** The story's key words that aren't cards yet, added in one go. */
    fun saveKeyWords(view: StoryView) = viewModelScope.launch {
        val language = view.language ?: return@launch
        val study = when (language) {
            CornerLanguage.MANDARIN -> StudyLanguage.MANDARIN
            CornerLanguage.CANTONESE -> StudyLanguage.CANTONESE
            CornerLanguage.FRENCH -> StudyLanguage.FRENCH
            else -> return@launch
        }
        var added = 0
        (view.keyWords + listOfNotNull(view.idiom)).filterNot { it.known }.forEach { k ->
            if (flashcards.add(study, k.word, k.reading, k.meaning.take(200), view.story.title.take(120))) added++
        }
        _message.value = if (added > 0) "$added added to flashcards" else "They're all in your flashcards already"
        _story.value = _story.value?.let { v -> v.copy(keyWords = v.keyWords.map { it.copy(known = true) }, idiom = v.idiom?.copy(known = true)) }
    }

    /** A chat with the language's partner about the story, its text attached and a question waiting. */
    fun talkOver(view: StoryView) {
        val lang = view.language ?: return
        val text = buildString {
            append(view.story.title).append("\n\n")
            view.story.paras.ifEmpty { listOf(app.localmind.assistant.domain.edition.EdPara(view.story.summary)) }.forEach { append(it.t).append("\n\n") }
            if (view.story.outlet.isNotBlank()) append("(").append(view.story.outlet).append(")")
        }.trim()
        askInbox.post(
            LaunchRequest.Shared(
                prompt = "Let's talk about this story from today's edition. Ask me one question about it at my level, then wait for my answer.",
                document = attachments.textDocument("${lang.label} corner · ${view.story.title.take(40)}", text),
                personaId = lang.partnerId
            )
        )
    }

    // --- the desk ---

    /** A chat with a desk persona, the edition's prices and headlines attached; its reply can be kept here. */
    fun askDesk(persona: Persona) {
        val s = shown.value ?: return
        store.askedDesk(s.edition.id, persona.id)
        askInbox.post(
            LaunchRequest.Shared(
                prompt = Editions.deskPrompt(s.edition),
                document = attachments.textDocument("Desk card · ${s.edition.name} edition", Editions.deskCard(s.edition, tiles.value)),
                personaId = persona.id
            )
        )
    }

    /** Who the edition asked for desk notes (edition id, persona id), for the chat's keep button. */
    val pendingDesk: StateFlow<Pair<String, String>?> = store.pendingDesk

    /** Keeps a desk persona's chat reply on the edition that asked; the message to show. */
    suspend fun keepDesk(text: String): String {
        val kept = store.keepDesk(text) ?: return "Couldn't keep it: open the edition and ask the desk again"
        if (_chosen.value?.edition?.id == kept.edition.id) _chosen.value = kept
        return "Kept on the ${kept.edition.name.lowercase()} edition's desk"
    }

    fun dropDesk(personaId: String) = viewModelScope.launch {
        val id = shown.value?.edition?.id ?: return@launch
        store.dropDesk(id, personaId)
        _chosen.value?.let { c -> if (c.edition.id == id) _chosen.value = c.copy(desk = c.desk.filterNot { it.personaId == personaId }) }
    }

    // --- settings ---

    fun edit(change: (EditionSetup) -> EditionSetup) = viewModelScope.launch {
        store.saveSetup { s -> change(s).copy(changedAt = System.currentTimeMillis()) }
    }

    /** Puts the settings in the mailbox; with [now], the Lobster makes an edition straight away. */
    fun send(now: Boolean) = viewModelScope.launch {
        val s = store.setup.value
        val at = System.currentTimeMillis()
        val text = Editions.planOrder(s, now, at)
        if (text == null) {
            _message.value = "Too many sources or words for one order: take a few out"
            return@launch
        }
        _message.value = runCatching {
            mailbox.send(OrderKind.EDITION, text, deliver = "edition")
            store.saveSetup { it.copy(sentAt = at) }
            if (now) "Sent: the Lobster will make an edition now" else "Sent to the Lobster"
        }.getOrElse { it.message ?: "Couldn't send the settings" }
    }
}
