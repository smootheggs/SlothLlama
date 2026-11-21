package app.localmind.assistant.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.Locale

/**
 * Reads a text aloud, block by block, with Android's own text-to-speech (offline once a voice is
 * installed). It tells the reader which block it's on, so the page can follow along. Chinese and
 * Japanese blocks get their own voice; everything else is read in the tablet's language.
 */
class ReadAloud(
    context: Context,
    private val onBlock: (Int) -> Unit,
    private val onEnd: () -> Unit
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    // The engine picked in Settings → Voice (null: the tablet's default).
    private val tts = TextToSpeech(appContext, this, app.localmind.assistant.ui.speech.SpeechEngine.preferred(appContext))
    @Volatile private var ready = false
    private var texts: List<String> = emptyList()
    /** Each block's language, when the caller knows it (the edition's corners); null = guess from the text. */
    private var locales: List<Locale?> = emptyList()
    private var index = 0
    private var playing = false
    private var startWhenReady = false
    private var warned = false
    var rate: Float = 1f
        private set

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.toIntOrNull()?.let { i -> main.post { onBlock(i) } }
            }
            override fun onDone(utteranceId: String?) {
                main.post { advance(utteranceId) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { advance(utteranceId) }
            }
        })
    }

    override fun onInit(status: Int) {
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                toast("Text-to-speech isn't available on this tablet")
                stopAll()
            } else if (startWhenReady) {
                startWhenReady = false
                speakCurrent()
            }
        }
    }

    /** Starts reading [blocks] at [from]; [languages] gives a block's language where it's known. */
    fun play(blocks: List<String>, from: Int, languages: List<Locale?> = emptyList()) {
        texts = blocks.map(::spoken)
        locales = languages
        index = from.coerceIn(0, maxOf(0, blocks.size - 1))
        playing = true
        if (ready) speakCurrent() else startWhenReady = true
    }

    fun pause() {
        playing = false
        tts.stop()
    }

    fun resume() {
        if (texts.isEmpty()) return
        playing = true
        speakCurrent()
    }

    /** Jumps to [block] (a tap on a paragraph while listening). */
    fun jumpTo(block: Int) {
        if (texts.isEmpty()) return
        index = block.coerceIn(0, texts.size - 1)
        playing = true
        tts.stop()
        speakCurrent()
    }

    fun setRate(value: Float) {
        rate = value
        if (playing) {
            tts.stop()
            speakCurrent()
        }
    }

    fun shutdown() {
        playing = false
        tts.stop()
        tts.shutdown()
    }

    private fun advance(utteranceId: String?) {
        if (!playing || utteranceId != index.toString()) return
        index++
        speakCurrent()
    }

    private fun speakCurrent() {
        if (!playing) return
        while (index < texts.size && texts[index].isBlank()) index++
        if (index >= texts.size) {
            stopAll()
            return
        }
        val text = texts[index]
        val locale = locales.getOrNull(index) ?: localeFor(text)
        val set = tts.setLanguage(locale)
        if (set == TextToSpeech.LANG_MISSING_DATA || set == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (!warned) {
                warned = true
                toast("No ${locale.displayLanguage} voice installed: opening voice settings")
                runCatching { appContext.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // Skip what can't be voiced and carry on with the rest.
            index++
            speakCurrent()
            return
        }
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, index.toString())
    }

    private fun stopAll() {
        playing = false
        onEnd()
    }

    private fun toast(message: String) = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()

    companion object {
        /** Speed choices: a touch slower, normal, brisk, quick. */
        val RATES = listOf(0.85f, 1f, 1.25f, 1.5f)

        private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
        private val KANA = Regex("[\\u3040-\\u30FF]")

        /** A block as it should be heard: no Markdown marks or bare links. */
        fun spoken(text: String): String = text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[*_`#>|]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        /** Japanese if it has kana, Chinese if it's mostly Chinese characters, otherwise the tablet's language. */
        fun localeFor(text: String): Locale {
            val letters = text.count { it.isLetter() }.coerceAtLeast(1)
            return when {
                KANA.containsMatchIn(text) -> Locale.JAPANESE
                HAN.findAll(text).count() * 2 >= letters -> Locale.SIMPLIFIED_CHINESE
                else -> Locale.getDefault()
            }
        }
    }
}
