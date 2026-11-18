package app.localmind.assistant.domain.practice

/** One character of the sentence to say, and whether it was heard. */
data class HeardChar(val char: Char, val heard: Boolean)

data class SpeakingScore(val chars: List<HeardChar>, val percent: Int) {
    val heardCount: Int get() = chars.count { it.heard }
}

/**
 * Compares what the learner was asked to say with what the speech recogniser heard.
 *
 * Only Chinese characters count. Both texts are first put in one script ([normalize], e.g.
 * traditional → simplified, since the recogniser may answer in either), then aligned with a
 * longest common subsequence, so one missed word doesn't mark everything after it wrong.
 * Two characters match when they're the same or [sameSound] says so (a homophone the recogniser
 * picked is still the right sound).
 */
object SpeakingCheck {

    fun hanOnly(text: String): String = text.filter(::isHan)

    fun score(
        expected: String,
        heard: String,
        normalize: (String) -> String = { it },
        sameSound: (Char, Char) -> Boolean = { _, _ -> false }
    ): SpeakingScore {
        val shown = hanOnly(expected)
        val a = normalize(shown)
        val b = normalize(hanOnly(heard))
        if (a.isEmpty()) return SpeakingScore(emptyList(), 0)
        fun same(x: Char, y: Char) = x == y || sameSound(x, y)

        // lcs[i][j] = longest common subsequence of a[i..] and b[j..]
        val lcs = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in a.length - 1 downTo 0) {
            for (j in b.length - 1 downTo 0) {
                lcs[i][j] = if (same(a[i], b[j])) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val heardAt = BooleanArray(a.length)
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            when {
                same(a[i], b[j]) && lcs[i][j] == lcs[i + 1][j + 1] + 1 -> { heardAt[i] = true; i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }
        // Extra words the learner added cost a little, so reading the whole reply isn't 100%.
        val extra = (b.length - heardAt.count { it }).coerceAtLeast(0)
        val percent = (100.0 * heardAt.count { it } / (a.length + extra / 2.0)).toInt().coerceIn(0, 100)
        // normalize() maps character for character, so positions line up with the shown text.
        val chars = shown.mapIndexed { k, c -> HeardChar(c, heardAt.getOrElse(k) { false }) }
        return SpeakingScore(chars, percent)
    }

    fun isHan(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

    /**
     * Sentences worth practising from a tutor reply: the Chinese lines (one of each 简/繁 pair),
     * split at sentence ends, 1–30 characters each, without repeats.
     */
    fun sentencesFrom(chineseOnly: String): List<String> =
        chineseOnly.split('。', '！', '？', '!', '?', '\n')
            .map { it.trim(' ', '，', ',', '、', '：', ':', '；', ';') }
            .filter { s -> hanOnly(s).length in 1..30 }
            .distinct()
}
