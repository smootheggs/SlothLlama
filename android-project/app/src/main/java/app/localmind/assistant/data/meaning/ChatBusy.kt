package app.localmind.assistant.data.meaning

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Whether a reply is being written, so background reading for meaning can wait its turn. */
object ChatBusy {
    private val count = AtomicInteger(0)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun begin() {
        _busy.value = count.incrementAndGet() > 0
    }

    fun end() {
        _busy.value = count.updateAndGet { maxOf(0, it - 1) } > 0
    }
}
