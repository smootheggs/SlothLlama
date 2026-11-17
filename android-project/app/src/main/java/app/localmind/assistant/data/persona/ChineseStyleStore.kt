package app.localmind.assistant.data.persona

import android.content.Context
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.ChineseStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers how the Chinese tutors should write (characters and romanization). */
@Singleton
class ChineseStyleStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("chinese_style", Context.MODE_PRIVATE)

    private val _style = MutableStateFlow(
        ChineseStyle(
            script = ChineseScript.entries.firstOrNull { it.name == prefs.getString(KEY_SCRIPT, null) }
                ?: ChineseScript.BOTH,
            romanization = prefs.getBoolean(KEY_ROMANIZATION, true)
        )
    )
    val style: StateFlow<ChineseStyle> = _style.asStateFlow()

    fun setScript(script: ChineseScript) = save(_style.value.copy(script = script))

    fun setRomanization(enabled: Boolean) = save(_style.value.copy(romanization = enabled))

    private fun save(style: ChineseStyle) {
        prefs.edit()
            .putString(KEY_SCRIPT, style.script.name)
            .putBoolean(KEY_ROMANIZATION, style.romanization)
            .apply()
        _style.value = style
    }

    private companion object {
        const val KEY_SCRIPT = "script"
        const val KEY_ROMANIZATION = "romanization"
    }
}
