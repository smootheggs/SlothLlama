package app.localmind.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * "Ask SlothLlama" in the text-selection menu of other apps. It only forwards the selected text to
 * the main screen, where it waits in the message box: nothing is sent until you tap send.
 */
class AskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_ASK)
                    .putExtra(MainActivity.EXTRA_ASK_TEXT, text.take(MAX_CHARS))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        finish()
    }

    private companion object {
        const val MAX_CHARS = 4000
    }
}
