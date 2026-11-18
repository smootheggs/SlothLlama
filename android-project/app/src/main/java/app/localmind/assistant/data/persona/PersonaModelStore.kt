package app.localmind.assistant.data.persona

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which model each persona runs on (persona id → model file name, e.g. "Qwen_Qwen3.5-4B-Q4_K_M.gguf").
 * Personas without an entry use the default model chosen on the Models screen. Kept apart from
 * the persona itself so built-in personas can have a model too without being copied.
 */
@Singleton
class PersonaModelStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("persona_models", Context.MODE_PRIVATE)

    private val _assignments = MutableStateFlow(read())
    val assignments: StateFlow<Map<String, String>> = _assignments.asStateFlow()

    fun fileFor(personaId: String): String? = _assignments.value[personaId]

    /** [fileName] null = use the default model. */
    fun set(personaId: String, fileName: String?) {
        prefs.edit().apply {
            if (fileName == null) remove(personaId) else putString(personaId, fileName)
        }.apply()
        _assignments.value = read()
    }

    private fun read(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
}
