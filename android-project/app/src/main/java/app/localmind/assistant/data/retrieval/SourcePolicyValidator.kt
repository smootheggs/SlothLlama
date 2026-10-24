package app.localmind.assistant.data.retrieval

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class UrlPolicyValidationResult(
    val isAllowed: Boolean,
    val domain: String,
    val rejectionReason: String?
)

/**
 * Enforces strict domain allowlisting, HTTPS validation, HTML script/chrome sanitization,
 * SHA-256 verification, and semantic text chunking for the Local Knowledge System.
 */
@Singleton
class SourcePolicyValidator @Inject constructor() {

    private val defaultAllowedDomains = mutableSetOf(
        "en.wikipedia.org",
        "dumps.wikimedia.org",
        "openstax.org",
        "math.libretexts.org",
        "developer.android.com",
        "arxiv.org"
    )

    fun validateUrl(url: String, networkAcquisitionEnabled: Boolean = true): UrlPolicyValidationResult {
        if (!networkAcquisitionEnabled) {
            return UrlPolicyValidationResult(false, "", "All network knowledge acquisition is disabled by user policy.")
        }
        return runCatching {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""
            if (scheme != "https") {
                return UrlPolicyValidationResult(false, host, "Cleartext HTTP or non-HTTPS URLs are strictly forbidden.")
            }
            if (host !in defaultAllowedDomains) {
                return UrlPolicyValidationResult(
                    false,
                    host,
                    "Domain '$host' is not in your approved Source Policy allowlist."
                )
            }
            UrlPolicyValidationResult(true, host, null)
        }.getOrElse {
            UrlPolicyValidationResult(false, "", "Malformed URL syntax.")
        }
    }

    /**
     * Strips scripts, styles, navigation chrome, advertisements, and event attributes while
     * preserving structural headings (h1-h4) and paragraphs.
     */
    fun sanitizeAndExtractHtml(rawHtml: String): String {
        val doc = Jsoup.parse(rawHtml)
        doc.select("script, style, nav, footer, header, aside, iframe, noscript, form, .ad, .advertisement").remove()
        val cleanHtml = Jsoup.clean(doc.body().html(), Safelist.basicWithImages().addTags("h1", "h2", "h3", "h4"))
        return Jsoup.parse(cleanHtml).text().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Splits normalized text into semantic chunks preserving heading context.
     */
    fun splitIntoSemanticChunks(
        title: String,
        rawText: String,
        targetChunkChars: Int = 520,
        overlapChars: Int = 80
    ): List<Pair<String, String>> {
        val paragraphs = rawText.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<Pair<String, String>>()
        var currentHeading = title
        val buffer = StringBuilder()

        for (para in paragraphs) {
            if (para.startsWith("#")) {
                if (buffer.isNotEmpty()) {
                    result.add(currentHeading to buffer.toString().trim())
                    buffer.clear()
                }
                currentHeading = para.trimStart('#', ' ')
            } else {
                if (buffer.length + para.length > targetChunkChars && buffer.isNotEmpty()) {
                    val chunkText = buffer.toString().trim()
                    result.add(currentHeading to chunkText)
                    val tail = chunkText.takeLast(overlapChars)
                    buffer.clear()
                    buffer.append(tail).append(" ")
                }
                buffer.append(para).append("\n\n")
            }
        }
        if (buffer.isNotEmpty()) {
            result.add(currentHeading to buffer.toString().trim())
        }
        return result
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
