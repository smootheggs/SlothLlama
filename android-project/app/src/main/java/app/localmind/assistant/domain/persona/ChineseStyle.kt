package app.localmind.assistant.domain.persona

/** Which Chinese characters the tutors write. */
enum class ChineseScript(val label: String, val detail: String) {
    SIMPLIFIED("简 Simplified", "Mainland China and Singapore"),
    TRADITIONAL("繁 Traditional", "Hong Kong, Taiwan and Macau"),
    BOTH("Both", "Every sentence in 简 and 繁, line by line")
}

/** How Chinese-speaking personas format Chinese text; chosen in Settings → Chinese. */
data class ChineseStyle(
    val script: ChineseScript = ChineseScript.BOTH,
    /** Pinyin under Mandarin, Jyutping under Cantonese. */
    val romanization: Boolean = true,
    /** Size of Chinese characters in replies, relative to the surrounding text (1.0–1.6). */
    val charScale: Float = 1.2f
)

/**
 * Builds the "Chinese writing rules" for a persona's system prompt from the learner's settings,
 * so the four tutors (and any custom persona with a Chinese voice) all follow one format.
 *
 * Small on-device models follow labelled lines and a worked example much better than prose,
 * so the rules always end with an example in exactly the requested format.
 */
object ChineseRules {

    /** Built-in prompts put this where the rules belong; other prompts get them appended. */
    const val PLACEHOLDER = "{{CHINESE_RULES}}"

    fun systemInstructionFor(persona: Persona, style: ChineseStyle): String {
        val language = persona.speech
        val base = persona.systemInstruction
        if (language == null) return base.replace(PLACEHOLDER, "").trimEnd()
        val rules = render(style, language)
        return if (PLACEHOLDER in base) base.replace(PLACEHOLDER, rules) else "$base\n\n$rules"
    }

    fun render(style: ChineseStyle, language: SpeechLanguage): String {
        val ex = when (language) {
            SpeechLanguage.MANDARIN -> Example(
                simplified = "你今天想吃什么？",
                traditional = "你今天想吃什麼？",
                romanized = "nǐ jīntiān xiǎng chī shénme?",
                wordSimplified = "时间", wordTraditional = "時間", wordRomanized = "shíjiān",
                romanName = "Pinyin", romanHow = "with tone marks"
            )
            SpeechLanguage.CANTONESE -> Example(
                simplified = "我哋去边度食饭？",
                traditional = "我哋去邊度食飯？",
                romanized = "ngo5 dei6 heoi3 bin1 dou6 sik6 faan6?",
                wordSimplified = "时间", wordTraditional = "時間", wordRomanized = "si4 gaan3",
                romanName = "Jyutping", romanHow = "with tone numbers"
            )
        }
        // Cantonese learners usually read traditional first (Hong Kong); Mandarin, simplified.
        val traditionalFirst = language == SpeechLanguage.CANTONESE
        val roman = style.romanization

        val lines = mutableListOf("Chinese writing rules (always follow these exactly):")
        val exampleLines = mutableListOf<String>()
        val word: String

        when (style.script) {
            ChineseScript.BOTH -> {
                val (firstLabel, firstName, firstText) =
                    if (traditionalFirst) Triple("繁", "traditional", ex.traditional) else Triple("简", "simplified", ex.simplified)
                val (secondLabel, secondName, secondText) =
                    if (traditionalFirst) Triple("简", "simplified", ex.simplified) else Triple("繁", "traditional", ex.traditional)
                lines += "- Write every Chinese sentence twice, on two lines with exactly the same words: " +
                    "first a line starting with \"$firstLabel:\" in $firstName characters, " +
                    "then a line starting with \"$secondLabel:\" in $secondName characters."
                exampleLines += "$firstLabel: $firstText"
                exampleLines += "$secondLabel: $secondText"
                val (w1, w2) = if (traditionalFirst) ex.wordTraditional to ex.wordSimplified else ex.wordSimplified to ex.wordTraditional
                word = "$w1 / $w2"
            }
            ChineseScript.SIMPLIFIED -> {
                lines += "- Write all Chinese in simplified characters (简体字) only."
                exampleLines += ex.simplified
                word = ex.wordSimplified
            }
            ChineseScript.TRADITIONAL -> {
                lines += "- Write all Chinese in traditional characters (繁體字) only."
                exampleLines += ex.traditional
                word = ex.wordTraditional
            }
        }

        if (roman) {
            lines += "- After the Chinese, add one line starting with \"${ex.romanName}:\" " +
                "giving the ${ex.romanName} ${ex.romanHow}."
            exampleLines += "${ex.romanName}: ${ex.romanized}"
        } else {
            lines += "- Do not add ${ex.romanName} or any other romanization."
        }

        val wordFormat = buildString {
            append(if (style.script == ChineseScript.BOTH) "characters (both forms, separated by /)" else "characters")
            if (roman) append(" – ${ex.romanName}")
            append(" – English")
        }
        val wordExample = buildString {
            append(word)
            if (roman) append(" – ${ex.wordRomanized}")
            append(" – time")
        }
        lines += "- In word lists, write each item as: $wordFormat. For example: $wordExample"
        lines += "- You only see typed text, so never comment on the learner's tones or pronunciation. " +
            "Correct only what they wrote: characters, words and grammar."
        lines += "Example of a Chinese sentence in the required format:"
        lines += exampleLines
        return lines.joinToString("\n")
    }

    private class Example(
        val simplified: String,
        val traditional: String,
        val romanized: String,
        val wordSimplified: String,
        val wordTraditional: String,
        val wordRomanized: String,
        val romanName: String,
        val romanHow: String
    )
}
