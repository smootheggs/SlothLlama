package app.localmind.assistant.domain.persona

/** How well the learner knows a language; chosen per language in Settings → Tutors. */
enum class TutorLevel(val label: String, val prompt: String) {
    BEGINNER("Beginner", "a beginner: knows a few words and greetings"),
    ELEMENTARY("A2", "elementary (about A2): manages simple everyday sentences"),
    INTERMEDIATE("B1–B2", "intermediate (about B1–B2): holds everyday conversations but still makes mistakes"),
    ADVANCED("C1", "advanced (about C1): wants natural idioms and nuance, with few explanations"),
    FLUENT("Fluent", "fluent: wants a real challenge (idioms, register, regional expressions, natural speed)");

    /** Advanced learners get replies without translation lines. */
    val skipsTranslation: Boolean get() = this >= ADVANCED

    companion object {
        /** Until the learner sets one. */
        fun defaultFor(language: SpeechLanguage): TutorLevel = when (language) {
            SpeechLanguage.CANTONESE -> BEGINNER
            else -> ELEMENTARY
        }
    }
}

/**
 * Turns a tutor persona's prompt into the system prompt for this learner: their level for the
 * language goes where [LEVEL] is, the reply format for that level where [FORMAT] is, and the
 * Chinese tutors also get the character / romanization rules ([ChineseRules]).
 */
object TutorRules {
    const val LEVEL = "{{LEVEL}}"
    const val FORMAT = "{{FORMAT}}"

    fun systemInstructionFor(persona: Persona, chinese: ChineseStyle, levels: Map<SpeechLanguage, TutorLevel>): String {
        val language = persona.speech
        if (language == null) {
            return ChineseRules.systemInstructionFor(persona.copy(systemInstruction = strip(persona.systemInstruction)), chinese)
        }
        val level = levels[language] ?: TutorLevel.defaultFor(language)
        val prompt = persona.systemInstruction
            .replace(LEVEL, level.prompt)
            .replace(FORMAT, format(language, level))
        return if (language.isChinese) ChineseRules.systemInstructionFor(persona.copy(systemInstruction = prompt), chinese)
        else prompt.replace(ChineseRules.PLACEHOLDER, "").trimEnd()
    }

    private fun strip(prompt: String) = prompt.replace(LEVEL, "").replace(FORMAT, "")

    /** The reply format for the non-Chinese tutors; Chinese formats come from [ChineseRules]. */
    private fun format(language: SpeechLanguage, level: TutorLevel): String {
        val name = language.label
        val example = NEW_WORD[language] ?: ""
        val newWord = "Introduce at most one new word per reply and mark it with ⭐, like: $example"
        val correction = "Only if the learner's last message had a mistake: a line starting with \"✏️\" " +
            "with the corrected $name and one short explanation" + if (level.skipsTranslation) " in $name." else " in English."
        return when {
            language == SpeechLanguage.JAPANESE && !level.skipsTranslation -> """
                Format every reply like this:
                1. One to three short, natural sentences in Japanese (normal kanji and kana). Use polite です/ます unless the learner switches to casual speech.
                2. A line starting with "Romaji:" with the Hepburn romanization.
                3. A line starting with "EN:" giving the English meaning.
                4. $correction
                5. End with one simple question in Japanese, followed by its own "Romaji:" and "EN:" lines.
                $newWord
            """.trimIndent()
            language == SpeechLanguage.JAPANESE -> """
                Reply in natural Japanese only, with no romaji or English unless the learner asks.
                $correction
                End with a question that keeps the conversation going.
                Now and then use an expression the learner may not know and mark it with ⭐, like: $example
            """.trimIndent()
            !level.skipsTranslation -> """
                Format every reply like this:
                1. One to three short, natural sentences in $name, each on its own line.
                2. A line starting with "EN:" giving the English meaning.
                3. $correction
                4. End with one simple question in $name on its own line, followed by its own "EN:" line.
                $newWord
            """.trimIndent()
            else -> """
                Reply in natural $name only, with no English unless the learner asks.
                $correction
                End with a question that keeps the conversation going.
                Now and then use an idiom or expression the learner may not know and mark it with ⭐, like: $example
            """.trimIndent()
        }
    }

    private val NEW_WORD = mapOf(
        SpeechLanguage.JAPANESE to "⭐ 駅 (eki) – station",
        SpeechLanguage.FRENCH to "⭐ la gare – train station",
        SpeechLanguage.GERMAN to "⭐ der Bahnhof – train station",
        SpeechLanguage.SPANISH to "⭐ la estación – station",
        SpeechLanguage.ITALIAN to "⭐ la stazione – station"
    )
}
