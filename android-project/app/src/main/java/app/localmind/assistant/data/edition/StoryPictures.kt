package app.localmind.assistant.data.edition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The papers' lead pictures (1.43), one per story read clean, kept beside the papers in
 * editions/pics and gone with them. Every picture the Lobster sends is decoded and drawn again
 * here before it's kept, so what's on the tablet is plain pixels in a JPEG this app wrote.
 */
class StoryPictures(private val dir: File) {

    fun file(name: String): File? = name.takeIf(::safe)?.let { File(dir, it) }?.takeIf { it.isFile }

    /** Decodes a kept picture at about [widthPx] wide; null if it's missing or won't decode. Blocking. */
    fun load(name: String, widthPx: Int): Bitmap? {
        val f = file(name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Keeps [bytes] as [name], drawn again at [MAX_SIDE_PX] at most; the name, or null if it isn't a usable picture. Blocking. */
    fun keep(name: String, bytes: ByteArray): String? {
        require(safe(name))
        if (bytes.size > MAX_IN_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w < MIN_SIDE || h < MIN_SIDE || w > MAX_DECODE_SIDE || h > MAX_DECODE_SIDE) return null
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val f = minOf(1f, MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (f < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt().coerceAtLeast(1), (decoded.height * f).toInt().coerceAtLeast(1), true)
        } else decoded
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(out.toByteArray())
        val target = File(dir, name)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) return null
        }
        return name
    }

    /** Drops the pictures of papers that are gone: each name starts with its paper's file name. */
    fun prune(paperBases: Set<String>) {
        dir.listFiles()?.forEach { f ->
            val base = f.name.substringBefore('~')
            if (base !in paperBases) f.delete()
        }
    }

    companion object {
        /** A paper's file name without ".json": its pictures are "<base>~<n>.jpg". */
        fun base(paperFile: String): String = paperFile.removeSuffix(".json")

        fun nameFor(paperFile: String, n: Int): String = "${base(paperFile)}~$n.jpg"

        /** Names this store makes: letters, digits and dashes, a tilde, a number, .jpg. */
        fun safe(name: String): Boolean = Regex("[A-Za-z0-9-]{1,40}~[0-9]{1,3}\\.jpg").matches(name)

        private const val MAX_IN_BYTES = 2 * 1024 * 1024
        private const val MIN_SIDE = 64
        private const val MAX_DECODE_SIDE = 4_000
        const val MAX_SIDE_PX = 960
    }
}
