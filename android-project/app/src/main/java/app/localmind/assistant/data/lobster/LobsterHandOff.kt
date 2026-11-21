package app.localmind.assistant.data.lobster

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * "Open the page" (1.43): SlothLlama hands a story's link to the SlothLobster, which opens it in
 * Chrome's in-app view. SlothLlama itself still never touches the network: it only names the
 * Lobster's screen, by its exact name, and Android lets it in only because both apps are signed
 * with the same key (the Lobster's signature-level hand-off permission). The Lobster opens only
 * links it brought in its own papers.
 */
object LobsterHandOff {

    const val ACTION_OPEN = "app.slothlobster.action.OPEN_STORY"
    /** The two taps that come back from the Lobster's tab. */
    const val ACTION_READ = "app.localmind.assistant.action.READ_CLEAN"
    const val ACTION_SAVE = "app.localmind.assistant.action.SAVE_FOR_LATER"
    const val EXTRA_URL = "url"
    const val EXTRA_CLEAN = "clean"
    private const val OPEN_ACTIVITY = "app.slothlobster.OpenStoryActivity"

    /** The Lobster beside this SlothLlama: the dev builds pair with each other. */
    fun lobsterPackage(own: String): String = if (own.endsWith(".debug")) "app.slothlobster.debug" else "app.slothlobster"

    fun permission(own: String): String = lobsterPackage(own) + ".permission.HAND_OFF"

    /**
     * Asks the Lobster to open [url]; [clean] says SlothLlama has the story's clean text, so the
     * tab offers "Read it clean". Null when it went; otherwise why it couldn't.
     */
    fun open(context: Context, url: String, clean: Boolean): String? {
        if (!url.startsWith("https://")) return "This story has no link to open"
        val own = context.packageName
        if (context.checkSelfPermission(permission(own)) != PackageManager.PERMISSION_GRANTED) {
            return if (installed(context, lobsterPackage(own))) {
                "Android hasn't paired SlothLlama with the Lobster yet: update SlothLlama once more now that the Lobster 1.11 is in"
            } else {
                "Opening pages needs the SlothLobster 1.11 or newer"
            }
        }
        val intent = Intent(ACTION_OPEN)
            .setComponent(ComponentName(lobsterPackage(own), OPEN_ACTIVITY))
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_CLEAN, clean)
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "Opening pages needs the SlothLobster 1.11 or newer"
        } catch (e: SecurityException) {
            "The Lobster wouldn't take it: both apps need to be signed with your key"
        }
    }

    private fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
}
