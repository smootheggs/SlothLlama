package app.localmind.assistant.ui.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.core.CoreWordsRepository
import app.localmind.assistant.data.core.LoadedCoreList
import app.localmind.assistant.data.persona.ChineseStyleStore
import app.localmind.assistant.data.study.FlashcardRepository
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.domain.core.CoreBand
import app.localmind.assistant.domain.core.CoreLanguage
import app.localmind.assistant.domain.core.CoreMark
import app.localmind.assistant.domain.core.CorePos
import app.localmind.assistant.domain.core.CoreWord
import app.localmind.assistant.domain.core.CoreWords
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.ChineseStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which marks the list shows. */
enum class MarkFilter(val label: String) { ALL("All"), NEW("New"), LEARNING("Learning"), KNOWN("Known") }

data class CoreFilters(
    val language: CoreLanguage = CoreLanguage.MANDARIN,
    val pos: CorePos? = null,
    val band: CoreBand = CoreBand.TOP_500,
    val marks: MarkFilter = MarkFilter.ALL,
    val query: String = ""
)

data class CoreUiState(
    val filters: CoreFilters = CoreFilters(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The rows to show, in frequency order. */
    val rows: List<CoreWord> = emptyList(),
    /** Parts of speech this language's list has, for the filter row. */
    val partsOfSpeech: List<CorePos> = emptyList(),
    val marks: Map<String, CoreMark> = emptyMap(),
    val knownCount: Int = 0,
    val learningCount: Int = 0,
    /** Share of everyday text your known words make up, 0..1. */
    val coverage: Double = 0.0,
    val total: Int = 0
)

/** The Core words page: the lists, the filters, your marks, and the open word card. */
@HiltViewModel
class CoreWordsViewModel @Inject constructor(
    private val repository: CoreWordsRepository,
    private val flashcards: FlashcardRepository,
    styleStore: ChineseStyleStore
) : ViewModel() {

    val style: StateFlow<ChineseStyle> = styleStore.style

    private val filters = MutableStateFlow(CoreFilters(language = repository.lastLanguage))
    private val loaded = MutableStateFlow<LoadedCoreList?>(null)
    private val failed = MutableStateFlow(false)

    private val _card = MutableStateFlow<CoreWord?>(null)
    /** The word whose card is open. */
    val card: StateFlow<CoreWord?> = _card.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<CoreUiState> = combine(filters, loaded, failed, repository.marks) { f, list, bad, marks ->
        if (list == null || list.language != f.language) {
            return@combine CoreUiState(filters = f, loading = !bad, failed = bad, marks = marks)
        }
        val prefix = f.language.code + ":"
        fun markOf(w: CoreWord) = marks[prefix + w.key] ?: CoreMark.NEW
        val base = if (f.query.isNotBlank()) CoreWords.search(f.query, list.words, list.index)
        else CoreWords.filter(list.words, f.band, f.pos)
        val rows = base.filter { w ->
            when (f.marks) {
                MarkFilter.ALL -> true
                MarkFilter.NEW -> markOf(w) == CoreMark.NEW
                MarkFilter.LEARNING -> markOf(w) == CoreMark.LEARNING
                MarkFilter.KNOWN -> markOf(w) == CoreMark.KNOWN
            }
        }
        val known = list.words.filter { markOf(it) == CoreMark.KNOWN }
        CoreUiState(
            filters = f,
            loading = false,
            rows = rows,
            partsOfSpeech = CorePos.entries.filter { p -> list.words.any { p.code in it.pos } },
            marks = marks,
            knownCount = known.size,
            learningCount = list.words.count { markOf(it) == CoreMark.LEARNING },
            coverage = known.sumOf { it.share } / 1_000_000.0,
            total = list.words.size
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoreUiState(filters = filters.value))

    init {
        load(filters.value.language)
    }

    private fun load(language: CoreLanguage) {
        failed.value = false
        viewModelScope.launch {
            runCatching { repository.load(language) }
                .onSuccess { if (filters.value.language == language) loaded.value = it }
                .onFailure { if (filters.value.language == language) failed.value = true }
        }
    }

    fun setLanguage(language: CoreLanguage) {
        if (language == filters.value.language) return
        repository.lastLanguage = language
        // A part of speech one list has and another hasn't (measure words) would empty the page.
        filters.value = filters.value.copy(language = language, pos = null, query = "")
        load(language)
    }

    fun setPos(pos: CorePos?) {
        filters.value = filters.value.copy(pos = pos)
    }

    fun setBand(band: CoreBand) {
        filters.value = filters.value.copy(band = band)
    }

    fun setMarkFilter(marks: MarkFilter) {
        filters.value = filters.value.copy(marks = marks)
    }

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun openWord(word: CoreWord) {
        _card.value = word
    }

    fun closeWord() {
        _card.value = null
    }

    fun markOf(word: CoreWord): CoreMark = repository.markOf(filters.value.language, word)

    fun setMark(word: CoreWord, mark: CoreMark) {
        repository.setMark(filters.value.language, word, mark)
    }

    fun addToFlashcards(word: CoreWord) {
        val language = filters.value.language
        val script = style.value.script
        viewModelScope.launch {
            val added = flashcards.add(
                language = when (language) {
                    CoreLanguage.MANDARIN -> StudyLanguage.MANDARIN
                    CoreLanguage.CANTONESE -> StudyLanguage.CANTONESE
                    CoreLanguage.FRENCH -> StudyLanguage.FRENCH
                },
                term = CoreText.headword(word, language, script, withArticle = true),
                pronunciation = CoreText.reading(word, language),
                meaning = word.gloss,
                example = word.examples.firstOrNull()?.let { e ->
                    listOf(e.text, e.roman, e.meaning).filter { it.isNotBlank() }.joinToString(" · ")
                }.orEmpty()
            )
            _message.value = if (added) "Added to flashcards" else "Already in your flashcards"
        }
    }

    /** What to put in the teacher's chat box about [word]. */
    fun askPrompt(word: CoreWord): String {
        val language = filters.value.language
        val head = CoreText.headword(word, language, style.value.script, withArticle = false)
        val reading = CoreText.reading(word, language).takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val kind = word.mainPos?.label?.let { " — " + (if (it.first() in "aeiou") "an " else "a ") + it }.orEmpty()
        return "Help me with the ${language.label} word $head$reading$kind, \"${word.gloss}\". " +
            "How is it used? Give me three everyday example sentences with translations, and tell me anything that trips learners up."
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** How a word is written and read on the page, shared by the list and the card. */
object CoreText {
    fun headword(word: CoreWord, language: CoreLanguage, script: ChineseScript, withArticle: Boolean): String = when (language) {
        CoreLanguage.MANDARIN -> if (script == ChineseScript.TRADITIONAL) word.traditional ?: word.word else word.word
        CoreLanguage.CANTONESE -> word.word
        CoreLanguage.FRENCH -> if (withArticle && word.gender != null) article(word) + word.word else word.word
    }

    fun reading(word: CoreWord, language: CoreLanguage): String = when (language) {
        CoreLanguage.MANDARIN -> word.pinyin.orEmpty()
        CoreLanguage.CANTONESE -> word.jyutping.orEmpty()
        CoreLanguage.FRENCH -> ""
    }

    private const val ELIDE = "aâàeéèêiîoôuûyhœ"

    /** "le ", "la ", "l'" for a French noun. */
    fun article(word: CoreWord): String {
        val first = word.word.firstOrNull()?.lowercaseChar() ?: return ""
        if (first in ELIDE && word.word !in ASPIRATE) return "l'"
        return if (word.gender == "f") "la " else "le "
    }

    /** Nouns starting with an h that doesn't elide: le héros, la honte. */
    private val ASPIRATE = setOf("héros", "haricot", "hasard", "haut", "hauteur", "honte", "huit", "hall", "hangar", "hibou", "homard", "housse", "hache", "haine", "hamac", "hockey", "hublot", "hurlement")

    /** "les livres", for a French noun. */
    fun plural(word: CoreWord): String = "les " + (word.plural ?: (word.word + "s"))
}
