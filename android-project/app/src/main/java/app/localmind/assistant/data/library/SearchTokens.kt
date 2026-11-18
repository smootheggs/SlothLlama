package app.localmind.assistant.data.library

import java.text.Normalizer

/**
 * The words the library indexes and searches by. Latin-script words are lower-cased without
 * accents ("Café" → "cafe"). Chinese, Japanese and Korean have no spaces, so a run of those
 * characters becomes its single characters and overlapping pairs (长城历史 → 长城 城历 历史 …):
 * a query's pairs then find the same pairs in the text, whatever the word boundaries are.
 * (Chinese is converted to simplified first, so either script finds both.)
 */
object SearchTokens {

    /** Tokens for indexing a passage. */
    fun forIndex(text: String): List<String> = tokens(text, query = false)

    /** Tokens for a query: pairs only for CJK (more precise), no stop-words, no repeats. */
    fun forQuery(text: String): List<String> = tokens(text, query = true).distinct()

    /** Words for Kiwix's own search: content words, and CJK runs whole (Kiwix splits them itself). */
    fun keywords(text: String, max: Int = 5): List<String> {
        val out = mutableListOf<String>()
        for (run in runs(text)) {
            if (run.cjk) {
                if (run.text.length >= 2) out += run.text
            } else {
                val w = run.text.lowercase()
                if (w.length >= 3 && w !in STOP && !w.all(Char::isDigit) || (w.all(Char::isDigit) && w.length == 4)) out += run.text
            }
        }
        return out.distinct().take(max)
    }

    private class Run(val text: String, val cjk: Boolean)

    private fun runs(text: String): List<Run> {
        val out = mutableListOf<Run>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                isCjk(c) -> {
                    val start = i
                    while (i < text.length && isCjk(text[i])) i++
                    out += Run(text.substring(start, i), true)
                }
                Character.isLetterOrDigit(c) -> {
                    val start = i
                    while (i < text.length && (Character.isLetterOrDigit(text[i]) || Character.getType(text[i]) == Character.NON_SPACING_MARK.toInt())) i++
                    out += Run(text.substring(start, i), false)
                }
                else -> i++
            }
        }
        return out
    }

    private fun tokens(text: String, query: Boolean): List<String> {
        val out = ArrayList<String>()
        for (run in runs(text)) {
            if (run.cjk) {
                val s = run.text
                if (s.length == 1 || !query) s.forEach { out += it.toString() }
                for (k in 0 until s.length - 1) out += s.substring(k, k + 2)
            } else {
                val w = fold(run.text.lowercase())
                if (w.length < 2 && !w.all(Char::isDigit)) continue
                if (w in STOP) continue
                // Very long tokens (hashes, URLs) only bloat the index.
                if (w.length <= 40) out += w
            }
        }
        return out
    }

    private fun fold(word: String): String =
        if (word.all { it.code < 0x250 }) Normalizer.normalize(word, Normalizer.Form.NFD).replace(MARKS, "") else word

    fun isCjk(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿' ||
            c in '぀'..'ヿ' || c in '가'..'힯'

    private val MARKS = Regex("\\p{Mn}+")

    /** Common words in English, French, German, Spanish and Italian (plus question words). */
    private val STOP = (
        "a an and are as at be been but by can could did do does for from had has have how i if in into is it its " +
            "me my no not of on or our she so than that the their them then there these they this to was we were " +
            "what when where which who why will with would you your about tell explain please give show find " +
            "le la les un une des du de et est en que qui dans pour pas sur au aux ce ces il elle nous vous ils " +
            "der die das ein eine und ist im in den dem des zu mit von fur nicht auf es sie wir ihr was wie " +
            "el los las unos unas y es en que por para con no se lo del al su como mas " +
            "il lo gli i una uno e di da per con non che si come piu"
        ).split(' ').toHashSet()
}
