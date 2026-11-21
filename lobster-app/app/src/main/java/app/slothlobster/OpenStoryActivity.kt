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
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSession
import app.slothlobster.core.Lobster
import app.slothlobster.web.Web

/**
 * SlothLlama's "open the page" (1.11): the story opens in Chrome's in-app view, opened here so
 * SlothLlama never touches the network. Only SlothLlama can ask (the activity needs the
 * signature-level hand-off permission, which only an app signed with the same key holds), and
 * only for a link the Lobster itself brought in a paper or brief; anything else is refused. Each
 * visit is in the Log.
 *
 * The tab has Reload as its toolbar button (1.12: the browser's own Refresh isn't in every
 * browser's menu, and a first load can stall), and two menu items that go back to SlothLlama:
 * Read it clean, and Save for later. Those go only to SlothLlama, by its exact name, and it in
 * turn takes them only from an app holding the same permission. Reload comes back here, and is
 * checked like any open.
 */
class OpenStoryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ask = runCatching { read(intent) }.getOrElse { Result.failure(it) }
        val story = ask.getOrElse {
            Toast.makeText(applicationContext, it.message ?: "Couldn't open that page", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Chrome when it's installed (your sign-ins are there); otherwise the default browser.
        val browser = CustomTabsClient.getPackageName(this, listOf(CHROME), true)
        TabLink.whenReady(this, browser, CONNECT_MS) { session ->
            runCatching { launch(story, browser, session) }
                .onFailure { Toast.makeText(applicationContext, "Couldn't open that page", Toast.LENGTH_SHORT).show() }
            finish()
        }
    }

    private class Story(val url: String, val clean: Boolean, val reload: Boolean)

    /** What to open: a link the Lobster brought, asked for by SlothLlama or by the tab's Reload. */
    private fun read(intent: Intent?): Result<Story> {
        val action = intent?.action
        if (action != ACTION_OPEN && action != ACTION_RELOAD) return Result.failure(IllegalArgumentException("Nothing to open"))
        val raw = intent.getStringExtra(EXTRA_URL).orEmpty().take(MAX_URL)
        val url = Web.safeUrl(raw) ?: return Result.failure(IllegalArgumentException("That isn't a link the Lobster opens"))
        val lobster = Lobster.get(applicationContext)
        if (!HandOff.brought(raw, url, lobster.store.value.sentHeadlines)) {
            return Result.failure(IllegalArgumentException("The Lobster only opens links from its own papers"))
        }
        return Result.success(Story(url, intent.getBooleanExtra(EXTRA_CLEAN, false), action == ACTION_RELOAD))
    }

    /**
     * The tab: Reload in its toolbar (the story again, through the same session, so the browser
     * loads it in the tab that's open), and in its menu Read it clean and Save for later, which go
     * back to SlothLlama.
     */
    private fun launch(story: Story, browser: String?, session: CustomTabsSession?) {
        val llama = HandOff.llamaPackage(packageName)
        val builder = (if (session != null) CustomTabsIntent.Builder(session) else CustomTabsIntent.Builder())
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setActionButton(reloadIcon(), "Reload", reload(story), true)
        // The buttons that go back to SlothLlama, only if it's signed with your key (1.12.1).
        if (HandOff.llamaIsOurs(this)) {
            if (story.clean) builder.addMenuItem("Read it clean in SlothLlama", back(ACTION_READ, story.url, llama))
            builder.addMenuItem("Save for later", back(ACTION_SAVE, story.url, llama))
        }
        val tab = builder.build()
        browser?.let { tab.intent.setPackage(it) }
        tab.launchUrl(this, Uri.parse(story.url))
        val lobster = Lobster.get(applicationContext)
        lobster.store.log(
            "page",
            if (story.reload) "Reloaded a story in ${TabLink.name(browser)}" else "Opened a story in ${TabLink.name(browser)}",
            "${story.url} · for SlothLlama" + if (session == null) " · without a session" else ""
        )
    }

    /** The tab's Reload: back here, to open the same story again. */
    private fun reload(story: Story): PendingIntent {
        val i = Intent(ACTION_RELOAD)
            .setComponent(ComponentName(this, OpenStoryActivity::class.java))
            .putExtra(EXTRA_URL, story.url)
            .putExtra(EXTRA_CLEAN, story.clean)
        return PendingIntent.getActivity(this, (ACTION_RELOAD + story.url).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

    /** A circular arrow, white on clear, for the tab's toolbar (the browser tints it to match). */
    private fun reloadIcon(): Bitmap {
        val px = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val u = px / 24f
        val canvas = Canvas(bmp)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.2f * u; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(RectF(5 * u, 5 * u, 19 * u, 19 * u), -60f, 300f, false, stroke)
        val head = Path().apply { moveTo(15.2f * u, 2.6f * u); lineTo(20.4f * u, 5.6f * u); lineTo(15.4f * u, 8.8f * u); close() }
        canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })
        return bmp
    }

    companion object {
        const val ACTION_OPEN = "app.slothlobster.action.OPEN_STORY"
        const val ACTION_READ = "app.localmind.assistant.action.READ_CLEAN"
        const val ACTION_SAVE = "app.localmind.assistant.action.SAVE_FOR_LATER"
        /** The tab's Reload, from the Lobster to itself. */
        private const val ACTION_RELOAD = "app.slothlobster.action.RELOAD_STORY"
        /** How long the first open waits for the browser's service before opening without it. */
        private const val CONNECT_MS = 700L
        const val EXTRA_URL = "url"
        const val EXTRA_CLEAN = "clean"
        private const val CHROME = "com.android.chrome"
        private const val LLAMA_ACTIVITY = "app.localmind.assistant.FromLobsterActivity"
        private const val MAX_URL = 2_000
    }
}
