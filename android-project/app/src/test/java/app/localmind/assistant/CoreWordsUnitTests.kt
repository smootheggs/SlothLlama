package app.localmind.assistant

import app.localmind.assistant.domain.core.CoreBand
import app.localmind.assistant.domain.core.CoreLanguage
import app.localmind.assistant.domain.core.CorePos
import app.localmind.assistant.domain.core.CoreWord
import app.localmind.assistant.domain.core.CoreWords
import app.localmind.assistant.domain.core.FrenchConjugator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Core words lists and the French conjugator. */
class CoreWordsUnitTests {

    private fun asset(name: String): String {
        // Gradle runs unit tests from the module directory; an IDE may run them from the project's.
        val file = listOf("src/main/assets/core/$name", "app/src/main/assets/core/$name").map(::File).first { it.exists() }
        return file.readText()
    }

    private fun list(language: CoreLanguage) = CoreWords.parse(asset("${language.code}.json")).words

    private fun strings(o: JsonObject, key: String) = o.getValue(key).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `the Kotlin conjugator matches the Python engine for every listed verb`() {
        val text = javaClass.classLoader!!.getResourceAsStream("core/fr_conj_golden.json")!!.bufferedReader().use { it.readText() }
        val golden = Json.parseToJsonElement(text).jsonObject
        assertTrue(golden.size > 300)
        val tenses = mapOf<String, (FrenchConjugator.Tables) -> List<String>>(
            "present" to { it.present }, "imparfait" to { it.imparfait }, "passe_simple" to { it.passeSimple },
            "futur" to { it.futur }, "conditionnel" to { it.conditionnel }, "subjonctif" to { it.subjonctif },
            "imperatif" to { it.imperatif }
        )
        for ((verb, value) in golden) {
            val g = value.jsonObject
            val conjugated = FrenchConjugator.conjugate(verb)
            assertNotNull(verb, conjugated)
            val c = conjugated!!
            for ((key, get) in tenses) assertEquals("$verb $key", strings(g, key), get(c.tables))
            assertEquals("$verb ppr", g.getValue("ppr").jsonPrimitive.content, c.tables.ppr)
            assertEquals("$verb participles", strings(g, "pps"), c.participles)
            assertEquals("$verb aux", g.getValue("aux").jsonPrimitive.content, c.aux)
            assertEquals("$verb both", g.getValue("both").jsonPrimitive.boolean, c.both)
            assertEquals("$verb reflexive", g.getValue("reflexive").jsonPrimitive.boolean, c.reflexive)
            val persons = (0 until 6).map { FrenchConjugator.withPronoun(it, c.tables.present[it], c.infinitive, c.reflexive) }
            assertEquals("$verb persons", strings(g, "persons"), persons)
        }
    }

    @Test
    fun `conjugation by hand, for the verbs that trip people up`() {
        fun present(v: String) = FrenchConjugator.conjugate(v)!!.tables.present
        assertEquals(listOf("suis", "es", "est", "sommes", "êtes", "sont"), present("être"))
        assertEquals(listOf("appelle", "appelles", "appelle", "appelons", "appelez", "appellent"), present("appeler"))
        assertEquals(listOf("achète", "achètes", "achète", "achetons", "achetez", "achètent"), present("acheter"))
        assertEquals("nettoierai", FrenchConjugator.conjugate("nettoyer")!!.tables.futur[0])
        assertEquals("commençons", present("commencer")[3])
        assertEquals("mangeons", present("manger")[3])
        assertEquals("préférerai", FrenchConjugator.conjugate("préférer")!!.tables.futur[0])
        assertEquals("finissons", present("finir")[3])
        assertEquals("rend", present("rendre")[2])
        assertEquals("faille", FrenchConjugator.conjugate("falloir")!!.tables.subjonctif[2])
        assertEquals("je me lève", FrenchConjugator.withPronoun(0, "lève", "lever", true))
        assertEquals("il s'assied", FrenchConjugator.withPronoun(2, "assied", "asseoir", true))
        assertEquals("j'aime", FrenchConjugator.withPronoun(0, "aime", "aimer", false))
        assertEquals("qu'il soit", FrenchConjugator.withQue(2, "soit", "être", false))
        assertEquals("lève-toi", FrenchConjugator.imperative(0, "lève", true))
        val aller = FrenchConjugator.conjugate("aller")!!
        assertEquals("sommes allé(e)s", FrenchConjugator.compound(aller, 3, FrenchConjugator.etre.present))
        val faire = FrenchConjugator.conjugate("faire")!!
        assertEquals("ai fait", FrenchConjugator.compound(faire, 0, FrenchConjugator.avoir.present))
        assertTrue("allons" in FrenchConjugator.forms("aller"))
    }

    @Test
    fun `the three lists load, in rank order, each word once`() {
        for (language in CoreLanguage.entries) {
            val words = list(language)
            assertTrue(language.name, words.size >= 1000)
            assertEquals(language.name, (1..words.size).toList(), words.map { it.rank })
            assertEquals(language.name, words.size, words.map(CoreWord::key).toSet().size)
            assertTrue(language.name, words.all { it.gloss.isNotBlank() && it.partsOfSpeech.isNotEmpty() })
            assertTrue(language.name, words.any { it.note != null && it.examples.isNotEmpty() })
        }
        val zh = list(CoreLanguage.MANDARIN)
        assertTrue(zh.all { !it.pinyin.isNullOrBlank() && it.hsk != null })
        val yue = list(CoreLanguage.CANTONESE)
        assertTrue(yue.all { !it.jyutping.isNullOrBlank() })
        // Every French verb in the list conjugates.
        val fr = list(CoreLanguage.FRENCH)
        assertTrue(fr.filter { it.mainPos == CorePos.VERB }.all { FrenchConjugator.conjugate(it.word) != null })
        assertTrue(fr.filter { it.mainPos == CorePos.NOUN }.all { it.gender == "m" || it.gender == "f" })
    }

    @Test
    fun `search finds words in any form, by reading, or by English`() {
        fun first(language: CoreLanguage, q: String): String? {
            val words = list(language)
            return CoreWords.search(q, words, CoreWords.index(language, words)).firstOrNull()?.word
        }
        assertEquals("aller", first(CoreLanguage.FRENCH, "allons"))
        assertEquals("œil", first(CoreLanguage.FRENCH, "yeux"))
        assertEquals("beau", first(CoreLanguage.FRENCH, "belle"))
        assertEquals("être", first(CoreLanguage.FRENCH, "etre"))
        assertEquals("你", first(CoreLanguage.MANDARIN, "ni3"))
        assertEquals("你", first(CoreLanguage.MANDARIN, "nǐ"))
        assertEquals("学习", first(CoreLanguage.MANDARIN, "學習"))
        assertEquals("吃", first(CoreLanguage.MANDARIN, "to eat"))
        assertEquals("係", first(CoreLanguage.CANTONESE, "hai6"))
        assertEquals("食", first(CoreLanguage.CANTONESE, "sik6"))
    }

    @Test
    fun `filters and coverage`() {
        val words = list(CoreLanguage.FRENCH)
        val top = CoreWords.filter(words, CoreBand.TOP_100, null)
        assertEquals(100, top.size)
        val verbs = CoreWords.filter(words, CoreBand.TOP_500, CorePos.VERB)
        assertTrue(verbs.isNotEmpty() && verbs.all { "v" in it.pos && it.rank <= 500 })
        val known = words.take(20).map { it.key }.toSet()
        val share = CoreWords.coverage(words, known)
        // The twenty commonest French words are about a third of everything written.
        assertTrue("$share", share in 0.2..0.5)
        assertEquals("preferer", CoreWords.fold("Préférer"))
        assertEquals("nihao", CoreWords.toneless("ni3 hao3"))
    }
}
