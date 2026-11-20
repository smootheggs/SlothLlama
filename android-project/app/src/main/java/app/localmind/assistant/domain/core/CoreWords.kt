package app.localmind.assistant.domain.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * The Core words lists: the commonest words of a language, ranked by how often they're used,
 * with their part of speech, a gloss and, for the little words, a short note. Built by
 * scripts/core_words/build.py into assets/core/{fr,zh,yue}.json.
 */
enum class CoreLanguage(val code: String, val label: String, val source: String) {
    MANDARIN("zh", "Mandarin", "Ranked by complete-hsk-vocabulary, glosses from CC-CEDICT"),
    CANTONESE("yue", "Cantonese", "Ranked by the Hong Kong Cantonese Corpus (spoken, 1997–98)"),
    FRENCH("fr", "French", "Ranked by wordfreq over books, films, news and the web");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: MANDARIN
    }
}

/** Parts of speech as the lists tag them. */
enum class CorePos(val code: String, val label: String, val plural: String) {
    NOUN("n", "noun", "Nouns"),
    VERB("v", "verb", "Verbs"),
    ADJECTIVE("adj", "adjective", "Adjectives"),
    ADVERB("adv", "adverb", "Adverbs"),
    PRONOUN("pron", "pronoun", "Pronouns"),
    DETERMINER("det", "determiner", "Determiners"),
    PREPOSITION("prep", "preposition", "Prepositions"),
    CONJUNCTION("conj", "conjunction", "Conjunctions"),
    MEASURE("cl", "measure word", "Measure words"),
    PARTICLE("part", "particle", "Particles"),
    PLACE("loc", "position word", "Position words"),
    NUMBER("num", "number", "Numbers"),
    INTERJECTION("interj", "interjection", "Interjections"),
    EXPRESSION("expr", "expression", "Expressions");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code }
    }
}

@Serializable
data class CoreExample(val text: String, val roman: String, val meaning: String)

@Serializable
data class CoreWord(
    /** The word: French lemma, simplified Chinese, or traditional Cantonese. */
    @SerialName("w") val word: String,
    @SerialName("p") val pos: List<String>,
    @SerialName("g") val gloss: String,
    /** 1 = most common. */
    @SerialName("r") val rank: Int,
    /** Its share of running text, in parts per million. */
    @SerialName("s") val share: Double = 0.0,
    // French
    @SerialName("gen") val gender: String? = null,
    /** Plural when it isn't word + s. */
    @SerialName("pl") val plural: String? = null,
    /** Adjective: feminine, masculine plural, feminine plural. */
    @SerialName("af") val adjectiveForms: List<String>? = null,
    /** Adjective before a vowel (beau → bel, nouveau → nouvel). */
    @SerialName("bv") val beforeVowel: String? = null,
    /** Other forms of a little word (le: la, les, l'). */
    @SerialName("fm") val forms: List<String>? = null,
    // Mandarin
    @SerialName("t") val traditional: String? = null,
    @SerialName("py") val pinyin: String? = null,
    @SerialName("pn") val pinyinNumbered: String? = null,
    @SerialName("hsk") val hsk: Int? = null,
    @SerialName("cl") val classifiers: List<String>? = null,
    /** Other readings: [pinyin, numbered pinyin, gloss]. */
    @SerialName("or") val otherReadingsRaw: List<List<String>>? = null,
    // Cantonese
    @SerialName("jp") val jyutping: String? = null,
    // Notes
    @SerialName("n") val note: String? = null,
    @SerialName("ex") val examplesRaw: List<List<String>>? = null
) {
    val partsOfSpeech: List<CorePos> get() = pos.mapNotNull { CorePos.fromCode(it) }
    val mainPos: CorePos? get() = partsOfSpeech.firstOrNull()
    val examples: List<CoreExample>
        get() = examplesRaw.orEmpty().filter { it.size == 3 }.map { CoreExample(it[0], it[1], it[2]) }

    /** A stable key for marks: the same word can be a noun and a verb (le dîner, dîner). */
    val key: String get() = word + "|" + pos.firstOrNull().orEmpty() + (gender?.let { "|$it" } ?: "")

    val otherReadings: List<List<String>> get() = otherReadingsRaw.orEmpty().filter { it.size == 3 }
}

@Serializable
data class CoreList(val lang: String, val words: List<CoreWord>)

/** How well you know a word. */
enum class CoreMark(val code: Int, val label: String) {
    NEW(0, "New"), LEARNING(1, "Learning"), KNOWN(2, "Known");

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: NEW
    }
}

/** The frequency bands the list can be cut to. */
enum class CoreBand(val top: Int?, val label: String) {
    TOP_100(100, "Top 100"), TOP_500(500, "500"), TOP_1000(1000, "1,000"), TOP_2000(2000, "2,000"), ALL(null, "All")
}

object CoreWords {
    val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CoreList = json.decodeFromString(CoreList.serializer(), text)

    /** Lower case, no accents or tone marks, no spaces: "Préférer" → "preferer", "nǐ hǎo" → "nihao". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .filter { !it.isWhitespace() && it != '\'' && it != '’' && it != '-' }

    /** Pinyin or Jyutping with the tone numbers dropped: "ni3 hao3" → "nihao". */
    fun toneless(s: String): String = fold(s).filter { !it.isDigit() }

    /**
     * Every written form a search should find a French word by: conjugated verbs, noun plurals,
     * adjective genders and numbers, a little word's other forms.
     */
    fun frenchForms(w: CoreWord): Set<String> {
        val out = linkedSetOf(w.word)
        when (w.mainPos) {
            CorePos.VERB -> out.addAll(FrenchConjugator.forms(w.word))
            CorePos.NOUN -> out.add(w.plural ?: (w.word + "s"))
            CorePos.ADJECTIVE -> {
                w.adjectiveForms?.let(out::addAll)
                w.beforeVowel?.let(out::add)
            }
            else -> w.forms?.let(out::addAll)
        }
        return out.filterTo(linkedSetOf()) { it.isNotBlank() && it != "-" }
    }

    /**
     * Index of folded forms → positions in [words], so "allons", "yeux" or "belle" finds
     * aller, œil and beau, and "ni3" or "nei" finds 你.
     */
    fun index(language: CoreLanguage, words: List<CoreWord>): Map<String, List<Int>> {
        val out = HashMap<String, MutableList<Int>>()
        fun put(key: String, i: Int) {
            if (key.isEmpty()) return
            val list = out.getOrPut(key) { mutableListOf() }
            if (list.lastOrNull() != i) list.add(i)
        }
        words.forEachIndexed { i, w ->
            when (language) {
                CoreLanguage.FRENCH -> frenchForms(w).forEach { put(fold(it), i) }
                CoreLanguage.MANDARIN -> {
                    put(w.word, i)
                    w.traditional?.let { put(it, i) }
                    w.pinyin?.let { put(toneless(it), i); put(fold(it), i) }
                    w.pinyinNumbered?.let { put(fold(it), i) }
                }
                CoreLanguage.CANTONESE -> {
                    put(w.word, i)
                    w.jyutping?.let { put(fold(it), i); put(toneless(it), i) }
                }
            }
        }
        return out
    }

    /**
     * Words matching [query]: exact forms first, then words starting with it, then glosses that
     * contain it as a word. Results keep the list's frequency order within each group.
     */
    fun search(query: String, words: List<CoreWord>, index: Map<String, List<Int>>, limit: Int = 80): List<CoreWord> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val fq = fold(q)
        val tq = toneless(q)
        val hits = LinkedHashSet<Int>()
        index[q]?.let(hits::addAll)
        index[fq]?.let(hits::addAll)
        if (tq != fq) index[tq]?.let(hits::addAll)
        if (hits.size < limit && fq.length >= 2) {
            words.indices.filterTo(hits) { i ->
                val w = words[i]
                w.word.startsWith(q) || fold(w.word).startsWith(fq) ||
                    (w.pinyin?.let { toneless(it).startsWith(tq) } ?: false) ||
                    (w.jyutping?.let { toneless(it).startsWith(tq) } ?: false)
            }
        }
        if (hits.size < limit && q.length >= 3) {
            val needle = Regex("\\b" + Regex.escape(q.lowercase()) + "\\b")
            words.indices.filterTo(hits) { i -> needle.containsMatchIn(words[i].gloss.lowercase()) }
        }
        return hits.take(limit).map { words[it] }
    }

    /**
     * How much of everyday text the known words cover: the share of running text their own
     * frequencies add up to (a rough guide; Chinese text isn't split into words the same way).
     */
    fun coverage(words: List<CoreWord>, known: Set<String>): Double =
        words.filter { it.key in known }.sumOf { it.share } / 1_000_000.0

    /** The list cut to [band] and, when [pos] is set, to words that can be that part of speech. */
    fun filter(words: List<CoreWord>, band: CoreBand, pos: CorePos?): List<CoreWord> =
        words.asSequence()
            .filter { band.top == null || it.rank <= band.top }
            .filter { pos == null || pos.code in it.pos }
            .toList()
}
