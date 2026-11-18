package app.localmind.assistant.data.meaning

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Vector helpers for search by meaning: storage, similarity and merging with word search. */
object Vectors {

    /** Float32, little-endian. */
    fun toBytes(v: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(v)
        return buf.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val out = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }

    /** Cosine similarity of two unit-length vectors (their dot product). */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var s = 0f
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    /** Similarity of [query] with row [row] of [matrix] (rows of [dims] values). */
    fun similarity(query: FloatArray, matrix: FloatArray, row: Int, dims: Int): Float {
        val base = row * dims
        var s = 0f
        for (i in 0 until minOf(dims, query.size)) s += query[i] * matrix[base + i]
        return s
    }

    /**
     * Merges the word search's ranking with the meaning search's (reciprocal rank fusion): a
     * passage high in both comes first. Passages only the meaning search found must reach
     * [floor] similarity, so a question the library can't answer still finds nothing.
     */
    fun fuse(byWords: List<Long>, byMeaning: List<Pair<Long, Float>>, floor: Float, k: Int = 60): List<Long> {
        val score = LinkedHashMap<Long, Double>()
        byWords.forEachIndexed { i, id -> score[id] = (score[id] ?: 0.0) + 1.0 / (k + i + 1) }
        val inWords = byWords.toHashSet()
        byMeaning.forEachIndexed { i, (id, sim) ->
            if (id in inWords || sim >= floor) score[id] = (score[id] ?: 0.0) + 1.0 / (k + i + 1)
        }
        return score.entries.sortedByDescending { it.value }.map { it.key }
    }
}
