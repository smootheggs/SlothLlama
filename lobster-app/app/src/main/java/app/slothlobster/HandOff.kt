package app.slothlobster

/** The hand-off with SlothLlama (1.11), the parts that need no Android: which app, which links. */
object HandOff {

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
