package app.localmind.assistant.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.localmind.assistant.BuildConfig
import app.localmind.assistant.data.local.ConversationEntity
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.LocalMindDatabase
import app.localmind.assistant.data.local.MessageEntity
import app.localmind.assistant.data.local.PersonaEntity
import app.localmind.assistant.data.local.VocabularyItemEntity
import app.localmind.assistant.data.local.UserMemoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Everything a backup holds. Models aren't included: they're large and can be imported again. */
@Serializable
data class BackupContents(
    val format: Int = BackupManager.FORMAT,
    val exportedAt: Long,
    val appVersion: String,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val personas: List<PersonaEntity>,
    val flashcards: List<VocabularyItemEntity>,
    /** SharedPreferences file → key → type-tagged value ("b:true", "i:9", "f:1.2", "l:…", "s:…"). */
    val settings: Map<String, Map<String, String>> = emptyMap(),
    /** What else was on the device (names only), so a restore can list what to import again. */
    val inventory: BackupInventory = BackupInventory(),
    /** Memory of you (Settings → Memory). */
    val memories: List<UserMemoryEntity> = emptyList(),
    /** SlothDesk's settings, trial log and paper journal (desk/state.json); prices are fetched again. */
    val desk: String? = null,
    /** The Later shelf: saved articles and how far you'd read them. */
    val later: String? = null,
    /** The idea book: trade ideas, notes and reviews (trading/ideas.json). */
    val trading: String? = null,
    /** SlothBooks' shelf: books, papers and your reading (books/shelf.json); the files stay in Documents/SlothBooks. */
    val books: String? = null,
    /** The edition's settings (editions/setup.json); the papers themselves aren't kept. */
    val edition: String? = null,
    /** Projects: each project's record and its documents (projects/projects.json). */
    val projects: String? = null
)

/** Names of the models and Library files, which are too large to go into a backup. */
@Serializable
data class BackupInventory(
    val models: List<String> = emptyList(),
    val library: List<String> = emptyList()
)

data class BackupSummary(
    val chats: Int,
    val messages: Int,
    val personas: Int,
    val flashcards: Int,
    val memories: Int = 0,
    /** After a restore: models the backup lists that aren't on this device. */
    val missingModels: List<String> = emptyList(),
    /** After a restore: Library files the backup lists that aren't on this device. */
    val missingLibrary: List<String> = emptyList()
)

class WrongPasswordException : Exception("Wrong password, or the backup file is damaged.")
class PasswordRequiredException : Exception("This backup is protected with a password.")

/**
 * Backups: one file with all chats, custom personas, flashcards and settings. Cloud backup is off
 * by design, so this is how data survives a reset or moves to a new tablet.
 *
 * File layout: "SLLB" · version (1) · flags (bit 0 = encrypted) · [salt 16 · IV 12] · payload.
 * The payload is gzipped JSON; with a password it's sealed with AES-256-GCM using a key from
 * PBKDF2-HMAC-SHA256 (310,000 rounds), so a wrong password or any tampering is detected.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LocalMindDatabase,
    private val dao: LocalMindDao,
    private val notes: app.localmind.assistant.data.tools.NotesStore,
    private val desk: app.localmind.assistant.data.desk.DeskStore,
    private val models: app.localmind.assistant.data.models.ModelLibrary,
    private val library: app.localmind.assistant.data.library.LibraryStore,
    private val home: app.localmind.assistant.data.home.HomePrefs,
    private val later: app.localmind.assistant.data.later.ReadingList,
    private val ideas: app.localmind.assistant.data.trading.IdeaBook,
    private val books: app.localmind.assistant.data.books.BookShelf,
    private val editions: app.localmind.assistant.data.edition.EditionStore,
    private val projects: app.localmind.assistant.data.projects.ProjectBook
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri, password: String?): BackupSummary = withContext(Dispatchers.IO) {
        val contents = BackupContents(
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            conversations = dao.getAllConversations(),
            messages = dao.getAllMessages(),
            personas = dao.getCustomPersonas(),
            flashcards = dao.getAllVocabulary(),
            settings = readSettings(),
            inventory = inventory(),
            memories = dao.getAllMemories(),
            desk = desk.backupText(),
            later = runCatching { later.backupText() }.getOrNull(),
            trading = runCatching { ideas.backupText() }.getOrNull(),
            books = runCatching { books.backupText() }.getOrNull(),
            edition = runCatching { editions.backupText() }.getOrNull(),
            projects = runCatching { projects.backupText() }.getOrNull()
        )
        val payload = gzip(json.encodeToString(BackupContents.serializer(), contents).toByteArray())
        val bytes = if (password.isNullOrEmpty()) header(encrypted = false) + payload else seal(payload, password)
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't write to that location.")
        out.use { it.write(bytes) }
        contents.summary()
    }

    /** True if the backup at [uri] needs a password. */
    suspend fun isEncrypted(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val head = context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(6) } ?: error("Couldn't open the file.")
        checkHeader(head)
        (head[5].toInt() and FLAG_ENCRYPTED) != 0
    }

    /**
     * Adds everything in the backup to this device. Items with the same id are replaced by the
     * backup's copy; nothing else is deleted. Settings take effect after the app restarts.
     */
    suspend fun restore(uri: Uri, password: String?): BackupSummary = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open the file.")
        checkHeader(bytes)
        val encrypted = (bytes[5].toInt() and FLAG_ENCRYPTED) != 0
        val payload = when {
            !encrypted -> bytes.copyOfRange(HEADER_SIZE, bytes.size)
            password.isNullOrEmpty() -> throw PasswordRequiredException()
            else -> unseal(bytes, password)
        }
        val contents = json.decodeFromString(BackupContents.serializer(), String(gunzip(payload)))
        require(contents.format <= FORMAT) { "This backup was made by a newer version of SlothLlama. Update the app first." }

        database.withTransaction {
            contents.conversations.forEach { dao.upsertConversation(it) }
            val conversationIds = contents.conversations.map { it.id }.toSet()
            contents.messages.filter { it.conversationId in conversationIds }.forEach { dao.insertMessage(it) }
            contents.personas.filter { !it.isBuiltIn }.forEach { dao.upsertPersona(it) }
            contents.flashcards.forEach { dao.upsertVocabularyItem(it) }
            contents.memories.forEach { dao.upsertMemory(it) }
        }
        writeSettings(contents.settings)
        notes.reload()
        home.reload()
        contents.desk?.let { runCatching { desk.mergeBackup(it) } }
        contents.later?.let { runCatching { later.mergeBackup(it) } }
        contents.trading?.let { runCatching { ideas.mergeBackup(it) } }
        contents.books?.let { runCatching { books.mergeBackup(it) } }
        contents.edition?.let { runCatching { editions.mergeBackup(it) } }
        contents.projects?.let { runCatching { projects.mergeBackup(it) } }
        val here = inventory()
        contents.summary().copy(
            missingModels = contents.inventory.models - here.models.toSet(),
            missingLibrary = contents.inventory.library - here.library.toSet()
        )
    }

    private suspend fun inventory(): BackupInventory = runCatching {
        library.refresh()
        BackupInventory(
            models = models.refresh().map { it.file.name }.sorted(),
            library = library.items.value.orEmpty().map { it.name }.sorted()
        )
    }.getOrDefault(BackupInventory())

    // --- Settings --------------------------------------------------------------------------

    private fun readSettings(): Map<String, Map<String, String>> = SETTINGS_FILES.associateWith { file ->
        context.getSharedPreferences(file, Context.MODE_PRIVATE).all.mapNotNull { (key, value) ->
            when (value) {
                is Boolean -> key to "b:$value"
                is Int -> key to "i:$value"
                is Long -> key to "l:$value"
                is Float -> key to "f:$value"
                is String -> key to "s:$value"
                else -> null
            }
        }.toMap()
    }

    private fun writeSettings(settings: Map<String, Map<String, String>>) {
        settings.filterKeys { it in SETTINGS_FILES }.forEach { (file, values) ->
            val editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            values.forEach { (key, tagged) ->
                val value = tagged.substringAfter(':', "")
                when (tagged.substringBefore(':')) {
                    "b" -> editor.putBoolean(key, value.toBoolean())
                    "i" -> value.toIntOrNull()?.let { editor.putInt(key, it) }
                    "l" -> value.toLongOrNull()?.let { editor.putLong(key, it) }
                    "f" -> value.toFloatOrNull()?.let { editor.putFloat(key, it) }
                    "s" -> editor.putString(key, value)
                }
            }
            editor.apply()
        }
    }

    // --- File format and crypto ------------------------------------------------------------

    private fun header(encrypted: Boolean) =
        MAGIC + byteArrayOf(VERSION, (if (encrypted) FLAG_ENCRYPTED else 0).toByte())

    private fun checkHeader(bytes: ByteArray) {
        require(bytes.size >= HEADER_SIZE && bytes.copyOfRange(0, 4).contentEquals(MAGIC)) {
            "That file isn't a SlothLlama backup."
        }
        require(bytes[4] <= VERSION) { "This backup was made by a newer version of SlothLlama. Update the app first." }
    }

    private fun seal(payload: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val head = header(encrypted = true)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(head)   // the header can't be altered without detection
        }
        return head + salt + iv + cipher.doFinal(payload)
    }

    private fun unseal(bytes: ByteArray, password: String): ByteArray {
        val head = bytes.copyOfRange(0, HEADER_SIZE)
        val salt = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + SALT_SIZE)
        val iv = bytes.copyOfRange(HEADER_SIZE + SALT_SIZE, HEADER_SIZE + SALT_SIZE + IV_SIZE)
        val sealed = bytes.copyOfRange(HEADER_SIZE + SALT_SIZE + IV_SIZE, bytes.size)
        return try {
            Cipher.getInstance(CIPHER).apply {
                init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(head)
            }.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ROUNDS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = read(buf, read, n - read)
            if (r < 0) break
            read += r
        }
        return buf.copyOf(read)
    }

    private fun BackupContents.summary() =
        BackupSummary(conversations.size, messages.size, personas.size, flashcards.size, memories = memories.size)

    companion object {
        const val FORMAT = 1
        private val MAGIC = "SLLB".toByteArray()
        private const val VERSION: Byte = 1
        private const val FLAG_ENCRYPTED = 1
        private const val HEADER_SIZE = 6
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val PBKDF2_ROUNDS = 310_000
        private const val CIPHER = "AES/GCM/NoPadding"
        /** Settings worth carrying over (appearance, Chinese, assistant, persona models). */
        private val SETTINGS_FILES = listOf("appearance", "chinese_style", "assistant", "persona_models", "tutor_levels", "notes", "memory", "home", "core_words")
    }
}
