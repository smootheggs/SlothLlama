package app.localmind.assistant.data.dictionary

import java.text.Normalizer

/** Pinyin helpers for CC-CEDICT's numbered pinyin ("ni3 hao3"). */
object Pinyin {

    private val SYLLABLE = Regex("^([A-Za-zÜü:]+)([1-5])$")
    private val MARKS = mapOf(
        'a' to "āáǎà", 'e' to "ēéěè", 'i' to "īíǐì", 'o' to "ōóǒò", 'u' to "ūúǔù", 'ü' to "ǖǘǚǜ"
    )
    private val VOWELS = "aeiouü"

    /** "ni3 hao3" → "nǐ hǎo", "lu:4" → "lǜ", "ma5" → "ma". Anything else is left as it is. */
    fun marked(numbered: String): String =
        numbered.split(' ').joinToString(" ") { mark(it) }

    private fun mark(syllable: String): String {
        val m = SYLLABLE.find(syllable) ?: return syllable.replace("u:", "ü").replace("U:", "Ü")
        val base = m.groupValues[1].replace("u:", "ü").replace("U:", "Ü")
        val tone = m.groupValues[2].toInt()
        if (tone == 5) return base
        val lower = base.lowercase()
        // The standard rule: a or e takes the mark; in "ou" the o does; otherwise the last vowel.
        val at = when {
            'a' in lower -> lower.indexOf('a')
            'e' in lower -> lower.indexOf('e')
            "ou" in lower -> lower.indexOf('o')
            else -> lower.indexOfLast { it in VOWELS }
        }
        if (at < 0) return base // "m2", "ng4", "r5"
        val marks = MARKS[lower[at]] ?: return base
        var c = marks[tone - 1]
        if (base[at].isUpperCase()) c = c.uppercaseChar()
        return base.substring(0, at) + c + base.substring(at + 1)
    }

    /**
     * What the dictionary indexes pinyin by (see scripts/build_dictionary.py): lowercase letters
     * only, ü as v. "nǐ hǎo", "ni3 hao3" and "nihao" all become "nihao"; "lǜ" and "lu:4" become "lv".
     */
    fun searchKey(text: String): String {
        val withV = text.lowercase()
            .replace("u:", "v")
            .replace(Regex("[üǖǘǚǜ]"), "v")
        return Normalizer.normalize(withV, Normalizer.Form.NFD).filter { it in 'a'..'z' }
    }
}
