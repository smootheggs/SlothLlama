package app.localmind.assistant.ui.speech

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech

/**
 * Which text-to-speech engine reads aloud: the tablet's default ("Automatic") or one you pick,
 * such as Google's or the tablet maker's own. Kept on the tablet with the app's other settings;
 * every speaker (tutors, Core words, the dictionary, flashcards, the Reader) starts with it.
 */
object SpeechEngine {

    /** An engine installed on the tablet: its package and the name Android shows for it. */
    data class Engine(val pkg: String, val label: String)

    const val GOOGLE = "com.google.android.tts"
    private const val PREFS = "speech"
    private const val KEY_ENGINE = "engine"

    /** The engine you picked, or null for Automatic (the tablet's default). */
    fun preferred(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENGINE, null)?.takeIf { it.isNotBlank() }

    fun setPreferred(context: Context, pkg: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (pkg == null) remove(KEY_ENGINE) else putString(KEY_ENGINE, pkg)
        }.apply()
    }

    /** The engines installed, as the system lists them (Google's first). */
    fun installed(context: Context): List<Engine> = runCatching {
        val pm = context.packageManager
        pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { Engine(it.serviceInfo.packageName, it.loadLabel(pm).toString().ifBlank { it.serviceInfo.packageName }) }
            .distinctBy { it.pkg }
            .sortedByDescending { it.pkg == GOOGLE }
    }.getOrDefault(emptyList())

    /** The tablet's own default engine (Settings → Text-to-speech output), if Android says. */
    fun tabletDefault(context: Context): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Opens the tablet's text-to-speech settings (engine, voices, speed). */
    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
