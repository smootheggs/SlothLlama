package app.localmind.assistant.data.ask

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.localmind.assistant.MainActivity
import app.localmind.assistant.R
import app.localmind.assistant.domain.persona.BuiltInPersonas

/**
 * Long-press the app icon: jump straight to a tutor or the flashcards. Published at runtime
 * (not in XML) so they work for both the release and the ".debug" app.
 */
object AppShortcuts {
    const val TARGET_FLASHCARDS = "flashcards"
    const val TARGET_PERSONA_PREFIX = "persona:"

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "mandarin", "Mandarin tutor", "Practise Mandarin", TARGET_PERSONA_PREFIX + BuiltInPersonas.MANDARIN_PARTNER.id),
            shortcut(context, "cantonese", "Cantonese tutor", "Practise Cantonese", TARGET_PERSONA_PREFIX + BuiltInPersonas.CANTONESE_PARTNER.id),
            shortcut(context, "flashcards", "Flashcards", "Review flashcards", TARGET_FLASHCARDS)
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, short: String, long: String, target: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_SHORTCUT)
                    .putExtra(MainActivity.EXTRA_TARGET, target)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            .build()
}
