package app.slothlobster.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** A page or file as it came back. */
class Fetched(
    val url: String,
    val contentType: String,
    val bytes: ByteArray
) {
    val mime: String get() = contentType.substringBefore(';').trim().lowercase()
    val charset: String? get() = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)?.trim('"', ' ')
    val isHtml: Boolean get() = mime == "text/html" || mime == "application/xhtml+xml"
    val text: String get() = String(bytes, runCatching { charset(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
}

class WebException(message: String) : IOException(message)

/**
 * The Lobster's only way online: HTTPS, a size cap, and never a device on your own network
 * (so a page or an order can't make it poke at your router or another app on the tablet).
 */
object Web {

    const val USER_AGENT = "SlothLobster/1.0 (personal research app; +https://github.com/smootheggs/SlothLlama)"
    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    const val MAX_BYTES = 12L * 1024 * 1024

    /** Lookups that resolve to a private, local or loopback address are refused. */
    private val safeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val ok = all.filter { isPublic(it) }
            if (ok.isEmpty()) throw UnknownHostException("$hostname is on a private network; the Lobster only goes to public sites")
            return ok
        }
    }

    /** Checks the address actually connected to, which also covers redirects and numeric links. */
    private val publicOnly = Interceptor { chain ->
        val address = chain.connection()?.route()?.socketAddress?.address
        if (address != null && !isPublic(address)) throw WebException("That address is on a private network")
        chain.proceed(chain.request())
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(safeDns)
            .addNetworkInterceptor(publicOnly)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    /**
     * (1.13.1) The same client over HTTP/1.1 only, for a second try when a site's front door
     * resets an app's HTTP/2 stream (FRED's did: "stream was reset: INTERNAL_ERROR").
     */
    private val http1: OkHttpClient by lazy { client.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).build() }

    /**
     * Gets a page or file. [asBrowser] sends an ordinary browser's name, for sites that turn
     * away unknown apps; APIs (Wikipedia) get the Lobster's own name, as they ask.
     */
    suspend fun get(
        url: String,
        asBrowser: Boolean = true,
        maxBytes: Long = MAX_BYTES,
        /** Extra headers, such as a data service's key (OkHttp drops Authorization on a redirect to another host). */
        headers: Map<String, String> = emptyMap()
    ): Fetched = withContext(Dispatchers.IO) {
        val safe = safeUrl(url) ?: throw WebException("Not a web link the Lobster can open: $url")
        val request = Request.Builder()
            .url(safe)
            .header("User-Agent", if (asBrowser) BROWSER_AGENT else USER_AGENT)
            .header("Accept-Language", "en,*;q=0.5")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        try {
            fetch(client, request, maxBytes)
        } catch (e: IOException) {
            if (e.message?.startsWith("stream was reset") != true) throw e
            fetch(http1, request, maxBytes)
        }
    }

    private fun fetch(c: OkHttpClient, request: Request, maxBytes: Long): Fetched =
        c.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            if (!finalUrl.startsWith("https://")) throw WebException("The site sent the Lobster to an unencrypted page")
            if (!response.isSuccessful) throw WebException("The site answered ${response.code}")
            val body = response.body ?: throw WebException("The site sent nothing back")
            if (body.contentLength() > maxBytes) throw WebException("Too big to bring back (over ${maxBytes / 1024 / 1024} MB)")
            val source = body.source()
            val buffer = Buffer()
            while (true) {
                val read = source.read(buffer, 64 * 1024)
                if (read == -1L) break
                if (buffer.size > maxBytes) throw WebException("Too big to bring back (over ${maxBytes / 1024 / 1024} MB)")
            }
            Fetched(finalUrl, response.header("Content-Type").orEmpty(), buffer.readByteArray())
        }

    /**
     * The link as an https:// address, or null if it isn't one the Lobster will open. Plain
     * http:// links are tried over https (the app never sends anything unencrypted).
     */
    fun safeUrl(raw: String): String? {
        var text = raw.trim()
        if (text.startsWith("http://", ignoreCase = true)) text = "https://" + text.substring(7)
        if (!text.contains("://")) text = "https://$text"
        val url = text.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https") return null
        val host = url.host.lowercase()
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return null
        if (!host.contains('.') && !host.contains(':')) return null
        literalAddress(host)?.let { if (!isPublic(it)) return null }
        return url.toString()
    }

    /** A host written as a number (1.2.3.4 or an IPv6 address), parsed without a lookup. */
    private fun literalAddress(host: String): InetAddress? {
        val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        return when {
            ipv4.matches(host) -> runCatching {
                InetAddress.getByAddress(host.split('.').map { it.toInt().toByte() }.toByteArray())
            }.getOrNull()
            host.contains(':') -> runCatching { InetAddress.getByName(host.trim('[', ']')) }.getOrNull()
            else -> null
        }
    }

    fun isPublic(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val b = address.address
        return when (address) {
            is Inet4Address -> {
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                !(first == 0 || first >= 240 || (first == 100 && second in 64..127) || (first == 192 && second == 0 && (b[2].toInt() and 0xFF) == 0))
            }
            is Inet6Address -> {
                val first = b[0].toInt() and 0xFF
                // fc00::/7 is the IPv6 private range.
                (first and 0xFE) != 0xFC
            }
            else -> false
        }
    }

    /** The first web link in some text, if any ("look at example.com/page" counts too). */
    fun firstLink(text: String): String? {
        Regex("""https?://[^\s<>"')\]]+""", RegexOption.IGNORE_CASE).find(text)?.let { return it.value.trimEnd('.', ',', ';', ':') }
        Regex("""\b(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,}(?:/[^\s<>"')\]]*)?""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return it.value.trimEnd('.', ',', ';', ':') }
        return null
    }

    /** "www.bbc.co.uk" → "bbc.co.uk", for showing where a source came from. */
    fun site(url: String): String = url.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: url
}
