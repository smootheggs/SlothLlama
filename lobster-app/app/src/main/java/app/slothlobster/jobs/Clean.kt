package app.slothlobster.jobs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.slothlobster.web.Web
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream

/**
 * A story read clean with the paper (1.11): the article's own text, as the reader shows it, and
 * its lead picture drawn again as a small JPEG. Nothing from the page is run; the text is the
 * page's words only, and a picture is decoded and re-encoded here, so only plain pixels travel.
 */
object Clean {

    /** Kept per story: enough for a long article, small enough for a paper of forty. */
    const val MAX_PARAS = 120
    const val MAX_CHARS = 24_000
    /** The page's own lines around an article (1.13.3): kept within an allowance of their own. */
    const val MAX_ASIDES = 60
    const val MAX_ASIDE_CHARS = 6_000
    const val MAX_ASIDE_LINE = 1_200
    /** Less than this and the page most likely kept its text back (a paywall, or a script that writes it). */
    const val MIN_CHARS = 600
    const val PIC_WIDTH = 720
    const val PIC_HEIGHT = 720
    const val MAX_PIC_BYTES = 4L * 1024 * 1024

    const val WHY_GOOGLE = "Google News links open only in a browser"
    const val WHY_SHUT = "only a few lines came back: a paywall, or a page that needs its script"
    const val WHY_NOT_PAGE = "it isn't a web page"
    const val WHY_TIME = "the paper ran out of time to read it"

    /** Why a link can't be read clean before trying, or null to try. */
    fun before(url: String): String? = when {
        url.isBlank() -> "there's no link"
        Web.site(url) == "news.google.com" -> WHY_GOOGLE
        else -> null
    }

    /**
     * The reader's lines as the story's paragraphs: headings and lists keep their Markdown marks,
     * the story's own title (already shown) is left out, and lines from the page around the
     * article ([asides], and short scraps such as bylines and "Share") are kept but marked aside,
     * so the reader can fold them away without anything being lost (1.13.3). The article within
     * [MAX_PARAS] and [MAX_CHARS]; the page's lines within [MAX_ASIDES] and [MAX_ASIDE_CHARS] of their own.
     */
    fun paras(lines: List<String>, title: String, asides: Set<Int> = emptySet()): List<EdPara> {
        val out = ArrayList<EdPara>()
        val own = fold(title)
        var chars = 0
        var count = 0
        var asideChars = 0
        var asideCount = 0
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trim()
            val words = line.trimStart('#', '-', '>', ' ').trim()
            if (words.isEmpty()) continue
            val heading = line.startsWith("#")
            if (heading && fold(words) == own) continue
            // Headings and list items may be short; a plain paragraph this short is a scrap.
            val scrap = !heading && !line.startsWith("- ") && words.length < 40 && !words.endsWith('.')
            if (out.lastOrNull()?.t == line) continue
            if (scrap || i in asides) {
                // The page's lines have their own allowance, so they never crowd out the article.
                val room = MAX_ASIDE_CHARS - asideChars
                if (asideCount >= MAX_ASIDES || room <= 0) continue
                val t = line.take(minOf(room, MAX_ASIDE_LINE))
                out += EdPara(t, aside = true)
                asideChars += t.length
                asideCount++
                continue
            }
            val room = MAX_CHARS - chars
            if (room <= 0) break
            out += EdPara(line.take(minOf(room, 4_000)))
            chars += line.length
            if (++count >= MAX_PARAS) break
        }
        return out
    }

    /** Why the text that came back isn't enough to read, or null when it is. */
    fun shut(paras: List<EdPara>): String? =
        if (paras.filterNot { it.t.startsWith("#") || it.aside }.sumOf { it.t.length } < MIN_CHARS) WHY_SHUT else null

    /** The page's own lead picture (its Open Graph or Twitter card image), as an https link. */
    fun leadPicture(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val raw = listOf("meta[property=og:image]", "meta[property=og:image:url]", "meta[name=twitter:image]", "meta[name=twitter:image:src]")
            .firstNotNullOfOrNull { sel -> doc.selectFirst(sel)?.absUrl("content")?.takeIf { it.isNotBlank() } }
            ?: return null
        // Over https, and never a private address (Web.safeUrl).
        return Web.safeUrl(raw)
    }

    /**
     * A picture drawn again at [PIC_WIDTH]×[PIC_HEIGHT] at most, as a JPEG; null for anything that
     * isn't a real picture (a logo or tracking pixel is too small, a giant one is refused).
     */
    fun redraw(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w < 240 || h < 135 || w > 10_000 || h > 10_000) return null
        var sample = 1
        while (w / (sample * 2) >= PIC_WIDTH && h / (sample * 2) >= PIC_HEIGHT / 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val f = minOf(1f, PIC_WIDTH.toFloat() / decoded.width, PIC_HEIGHT.toFloat() / decoded.height)
        val scaled = if (f < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt().coerceAtLeast(1), (decoded.height * f).toInt().coerceAtLeast(1), true)
        } else decoded
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
        return out.toByteArray()
    }

    private fun fold(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
