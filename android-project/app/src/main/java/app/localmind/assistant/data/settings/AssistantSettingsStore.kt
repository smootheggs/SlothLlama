package app.localmind.assistant.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the assistant generates replies; chosen in Settings → Assistant. */
@Singleton
class AssistantSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    private val _thinking = MutableStateFlow(prefs.getBoolean(KEY_THINKING, false))

    /**
     * Reasoning models (Qwen3 / Qwen3.5, DeepSeek-R1 distills) think before answering when this is on.
     * Off by default: on a tablet, thinking adds a long silent pause to every reply.
     */
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    fun setThinking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THINKING, enabled).apply()
        _thinking.value = enabled
    }

    private companion object {
        const val KEY_THINKING = "thinking"
    }
}
