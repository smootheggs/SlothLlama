package app.slothlobster

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

/**
 * The Lobster's link to the browser's in-app view (1.12): one connection to its Custom Tabs
 * service, kept while the Lobster runs. Connecting warms the browser up (Chrome's own advice for
 * a faster first load), and the one session lets Reload send the story again to the tab that's
 * already open, which the browser loads in place.
 */
object TabLink {

    private var session: CustomTabsSession? = null
    private var boundTo: String? = null
    private var connection: CustomTabsServiceConnection? = null

    /**
     * Calls [then] with the session for [browser] once there is one, or with null after
     * [timeoutMs] (or when the browser offers none): the tab opens either way, only without the
     * reload-in-place. On the main thread.
     */
    fun whenReady(context: Context, browser: String?, timeoutMs: Long, then: (CustomTabsSession?) -> Unit) {
        if (browser == null) {
            then(null)
            return
        }
        session?.takeIf { boundTo == browser }?.let {
            then(it)
            return
        }
        val main = Handler(Looper.getMainLooper())
        var done = false
        fun finish(s: CustomTabsSession?) {
            if (done) return
            done = true
            then(s)
        }
        main.postDelayed({ finish(null) }, timeoutMs)
        val conn = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                runCatching { client.warmup(0L) }
                val s = runCatching { client.newSession(null) }.getOrNull()
                session = s
                boundTo = browser
                main.post { finish(s) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                session = null
                boundTo = null
                connection = null
            }
        }
        val bound = runCatching { CustomTabsClient.bindCustomTabsService(context.applicationContext, browser, conn) }.getOrDefault(false)
        if (bound) {
            connection?.let { old -> runCatching { context.applicationContext.unbindService(old) } }
            connection = conn
        } else {
            finish(null)
        }
    }

    /** The browser's name for the Log. */
    fun name(pkg: String?): String = when (pkg) {
        null -> "the default browser"
        "com.android.chrome" -> "Chrome"
        "com.chrome.beta" -> "Chrome Beta"
        "com.microsoft.emmx" -> "Edge"
        "org.mozilla.firefox" -> "Firefox"
        "com.sec.android.app.sbrowser" -> "Samsung Internet"
        "com.brave.browser" -> "Brave"
        else -> pkg
    }
}
