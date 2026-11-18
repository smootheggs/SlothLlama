package app.slothlobster.mailbox

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The Lobster's side of the mailbox it shares with SlothLlama (the format is in
 * android-project/docs/LOBSTER_MAILBOX.md):
 *
 *   orders/          SlothLlama writes; the Lobster reads and removes an order when it takes it
 *   catch/           the Lobster writes what it brings back; SlothLlama removes it once filed
 *   status.lobster   the Lobster's heartbeat
 *   STOP             while it exists, the Lobster pauses (either app can create or remove it)
 *
 * Everything except STOP is sealed with the key from SlothLlama's pairing code. Calls here touch
 * files, so run them off the main thread.
 */
class Mailbox(private val context: Context, private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val vault = KeyVault(prefs)
    @Volatile private var cachedKey: ByteArray? = null

    // --- pairing ---

    @Synchronized
    fun key(): ByteArray? {
        cachedKey?.let { return it }
        return vault.load()?.also { cachedKey = it }
    }

    val isPaired: Boolean get() = key() != null

    /** Saves the key from SlothLlama's pairing code. False if the code isn't a valid one. */
    @Synchronized
    fun pair(code: String): Boolean {
        val key = MailboxCrypto.fromPairingCode(code) ?: return false
        vault.save(key)
        cachedKey = key
        return true
    }

    @Synchronized
    fun unpair() {
        vault.clear()
        cachedKey = null
    }

    /** The last three characters of the code, to tell pairings apart without showing the key. */
    fun codeHint(): String? = key()?.let { MailboxCrypto.pairingCode(it).takeLast(3) }

    // --- folder ---

    fun setFolder(uri: Uri) {
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
    }

    val hasFolderChoice: Boolean get() = prefs.getString(KEY_FOLDER, null) != null

    fun folderName(): String? = folder()?.name

    /** Paired, with a folder that still opens. */
    fun isReady(): Boolean = isPaired && folder() != null

    // --- pause ---

    fun isStopped(): Boolean = folder()?.findFile(STOP) != null

    fun setStop(on: Boolean) {
        val root = folder() ?: return
        val stop = root.findFile(STOP)
        if (on && stop == null) root.createFile("application/octet-stream", STOP)
        if (!on) stop?.delete()
    }

    // --- orders ---

    /**
     * The orders waiting in the folder that open with our key. Each one taken is removed from
     * the folder (that's how SlothLlama shows "With the Lobster"). Files that don't open are
     * left alone: they may still be syncing, or belong to another pairing.
     */
    fun takeOrders(): List<LobsterOrder> {
        val key = key() ?: return emptyList()
        val root = folder() ?: return emptyList()
        val files = dir(root, ORDERS).listFiles()
            .filter { it.isFile && it.name?.endsWith(".order") == true }
            .sortedBy { it.name }
        return files.mapNotNull { file ->
            val order = read(file)?.let { MailboxCrypto.open(key, "order", it) }
                ?.let { runCatching { json.decodeFromString(LobsterOrder.serializer(), String(it)) }.getOrNull() }
                ?: return@mapNotNull null
            file.delete()
            order
        }
    }

    // --- catch ---

    /** Puts a catch in the folder for SlothLlama. Returns its file name, or null without a mailbox. */
    fun deliver(catch: LobsterCatch): String? {
        val key = key() ?: return null
        val root = folder() ?: return null
        val name = "${catch.createdAt}-${catch.id.take(8)}.catch"
        val bytes = MailboxCrypto.seal(key, "catch", json.encodeToString(LobsterCatch.serializer(), catch).toByteArray())
        write(dir(root, CATCH), name, bytes)
        return name
    }

    /** Names of the catch files still waiting for SlothLlama (gone means it filed them). */
    fun waitingCatch(): Set<String> {
        val root = folder() ?: return emptySet()
        return dir(root, CATCH).listFiles().mapNotNull { it.name }.filter { it.endsWith(".catch") }.toSet()
    }

    // --- status ---

    fun writeStatus(status: LobsterStatus) {
        val key = key() ?: return
        val root = folder() ?: return
        val bytes = MailboxCrypto.seal(key, "status", json.encodeToString(LobsterStatus.serializer(), status).toByteArray())
        val tmpName = "$STATUS.${UUID.randomUUID().toString().take(6)}.tmp"
        val tmp = root.createFile("application/octet-stream", tmpName) ?: return
        val ok = runCatching {
            context.contentResolver.openOutputStream(tmp.uri, "w")?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!ok) {
            tmp.delete()
            return
        }
        // A rename onto an existing name would make "status (1).lobster", so the old one goes first.
        root.findFile(STATUS)?.delete()
        if (!tmp.renameTo(STATUS)) tmp.delete()
    }

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
        private const val TAG = "Mailbox"
        private const val KEY_FOLDER = "folder"
        const val ORDERS = "orders"
        const val CATCH = "catch"
        const val STATUS = "status.lobster"
        const val STOP = "STOP"
        private const val MAX_FILE_BYTES = 1L * 1024 * 1024
    }
}
