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
            romanization = prefs.getBoolean(KEY_ROMANIZATION, true),
            charScale = prefs.getFloat(KEY_CHAR_SCALE, 1.2f).coerceIn(1f, 1.6f)
        )
    )
    val style: StateFlow<ChineseStyle> = _style.asStateFlow()

    fun setScript(script: ChineseScript) = save(_style.value.copy(script = script))

    fun setRomanization(enabled: Boolean) = save(_style.value.copy(romanization = enabled))

    fun setCharScale(scale: Float) = save(_style.value.copy(charScale = scale.coerceIn(1f, 1.6f)))

    private fun save(style: ChineseStyle) {
        prefs.edit()
            .putString(KEY_SCRIPT, style.script.name)
            .putBoolean(KEY_ROMANIZATION, style.romanization)
            .putFloat(KEY_CHAR_SCALE, style.charScale)
            .apply()
        _style.value = style
    }

    private companion object {
        const val KEY_SCRIPT = "script"
        const val KEY_ROMANIZATION = "romanization"
        const val KEY_CHAR_SCALE = "char_scale"
    }
}
