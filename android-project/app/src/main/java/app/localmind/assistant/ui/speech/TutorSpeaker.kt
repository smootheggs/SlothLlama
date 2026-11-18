package app.localmind.assistant.ui.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
                toast("No ${language.label} voice installed. Opening voice settings…")
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
        val spoken = chineseOnly(text)
        if (spoken.isBlank()) return Prepared.NothingToSay
        val locale = language.localeTags
            .map { Locale.forLanguageTag(it) }
            .firstOrNull { isUsable(tts.isLanguageAvailable(it)) }
        if (locale == null || !isUsable(tts.setLanguage(locale))) return Prepared.NoVoice
        tts.setSpeechRate(rate)
        return Prepared.Ready(spoken)
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

    private fun toast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** A little slower than normal speech, for learners. */
        const val LEARNER_RATE = 0.85f
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
