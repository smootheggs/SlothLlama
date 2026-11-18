package app.localmind.assistant.domain.persona

/**
 * Languages the tutors speak: read aloud with Android's built-in text-to-speech ([localeTags],
 * tried in order) and heard with the offline voice models ([speechCode]).
 */
enum class SpeechLanguage(val label: String, val localeTags: List<String>, val speechCode: String) {
    MANDARIN("Mandarin", listOf("zh-CN"), "zh"),
    // Engines name Cantonese differently; try each in order.
    CANTONESE("Cantonese", listOf("yue-HK", "zh-HK"), "yue"),
    JAPANESE("Japanese", listOf("ja-JP"), "ja"),
    FRENCH("French", listOf("fr-FR", "fr-CA"), "fr"),
    GERMAN("German", listOf("de-DE", "de-AT"), "de"),
    SPANISH("Spanish", listOf("es-ES", "es-US", "es-MX"), "es"),
    ITALIAN("Italian", listOf("it-IT"), "it");

    /** Written in Chinese characters, with the Chinese settings (简/繁, Pinyin/Jyutping) applying. */
    val isChinese: Boolean get() = this == MANDARIN || this == CANTONESE

    /** A flag for the language; Mandarin's follows the characters chosen in Settings → Chinese. */
    fun flag(script: ChineseScript): String = when (this) {
        MANDARIN -> if (script == ChineseScript.TRADITIONAL) "🇹🇼" else "🇨🇳"
        CANTONESE -> "🇭🇰"
        JAPANESE -> "🇯🇵"
        FRENCH -> "🇫🇷"
        GERMAN -> "🇩🇪"
        SPANISH -> "🇪🇸"
        ITALIAN -> "🇮🇹"
    }
}

data class Persona(
    val id: String,
    val name: String,
    /** One or two characters shown in the picker (e.g. 普 for Mandarin). */
    val badge: String,
    val tagline: String,
    val systemInstruction: String,
    val temperature: Float,
    val maxOutputTokens: Int,
    /** Null = no read-aloud button. */
    val speech: SpeechLanguage?,
    val starterPrompts: List<String>,
    /** Built-ins live in code; custom personas are stored in the database and can be edited. */
    val isBuiltIn: Boolean = true
)
