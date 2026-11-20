package app.localmind.assistant.data.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.SAXParserFactory

/**
 * Book covers, kept as small JPEGs in the app's private storage (books/covers). Every image is
 * decoded and drawn again here before it's kept, whatever it came from (a PDF's first page, an
 * EPUB's own cover, a photo, the Lobster), so only plain pixels are ever stored or shown. Covers
 * aren't in backups: the Lobster can fetch them again.
 */
@Singleton
class CoverStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir = File(File(context.filesDir, "books"), "covers").apply { mkdirs() }

    fun file(name: String): File? = name.takeIf { safe(it) }?.let { File(dir, it) }?.takeIf { it.isFile }

    /** Decodes a kept cover at about [widthPx] wide; null if it's missing or won't decode. */
    fun load(name: String, widthPx: Int): Bitmap? {
        val f = file(name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Keeps [bytes] (a JPEG, PNG or WebP) as [name]; returns the name, or null if it isn't a usable picture. Blocking. */
    fun keep(name: String, bytes: ByteArray, crop: Boolean = false): String? {
        require(safe(name))
        if (bytes.size > MAX_IN_BYTES || !looksLikeImage(bytes)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w < MIN_SIDE || h < MIN_SIDE || w > MAX_SIDE || h > MAX_SIDE) return null
        var sample = 1
        while (h / (sample * 2) >= HEIGHT_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return write(name, fit(decoded, crop))
    }

    /** A photo you picked (of your own copy): cropped to a book's shape. Blocking. */
    fun keepPhoto(name: String, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { read(it, MAX_PHOTO_BYTES) } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= HEIGHT_PX * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return write(name, fit(decoded, crop = true))
    }

    /** The first page of a PDF on the tablet. Blocking. */
    fun fromPdf(name: String, uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return@use null
                r.openPage(0).use { p ->
                    val w = WIDTH_PX
                    val h = (w * p.height.toFloat() / p.width.coerceAtLeast(1)).toInt().coerceIn(1, HEIGHT_PX * 2)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    write(name, fit(bmp, crop = true))
                }
            }
        }
    }.getOrNull()

    /** An EPUB's own cover picture (as its package names it). Blocking. */
    fun fromEpub(name: String, uri: Uri): String? = runCatching {
        // First pass: the package file, which says which picture is the cover.
        var opfPath: String? = null
        val small = HashMap<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name == "META-INF/container.xml" || e.name.endsWith(".opf", ignoreCase = true)) small[e.name] = read(zip, MAX_XML_BYTES)
                }
            }
        }
        opfPath = small["META-INF/container.xml"]?.let { xml ->
            var path: String? = null
            parse(xml, object : DefaultHandler() {
                override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                    if (q.endsWith("rootfile") && path == null) path = a.getValue("full-path")
                }
            })
            path
        } ?: small.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
        val opf = opfPath?.let { small[it] } ?: return@runCatching null
        val base = opfPath!!.substringBeforeLast('/', "")
        var coverId: String? = null
        var coverHref: String? = null
        val items = HashMap<String, String>()
        val images = mutableListOf<String>()
        parse(opf, object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                val tag = q.substringAfter(':')
                if (tag == "meta" && a.getValue("name") == "cover") coverId = a.getValue("content")
                if (tag == "item") {
                    val id = a.getValue("id") ?: return
                    val href = a.getValue("href") ?: return
                    items[id] = href
                    if (a.getValue("properties")?.contains("cover-image") == true) coverHref = href
                    if (a.getValue("media-type")?.startsWith("image/") == true) images += href
                }
            }
        })
        val href = coverHref ?: coverId?.let { items[it] } ?: images.firstOrNull { "cover" in it.lowercase() } ?: return@runCatching null
        val target = (if (base.isEmpty()) href else "$base/$href").replace("%20", " ")
        // Second pass: just that picture.
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var found: ByteArray? = null
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name == target) { found = read(zip, MAX_IN_BYTES.toLong()); break }
                }
                found
            }
        } ?: return@runCatching null
        keep(name, bytes)
    }.getOrNull()

    fun delete(name: String) {
        if (safe(name)) File(dir, name).delete()
    }

    /** Drops covers no book points at any more. */
    fun prune(keepNames: Set<String>) {
        dir.listFiles()?.forEach { f -> if (f.name !in keepNames) f.delete() }
    }

    private fun write(name: String, bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(out.toByteArray())
        val f = File(dir, name)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        return name
    }

    /** Scales to [HEIGHT_PX] tall; with [crop], trims to a book's 2:3 shape first. */
    private fun fit(src: Bitmap, crop: Boolean): Bitmap {
        var b = src
        if (crop) {
            val want = 2f / 3f
            val have = b.width.toFloat() / b.height
            b = if (have > want) {
                val w = (b.height * want).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(b, (b.width - w) / 2, 0, w, b.height)
            } else {
                val h = (b.width / want).toInt().coerceAtLeast(1).coerceAtMost(b.height)
                Bitmap.createBitmap(b, 0, 0, b.width, h)
            }
        }
        if (b.height > HEIGHT_PX) {
            val w = (b.width * HEIGHT_PX.toFloat() / b.height).toInt().coerceAtLeast(1)
            b = Bitmap.createScaledBitmap(b, w, HEIGHT_PX, true)
        }
        return b
    }

    private fun read(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            check(total <= limit) { "Too large" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        f.newSAXParser().parse(xml.inputStream(), handler)
    }

    companion object {
        const val WIDTH_PX = 300
        const val HEIGHT_PX = 450
        private const val MIN_SIDE = 40
        private const val MAX_SIDE = 6000
        private const val MAX_IN_BYTES = 6 * 1024 * 1024
        private const val MAX_PHOTO_BYTES = 30L * 1024 * 1024
        private const val MAX_XML_BYTES = 2L * 1024 * 1024

        /** A cover's file name: letters, digits and . - only, never a path. */
        fun safe(name: String): Boolean = name.isNotEmpty() && name.length < 120 && name.all { it.isLetterOrDigit() || it == '.' || it == '-' } && !name.startsWith(".")

        fun nameFor(bookId: String, choice: Int = 0): String {
            val id = bookId.filter { it.isLetterOrDigit() || it == '-' }.take(64)
            return if (choice == 0) "$id.jpg" else "$id.c$choice.jpg"
        }

        /** JPEG, PNG or WebP, by their first bytes. */
        fun looksLikeImage(b: ByteArray): Boolean = b.size > 12 && (
            (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) ||
                (b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()) ||
                (b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
                    b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() && b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte())
            )
    }
}
