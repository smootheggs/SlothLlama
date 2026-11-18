package app.localmind.assistant.data.ask

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Text selected in another app and sent with "Ask SlothLlama", waiting for the chat screen. */
@Singleton
class AskInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun post(text: String) {
        _pending.value = text
    }

    /** Hands the text over once. */
    fun take(): String? = _pending.value.also { _pending.value = null }
}
