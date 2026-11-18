package app.localmind.assistant.data.study

import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.VocabularyItemEntity
import app.localmind.assistant.domain.persona.SpeechLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** [romanization]: the label for the pronunciation field ("" = the language needs none). */
enum class StudyLanguage(val code: String, val label: String, val speech: SpeechLanguage, val romanization: String) {
    MANDARIN("zh", "Mandarin", SpeechLanguage.MANDARIN, "Pinyin"),
    CANTONESE("yue", "Cantonese", SpeechLanguage.CANTONESE, "Jyutping"),
    JAPANESE("ja", "Japanese", SpeechLanguage.JAPANESE, "Romaji"),
    FRENCH("fr", "French", SpeechLanguage.FRENCH, ""),
    GERMAN("de", "German", SpeechLanguage.GERMAN, ""),
    SPANISH("es", "Spanish", SpeechLanguage.SPANISH, ""),
    ITALIAN("it", "Italian", SpeechLanguage.ITALIAN, "");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: MANDARIN
        fun from(speech: SpeechLanguage?) = entries.firstOrNull { it.speech == speech } ?: MANDARIN
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
    val dueAt: Long,
    /** FSRS memory state ("fsrs1|stability|difficulty|last review|reviews|lapses"); "" = not yet scheduled by FSRS. */
    val memory: String = ""
) {
    fun isDue(now: Long = System.currentTimeMillis()) = dueAt <= now
}

/** How well a card was remembered. */
enum class Grade(val label: String) { AGAIN("Again"), HARD("Hard"), GOOD("Good"), EASY("Easy") }

/** A card's memory as FSRS models it: how long it lasts (stability) and how hard it is (difficulty). */
data class CardMemory(
    /** Days until recall drops to 90%. */
    val stability: Double,
    /** 1 (easy) to 10 (hard). */
    val difficulty: Double,
    val lastReview: Long,
    val reviews: Int = 0,
    val lapses: Int = 0
) {
    fun encode(): String =
        String.format(Locale.US, "fsrs1|%.4f|%.4f|%d|%d|%d", stability, difficulty, lastReview, reviews, lapses)

    companion object {
        fun decode(text: String): CardMemory? {
            val p = text.split('|')
            if (p.size != 6 || p[0] != "fsrs1") return null
            return runCatching {
                CardMemory(p[1].toDouble(), p[2].toDouble(), p[3].toLong(), p[4].toInt(), p[5].toInt())
            }.getOrNull()?.takeIf { it.stability > 0 && it.difficulty in 1.0..10.0 }
        }
    }
}

/**
 * FSRS-5 spaced repetition (the scheduler Anki offers since 23.10), with its published default
 * parameters and a 90% retention target. It models each card's stability and difficulty, and
 * needs fewer reviews than SM-2 for the same recall. Cards scheduled by the old SM-2 code are
 * taken over from their current interval and ease.
 */
object Scheduler {
    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE
    const val RETENTION = 0.9
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    )

    data class Next(val ease: Float, val intervalDays: Int, val dueAt: Long, val memory: String = "")

    fun next(card: Flashcard, grade: Grade, now: Long = System.currentTimeMillis()): Next {
        val g = grade.ordinal + 1
        val before = CardMemory.decode(card.memory) ?: fromSm2(card)
        val stability: Double
        val difficulty: Double
        if (before == null) {
            stability = W[g - 1]
            difficulty = initialDifficulty(g)
        } else {
            val elapsed = max(0.0, (now - before.lastReview).toDouble() / DAY)
            val r = retrievability(elapsed, before.stability)
            stability = when {
                elapsed < 1.0 -> shortTerm(before.stability, g)
                g == 1 -> forget(before.difficulty, before.stability, r)
                else -> recall(before.difficulty, before.stability, r, g)
            }
            difficulty = nextDifficulty(before.difficulty, g)
        }
        val s = stability.coerceIn(0.01, 36_500.0)
        val memory = CardMemory(
            stability = s,
            difficulty = difficulty,
            lastReview = now,
            reviews = (before?.reviews ?: 0) + 1,
            lapses = (before?.lapses ?: 0) + if (g == 1 && before != null) 1 else 0
        ).encode()
        if (g == 1) return Next(card.ease, 0, now + 10 * MINUTE, memory)
        val days = interval(s).roundToInt().coerceIn(1, 3650)
        return Next(card.ease, days, now + days * DAY, memory)
    }

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    /** Days until recall falls to [RETENTION]. */
    fun interval(stability: Double): Double = stability / FACTOR * (RETENTION.pow(1 / DECAY) - 1)

    private fun initialDifficulty(g: Int): Double = (W[4] - exp(W[5] * (g - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val delta = -W[6] * (g - 3)
        val damped = d + delta * (10 - d) / 9
        return (W[7] * initialDifficulty(4) + (1 - W[7]) * damped).coerceIn(1.0, 10.0)
    }

    private fun recall(d: Double, s: Double, r: Double, g: Int): Double {
        val hard = if (g == 2) W[15] else 1.0
        val easy = if (g == 4) W[16] else 1.0
        return s * (1 + exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hard * easy)
    }

    private fun forget(d: Double, s: Double, r: Double): Double =
        min(s, W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r)))

    private fun shortTerm(s: Double, g: Int): Double = s * exp(W[17] * (g - 3 + W[18]))

    /** A card last scheduled by SM-2: its interval stands in for stability, its ease for difficulty. */
    private fun fromSm2(card: Flashcard): CardMemory? {
        if (card.intervalDays <= 0) return null
        return CardMemory(
            stability = card.intervalDays.toDouble(),
            difficulty = (5 + (2.5 - card.ease) * 3.33).coerceIn(1.0, 10.0),
            lastReview = card.dueAt - card.intervalDays * DAY,
            reviews = 1
        )
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
        val existing = dao.findVocabularyItem(card.term, card.language.code) ?: return
        // Schedule from the stored card, so the memory is always the latest one.
        val next = Scheduler.next(existing.toCard(), grade)
        dao.upsertVocabularyItem(
            // The FSRS memory lives in the card's unused level column, so the database needs no migration.
            existing.copy(easeFactor = next.ease, intervalDays = next.intervalDays, nextReviewAt = next.dueAt, cefrLevel = next.memory)
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
        dueAt = nextReviewAt,
        memory = cefrLevel
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
    /** "⭐ la gare (ga-ʁ) – train station": word, optional reading, optional meaning. */
    private val STAR_WORD = Regex("⭐\\s*([^–—\\-(（\\n:]{1,40}?)\\s*(?:[（(]([^)）]{1,60})[)）])?\\s*(?:[–—-]\\s*([^\\n]{1,80}))?\\s*(?:\\n|$)")
    /** Lines that are labels or sentences rather than a word: "EN: …", "✏️ …", questions. */
    private val NOT_A_TERM = Regex("^(?:EN|Romaji)\\s*[:：]|[✏?¿!¡.]", RegexOption.IGNORE_CASE)

    /**
     * [chinese] = false for the other tutors: then any short "word – meaning" item counts, and
     * ⭐ words are written in letters ("⭐ la gare – train station").
     */
    fun from(text: String, chinese: Boolean = true): List<WordCandidate> {
        val lines = text.lines()
        val found = LinkedHashMap<String, WordCandidate>()

        for (raw in lines) {
            val line = raw.replace(LIST_MARKER, "").replace("**", "").replace("⭐", "").trim()
            val parts = line.split(DASH).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2 || parts[0].length > 30) continue
            if (chinese && !HAN.containsMatchIn(parts[0])) continue
            if (!chinese && (NOT_A_TERM.containsMatchIn(parts[0]) || parts[0].split(' ').size > 5)) continue
            val (pronunciation, meaning) = when {
                parts.size >= 3 -> parts[1] to parts.drop(2).joinToString(" – ")
                else -> "" to parts[1]
            }
            if (HAN.containsMatchIn(meaning) && parts.size == 2) continue  // a sentence, not "word – meaning"
            found.putIfAbsent(parts[0], WordCandidate(parts[0], pronunciation, meaning, exampleFor(parts[0], lines)))
        }

        if (chinese) {
            for (match in STAR.findAll(text)) {
                val term = match.groupValues[1].trim()
                if (term.isEmpty()) continue
                found.putIfAbsent(term, WordCandidate(term, match.groupValues[2].trim(), "", exampleFor(term, lines)))
            }
        } else {
            for (match in STAR_WORD.findAll(text)) {
                val term = match.groupValues[1].trim()
                if (term.isEmpty() || term.split(' ').size > 5) continue
                found.putIfAbsent(
                    term,
                    WordCandidate(term, match.groupValues[2].trim(), match.groupValues[3].trim(), exampleFor(term, lines))
                )
            }
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
