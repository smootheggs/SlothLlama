package app.localmind.assistant.ui.lobster

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.data.lobster.CaughtItem
import app.localmind.assistant.data.lobster.LobsterCatch
import app.localmind.assistant.data.lobster.LobsterCheck
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.MailboxState
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.lobster.SentOrder
import app.localmind.assistant.data.tools.NotesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LobsterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mailbox: LobsterMailbox,
    private val library: LibraryStore,
    private val notes: NotesStore,
    private val attachments: AttachmentStore,
    private val askInbox: AskInbox
) : ViewModel() {

    val state: StateFlow<MailboxState> = mailbox.state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun refresh() = act(quiet = true) { mailbox.refresh() }

    /** The page is open: the "brought back" notification has done its job. */
    fun opened() {
        LobsterCheck.clear(context)
        act(quiet = true) { mailbox.newCatch() }
    }

    fun setEnabled(on: Boolean) {
        mailbox.setEnabled(on)
        refresh()
    }

    fun chooseFolder(uri: Uri) = act { mailbox.setFolder(uri); "Mailbox folder set." }

    fun pairingCode(): String = mailbox.pairingCode()

    fun newKey() = act { mailbox.newKey(); mailbox.refresh(); "New key made. Pair the Lobster again with the new code." }

    fun setPaused(paused: Boolean) = act { mailbox.setPaused(paused); if (paused) "The Lobster will pause." else "The Lobster can carry on." }

    fun send(kind: OrderKind, text: String, deep: Boolean, deliver: String) = act {
        if (text.isBlank()) return@act null
        mailbox.send(kind, text, deep, deliver)
        "Order sent 🦞"
    }

    fun cancel(order: SentOrder) = act { mailbox.cancel(order); "Order taken back." }

    fun toLibrary(item: CaughtItem) = file(item) { c ->
        val file = c.fileName?.let { name -> c.fileBase64?.let { saveTemp(name, it) } }
        try {
            if (file != null) library.addDocument(Uri.fromFile(file)) else library.addText(c.title, withSources(c))
        } finally {
            file?.delete()
        }
        "Added to your library 📚"
    }

    fun toNotes(item: CaughtItem) = file(item) { c ->
        notes.add("${c.title}\n\n${withSources(c)}".take(MAX_NOTE_CHARS))
        "Saved to Notes 📝"
    }

    fun toChat(item: CaughtItem) = file(item) { c ->
        askInbox.post(LaunchRequest.Shared(prompt = "", document = attachments.textDocument(c.title, withSources(c))))
        null
    }

    fun dismiss(item: CaughtItem) = act { mailbox.remove(item); null }

    fun clearMessage() {
        _message.value = null
    }

    private fun file(item: CaughtItem, block: suspend (LobsterCatch) -> String?) = act {
        val c = item.item ?: return@act null
        val result = block(c)
        mailbox.remove(item)
        result
    }

    private fun act(quiet: Boolean = false, block: suspend () -> Any?) {
        viewModelScope.launch {
            if (!quiet) _busy.value = true
            try {
                val result = block()
                if (result is String) _message.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!quiet) _message.value = e.message?.takeIf { it.isNotBlank() } ?: "That didn't work."
            } finally {
                if (!quiet) _busy.value = false
            }
        }
    }

    private suspend fun saveTemp(name: String, base64: String): File? = withContext(Dispatchers.IO) {
        val safe = name.replace(Regex("[^\\w.\\- ]"), "_").take(100)
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return@withContext null
        File(context.cacheDir, "lobster").apply { mkdirs() }.resolve(safe).apply { writeBytes(bytes) }
    }

    private fun withSources(c: LobsterCatch): String = buildString {
        append(c.text.trim())
        if (c.sources.isNotEmpty()) {
            append("\n\nSources:\n")
            c.sources.forEachIndexed { i, s -> append("[${i + 1}] ${s.title.ifBlank { s.url }} ${s.url}\n") }
        }
    }.trim()

    private companion object {
        const val MAX_NOTE_CHARS = 6_000
    }
}
