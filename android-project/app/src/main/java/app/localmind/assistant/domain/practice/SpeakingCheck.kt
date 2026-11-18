package app.localmind.assistant.domain.practice

import java.text.Normalizer

/**
 * One piece of the sentence to say (a character for Chinese and Japanese, a word otherwise) and
 * whether it was heard.
 */
data class HeardUnit(val text: String, val heard: Boolean)

/** [spaced]: units are words, shown with spaces between them. */
data class SpeakingScore(val units: List<HeardUnit>, val percent: Int, val spaced: Boolean) {
    val heardCount: Int get() = units.count { it.heard }
}

/**
 * Compares what the learner was asked to say with what the speech recogniser heard.
 *
 * Chinese and Japanese are compared character by character, other languages word by word.
 * Both sides are aligned with a longest common subsequence, so one missed word doesn't mark
 * everything after it wrong. Two units match when their [key]s are equal (e.g. the same
 * character in simplified form, or the same word without accents or capitals) or [sameSound]
 * says so (a homophone the recogniser picked is still the right sound).
 */
object SpeakingCheck {

    /** Splits text into the units the learner is scored on. */
    enum class Units {
        /** Chinese characters only. */
        HAN,
        /** Chinese characters and kana. */
        JAPANESE,
        /** Words (letters, accents and apostrophes). */
        WORDS
    }

    fun units(text: String, kind: Units): List<String> = when (kind) {
        Units.HAN -> text.filter(::isHan).map { it.toString() }
        Units.JAPANESE -> text.filter { isHan(it) || isKana(it) }.map { it.toString() }
        Units.WORDS -> WORD.findAll(text).map { it.value }.toList()
    }

    fun score(
        expected: String,
        heard: String,
        kind: Units,
        key: (String) -> String = defaultKey(kind),
        sameSound: (String, String) -> Boolean = { _, _ -> false }
    ): SpeakingScore {
        val shown = units(expected, kind)
        val a = shown.map(key)
        val b = units(heard, kind).map(key)
        if (a.isEmpty()) return SpeakingScore(emptyList(), 0, kind == Units.WORDS)
        fun same(x: String, y: String) = x == y || sameSound(x, y)

        // lcs[i][j] = longest common subsequence of a[i..] and b[j..]
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                lcs[i][j] = if (same(a[i], b[j])) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val heardAt = BooleanArray(a.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                same(a[i], b[j]) && lcs[i][j] == lcs[i + 1][j + 1] + 1 -> { heardAt[i] = true; i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }
        val matched = heardAt.count { it }
        // Extra words the learner added cost a little, so reading the whole reply isn't 100%.
        val extra = (b.size - matched).coerceAtLeast(0)
        val percent = (100.0 * matched / (a.size + extra / 2.0)).toInt().coerceIn(0, 100)
        return SpeakingScore(shown.mapIndexed { k, u -> HeardUnit(u, heardAt[k]) }, percent, kind == Units.WORDS)
    }

    /** Same word regardless of capitals and accents ("Café" = "cafe"); same kana in either script. */
    fun defaultKey(kind: Units): (String) -> String = when (kind) {
        Units.WORDS -> { w -> foldAccents(w.lowercase()).replace('’', '\'') }
        Units.JAPANESE -> { c -> c.map(::katakanaToHiragana).joinToString("") }
        Units.HAN -> { c -> c }
    }

    fun foldAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING, "")

    private fun katakanaToHiragana(c: Char): Char = if (c in 'ァ'..'ヶ') c - 0x60 else c

    fun isHan(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

    fun isKana(c: Char): Boolean = c in '぀'..'ヿ'

    /** Kept for Chinese callers: the Chinese characters in [text]. */
    fun hanOnly(text: String): String = text.filter(::isHan)

    /**
     * Sentences worth practising from the spoken part of a reply (see TutorSpeaker.spokenText):
     * split at sentence ends; 1–30 characters for Chinese and Japanese, 1–20 words otherwise;
     * without repeats.
     */
    fun sentencesFrom(spoken: String, kind: Units = Units.HAN): List<String> =
        spoken.split('。', '！', '？', '!', '?', '\n', '.', '¡', '¿')
            .map { it.trim(' ', '，', ',', '、', '：', ':', '；', ';', '«', '»', '"', '“', '”') }
            .filter { s -> units(s, kind).size in 1..(if (kind == Units.WORDS) 20 else 30) }
            .distinct()

    private val WORD = Regex("[\\p{L}\\p{M}][\\p{L}\\p{M}'’-]*")
    private val COMBINING = Regex("\\p{Mn}+")
}
