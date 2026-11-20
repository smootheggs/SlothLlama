package app.slothlobster.jobs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.slothlobster.web.Web
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Base64

/*
 * Book covers for SlothBooks. SlothLlama sends lines of "id <tab> title <tab> author"; the
 * Lobster looks each book up by title and author (Open Library first, Google Books after; both
 * public and free, no key) and sends back small JPEGs, re-drawn here at 300×450 at most. Nothing
 * about you goes out but the titles and authors searched.
 */

/** One book asked for: SlothLlama's short id, the title and author to search. */
data class CoverAsk(val id: String, val title: String, val author: String)

@Serializable
data class CoverFound(val id: String, val source: String, val images: List<String>)

/** What a "covers" catch carries (its file, slothbooks-covers.json). */
@Serializable
data class CoverBatch(
    val format: String = "slothbooks-covers",
    val v: Int = 1,
    /** "set": use the first cover; "pick": candidates to choose from. */
    val mode: String = "set",
    val covers: List<CoverFound> = emptyList(),
    val missing: List<String> = emptyList()
)

object Covers {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val MAX_BOOKS = 120
    private const val MAX_IMAGE_BYTES = 3L * 1024 * 1024
    private const val WIDTH = 300
    private const val HEIGHT = 450

    /** "choices: 3" in the order's head asks for candidates; the rest are "id\ttitle\tauthor" lines. */
    fun parse(text: String): Pair<Int, List<CoverAsk>> {
        val choices = Regex("""choices:\s*(\d)""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
        val asks = text.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0].trim().filter { it.isLetterOrDigit() || it == '-' }.take(40)
            val title = parts[1].trim().take(120)
            if (id.isEmpty() || title.isEmpty()) null else CoverAsk(id, title, parts.getOrNull(2)?.trim()?.take(80).orEmpty())
        }.take(MAX_BOOKS)
        return choices to asks
    }

    fun encode(batch: CoverBatch): String = json.encodeToString(CoverBatch.serializer(), batch)

    /** Up to [choices] covers for one book, each as a base64 JPEG; the source's name with them. */
    suspend fun find(ask: CoverAsk, choices: Int): CoverFound? {
        val urls = LinkedHashMap<String, String>() // image url → source
        runCatching { openLibrary(ask, choices) }.getOrNull()?.forEach { urls.putIfAbsent(it, "Open Library") }
        if (urls.size < choices) runCatching { googleBooks(ask, choices) }.getOrNull()?.forEach { if (urls.size < choices) urls.putIfAbsent(it, "Google Books") }
        val images = mutableListOf<String>()
        var source = ""
        for ((url, from) in urls) {
            if (images.size >= choices) break
            val jpeg = runCatching { image(url) }.getOrNull() ?: continue
            images += Base64.getEncoder().encodeToString(jpeg)
            if (source.isEmpty()) source = from
        }
        return if (images.isEmpty()) null else CoverFound(ask.id, source, images)
    }

    private suspend fun openLibrary(ask: CoverAsk, choices: Int): List<String> {
        val q = buildString {
            append("https://openlibrary.org/search.json?title=").append(enc(ask.title))
            if (ask.author.isNotBlank()) append("&author=").append(enc(firstAuthor(ask.author)))
            append("&limit=8&fields=title,author_name,cover_i")
        }
        val docs = Json.parseToJsonElement(Web.get(q, asBrowser = false, maxBytes = 2L * 1024 * 1024).text)
            .jsonObject["docs"]?.jsonArray ?: return emptyList()
        val want = norm(ask.title)
        return docs.mapNotNull { it as? JsonObject }
            .mapNotNull { d ->
                val cover = d["cover_i"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val title = d["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                // Exact titles first, then ones that start the same way; others only to fill the choices.
                val rank = when {
                    norm(title) == want -> 0
                    norm(title).startsWith(want) || want.startsWith(norm(title)) -> 1
                    else -> 2
                }
                rank to "https://covers.openlibrary.org/b/id/$cover-L.jpg"
            }
            .filter { it.first < 2 || choices > 1 }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
            .take(choices)
    }

    private suspend fun googleBooks(ask: CoverAsk, choices: Int): List<String> {
        val q = "intitle:\"${ask.title}\"" + if (ask.author.isNotBlank()) " inauthor:\"${firstAuthor(ask.author)}\"" else ""
        val url = "https://www.googleapis.com/books/v1/volumes?q=${enc(q)}&maxResults=5&printType=books&fields=items(volumeInfo(title,imageLinks))"
        val items = Json.parseToJsonElement(Web.get(url, asBrowser = false, maxBytes = 2L * 1024 * 1024).text)
            .jsonObject["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.get("volumeInfo") as? JsonObject }
            .mapNotNull { v ->
                val links = v["imageLinks"] as? JsonObject ?: return@mapNotNull null
                (links["thumbnail"] ?: links["smallThumbnail"])?.jsonPrimitive?.contentOrNull
                    ?.replace("http://", "https://")?.replace("&edge=curl", "")
            }
            .distinct()
            .take(choices)
    }

    /** Fetches a picture and draws it again as a small JPEG; null if it isn't a real cover (a placeholder, too small). */
    private suspend fun image(url: String): ByteArray? {
        val page = Web.get(url, asBrowser = false, maxBytes = MAX_IMAGE_BYTES)
        val bytes = page.bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        // Open Library answers a missing cover with a 1×1 picture; anything this small isn't a cover.
        if (w < 40 || h < 60 || w > 6000 || h > 6000) return null
        var sample = 1
        while (h / (sample * 2) >= HEIGHT) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = if (decoded.height > HEIGHT || decoded.width > WIDTH) {
            val f = minOf(HEIGHT.toFloat() / decoded.height, WIDTH.toFloat() / decoded.width)
            Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt().coerceAtLeast(1), (decoded.height * f).toInt().coerceAtLeast(1), true)
        } else decoded
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return out.toByteArray()
    }

    private fun firstAuthor(a: String) = a.split(",", " and ", " & ").first().trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("^(the|a|an) "), "").replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
