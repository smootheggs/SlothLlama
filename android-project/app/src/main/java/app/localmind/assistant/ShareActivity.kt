package app.localmind.assistant

import app.localmind.assistant.domain.persona.BuiltInPersonas
import androidx.compose.material.icons.outlined.ShowChart
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.tools.NotesStore
import app.localmind.assistant.domain.attachments.DocumentText
import app.localmind.assistant.ui.theme.AppearanceStore
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.theme.SlothTheme
import app.localmind.assistant.ui.theme.resolveColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Share to SlothLlama" from any app: text, links, photos, PDFs and books. A small sheet offers
 * what fits (explain, summarize, translate, read a photo's text, save to Notes, add to the
 * Library, send a link to the Lobster). Chat options open a new chat with the content attached
 * and the request in the box: nothing is sent until you tap send. The shared file is read here,
 * while this screen still has permission to open it.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearanceStore
    @Inject lateinit var askInbox: AskInbox
    @Inject lateinit var attachments: AttachmentStore
    @Inject lateinit var notes: NotesStore
    @Inject lateinit var library: LibraryStore
    @Inject lateinit var mailbox: LobsterMailbox
    @Inject lateinit var paperFiles: app.localmind.assistant.data.books.PaperFiles
    @Inject lateinit var shelf: app.localmind.assistant.data.books.BookShelf

    private sealed interface Incoming {
        data class Text(val text: String, val subject: String?) : Incoming
        data class Photo(val uri: Uri) : Incoming
        data class Files(val uris: List<Uri>) : Incoming
    }

    private class Choice(val icon: ImageVector, val label: String, val detail: String, val action: suspend () -> String?)

    private var working by mutableStateOf<String?>(null)
    private var done by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = read(intent)
        if (incoming == null) {
            finish()
            return
        }
        val choices = choicesFor(incoming)
        val preview = previewOf(incoming)
        setContent {
            val mode by appearance.mode.collectAsStateWithLifecycle()
            SlothTheme(resolveColors(mode)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(enabled = working == null) { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Sheet(preview, choices)
                }
            }
        }
    }

    @Composable
    private fun Sheet(preview: String, choices: List<Choice>) {
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(P.Elevated)
                // Taps on the sheet itself don't close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text("Share to SlothLlama 🦥", color = P.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                preview, color = P.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            val message = done ?: working
            if (message != null) {
                Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (done == null) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = P.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(message, color = if (done != null) P.Text else P.Accent, fontSize = 15.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    choices.forEach { c -> ChoiceRow(c) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { finish() }, enabled = working == null) {
                    Text(if (done != null) "Done" else "Cancel", color = P.Muted)
                }
            }
        }
    }

    @Composable
    private fun ChoiceRow(c: Choice) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { start(c) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(c.icon, contentDescription = null, tint = P.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(c.label, color = P.Text, fontSize = 15.sp)
                Text(c.detail, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    private fun start(c: Choice) {
        if (working != null) return
        working = "Working…"
        lifecycleScope.launch {
            val result = try {
                c.action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.message?.takeIf { it.isNotBlank() } ?: "That didn't work."
            }
            working = null
            if (result == null) {
                finish()
            } else {
                done = result
                delay(1_600)
                finish()
            }
        }
    }

    // --- what was shared ---

    private fun read(intent: Intent?): Incoming? {
        intent ?: return null
        val type = intent.type.orEmpty()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
                when {
                    stream != null && type.startsWith("image/") -> Incoming.Photo(stream)
                    stream != null -> Incoming.Files(listOf(stream))
                    text.isNotEmpty() -> Incoming.Text(text.take(MAX_TEXT), subject)
                    else -> null
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                when {
                    uris.isEmpty() -> null
                    type.startsWith("image/") -> Incoming.Photo(uris.first())
                    else -> Incoming.Files(uris.take(MAX_FILES))
                }
            }
            else -> null
        }
    }

    private fun previewOf(incoming: Incoming): String = when (incoming) {
        is Incoming.Text -> incoming.subject?.let { "$it · ${incoming.text}" } ?: incoming.text
        is Incoming.Photo -> "A photo"
        is Incoming.Files -> incoming.uris.joinToString(", ") { nameOf(it) }
    }

    private fun choicesFor(incoming: Incoming): List<Choice> = when (incoming) {
        is Incoming.Text -> textChoices(incoming)
        is Incoming.Photo -> listOf(
            Choice(Icons.Outlined.ChatBubbleOutline, "Ask about it", "A new chat with the photo") { photoChat(incoming.uri, "") },
            Choice(Icons.Outlined.AddPhotoAlternate, "Describe it", "What's in the photo") { photoChat(incoming.uri, "What's in this photo?") },
            Choice(Icons.Outlined.DocumentScanner, "Read the text", "Signs, menus, letters, receipts") {
                photoChat(incoming.uri, "Read out all the text in this photo, then explain it briefly.")
            },
            Choice(Icons.Outlined.Translate, "Translate the text", "Into English") {
                photoChat(incoming.uri, "Translate the text in this photo into English.")
            }
        )
        is Incoming.Files -> buildList {
            if (incoming.uris.size == 1) {
                val uri = incoming.uris.first()
                add(Choice(Icons.Outlined.ChatBubbleOutline, "Ask about it", "A new chat with the document") { fileChat(uri, "") })
                add(Choice(Icons.Outlined.Compress, "Summarize", "The main points, in a new chat") {
                    fileChat(uri, "Summarize this in a few bullet points.")
                })
                add(Choice(Icons.Outlined.ShowChart, "Read it like a trader", "What it means for metals and FX, in a new chat") {
                    fileChat(uri, BuiltInPersonas.READ_LIKE_A_TRADER, personaId = BuiltInPersonas.TRADER.id)
                })
            }
            val keepable = incoming.uris.filter { nameOf(it).let { n -> n.endsWith(".pdf", true) || n.endsWith(".epub", true) } }
            if (keepable.isNotEmpty()) {
                add(Choice(Icons.Outlined.LocalLibrary, "Keep in SlothBooks", "Saved in Documents/SlothBooks, with a card") {
                    keepable.forEach { uri ->
                        working = "Keeping ${nameOf(uri)}…"
                        val epub = nameOf(uri).endsWith(".epub", true)
                        val folder = if (epub) app.localmind.assistant.data.books.PaperFiles.BOOKS else app.localmind.assistant.data.books.PaperFiles.PAPERS
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val (saved, name) = paperFiles.copyIn(uri, folder)
                            shelf.addFileBlocking(
                                app.localmind.assistant.data.books.PaperFiles.titleFrom(name),
                                if (epub) app.localmind.assistant.domain.books.Kinds.BOOK else app.localmind.assistant.domain.books.Kinds.PAPER,
                                saved.toString(), name, "", fresh = true
                            )
                        }
                    }
                    if (keepable.size == 1) "Kept in SlothBooks 📚" else "Kept ${keepable.size} files in SlothBooks 📚"
                })
            }
            add(Choice(Icons.Outlined.LocalLibrary, "Add to Library", "Indexed here, found when you ask") {
                incoming.uris.forEach { uri ->
                    working = "Adding ${nameOf(uri)}…"
                    library.addDocument(uri)
                }
                if (incoming.uris.size == 1) "Added to your library 📚" else "Added ${incoming.uris.size} items to your library 📚"
            })
        }
    }

    private fun textChoices(t: Incoming.Text): List<Choice> {
        val link = LINK.matchEntire(t.text)?.value ?: LINK_IN.find(t.text)?.value?.takeIf { t.text.length < 400 }
        val long = t.text.length > SHORT_TEXT
        val title = t.subject ?: t.text.lineSequence().first().take(80)
        return buildList {
            if (link != null) {
                if (mailbox.isReady) {
                    val pdf = link.substringBefore('?').endsWith(".pdf", true) || "arxiv.org/pdf/" in link
                    add(Choice(Icons.Outlined.Sailing, "Ask the Lobster to fetch it 🦞", if (pdf) "Kept in SlothBooks as a paper" else "Brought back to your Library") {
                        mailbox.send(OrderKind.FETCH, listOfNotNull(t.subject, link).joinToString("\n"), deliver = "library")
                        "Sent to the Lobster 🦞"
                    })
                }
                add(Choice(Icons.Outlined.StickyNote2, "Save the link to Notes", link) {
                    notes.add(listOfNotNull(t.subject, link).joinToString("\n"))
                    "Saved to Notes 📝"
                })
            }
            if (link == null || t.text.length > link.length + 40) {
                add(Choice(Icons.Outlined.Lightbulb, "Explain", "In a new chat") {
                    textChat(t, if (long) "Explain the main points of this." else "Explain this:\n\n${t.text}")
                })
                add(Choice(Icons.Outlined.Compress, "Summarize", "The main points, in a new chat") {
                    textChat(t, "Summarize this in a few bullet points.", forceDocument = true)
                })
                add(Choice(Icons.Outlined.ShowChart, "Read it like a trader", "What it means for metals and FX, in a new chat") {
                    textChat(t, BuiltInPersonas.READ_LIKE_A_TRADER, forceDocument = true, personaId = BuiltInPersonas.TRADER.id)
                })
                add(Choice(Icons.Outlined.Translate, "Translate", "Into English, in a new chat") {
                    textChat(t, if (long) "Translate this into English." else "Translate this into English:\n\n${t.text}")
                })
                add(Choice(Icons.Outlined.StickyNote2, "Save to Notes", "Kept on this tablet") {
                    notes.add(listOfNotNull(t.subject, t.text).joinToString("\n\n"))
                    "Saved to Notes 📝"
                })
                if (long) {
                    add(Choice(Icons.Outlined.LocalLibrary, "Add to Library", "Indexed here, found when you ask") {
                        working = "Adding to your library…"
                        library.addText(title, t.text)
                        "Added to your library 📚"
                    })
                }
            }
        }
    }

    // --- opening a chat ---

    /** Long text goes as a document; short text sits in the box itself. Returns null: no message. */
    private fun textChat(t: Incoming.Text, prompt: String, forceDocument: Boolean = false, personaId: String? = null): String? {
        val asDocument = forceDocument || t.text.length > SHORT_TEXT
        val document = if (asDocument) attachments.textDocument(t.subject ?: "Shared text", t.text) else null
        openChat(LaunchRequest.Shared(prompt = prompt, document = document, personaId = personaId))
        return null
    }

    private suspend fun photoChat(uri: Uri, prompt: String): String? {
        working = "Reading the photo…"
        val photo = attachments.savePhoto(uri)
        openChat(LaunchRequest.Shared(prompt = prompt, photo = photo))
        return null
    }

    private suspend fun fileChat(uri: Uri, prompt: String, personaId: String? = null): String? {
        working = "Reading ${nameOf(uri)}…"
        val document: DocumentText = attachments.readDocument(uri)
        openChat(LaunchRequest.Shared(prompt = prompt, document = document, personaId = personaId))
        return null
    }

    private fun openChat(request: LaunchRequest) {
        askInbox.post(request)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun nameOf(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    private companion object {
        const val MAX_TEXT = 200_000
        const val MAX_FILES = 20
        /** Up to this, shared text goes straight into the message box. */
        const val SHORT_TEXT = 600
        val LINK = Regex("https?://\\S+")
        /** A link inside a short text ("Title https://…"). */
        val LINK_IN = Regex("https?://\\S+")
    }
}
