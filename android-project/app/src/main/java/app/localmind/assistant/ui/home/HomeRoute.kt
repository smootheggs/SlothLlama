package app.localmind.assistant.ui.home

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.backup.AutoBackupState
import app.localmind.assistant.data.lobster.MailboxState
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.ui.chat.ChatViewModel
import app.localmind.assistant.ui.library.LibraryViewModel
import app.localmind.assistant.ui.notes.NotesViewModel
import app.localmind.assistant.ui.study.FlashcardsViewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** The Home page, fed from the chat's state, flashcards, the library, notes, backups and the Lobster. */
@Composable
fun HomeRoute(
    chatViewModel: ChatViewModel,
    script: ChineseScript,
    onOpenChat: () -> Unit,
    onOpenChats: () -> Unit,
    onFlashcards: () -> Unit,
    onDictionary: () -> Unit,
    onPersonas: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    onNotes: () -> Unit,
    onLobster: (() -> Unit)? = null,
    /** The Lobster's mailbox, when the Lobster is switched on. */
    lobster: MailboxState? = null
) {
    val ui by chatViewModel.uiState.collectAsStateWithLifecycle()
    val flashcards: FlashcardsViewModel = hiltViewModel()
    val cards by flashcards.uiState.collectAsStateWithLifecycle()
    val library: LibraryViewModel = hiltViewModel()
    val shelf by library.items.collectAsStateWithLifecycle()
    val notesVm: NotesViewModel = hiltViewModel()
    val notes by notesVm.notes.collectAsStateWithLifecycle()
    val home: HomeViewModel = hiltViewModel()
    val backup by home.backup.collectAsStateWithLifecycle()
    val meaningOn by home.meaningOn.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    val lobsterLine = lobster?.let { lobsterStatus(it, now) }
    val today = buildList {
        val due = cards.dueCount
        add(
            if (due > 0) TodayItem(Icons.Outlined.Style, "$due due", "Flashcards", "Review now", Tone.ACCENT, onFlashcards)
            else TodayItem(Icons.Outlined.Style, "All done", "Flashcards", "Nothing due today", Tone.QUIET, onFlashcards)
        )
        if (lobster != null && onLobster != null) {
            val caught = lobster.catch.size
            add(
                if (caught > 0) TodayItem(Icons.Outlined.Sailing, "$caught new", "From the Lobster", "Brought back · file it", Tone.LOBSTER, onLobster)
                else TodayItem(Icons.Outlined.Sailing, "Nothing new", "From the Lobster", lobsterLine.orEmpty(), Tone.QUIET, onLobster)
            )
        }
        add(backupItem(backup, now, onSettings))
    }

    val things = buildList {
        val items = shelf?.size ?: 0
        add(
            HomeTile(
                Icons.Outlined.LocalLibrary, "Library",
                when (items) {
                    0 -> "Books, documents, Wikipedia"
                    1 -> "1 item"
                    else -> "$items items"
                } + if (meaningOn && items > 0) " · search by meaning on" else "",
                onLibrary
            )
        )
        add(
            HomeTile(
                Icons.Outlined.StickyNote2, "Notes",
                when (notes.size) {
                    0 -> "Say \"take a note: …\""
                    1 -> "1 note"
                    else -> "${notes.size} notes"
                },
                onNotes
            )
        )
        add(HomeTile(Icons.Outlined.Translate, "Dictionary", "Chinese · offline", onDictionary))
        if (onLobster != null && lobsterLine != null) {
            add(HomeTile(Icons.Outlined.Sailing, "SlothLobster", lobsterLine, onLobster, Tone.LOBSTER))
        }
    }

    val more = listOf(
        HomeTile(Icons.Outlined.ChatBubbleOutline, "Chats", "", onOpenChats),
        HomeTile(Icons.Outlined.PersonOutline, "Personas", "", onPersonas),
        HomeTile(Icons.Outlined.Memory, "Models", ui.activeModelName.ifBlank { "choose one" }, onModels)
    )

    HomeScreen(
        script = script,
        conversations = ui.conversations,
        personas = ui.personas,
        today = today,
        things = things,
        more = more,
        onSearch = onOpenChats,
        onSettings = onSettings,
        onOpenConversation = { id ->
            chatViewModel.selectConversation(id)
            onOpenChat()
        },
        onNewChat = {
            chatViewModel.createNewConversation()
            onOpenChat()
        },
        onStartWith = { tutor ->
            chatViewModel.startConversationWith(tutor.id)
            onOpenChat()
        }
    )
}

/** "Awake", "Working: …", "Paused", "Last seen 2 hr. ago". */
private fun lobsterStatus(state: MailboxState, now: Long): String {
    val status = state.status
    return when {
        state.paused -> "Paused"
        status == null -> "Not heard from yet"
        now - status.at < AWAKE_MS -> status.working?.let { "Working: $it" } ?: "Awake"
        else -> "Last seen " + DateUtils.getRelativeTimeSpanString(status.at, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    }
}

private fun backupItem(state: AutoBackupState, now: Long, onSettings: () -> Unit): TodayItem = when {
    !state.enabled -> TodayItem(Icons.Outlined.Shield, "Off", "Weekly backup", "Turn it on in Settings", Tone.QUIET, onSettings)
    state.lastAt == 0L -> TodayItem(Icons.Outlined.Shield, "Soon", "Weekly backup", "The first one runs within a day", Tone.ACCENT, onSettings)
    !state.lastOk -> TodayItem(Icons.Outlined.Shield, "Didn't save", "Weekly backup", state.lastResult ?: "Open Settings to see why", Tone.PROBLEM, onSettings)
    else -> TodayItem(Icons.Outlined.Shield, "Saved ${dayLabel(state.lastAt, now)}", "Weekly backup", "The newest 4 are kept", Tone.ACCENT, onSettings)
}

/** "today", "yesterday", "Sun" within the week, else a short date. */
private fun dayLabel(time: Long, now: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val daysAgo = ((startOfDay(today) - startOfDay(day)) / DateUtils.DAY_IN_MILLIS).toInt()
    return when {
        daysAgo <= 0 -> "today"
        daysAgo == 1 -> "yesterday"
        daysAgo < 7 -> android.text.format.DateFormat.format("EEE", Date(time)).toString()
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(time))
    }
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** A heartbeat younger than this means the Lobster is up (as on the Lobster page). */
private const val AWAKE_MS = 3 * 60 * 1000L
