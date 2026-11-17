package app.localmind.assistant.data.inference

import app.localmind.assistant.domain.inference.EngineRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One shared status for whichever runtime (llama.cpp or MediaPipe) currently holds the model. */
@Singleton
class EngineStatus @Inject constructor() {
    private val _state = MutableStateFlow<EngineRuntimeState>(EngineRuntimeState.Idle)
    val state: StateFlow<EngineRuntimeState> = _state.asStateFlow()

    fun set(value: EngineRuntimeState) {
        _state.value = value
    }
}
