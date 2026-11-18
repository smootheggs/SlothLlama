package app.localmind.assistant.data.study

import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.VocabularyItemEntity
import app.localmind.assistant.domain.persona.SpeechLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

enum class StudyLanguage(val code: String, val label: String, val speech: SpeechLanguage, val romanization: String) {
    MANDARIN("zh", "Mandarin", SpeechLanguage.MANDARIN, "Pinyin"),
    CANTONESE("yue", "Cantonese", SpeechLanguage.CANTONESE, "Jyutping");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: MANDARIN
        fun from(speech: SpeechLanguage?) = if (speech == SpeechLanguage.CANTONESE) CANTONESE else MANDARIN
    }
}

data class Flashcard(
    val id: String,
    val language: StudyLanguage,
    val term: String,
    val pronunciation: String,
    val meaning: String,
    val example: String,
    val ease: Float,
    val intervalDays: Int,
    val dueAt: Long
) {
    fun isDue(now: Long = System.currentTimeMillis()) = dueAt <= now
}

/** How well a card was remembered. */
enum class Grade(val label: String) { AGAIN("Again"), HARD("Hard"), GOOD("Good"), EASY("Easy") }

/**
 * SM-2 spaced repetition (the scheduler behind Anki and SuperMemo). The interval itself carries
 * the card's progress: 0 = new or forgotten, then 1 day, 6 days, and growing by the ease factor.
 */
object Scheduler {
    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE
    private const val MIN_EASE = 1.3f

    data class Next(val ease: Float, val intervalDays: Int, val dueAt: Long)

    fun next(card: Flashcard, grade: Grade, now: Long = System.currentTimeMillis()): Next {
        val i = card.intervalDays
        return when (grade) {
            Grade.AGAIN -> Next(max(MIN_EASE, card.ease - 0.2f), 0, now + 10 * MINUTE)
            Grade.HARD -> {
                val days = if (i == 0) 1 else max(i + 1, (i * 1.2f).roundToInt())
                Next(max(MIN_EASE, card.ease - 0.15f), days, now + days * DAY)
            }
            Grade.GOOD -> {
                val days = when (i) { 0 -> 1; 1 -> 6; else -> max(i + 1, (i * card.ease).roundToInt()) }
                Next(card.ease, days, now + days * DAY)
            }
            Grade.EASY -> {
                val days = when (i) { 0 -> 4; else -> max(i + 2, (i * card.ease * 1.3f).roundToInt()) }
                Next(card.ease + 0.15f, days, now + days * DAY)
            }
        }
    }

    /** "10m", "1d", "6d", "3mo" for the grade buttons. */
    fun label(next: Next, now: Long = System.currentTimeMillis()): String {
        val minutes = (next.dueAt - now) / MINUTE
        return when {
            next.intervalDays == 0 -> "${minutes.coerceAtLeast(1)}m"
            next.intervalDays < 30 -> "${next.intervalDays}d"
            next.intervalDays < 365 -> "${(next.intervalDays / 30f).roundToInt()}mo"
            else -> "${"%.1f".format(next.intervalDays / 365f)}y"
        }
    }
}

/** Flashcards live in the existing vocabulary_items table, so no database migration is needed. */
@Singleton
class FlashcardRepository @Inject constructor(private val dao: LocalMindDao) {

    val cards: Flow<List<Flashcard>> = dao.observeVocabularyQueue().map { items -> items.map { it.toCard() } }

    /** Returns false if the same word (same language) is already a card. */
    suspend fun add(language: StudyLanguage, term: String, pronunciation: String, meaning: String, example: String): Boolean {
        val cleanTerm = term.trim()
        if (cleanTerm.isEmpty()) return false
        if (dao.findVocabularyItem(cleanTerm, language.code) != null) return false
        dao.upsertVocabularyItem(
            VocabularyItemEntity(
                id = UUID.randomUUID().toString(),
                targetLanguage = language.code,
                term = cleanTerm,
                transliteration = pronunciation.trim().ifEmpty { null },
                translation = meaning.trim(),
                exampleSentence = example.trim(),
                cefrLevel = "",
                easeFactor = 2.5f,
                intervalDays = 0,
                nextReviewAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun review(card: Flashcard, grade: Grade) {
        val next = Scheduler.next(card, grade)
        val existing = dao.findVocabularyItem(card.term, card.language.code) ?: return
        dao.upsertVocabularyItem(
            existing.copy(easeFactor = next.ease, intervalDays = next.intervalDays, nextReviewAt = next.dueAt)
        )
    }

    suspend fun delete(card: Flashcard) = dao.deleteVocabularyItem(card.id)

    private fun VocabularyItemEntity.toCard() = Flashcard(
        id = id,
        language = StudyLanguage.fromCode(targetLanguage),
        term = term,
        pronunciation = transliteration.orEmpty(),
        meaning = translation,
        example = exampleSentence,
        ease = easeFactor,
        intervalDays = intervalDays,
        dueAt = nextReviewAt
    )
}

/** A word the tutor introduced, ready to become a card (the learner can still edit it). */
data class WordCandidate(val term: String, val pronunciation: String, val meaning: String, val example: String)

/**
 * Finds the words worth saving in a tutor reply: word-list lines like
 * "时间 / 時間 – shíjiān – time" (the format Settings → Chinese asks for) and ⭐-marked new words.
 */
object WordCandidates {
    private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
    private val LIST_MARKER = Regex("^\\s*(?:[-*•·]|\\d+[.)、])\\s*")
    private val DASH = Regex("\\s+[–—-]\\s+")
    private val STAR = Regex("⭐\\s*([\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF][\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF /／]*)\\s*(?:[（(]([^)）]{1,60})[)）])?")
    private val SCRIPT_LABEL = Regex("^\\s*[简繁簡]\\s*[:：]\\s*")

    fun from(text: String): List<WordCandidate> {
        val lines = text.lines()
        val found = LinkedHashMap<String, WordCandidate>()

        for (raw in lines) {
            val line = raw.replace(LIST_MARKER, "").replace("**", "").replace("⭐", "").trim()
            val parts = line.split(DASH).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2 || !HAN.containsMatchIn(parts[0]) || parts[0].length > 30) continue
            val (pronunciation, meaning) = when {
                parts.size >= 3 -> parts[1] to parts.drop(2).joinToString(" – ")
                else -> "" to parts[1]
            }
            if (HAN.containsMatchIn(meaning) && parts.size == 2) continue  // a sentence, not "word – meaning"
            found.putIfAbsent(parts[0], WordCandidate(parts[0], pronunciation, meaning, exampleFor(parts[0], lines)))
        }

        for (match in STAR.findAll(text)) {
            val term = match.groupValues[1].trim()
            if (term.isEmpty()) continue
            found.putIfAbsent(term, WordCandidate(term, match.groupValues[2].trim(), "", exampleFor(term, lines)))
        }
        return found.values.toList()
    }

    /** The first sentence in the reply that uses the word (first written form). */
    private fun exampleFor(term: String, lines: List<String>): String {
        val key = term.split('/', '／').first().trim()
        if (key.isEmpty()) return ""
        return lines.asSequence()
            .map { it.replace(SCRIPT_LABEL, "").trim() }
            .firstOrNull { it.contains(key) && it != term && !DASH.containsMatchIn(it) && it.length in (key.length + 2)..120 }
            .orEmpty()
    }
}
