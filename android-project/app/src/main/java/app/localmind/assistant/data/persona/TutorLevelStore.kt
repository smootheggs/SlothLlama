package app.localmind.assistant.data.persona

import android.content.Context
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.persona.TutorLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The learner's level in each tutor language (Settings → Tutors). Part of backups. */
@Singleton
class TutorLevelStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("tutor_levels", Context.MODE_PRIVATE)

    private val _levels = MutableStateFlow(read())
    val levels: StateFlow<Map<SpeechLanguage, TutorLevel>> = _levels.asStateFlow()

    fun set(language: SpeechLanguage, level: TutorLevel) {
        prefs.edit().putString(language.name, level.name).apply()
        _levels.value = _levels.value + (language to level)
    }

    /** Picks up values a backup restore wrote. */
    fun reload() {
        _levels.value = read()
    }

    private fun read(): Map<SpeechLanguage, TutorLevel> = SpeechLanguage.entries.associateWith { language ->
        prefs.getString(language.name, null)
            ?.let { saved -> TutorLevel.entries.firstOrNull { it.name == saved } }
            ?: TutorLevel.defaultFor(language)
    }
}
