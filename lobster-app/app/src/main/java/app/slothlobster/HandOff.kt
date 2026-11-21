package app.slothlobster

import android.content.Context
import android.content.pm.PackageManager

/** The hand-off with SlothLlama (1.11): which app, which links, and whether it's yours. */
object HandOff {

    /** Whether the installed SlothLlama is signed with the same key as this Lobster. */
    fun llamaIsOurs(context: Context): Boolean = runCatching {
        context.packageManager.checkSignatures(context.packageName, llamaPackage(context.packageName)) == PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)

    /** SlothLlama's name next to this Lobster: the dev builds pair with each other. */
    fun llamaPackage(own: String): String =
        if (own.endsWith(".debug")) "app.localmind.assistant.debug" else "app.localmind.assistant"

    /** Whether the Lobster brought this link (in a paper or a brief), as it sent it or once tidied. */
    fun brought(raw: String, url: String, sent: List<String>): Boolean {
        val keys = setOf(key(raw), key(url))
        return sent.any { it in keys }
    }

    /** The same key as the Engine's "already sent" check. */
    private fun key(link: String): String = link.trim().lowercase().take(300)
}
