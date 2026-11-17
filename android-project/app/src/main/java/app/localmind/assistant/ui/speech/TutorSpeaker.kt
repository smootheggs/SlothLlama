package app.localmind.assistant.ui.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.localmind.assistant.domain.persona.SpeechLanguage
import java.util.Locale

/**
 * Reads tutor replies aloud with Android's own text-to-speech engine (offline once the
 * voice is installed). Only the Chinese characters are spoken, not pinyin, Jyutping or
 * the English lines.
 */
class TutorSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, language: SpeechLanguage) {
        if (!ready) {
            toast("Text-to-speech is still starting — try again in a second")
            return
        }
        val spoken = chineseOnly(text)
        if (spoken.isBlank()) {
            toast("Nothing to read aloud in this reply")
            return
        }
        val locale = language.localeTags
            .map { Locale.forLanguageTag(it) }
            .firstOrNull { isUsable(tts.isLanguageAvailable(it)) }
        if (locale == null || !isUsable(tts.setLanguage(locale))) {
            toast("No ${language.label} voice installed. Opening voice settings…")
            openVoiceInstaller()
            return
        }
        tts.setSpeechRate(0.85f) // a little slower for learners
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "tutor-reply")
    }

    fun stop() {
        tts.stop()
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

    private fun toast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
        private val NOT_CHINESE = Regex("[^\\u3000-\\u303F\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\uFF00-\\uFFEF]+")

        /** "简:" / "繁:" line labels used when the tutors write both scripts. */
        private val SCRIPT_LABEL = Regex("^\\s*[简繁簡]\\s*[:：]\\s*")

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
