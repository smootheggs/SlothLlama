package app.localmind.assistant.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.domain.attachments.Attachments
import app.localmind.assistant.domain.attachments.DocumentText
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The pixels the model sees: [width] x [height], 3 bytes (R, G, B) per pixel. */
class RgbImage(val rgb: ByteArray, val width: Int, val height: Int)

/**
 * Photos and documents attached to messages. Photos are re-encoded (smaller, no EXIF location
 * or camera data) into the app's private storage; documents are read as text. Nothing leaves
 * the tablet.
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LocalMindDao
) {

    private val dir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }
    @Volatile private var pdfReady = false

    /** The stored photo, or null if [name] isn't one of ours (or it was cleaned up). */
    fun photoFile(name: String): File? =
        name.takeIf { Attachments.PHOTO_FILE.matches(it) }?.let { File(dir, it) }?.takeIf { it.isFile }

    /** Copies a picked photo in, upright and at most [MAX_STORED_SIDE] pixels on its long side. */
    suspend fun savePhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = info.size.width to info.size.height
            val scale = MAX_STORED_SIDE.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }
        val name = "photo-${UUID.randomUUID()}.jpg"
        val file = File(dir, name)
        try {
            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) { "Couldn't save the photo" } }
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        name
    }

    /** The photo as RGB pixels for the vision model, at most [maxSide] on its long side. */
    suspend fun rgbFor(name: String, maxSide: Int = MODEL_SIDE): RgbImage? = withContext(Dispatchers.IO) {
        val file = photoFile(name) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return@withContext null
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val rgb = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgb[i * 3] = (p shr 16 and 0xFF).toByte()
            rgb[i * 3 + 1] = (p shr 8 and 0xFF).toByte()
            rgb[i * 3 + 2] = (p and 0xFF).toByte()
        }
        RgbImage(rgb, w, h)
    }

    /**
     * Reads a document as text: PDF (up to [MAX_PDF_PAGES] pages) or any text file (.txt, .md,
     * .csv, subtitles…). Long documents are cut to what fits in the model's memory.
     */
    suspend fun readDocument(uri: Uri): DocumentText = withContext(Dispatchers.IO) {
        val doc = extractText(uri, MAX_PDF_PAGES)
        val fitted = fit(doc.text, MAX_DOCUMENT_TOKENS)
        DocumentText(
            name = doc.name,
            text = fitted,
            pages = doc.pages,
            truncated = fitted.length < doc.text.length || doc.pages > MAX_PDF_PAGES
        )
    }

    /** A document's full text, for the library (and, cut to size, for chat attachments). */
    class Extracted(val name: String, val text: String, val pages: Int)

    /**
     * Reads the whole text of a PDF (up to [maxPdfPages] pages), an EPUB book, an HTML page or
     * any text file (.txt, .md, .csv, subtitles…).
     */
    suspend fun extractText(uri: Uri, maxPdfPages: Int = MAX_LIBRARY_PDF_PAGES): Extracted = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val lower = name.lowercase()
        val isPdf = mime == "application/pdf" || lower.endsWith(".pdf")
        val isEpub = mime == "application/epub+zip" || lower.endsWith(".epub")
        val isHtml = mime == "text/html" || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")
        var pages = 0
        val raw = when {
            isPdf -> {
                if (!pdfReady) {
                    PDFBoxResourceLoader.init(context)
                    pdfReady = true
                }
                val input = context.contentResolver.openInputStream(uri) ?: error("Can't open $name")
                try {
                    input.use { stream ->
                        PDDocument.load(stream).use { doc ->
                            pages = doc.numberOfPages
                            PDFTextStripper().apply {
                                startPage = 1
                                endPage = minOf(pages, maxPdfPages)
                            }.getText(doc)
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "PDF read failed", e)
                    val locked = e.javaClass.simpleName.contains("Password") || e is NoClassDefFoundError
                    error(if (locked) "$name is password-protected, so it can't be read." else "Couldn't read $name as a PDF.")
                }
            }
            isEpub -> context.contentResolver.openInputStream(uri)?.use { EpubText.read(it) } ?: error("Can't open $name")
            else -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(MAX_TEXT_BYTES) }
                    ?: error("Can't open $name")
                if (bytes.take(512).count { it == 0.toByte() } > 0 && !looksUtf16(bytes)) {
                    error("$name doesn't look like a text file. Documents can be PDF, EPUB, HTML or text.")
                }
                val text = decodeText(bytes)
                if (isHtml) EpubText.htmlToText(text) else text
            }
        }
        val text = raw.replace("\r\n", "\n").replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
        if (text.isBlank()) {
            error(if (isPdf) "$name has no text to read (it may be scanned images)." else "$name is empty.")
        }
        Extracted(name, text, pages)
    }

    /** Deletes photos no message mentions any more (chats deleted, photos removed in an edit). */
    suspend fun cleanUp() = withContext(Dispatchers.IO) {
        val files = dir.listFiles().orEmpty()
        if (files.isEmpty()) return@withContext
        val referenced = dao.getAllMessages().flatMapTo(HashSet()) { Attachments.photosIn(it.content) }
        val now = System.currentTimeMillis()
        files.forEach { f ->
            // Recent ones may be attached to a message that hasn't been sent yet.
            if (f.name !in referenced && now - f.lastModified() > GRACE_MS) f.delete()
        }
    }

    private fun fit(text: String, maxTokens: Int): String {
        if (Attachments.estimateTokens(text) <= maxTokens) return text
        var used = 0
        var end = 0
        while (end < text.length) {
            val c = text[end]
            used += if (c in '㐀'..'鿿' || c in '豈'..'﫿') 4 else 1
            if (used > maxTokens * 4) break
            end++
        }
        // End on a line or sentence boundary when there is one nearby.
        val cut = text.lastIndexOfAny(charArrayOf('\n', '。', '.', '!', '?', '！', '？'), end).takeIf { it > end * 0.8 } ?: end
        return text.substring(0, cut + if (cut < end) 1 else 0).trimEnd()
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && looksUtf16(bytes)) {
            val le = bytes[0] == 0xFF.toByte()
            return String(bytes, if (le) Charsets.UTF_16LE else Charsets.UTF_16BE).removePrefix("﻿")
        }
        val utf8 = Charsets.UTF_8.newDecoder()
        return runCatching { utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse {
                // Older Chinese files are often GB18030 (simplified) or Big5 (traditional).
                val gb = String(bytes, Charset.forName("GB18030"))
                val big5 = String(bytes, Charset.forName("Big5"))
                if (gb.count { it == '�' } <= big5.count { it == '�' }) gb else big5
            }
            .removePrefix("﻿")
    }

    private fun looksUtf16(bytes: ByteArray) =
        bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"

    private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "AttachmentStore"
        /** Stored photos: plenty for a later look, small on disk. */
        const val MAX_STORED_SIDE = 1600
        /** What the vision model gets: enough detail for text in photos, quick to encode. */
        const val MODEL_SIDE = 896
        /** Leaves room in a 6k-token context for the chat and the reply. */
        const val MAX_DOCUMENT_TOKENS = 2_600
        const val MAX_PDF_PAGES = 40
        /** The library indexes long documents in full (a 500-page PDF is fine). */
        const val MAX_LIBRARY_PDF_PAGES = 1_000
        private const val MAX_TEXT_BYTES = 16 * 1024 * 1024
        /** A photo just attached but not sent yet isn't cleaned up. */
        private const val GRACE_MS = 24 * 60 * 60 * 1000L
    }
}
