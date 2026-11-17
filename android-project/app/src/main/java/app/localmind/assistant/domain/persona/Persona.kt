package app.localmind.assistant.domain.persona

/** Languages the app can read aloud with Android's built-in text-to-speech. */
enum class SpeechLanguage(val label: String, val localeTags: List<String>) {
    MANDARIN("Mandarin", listOf("zh-CN")),
    // Engines name Cantonese differently; try each in order.
    CANTONESE("Cantonese", listOf("yue-HK", "zh-HK"))
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
    val starterPrompts: List<String>
)
