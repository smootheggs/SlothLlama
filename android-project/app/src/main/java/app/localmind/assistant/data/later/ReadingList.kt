package app.localmind.assistant.data.later

import android.content.Context
import app.localmind.assistant.data.lobster.LobsterCatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** An article on the Later shelf. Its text is kept in its own file. */
@Serializable
data class LaterItem(
    val id: String,
    val title: String,
    val url: String = "",
    val site: String = "",
    /** About how long it takes to read, in minutes. */
    val minutes: Int = 1,
    val savedAt: Long = 0,
    /** How far you've read, 0 to 1. */
    val progress: Float = 0f,
    val openedAt: Long = 0,
    /** When you finished or archived it (0 = still to read). */
    val archivedAt: Long = 0,
    val favourite: Boolean = false,
    /** False when the Lobster couldn't bring back a readable copy (the item holds the link and why). */
    val readable: Boolean = true
) {
    val archived: Boolean get() = archivedAt > 0
    /** Minutes still to read. */
    val minutesLeft: Int get() = maxOf(1, Math.round(minutes * (1f - progress.coerceIn(0f, 1f))))
}

/** A backup's copy of the shelf: the items and their texts. */
@Serializable
data class LaterBackup(val items: List<LaterItem> = emptyList(), val texts: Map<String, String> = emptyMap())

/**
 * The Later shelf: articles saved for later (with the Lobster's "Save for later"), kept as files on
 * the tablet like Pocket kept them on its servers, but nowhere else. The catch from the mailbox is
 * moved here by [LobsterMailbox], so saved pages never crowd the catch.
 */
@Singleton
class ReadingList @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "later").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    private val _items = MutableStateFlow(readIndex())
    /** Newest first. */
    val items: StateFlow<List<LaterItem>> = _items.asStateFlow()

    /**
     * Shelves a catch the Lobster saved for later. The same link saved again replaces the text and
     * comes back to the top, keeping where you'd got to. Blocking: call off the main thread.
     */
    fun importCatch(c: LobsterCatch): LaterItem = synchronized(lock) {
        val url = c.sources.firstOrNull { it.url.isNotBlank() }?.url ?: linkIn(c.text).orEmpty()
        val text = cleanArticle(c.text, c.title)
        val readable = !c.text.startsWith("The Lobster couldn't bring back")
        val existing = if (url.isBlank()) null else _items.value.firstOrNull { it.url == url }
        val item = LaterItem(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = c.title.ifBlank { siteOf(url).ifBlank { "Saved page" } }.take(200),
            url = url,
            site = siteOf(url),
            minutes = readingMinutes(text),
            savedAt = c.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            progress = existing?.progress ?: 0f,
            openedAt = existing?.openedAt ?: 0,
            archivedAt = 0,
            favourite = existing?.favourite ?: false,
            readable = readable || existing?.readable == true
        )
        // A failed re-save never overwrites a good copy.
        if (readable || existing == null || !existing.readable) File(dir, "${item.id}.md").writeText(text)
        save(listOf(item) + _items.value.filterNot { it.id == item.id })
        item
    }

    fun text(id: String): String? = File(dir, "$id.md").takeIf { it.exists() }?.readText()

    fun setProgress(id: String, progress: Float) = change(id) {
        it.copy(progress = progress.coerceIn(0f, 1f), openedAt = System.currentTimeMillis())
    }

    fun setArchived(id: String, on: Boolean) = change(id) { it.copy(archivedAt = if (on) System.currentTimeMillis() else 0) }

    fun setFavourite(id: String, on: Boolean) = change(id) { it.copy(favourite = on) }

    fun remove(id: String) = synchronized(lock) {
        File(dir, "$id.md").delete()
        save(_items.value.filterNot { it.id == id })
    }

    /** The shelf for a backup: newest first, up to about [maxChars] of text. */
    fun backupText(maxChars: Int = 15_000_000): String = synchronized(lock) {
        var total = 0
        val texts = LinkedHashMap<String, String>()
        _items.value.forEach { item ->
            val t = text(item.id) ?: return@forEach
            if (total + t.length > maxChars) return@forEach
            total += t.length
            texts[item.id] = t
        }
        json.encodeToString(LaterBackup.serializer(), LaterBackup(_items.value.filter { it.id in texts }, texts))
    }

    /** Adds a backup's articles that aren't on the shelf yet. */
    fun mergeBackup(text: String) = synchronized(lock) {
        val b = json.decodeFromString(LaterBackup.serializer(), text)
        val have = _items.value.map { it.id }.toSet() + _items.value.map { it.url }.filter { it.isNotBlank() }
        val added = b.items.filter { it.id !in have && (it.url.isBlank() || it.url !in have) && b.texts[it.id] != null }
        added.forEach { File(dir, "${it.id}.md").writeText(b.texts.getValue(it.id)) }
        save((_items.value + added).sortedByDescending { it.savedAt })
    }

    private fun change(id: String, edit: (LaterItem) -> LaterItem) = synchronized(lock) {
        save(_items.value.map { if (it.id == id) edit(it) else it })
    }

    private fun save(list: List<LaterItem>) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json.encodeToString(LIST, list))
        if (!tmp.renameTo(index)) {
            index.delete()
            tmp.renameTo(index)
        }
        _items.value = list
    }

    private fun readIndex(): List<LaterItem> =
        runCatching { if (index.exists()) json.decodeFromString(LIST, index.readText()) else emptyList() }.getOrDefault(emptyList())

    companion object {
        private val LIST = ListSerializer(LaterItem.serializer())

        fun siteOf(url: String): String =
            url.substringAfter("://", "").substringBefore('/').substringBefore('?').removePrefix("www.").lowercase()

        private fun linkIn(text: String): String? = Regex("""https://[^\s<>"')\]]+""").find(text)?.value?.trimEnd('.', ',', ';', ':')

        /** About how long a text takes to read: 230 words or 500 Chinese characters a minute. */
        fun readingMinutes(text: String): Int {
            val han = text.count { it.code in 0x3400..0x9FFF }
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            return maxOf(1, Math.round(words / 230.0 + han / 500.0).toInt())
        }

        /**
         * The article without the Lobster's header (its "# Title" and "Source: …" lines): the reader
         * shows the title and site itself.
         */
        fun cleanArticle(text: String, title: String): String {
            var lines = text.trim().lines()
            if (lines.firstOrNull()?.trim()?.let { it.startsWith("# ") && it.removePrefix("# ").trim() == title.trim() } == true) {
                lines = lines.drop(1).dropWhile { it.isBlank() }
            }
            if (lines.firstOrNull()?.startsWith("Source: ") == true) lines = lines.drop(1).dropWhile { it.isBlank() }
            return lines.joinToString("\n").trim()
        }
    }
}
