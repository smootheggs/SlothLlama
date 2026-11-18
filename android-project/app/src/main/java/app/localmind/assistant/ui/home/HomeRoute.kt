package app.localmind.assistant.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.ui.chat.ChatViewModel
import app.localmind.assistant.ui.library.LibraryViewModel
import app.localmind.assistant.ui.notes.NotesViewModel
import app.localmind.assistant.ui.study.FlashcardsViewModel

/** The Home page, fed from the chat's state (conversations, personas, model) and flashcards. */
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
    onNotes: () -> Unit
) {
    val ui by chatViewModel.uiState.collectAsStateWithLifecycle()
    val flashcards: FlashcardsViewModel = hiltViewModel()
    val cards by flashcards.uiState.collectAsStateWithLifecycle()
    val library: LibraryViewModel = hiltViewModel()
    val shelf by library.items.collectAsStateWithLifecycle()
    val notesVm: NotesViewModel = hiltViewModel()
    val notes by notesVm.notes.collectAsStateWithLifecycle()
    HomeScreen(
        script = script,
        conversations = ui.conversations,
        personas = ui.personas,
        tiles = defaultHomeTiles(
            flashcardsDue = cards.dueCount,
            modelStatus = ui.activeModelName,
            onFlashcards = onFlashcards,
            onDictionary = onDictionary,
            onPersonas = onPersonas,
            onModels = onModels,
            onSettings = onSettings,
            onChats = onOpenChats,
            libraryDetail = when (val n = shelf?.size ?: 0) {
                0 -> "Books, documents, Wikipedia"
                1 -> "1 item"
                else -> "$n items"
            },
            onLibrary = onLibrary,
            notesDetail = when (notes.size) {
                0 -> "Say \"take a note: …\""
                1 -> "1 note"
                else -> "${notes.size} notes"
            },
            onNotes = onNotes
        ),
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
