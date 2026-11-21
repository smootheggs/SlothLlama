package app.slothlobster

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import app.slothlobster.core.Lobster
import app.slothlobster.web.Web

/**
 * SlothLlama's "open the page" (1.11): the story opens in Chrome's in-app view, opened here so
 * SlothLlama never touches the network. Only SlothLlama can ask (the activity needs the
 * signature-level hand-off permission, which only an app signed with the same key holds), and
 * only for a link the Lobster itself brought in a paper or brief; anything else is refused. Each
 * visit is in the Log.
 *
 * The tab has Chrome's own menu (Refresh, Find in page, Open in Chrome), plus Save for later as
 * its toolbar button and two menu items that go back to SlothLlama: Read it clean, and Save for
 * later. Those go only to SlothLlama, by its exact name, and it in turn takes them only from an
 * app holding the same permission.
 */
class OpenStoryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val problem = runCatching { openTab(intent) }.getOrElse { "Couldn't open that page" }
        if (problem != null) Toast.makeText(applicationContext, problem, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Opens the tab; a reason when it won't. */
    private fun openTab(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN) return "Nothing to open"
        val raw = intent.getStringExtra(EXTRA_URL).orEmpty().take(MAX_URL)
        val url = Web.safeUrl(raw) ?: return "That isn't a link the Lobster opens"
        val lobster = Lobster.get(applicationContext)
        if (!HandOff.brought(raw, url, lobster.store.value.sentHeadlines)) return "The Lobster only opens links from its own papers"
        val clean = intent.getBooleanExtra(EXTRA_CLEAN, false)
        val llama = HandOff.llamaPackage(packageName)

        val save = back(ACTION_SAVE, url, llama)
        val builder = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setActionButton(bookmark(), "Save for later", save, true)
        if (clean) builder.addMenuItem("Read it clean in SlothLlama", back(ACTION_READ, url, llama))
        builder.addMenuItem("Save for later", save)
        val tab = builder.build()
        // Chrome when it's installed (your sign-ins are there); otherwise the default browser.
        CustomTabsClient.getPackageName(this, listOf(CHROME), true)?.let { tab.intent.setPackage(it) }
        tab.launchUrl(this, Uri.parse(url))
        lobster.store.log("page", "Opened a story in Chrome", "$url · for SlothLlama")
        return null
    }

    /** A tap in the tab that goes back to SlothLlama, by its exact name, with the story's link. */
    private fun back(action: String, url: String, llama: String): PendingIntent {
        val i = Intent(action)
            .setComponent(ComponentName(llama, LLAMA_ACTIVITY))
            .putExtra(EXTRA_URL, url)
        // One per action and link, so an older tab's buttons keep their own story.
        val code = (action + url).hashCode()
        return PendingIntent.getActivity(this, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A bookmark, white on clear, for the tab's toolbar (Chrome tints it to match). */
    private fun bookmark(): Bitmap {
        val px = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val u = px / 24f
        val path = Path().apply {
            moveTo(6 * u, 3 * u); lineTo(18 * u, 3 * u); lineTo(18 * u, 21 * u)
            lineTo(12 * u, 17 * u); lineTo(6 * u, 21 * u); close()
        }
        Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })
        return bmp
    }

    companion object {
        const val ACTION_OPEN = "app.slothlobster.action.OPEN_STORY"
        const val ACTION_READ = "app.localmind.assistant.action.READ_CLEAN"
        const val ACTION_SAVE = "app.localmind.assistant.action.SAVE_FOR_LATER"
        const val EXTRA_URL = "url"
        const val EXTRA_CLEAN = "clean"
        private const val CHROME = "com.android.chrome"
        private const val LLAMA_ACTIVITY = "app.localmind.assistant.FromLobsterActivity"
        private const val MAX_URL = 2_000
    }
}
