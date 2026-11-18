package app.localmind.assistant.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.dictionary.ChineseDictionary
import app.localmind.assistant.data.voice.Endpointing
import app.localmind.assistant.data.voice.VoiceRecorder
import app.localmind.assistant.data.voice.VoiceTranscriber
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.practice.SpeakingCheck
import app.localmind.assistant.domain.practice.SpeakingScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PracticePhase { READY, LISTENING, CHECKING, RESULT }

data class PracticeUiState(
    val target: String = "",
    val language: SpeechLanguage = SpeechLanguage.MANDARIN,
    /** Pinyin or Jyutping for [target]. */
    val reading: String = "",
    val phase: PracticePhase = PracticePhase.READY,
    val heard: String = "",
    val score: SpeakingScore? = null,
    val message: String? = null,
    /** The speech model is imported (Models → Voice input). */
    val canListen: Boolean = false
)

/** Repeat after me: hear a sentence, say it, and see which characters came through. */
@HiltViewModel
class SpeakingPracticeViewModel @Inject constructor(
    private val recorder: VoiceRecorder,
    private val transcriber: VoiceTranscriber,
    private val dictionary: ChineseDictionary
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()
    /** Microphone loudness 0..1 while listening. */
    val level: StateFlow<Float> = recorder.level
    private var job: Job? = null

    fun open(target: String, language: SpeechLanguage) {
        close()
        _state.value = PracticeUiState(target = target, language = language, canListen = transcriber.isReady)
        viewModelScope.launch {
            val reading = runCatching { dictionary.romanize(target, language) }.getOrDefault("")
            _state.update { if (it.target == target) it.copy(reading = reading) else it }
        }
    }

    /** Starts listening; stops by itself when the learner pauses. Needs RECORD_AUDIO. */
    fun listen() {
        val current = _state.value
        if (!current.canListen || current.phase == PracticePhase.LISTENING || current.phase == PracticePhase.CHECKING) return
        job = viewModelScope.launch {
            try {
                _state.update { it.copy(phase = PracticePhase.LISTENING, message = null) }
                val samples = recorder.record(maxSeconds = 20, endpointing = Endpointing(silenceMs = 1_000, noSpeechMs = 6_000))
                if (samples.size < VoiceTranscriber.SAMPLE_RATE / 4) {
                    _state.update { it.copy(phase = PracticePhase.READY, message = "Didn't hear anything. Tap the mic and say it out loud.") }
                    return@launch
                }
                _state.update { it.copy(phase = PracticePhase.CHECKING) }
                val code = if (current.language == SpeechLanguage.CANTONESE) "yue" else "zh"
                val heard = transcriber.transcribe(samples, code)
                val target = current.target
                val chars = SpeakingCheck.hanOnly(target + heard).toSet()
                val simplified = dictionary.toSimplified(chars.joinToString(""))
                val toSimple = chars.zip(simplified.toList()).toMap()
                val readings = dictionary.readingsOf(simplified.toSet() + chars, current.language)
                val score = SpeakingCheck.score(
                    expected = target,
                    heard = heard,
                    normalize = { s -> s.map { toSimple[it] ?: it }.joinToString("") },
                    sameSound = { x, y ->
                        val rx = readings[x].orEmpty()
                        val ry = readings[y].orEmpty()
                        rx.isNotEmpty() && rx.any { it in ry }
                    }
                )
                _state.update { it.copy(phase = PracticePhase.RESULT, heard = heard, score = score) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(phase = PracticePhase.READY, message = "Couldn't listen: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    /** Stop listening now and check what was said so far. */
    fun finishListening() = recorder.stop()

    fun close() {
        recorder.stop()
        job?.cancel()
        job = null
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
