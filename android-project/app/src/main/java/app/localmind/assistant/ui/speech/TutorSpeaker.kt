package app.localmind.assistant.ui.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.localmind.assistant.domain.persona.SpeechLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads tutor replies aloud with Android's own text-to-speech engine (offline once the
 * voice is installed). Only the Chinese characters are spoken, not pinyin, Jyutping or
 * the English lines.
 */
class TutorSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    @Volatile private var ready = false
    /** Utterances someone is waiting on (hands-free mode, repeat-after-me). */
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val nextId = AtomicInteger()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
    }

    private fun finished(utteranceId: String?) {
        utteranceId?.let { waiting.remove(it)?.complete(Unit) }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, language: SpeechLanguage) {
        if (!ready) {
            toast("Text-to-speech is still starting — try again in a second")
            return
        }
        when (val p = prepare(text, language)) {
            Prepared.NothingToSay -> toast("Nothing to read aloud in this reply")
            Prepared.NoVoice -> {
                if (language == SpeechLanguage.CANTONESE) {
                    toast(
                        "No Cantonese voice on this tablet, so it isn't read in Mandarin instead. " +
                            "Add one: Speech Services by Google → Install voice data → Chinese (Hong Kong).",
                        long = true
                    )
                } else {
                    toast("No ${language.label} voice installed. Opening voice settings…")
                }
                openVoiceInstaller()
            }
            is Prepared.Ready -> tts.speak(p.text, TextToSpeech.QUEUE_FLUSH, null, "tutor-reply")
        }
    }

    /**
     * Speaks [text] and returns once it has been said (or stopped). For a Chinese [language] only
     * the Chinese is read, as with [speak]; with null the whole text is read in the tablet's own
     * language. Returns false if nothing could be said (no voice installed, nothing to read).
     * Cancelling the caller stops the speech.
     */
    suspend fun speakAndWait(text: String, language: SpeechLanguage?, rate: Float = LEARNER_RATE): Boolean {
        withTimeoutOrNull(3_000) { while (!ready) delay(100) } ?: return false
        val p = if (language != null) prepare(text, language, rate) else prepareNative(text, rate)
        if (p !is Prepared.Ready) return false
        val id = "wait-${nextId.incrementAndGet()}"
        val done = CompletableDeferred<Unit>()
        waiting[id] = done
        if (tts.speak(p.text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            waiting.remove(id)
            return false
        }
        try {
            done.await()
        } catch (e: CancellationException) {
            waiting.remove(id)
            tts.stop()
            throw e
        }
        return true
    }

    private sealed interface Prepared {
        data object NothingToSay : Prepared
        data object NoVoice : Prepared
        data class Ready(val text: String) : Prepared
    }

    private fun prepare(text: String, language: SpeechLanguage, rate: Float = LEARNER_RATE): Prepared {
        val spoken = spokenText(text, language)
        if (spoken.isBlank()) return Prepared.NothingToSay
        if (!chooseVoice(language)) return Prepared.NoVoice
        tts.setSpeechRate(rate)
        return Prepared.Ready(spoken)
    }

    /** The engine's voices, as plain facts ([VoiceChoice] decides between them). */
    private fun voices(): List<Pair<Voice, VoiceChoice.Candidate>> =
        runCatching { tts.voices?.toList() }.getOrNull().orEmpty().map { v ->
            v to VoiceChoice.Candidate(
                name = v.name,
                locale = v.locale,
                needsNetwork = v.isNetworkConnectionRequired,
                notInstalled = v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true,
                quality = v.quality
            )
        }

    /**
     * Sets a voice for [language]. Cantonese takes only a real Cantonese voice: an engine without
     * one says "Chinese (Hong Kong)" is fine and then reads in Mandarin, so a language-only match
     * doesn't count. Only voices on the tablet are picked by name; online ones never are.
     */
    private fun chooseVoice(language: SpeechLanguage): Boolean {
        val listed = voices()
        VoiceChoice.pick(language, listed.map { it.second })?.let { name ->
            val voice = listed.first { it.second.name == name }.first
            if (runCatching { tts.setVoice(voice) }.getOrDefault(TextToSpeech.ERROR) == TextToSpeech.SUCCESS) return true
        }
        val locales = language.localeTags.map { Locale.forLanguageTag(it) }
        if (language == SpeechLanguage.CANTONESE) {
            // Engines that don't list their voices: the country has to match, not just "Chinese".
            val locale = locales.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_COUNTRY_AVAILABLE } ?: return false
            if (!isUsable(tts.setLanguage(locale))) return false
            val now = runCatching { tts.voice }.getOrNull()
            return now == null || VoiceChoice.isCantonese(now.locale, now.name)
        }
        val locale = locales.firstOrNull { isUsable(tts.isLanguageAvailable(it)) } ?: return false
        return isUsable(tts.setLanguage(locale))
    }

    /** For personas without a Chinese voice: the reply without Markdown, in the tablet's language. */
    private fun prepareNative(text: String, rate: Float): Prepared {
        val spoken = text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("[*_`#>|]+"), "")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .trim()
        if (spoken.isBlank()) return Prepared.NothingToSay
        if (!isUsable(tts.setLanguage(Locale.getDefault()))) return Prepared.NoVoice
        tts.setSpeechRate(maxOf(rate, 1f))
        return Prepared.Ready(spoken)
    }

    fun stop() {
        tts.stop()
        waiting.values.forEach { it.complete(Unit) }
        waiting.clear()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun isUsable(result: Int) =
        result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

    private fun openVoiceInstaller() {
        runCatching {
            appContext.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(appContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** A little slower than normal speech, for learners. */
        const val LEARNER_RATE = 0.85f
        private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
        private val NOT_CHINESE = Regex("[^\\u3000-\\u303F\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\uFF00-\\uFFEF]+")

        /** "简:" / "繁:" line labels used when the tutors write both scripts. */
        private val SCRIPT_LABEL = Regex("^\\s*(?:[简繁簡]|繁/简)\\s*[:：]\\s*")

        private val JAPANESE = Regex("[\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF]")
        private val NOT_JAPANESE = Regex("[^\\u3000-\\u303F\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\uFF00-\\uFFEF]+")
        /** Lines that are notes about the reply, not the language being practised. */
        private val NOTE_LINE = Regex("^\\s*(?:EN|Romaji|Rōmaji|English)\\s*[:：]|^\\s*[✏⭐💡📝]", RegexOption.IGNORE_CASE)
        private val MARKDOWN = Regex("[*_`#>|]+")

        /** What to read aloud from a tutor reply in [language] (see [chineseOnly] and [targetLines]). */
        fun spokenText(text: String, language: SpeechLanguage): String = when {
            language.isChinese -> chineseOnly(text)
            language == SpeechLanguage.JAPANESE -> japaneseOnly(text)
            else -> targetLines(text)
        }

        /** Japanese lines (kana and kanji), minus romaji, English and notes. */
        fun japaneseOnly(text: String): String =
            text.lines()
                .filter { JAPANESE.containsMatchIn(it) && !NOTE_LINE.containsMatchIn(it) }
                .map { it.replace(NOT_JAPANESE, " ").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("。")

        /**
         * For French, German, Spanish and Italian the tutors write each sentence on its own line
         * followed by an "EN:" translation, so the lines just above each "EN:" line are the ones
         * in the language being practised. Explanations (✏️, ⭐, English) are skipped. If the
         * model ignored the format, the whole reply (without Markdown) is read.
         */
        fun targetLines(text: String): String {
            val lines = text.lines()
            val spoken = mutableListOf<String>()
            var block = mutableListOf<String>()
            var sawEnglish = false
            for (raw in lines) {
                val line = raw.replace(MARKDOWN, "").trim()
                when {
                    line.startsWith("EN:", ignoreCase = true) || line.startsWith("EN：") -> {
                        spoken += block
                        block = mutableListOf()
                        sawEnglish = true
                    }
                    line.isEmpty() || NOTE_LINE.containsMatchIn(line) -> block = mutableListOf()
                    else -> block += line
                }
            }
            return if (sawEnglish) spoken.joinToString(" ")
            else lines.map { it.replace(MARKDOWN, "").trim() }
                .filter { it.isNotEmpty() && !NOTE_LINE.containsMatchIn(it) }
                .joinToString(" ")
        }

        /**
         * Keeps the lines that contain Chinese characters, minus any Latin text on them.
         * When a sentence comes as a 简 / 繁 pair, only the first line is read, and in word lists
         * like "时间 / 時間 – shíjiān – time" only the first form is read, so nothing is said twice.
         */
        fun chineseOnly(text: String): String {
            val spoken = mutableListOf<String>()
            var previousWasScriptLine = false
            for (raw in text.lines()) {
                val isScriptLine = SCRIPT_LABEL.containsMatchIn(raw)
                val secondOfPair = isScriptLine && previousWasScriptLine
                previousWasScriptLine = isScriptLine && !secondOfPair
                if (secondOfPair || !HAN.containsMatchIn(raw)) continue

                var line = raw.replace(SCRIPT_LABEL, "")
                val forms = line.split('/', '／')
                if (forms.size >= 2 && HAN.containsMatchIn(forms[0]) && HAN.containsMatchIn(forms[1])) line = forms[0]
                line = line.replace(NOT_CHINESE, " ").trim()
                if (line.isNotEmpty()) spoken += line
            }
            return spoken.joinToString("。")
        }
    }
}

/** (text) -> Unit when the current persona has a voice; null hides the speak button. */
val LocalSpeakAction = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
fun rememberTutorSpeaker(): TutorSpeaker {
    val context = LocalContext.current
    val speaker = remember { TutorSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}

/**
 * Which text-to-speech voice reads a language: a voice that speaks it (for Cantonese, really
 * Cantonese), installed and on the tablet, best quality first.
 */
object VoiceChoice {
    data class Candidate(
        val name: String,
        val locale: Locale?,
        val needsNetwork: Boolean,
        val notInstalled: Boolean,
        val quality: Int
    )

    /** A Cantonese voice: yue, or Chinese for Hong Kong or Macau (Mandarin voices there are rare). */
    fun isCantonese(locale: Locale?, name: String?): Boolean {
        val n = name.orEmpty().lowercase()
        if (n.contains("yue") || n.contains("cantonese")) return true
        if (locale == null) return false
        val lang = locale.language.lowercase()
        if (lang == "yue") return true
        return lang == "zh" && (locale.country.equals("HK", true) || locale.country.equals("MO", true))
    }

    private fun speaks(language: SpeechLanguage, c: Candidate): Boolean {
        if (language == SpeechLanguage.CANTONESE) return isCantonese(c.locale, c.name)
        val locale = c.locale ?: return false
        // Mandarin from a Cantonese voice would be the same mistake the other way round.
        if (language == SpeechLanguage.MANDARIN && isCantonese(locale, c.name)) return false
        return language.localeTags.any { tag -> Locale.forLanguageTag(tag).language == locale.language }
    }

    /** The name of the voice to use, or null to leave it to the engine. */
    fun pick(language: SpeechLanguage, voices: List<Candidate>): String? {
        val wanted = language.localeTags.map { Locale.forLanguageTag(it) }
        return voices
            // A voice that needs the network sends the text off the tablet, so it's never picked.
            .filter { !it.notInstalled && !it.needsNetwork && speaks(language, it) }
            .sortedWith(
                // The listed regions in order (zh-CN before zh-TW), then the rest; best quality first.
                compareBy<Candidate> { c -> wanted.indexOfFirst { it.country.equals(c.locale?.country, true) }.let { if (it < 0) wanted.size else it } }
                    .thenByDescending { it.quality }
            )
            .firstOrNull()?.name
    }
}
