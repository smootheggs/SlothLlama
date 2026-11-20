package app.slothlobster

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import app.slothlobster.core.Lobster
import app.slothlobster.mailbox.OrderKind
import app.slothlobster.web.Web

/**
 * "Save for later" in the share sheet: one tap, no screen. The link becomes a Fetch order that
 * SlothLlama files on its Later shelf instead of the catch. Only https links to public sites are
 * taken (the same check every fetch gets), and at most [MAX_PER_HOUR] an hour, so another app
 * can't use it to flood the Lobster.
 */
class SaveForLaterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = runCatching { save(intent) }.getOrElse { "Couldn't save that" }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun save(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) return "Nothing to save"
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
            .joinToString("\n")
        val link = Web.firstLink(text)?.let(Web::safeUrl) ?: return "There's no link to save"
        val lobster = Lobster.get(applicationContext)
        val hourAgo = System.currentTimeMillis() - HOUR_MS
        val recent = lobster.store.value.jobs.count { it.deliver == LATER && it.createdAt > hourAgo }
        if (recent >= MAX_PER_HOUR) return "That's a lot of saves this hour; try again a little later"
        lobster.newOrder(OrderKind.FETCH, link, deep = false, deliver = LATER)
        lobster.store.log("order", "Saved for later", "${Web.site(link)} · shared from ${referrer?.host ?: "another app"}")
        return "Saved for later 🦞"
    }

    companion object {
        const val LATER = "later"
        private const val MAX_PER_HOUR = 30
        private const val HOUR_MS = 60 * 60 * 1000L
    }
}
