package app.localmind.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.localmind.assistant.data.edition.EditionStore
import app.localmind.assistant.data.later.ReadingList
import app.localmind.assistant.data.lobster.LobsterHandOff
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.domain.edition.Editions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The two taps in the Lobster's Chrome tab that come back here (1.43): "Read it clean in
 * SlothLlama" and "Save for later". No screen of its own. Only the SlothLobster can start it (the
 * manifest asks for its signature-level hand-off permission, held only by apps signed with your
 * key), and only for a story in a paper on the tablet; anything else is ignored.
 */
@AndroidEntryPoint
class FromLobsterActivity : ComponentActivity() {

    @Inject lateinit var editions: EditionStore
    @Inject lateinit var later: ReadingList
    @Inject lateinit var mailbox: LobsterMailbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        val url = intent?.getStringExtra(LobsterHandOff.EXTRA_URL).orEmpty().take(MAX_URL)
        if (!url.startsWith("https://") || (action != LobsterHandOff.ACTION_READ && action != LobsterHandOff.ACTION_SAVE)) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = try {
                if (action == LobsterHandOff.ACTION_READ) read(url) else save(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't do that: ${e.message ?: "something went wrong"}"
            }
            if (message != null) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** Back to the paper with the story open clean (the tab above it closes). */
    private suspend fun read(url: String): String? {
        val found = editions.findNews(url) ?: return "That story isn't in a paper here any more"
        if (!Editions.hasClean(found.second)) return "That one can only be read on its page"
        editions.askOpen(found.second.url)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        return null
    }

    /** On the Later shelf at once when its clean text is here; otherwise the Lobster fetches it. */
    private suspend fun save(url: String): String {
        val story = editions.findNews(url)?.second ?: return "That story isn't in a paper here any more"
        if (Editions.hasClean(story)) {
            withContext(Dispatchers.IO) { later.keepText(story.title, story.url, Editions.cleanText(story)) }
            return "Saved for later 🦥"
        }
        mailbox.send(OrderKind.FETCH, story.url, deliver = "later")
        return "The Lobster will fetch it to Later"
    }

    private companion object {
        const val MAX_URL = 2_000
    }
}
