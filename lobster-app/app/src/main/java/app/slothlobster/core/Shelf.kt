package app.slothlobster.core

import android.util.Log
import app.slothlobster.mailbox.LobsterCatch
import app.slothlobster.mailbox.LobsterSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A catch as kept on this device, to read in full in the Lobster (attached files aren't kept). */
@Serializable
data class KeptCatch(
    val id: String,
    val title: String,
    val kind: String,
    val text: String,
    val sources: List<LobsterSource> = emptyList(),
    val createdAt: Long = 0,
    /** The name of a fetched file that went to SlothLlama with it. */
    val fileName: String? = null
)

/**
 * The full text of everything the Lobster brought back, in its private storage, so it can be
 * read on the phone without SlothLlama. Only the last hundred are kept, like the Deck's list.
 */
class Shelf(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun keep(catch: LobsterCatch) {
        runCatching {
            dir.mkdirs()
            val kept = KeptCatch(
                id = catch.id,
                title = catch.title,
                kind = catch.kind,
                text = catch.text.take(MAX_CHARS),
                sources = catch.sources,
                createdAt = catch.createdAt,
                fileName = catch.fileName
            )
            file(catch.id).writeText(json.encodeToString(KeptCatch.serializer(), kept))
        }.onFailure { Log.w(TAG, "Couldn't keep a catch", it) }
    }

    fun load(id: String): KeptCatch? = runCatching {
        val f = file(id)
        if (f.isFile) json.decodeFromString(KeptCatch.serializer(), f.readText()) else null
    }.getOrNull()

    fun has(id: String): Boolean = runCatching { file(id).isFile }.getOrDefault(false)

    /** Removes kept catches that are no longer in the list of deliveries. */
    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { f ->
            if (f.name.removeSuffix(".json") !in keep) f.delete()
        }
    }

    // Ids are UUIDs made by the Lobster; anything else is refused rather than used as a path.
    private fun file(id: String): File {
        require(Regex("[A-Za-z0-9-]{1,64}").matches(id)) { "Not a catch id" }
        return File(dir, "$id.json")
    }

    private companion object {
        const val TAG = "Shelf"
        const val MAX_CHARS = 300_000
    }
}
