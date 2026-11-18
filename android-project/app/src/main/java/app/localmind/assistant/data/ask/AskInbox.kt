package app.localmind.assistant.data.ask

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something the app was opened to do: from "Ask SlothLlama" or a home-screen shortcut. */
sealed interface LaunchRequest {
    /** Text selected in another app, to be explained in a new chat. */
    data class Ask(val text: String) : LaunchRequest
    /** Start a new chat with this persona. */
    data class StartChat(val personaId: String) : LaunchRequest
    /** Open the flashcards. */
    data object Flashcards : LaunchRequest
}

/** Holds the latest launch request until the main screen picks it up. */
@Singleton
class AskInbox @Inject constructor() {
    private val _pending = MutableStateFlow<LaunchRequest?>(null)
    val pending: StateFlow<LaunchRequest?> = _pending.asStateFlow()

    fun post(request: LaunchRequest) {
        _pending.value = request
    }

    /** Hands the request over once. */
    fun take(): LaunchRequest? = _pending.value.also { _pending.value = null }
}
