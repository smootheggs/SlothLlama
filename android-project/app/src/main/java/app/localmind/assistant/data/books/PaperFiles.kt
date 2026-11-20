package app.localmind.assistant.data.books

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps PDFs and EPUBs as ordinary files on the tablet, in Documents/SlothBooks/Papers and
 * Documents/SlothBooks/Books, where a file manager (or another reader app) finds them too. Written
 * through the system's media store: no storage permission, and nothing leaves the tablet.
 */
@Singleton
class PaperFiles @Inject constructor(@ApplicationContext private val context: Context) {

    /** Saves [bytes] as [name] in Documents/SlothBooks/[folder]; returns the file's address. Blocking. */
    fun save(name: String, bytes: ByteArray, folder: String): Uri {
        require(bytes.isNotEmpty()) { "The file is empty." }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$ROOT/$folder")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Couldn't save $name in Documents/$ROOT.")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Couldn't write $name.")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** Copies a file you picked or shared into Documents/SlothBooks/[folder]. Blocking. */
    fun copyIn(from: Uri, folder: String): Pair<Uri, String> {
        val name = displayName(from)
        val size = runCatching {
            context.contentResolver.query(from, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        }.getOrNull() ?: -1L
        check(size <= MAX_BYTES) { "$name is over ${MAX_BYTES / (1024 * 1024)} MB." }
        val bytes = context.contentResolver.openInputStream(from)?.use { it.readBytes() } ?: error("Couldn't open $name.")
        check(bytes.size <= MAX_BYTES) { "$name is over ${MAX_BYTES / (1024 * 1024)} MB." }
        return save(name, bytes, folder) to name
    }

    /** True when the file at [uri] can still be opened (it may have been deleted or moved). */
    fun exists(uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"

    companion object {
        const val ROOT = "SlothBooks"
        const val PAPERS = "Papers"
        const val BOOKS = "Books"
        private const val MAX_BYTES = 200L * 1024 * 1024

        fun mimeOf(name: String): String = when {
            name.endsWith(".epub", ignoreCase = true) -> "application/epub+zip"
            else -> "application/pdf"
        }

        /** Keeps letters, digits, spaces and . - _ ( ); never a path. */
        fun safeName(name: String): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^\\p{L}\\p{N} ._()\\-]"), "_").trim().trimStart('.')
            return base.ifBlank { "document.pdf" }.take(120)
        }

        /** "organizing-intelligence.pdf" → "organizing intelligence". */
        fun titleFrom(fileName: String): String =
            fileName.substringBeforeLast('.').replace(Regex("[_\\-]+"), " ").trim().ifBlank { fileName }
    }
}
