package app.localmind.assistant.data.lobster

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the Lobster page shows. */
data class MailboxState(
    /** The Lobster is switched on in SlothLlama. */
    val enabled: Boolean = false,
    /** The mailbox folder's name, once one is chosen. */
    val folderName: String? = null,
    /** The folder was chosen but can't be opened any more (moved, deleted, permission gone). */
    val folderMissing: Boolean = false,
    val status: LobsterStatus? = null,
    val paused: Boolean = false,
    val catch: List<CaughtItem> = emptyList(),
    val orders: List<OrderView> = emptyList(),
    val lastChecked: Long = 0
)

/** A catch file, read and decrypted (or not readable yet). */
data class CaughtItem(val fileName: String, val item: LobsterCatch?, val unreadable: Boolean = false)

data class OrderView(val sent: SentOrder, val state: OrderState)

enum class OrderState(val label: String) {
    WAITING("Waiting in the mailbox"),
    PICKED_UP("With the Lobster"),
    DONE("Brought back")
}

/**
 * The mailbox shared with the Sloth Lobster, the separate app that goes online for you.
 * SlothLlama itself never goes online: it only reads and writes this folder.
 *
 *   orders/   jobs for the Lobster (SlothLlama writes them; the Lobster removes them when taken)
 *   catch/    what the Lobster brings back (the Lobster writes; SlothLlama removes once filed)
 *   status.lobster   the Lobster's heartbeat
 *   STOP      while this file exists, the Lobster pauses
 *
 * Everything but STOP is encrypted with a key only the two apps hold ([MailboxCrypto]).
 * Whatever comes back is data, never instructions: it's shown, filed or quoted, never acted on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LobsterMailbox @Inject constructor(@ApplicationContext private val context: Context) {

    // Not in SlothLlama's backups: the key stays on this tablet (pair again after a restore).
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(MailboxState(enabled = prefs.getBoolean(KEY_ENABLED, false)))
    val state: StateFlow<MailboxState> = _state.asStateFlow()

    /** Switched on with a folder chosen (whether the folder still opens is checked on use). */
    val isReady: Boolean get() = _state.value.enabled && prefs.getString(KEY_FOLDER, null) != null

    // --- setup ---

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _state.value = _state.value.copy(enabled = on)
        LobsterCheck.schedule(context, on)
    }

    /**
     * For the background check: (items waiting, how many weren't there last time). Remembers
     * what it has seen, so each catch is announced once.
     */
    suspend fun newCatch(): Pair<Int, Int> = withContext(io) {
        refreshLocked()
        val names = _state.value.catch.filter { it.item != null }.map { it.fileName }.toSet()
        val seen = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_NOTIFIED, names).apply()
        names.size to (names - seen).size
    }

    /** The folder picked with the system picker; the app keeps permission to read and write it. */
    suspend fun setFolder(uri: Uri) = withContext(io) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        prefs.getString(KEY_FOLDER, null)?.let { old ->
            if (old != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        context.contentResolver.takePersistableUriPermission(uri, flags)
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        folder()?.let { root ->
            dir(root, ORDERS)
            dir(root, CATCH)
        }
        refreshLocked()
    }

    private val vault = KeyVault(prefs)
    @Volatile private var cachedKey: ByteArray? = null

    /** The shared key, made on first use and kept wrapped by the Keystore ([KeyVault]). */
    @Synchronized
    fun key(): ByteArray {
        cachedKey?.let { return it }
        val key = vault.load() ?: MailboxCrypto.newKey().also(vault::save)
        cachedKey = key
        return key
    }

    /** The pairing code to give the Lobster once. */
    fun pairingCode(): String = MailboxCrypto.pairingCode(key())

    /** A new key: the Lobster must be paired again; files sealed with the old key can't be read. */
    @Synchronized
    fun newKey() {
        val key = MailboxCrypto.newKey()
        vault.save(key)
        cachedKey = key
    }

    // --- orders ---

    /** Puts an order in the mailbox. Throws with a readable message if it can't. */
    suspend fun send(kind: OrderKind, text: String, deep: Boolean = false, deliver: String = "library"): LobsterOrder = withContext(io) {
        check(_state.value.enabled) { "The Sloth Lobster is switched off (Lobster page)." }
        val root = folder() ?: error("Choose the Lobster's mailbox folder first (Lobster page).")
        val order = LobsterOrder(
            id = UUID.randomUUID().toString(),
            kind = kind.id,
            text = text.trim().take(MAX_ORDER_CHARS),
            depth = if (deep) "deep" else "quick",
            deliver = deliver,
            createdAt = System.currentTimeMillis()
        )
        val name = "${order.createdAt}-${order.id.take(8)}.order"
        val bytes = MailboxCrypto.seal(key(), "order", json.encodeToString(LobsterOrder.serializer(), order).toByteArray())
        write(dir(root, ORDERS), name, bytes)
        val sent = (listOf(SentOrder(order, name)) + sentOrders()).take(MAX_KEPT_ORDERS)
        prefs.edit().putString(KEY_SENT, json.encodeToString(SENT_LIST, sent)).apply()
        refreshLocked()
        order
    }

    /** Takes an order back if the Lobster hasn't picked it up yet. */
    suspend fun cancel(sent: SentOrder) = withContext(io) {
        folder()?.let { root -> dir(root, ORDERS).findFile(sent.fileName)?.delete() }
        prefs.edit().putString(KEY_SENT, json.encodeToString(SENT_LIST, sentOrders().filterNot { it.order.id == sent.order.id })).apply()
        refreshLocked()
    }

    // --- pause ---

    suspend fun setPaused(paused: Boolean) = withContext(io) {
        val root = folder() ?: return@withContext
        val stop = root.findFile(STOP)
        if (paused && stop == null) root.createFile("application/octet-stream", STOP)
        if (!paused) stop?.delete()
        refreshLocked()
    }

    // --- reading ---

    suspend fun refresh() = withContext(io) { refreshLocked() }

    /** Removes a catch file once it's been filed or dismissed. */
    suspend fun remove(item: CaughtItem) = withContext(io) {
        folder()?.let { root -> dir(root, CATCH).findFile(item.fileName)?.delete() }
        item.item?.orderId?.let { id ->
            prefs.edit().putStringSet(KEY_DONE, (prefs.getStringSet(KEY_DONE, emptySet()).orEmpty() + id).toSet()).apply()
        }
        refreshLocked()
    }

    private fun refreshLocked() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val chosen = prefs.getString(KEY_FOLDER, null)
        val root = folder()
        if (root == null) {
            _state.value = MailboxState(enabled = enabled, folderMissing = chosen != null, orders = orderViews(emptySet(), emptySet()))
            return
        }
        val key = key()
        val status = root.findFile(STATUS)?.let { f -> read(f)?.let { MailboxCrypto.open(key, "status", it) } }
            ?.let { runCatching { json.decodeFromString(LobsterStatus.serializer(), String(it)) }.getOrNull() }
        val catchFiles = dir(root, CATCH).listFiles().filter { it.isFile && it.name?.endsWith(".catch") == true }
            .sortedByDescending { it.name }
        val now = System.currentTimeMillis()
        val catch = catchFiles.take(MAX_CATCH).map { f ->
            val item = read(f)?.let { MailboxCrypto.open(key, "catch", it) }
                ?.let { runCatching { json.decodeFromString(LobsterCatch.serializer(), String(it)) }.getOrNull() }
            // A file still being synced fails to open; only call it unreadable once it's had time.
            CaughtItem(f.name.orEmpty(), item, unreadable = item == null && now - f.lastModified() > UNREADABLE_AFTER_MS)
        }.filter { it.item != null || it.unreadable }
        val waiting = dir(root, ORDERS).listFiles().mapNotNull { it.name }.toSet()
        val answered = catch.mapNotNull { it.item?.orderId }.toSet() + prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()
        _state.value = MailboxState(
            enabled = enabled,
            folderName = root.name,
            status = status,
            paused = root.findFile(STOP) != null,
            catch = catch,
            orders = orderViews(waiting, answered),
            lastChecked = now
        )
    }

    private fun orderViews(waiting: Set<String>, answered: Set<String>): List<OrderView> =
        sentOrders().map { s ->
            OrderView(
                s,
                when {
                    s.order.id in answered -> OrderState.DONE
                    s.fileName in waiting -> OrderState.WAITING
                    else -> OrderState.PICKED_UP
                }
            )
        }

    private fun sentOrders(): List<SentOrder> =
        prefs.getString(KEY_SENT, null)?.let { runCatching { json.decodeFromString(SENT_LIST, it) }.getOrNull() }.orEmpty()

    // --- files ---

    private fun folder(): DocumentFile? {
        val uri = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse) ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()?.takeIf { it.exists() && it.canWrite() }
    }

    private fun dir(root: DocumentFile, name: String): DocumentFile =
        root.findFile(name)?.takeIf { it.isDirectory } ?: root.createDirectory(name) ?: error("Can't write to the mailbox folder.")

    private fun read(file: DocumentFile): ByteArray? = try {
        if (file.length() > MAX_FILE_BYTES) null
        else context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read ${file.name}", e)
        null
    }

    /** Written under a temporary name and renamed when complete, so no one reads half a file. */
    private fun write(dir: DocumentFile, name: String, bytes: ByteArray) {
        val tmp = dir.createFile("application/octet-stream", "$name.tmp") ?: error("Can't write to the mailbox folder.")
        context.contentResolver.openOutputStream(tmp.uri, "w")?.use { it.write(bytes) } ?: error("Can't write to the mailbox folder.")
        if (!tmp.renameTo(name)) {
            tmp.delete()
            error("Can't write to the mailbox folder.")
        }
    }

    companion object {
        private const val TAG = "LobsterMailbox"
        const val PREFS = "lobster"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FOLDER = "folder"
        private const val KEY_SENT = "sent"
        private const val KEY_DONE = "done"
        private const val KEY_NOTIFIED = "notified"
        const val ORDERS = "orders"
        const val CATCH = "catch"
        const val STATUS = "status.lobster"
        const val STOP = "STOP"
        private const val MAX_ORDER_CHARS = 4_000
        private const val MAX_KEPT_ORDERS = 30
        private const val MAX_CATCH = 50
        private const val MAX_FILE_BYTES = 20L * 1024 * 1024
        private const val UNREADABLE_AFTER_MS = 10 * 60 * 1000L
        private val SENT_LIST = ListSerializer(SentOrder.serializer())
    }
}
