package app.localmind.assistant.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.persona.PersonaModelStore
import app.localmind.assistant.domain.attachments.Attachments
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.inference.GenerationEvent
import app.localmind.assistant.domain.inference.GenerationRequest
import app.localmind.assistant.domain.inference.LocalInferenceEngine
import app.localmind.assistant.domain.inference.PromptTurn
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.domain.persona.BuiltInPersonas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One question asked beside the text, and its answer so far. */
data class AskTurn(val question: String, val answer: String, val badge: String)

data class AskBesideState(
    /** The text being asked about (a reader key), so a new text starts afresh. */
    val docKey: String? = null,
    val turns: List<AskTurn> = emptyList(),
    /** The Trader answers instead of SlothLlama. */
    val trader: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

/**
 * Ask beside the text (1.54), on a very large screen: questions about the open text, answered
 * by the local model next to it, without leaving the reader. The text goes in as an attached
 * document, marked as reference material that's never to be obeyed (as in a chat), and only the
 * question you type is an instruction. Nothing is kept: "Continue in a chat" takes it to Chats.
 */
@HiltViewModel
class AskBesideViewModel @Inject constructor(
    private val engine: LocalInferenceEngine,
    private val personaModels: PersonaModelStore,
    private val attachments: AttachmentStore,
    private val askInbox: AskInbox
) : ViewModel() {

    private val _state = MutableStateFlow(AskBesideState())
    val state: StateFlow<AskBesideState> = _state.asStateFlow()

    private var title = ""
    private var text = ""
    private var job: Job? = null

    /** The text now open. A different one clears the questions (and stops an answer). */
    fun show(key: String, title: String, text: String) {
        this.title = title
        // The page's folded lines (links, dates, captions) aren't part of what's asked about.
        this.text = Editions.withoutAsides(text)
        if (key != _state.value.docKey) {
            stop()
            _state.update { AskBesideState(docKey = key, trader = it.trader) }
        }
    }

    fun setTrader(on: Boolean) = _state.update { it.copy(trader = on) }

    fun ask(question: String) {
        val q = question.trim().take(MAX_QUESTION)
        val s = _state.value
        if (q.isEmpty() || s.busy || text.isBlank()) return
        val persona = if (s.trader) BuiltInPersonas.TRADER else BuiltInPersonas.GENERAL_ASSISTANT
        // Earlier questions and answers beside this text; the text itself goes with each question.
        val history = s.turns.takeLast(HISTORY_TURNS).filter { it.answer.isNotBlank() }.flatMap {
            listOf(PromptTurn(MessageRole.USER.name, it.question), PromptTurn(MessageRole.ASSISTANT.name, it.answer))
        }
        val request = GenerationRequest(
            conversationId = CONVERSATION,
            modelId = personaModels.fileFor(persona.id).orEmpty(),
            personaSystemInstruction = persona.systemInstruction,
            conversationSummary = null,
            recentMessages = history,
            retrievedPassages = emptyList(),
            userMemories = emptyList(),
            userPrompt = Attachments.compose(q, photo = null, document = attachments.textDocument(title, text)),
            temperature = persona.temperature,
            maxOutputTokens = persona.maxOutputTokens
        )
        _state.update { it.copy(turns = it.turns + AskTurn(q, "", persona.badge), busy = true, error = null) }
        job = viewModelScope.launch {
            try {
                engine.generate(request).collect { event ->
                    when (event) {
                        is GenerationEvent.TokenDelta -> answer { it + event.textDelta }
                        is GenerationEvent.Completed -> answer { event.fullText.ifBlank { it } }
                        is GenerationEvent.Error -> _state.update { it.copy(error = event.userMessage) }
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "The model couldn't answer.") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun answer(change: (String) -> String) = _state.update { s ->
        val last = s.turns.lastOrNull() ?: return@update s
        s.copy(turns = s.turns.dropLast(1) + last.copy(answer = change(last.answer)))
    }

    fun stop() {
        if (job?.isActive == true) {
            viewModelScope.launch { engine.cancelGeneration() }
        }
    }

    /** Opens a chat with the text attached and the last question in the box, to carry on there. */
    fun continueInChat() {
        if (text.isBlank()) return
        val s = _state.value
        askInbox.post(
            LaunchRequest.Shared(
                prompt = s.turns.lastOrNull()?.question.orEmpty(),
                document = attachments.textDocument(title, text),
                personaId = if (s.trader) BuiltInPersonas.TRADER.id else null
            )
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        const val CONVERSATION = "reader-ask-beside"
        const val MAX_QUESTION = 2_000
        /** Earlier questions carried along: enough to follow up, little enough to leave the text room. */
        const val HISTORY_TURNS = 2
    }
}
