package app.localmind.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.ui.chat.components.ChatInputBar
import app.localmind.assistant.ui.chat.components.ClearAllConversationsDialog
import app.localmind.assistant.ui.chat.components.ConversationDrawerContent
import app.localmind.assistant.ui.chat.components.DefaultStarterPrompts
import app.localmind.assistant.ui.chat.components.DeleteConversationDialog
import app.localmind.assistant.ui.chat.components.MessageList
import app.localmind.assistant.ui.chat.components.RenameConversationDialog
import app.localmind.assistant.ui.speech.LocalSpeakAction
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.data.study.StudyLanguage
import app.localmind.assistant.ui.study.AddFlashcardDialog
import app.localmind.assistant.ui.study.FlashcardsViewModel
import app.localmind.assistant.ui.study.LocalSaveWordAction
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import app.localmind.assistant.ui.theme.SlothPalette as P

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenPersonas: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFlashcards: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceLevel by viewModel.voiceLevel.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val speaker = rememberTutorSpeaker()
    val persona = uiState.activePersona
    val speakAction: ((String) -> Unit)? = persona.speech?.let { language ->
        { text: String -> speaker.speak(text, language) }
    }
    val flashcards: FlashcardsViewModel = hiltViewModel()
    val flashcardState by flashcards.uiState.collectAsStateWithLifecycle()
    val flashcardMessage by flashcards.message.collectAsStateWithLifecycle()
    var saveWordsFrom by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashcardMessage) {
        flashcardMessage?.let {
            snackbarHostState.showSnackbar(it)
            flashcards.clearMessage()
        }
    }

    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.isDrawerOpen && drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen) viewModel.setDrawerOpen(drawerState.isOpen)
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = P.Card,
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.width(320.dp)
            ) {
                ConversationDrawerContent(
                    conversations = uiState.conversations,
                    archivedConversations = uiState.archivedConversations,
                    activeConversationId = uiState.activeConversationId,
                    onSelectConversation = { id -> closeDrawerThen { viewModel.selectConversation(id) } },
                    onNewConversation = { closeDrawerThen { viewModel.createNewConversation() } },
                    onRenameConversation = viewModel::requestRenameConversation,
                    onArchiveConversation = viewModel::archiveConversation,
                    onDeleteConversation = viewModel::requestDeleteConversation,
                    onClearAll = viewModel::requestClearAll,
                    onOpenPersonas = { closeDrawerThen(onOpenPersonas) },
                    onOpenModels = { closeDrawerThen(onOpenModels) },
                    onOpenSettings = { closeDrawerThen(onOpenSettings) },
                    onOpenFlashcards = { closeDrawerThen(onOpenFlashcards) },
                    flashcardsDue = flashcardState.dueCount,
                    modelStatus = uiState.activeModelName
                )
            }
        }
    ) {
        CompositionLocalProvider(
            LocalSpeakAction provides speakAction,
            LocalSaveWordAction provides { text: String -> saveWordsFrom = text }
        ) {
            Scaffold(
                containerColor = P.Canvas,
                // Insets are handled once: the top bar takes the status bar, the composer
                // takes the gesture bar and keyboard.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Column {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = P.Text)
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        uiState.activeConversation?.title ?: "New chat",
                                        color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    ModelStatusLine(uiState)
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.createNewConversation() }, enabled = !uiState.isGenerating) {
                                    Icon(Icons.Outlined.EditNote, contentDescription = "New chat", tint = P.Text)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = P.Canvas)
                        )
                        HorizontalDivider(color = P.Border)
                    }
                },
                bottomBar = {
                    ChatInputBar(
                        inputText = uiState.inputText,
                        onInputTextChanged = viewModel::onInputTextChanged,
                        onSendMessage = viewModel::sendMessage,
                        onStopGeneration = viewModel::stopGeneration,
                        isGenerating = uiState.isGenerating,
                        editingMessage = uiState.editingMessage,
                        onCancelEdit = viewModel::cancelEditingMessage,
                        onSubmitEdit = viewModel::submitEditedMessage,
                        activePersona = persona,
                        personas = uiState.personas,
                        onSelectPersona = { selected ->
                            speaker.stop()
                            viewModel.startConversationWith(selected.id)
                        },
                        voiceState = voiceState,
                        voiceLevel = voiceLevel,
                        onStartVoice = {
                            speaker.stop()
                            viewModel.startVoiceInput()
                        },
                        onFinishVoice = viewModel::finishVoiceInput,
                        onCancelVoice = viewModel::cancelVoiceInput
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = modifier
            ) { innerPadding ->
                Box(Modifier.fillMaxSize().padding(innerPadding).background(P.Canvas)) {
                    MessageList(
                        messages = uiState.messages,
                        isGenerating = uiState.isGenerating,
                        onEditUserMessage = viewModel::startEditingMessage,
                        onRegenerate = viewModel::regenerateLastResponse,
                        onRetry = viewModel::regenerateLastResponse,
                        onStarterPromptSelected = { prompt ->
                            viewModel.onInputTextChanged(prompt)
                            viewModel.sendMessage()
                        },
                        emptyBadge = persona.badge,
                        emptyTitle = persona.name,
                        emptySubtitle = persona.tagline.ifBlank { "Private · on-device · offline" },
                        starterPrompts = persona.starterPrompts.ifEmpty { DefaultStarterPrompts },
                        showMascot = persona.id == BuiltInPersonas.GENERAL_ASSISTANT.id
                    )
                }
            }
        }
    }

    uiState.renameTarget?.let { conv ->
        RenameConversationDialog(
            conversation = conv,
            onConfirm = viewModel::confirmRenameConversation,
            onDismiss = viewModel::dismissRenameDialog
        )
    }
    uiState.deleteTarget?.let { conv ->
        DeleteConversationDialog(
            conversation = conv,
            onConfirm = viewModel::confirmDeleteConversation,
            onDismiss = viewModel::dismissDeleteDialog
        )
    }
    saveWordsFrom?.let { text ->
        AddFlashcardDialog(
            replyText = text,
            defaultLanguage = StudyLanguage.from(persona.speech),
            onSave = flashcards::add,
            onDismiss = { saveWordsFrom = null }
        )
    }
    if (uiState.isClearAllConfirmOpen) {
        ClearAllConversationsDialog(
            onConfirm = viewModel::confirmClearAll,
            onDismiss = viewModel::dismissClearAllDialog
        )
    }
}

/** "● Qwen2.5-3B-Instruct" — a small coloured dot shows whether the model is ready. */
@Composable
private fun ModelStatusLine(uiState: ChatUiState) {
    val dot = when (uiState.modelStatus) {
        ModelRuntimeStatus.GENERATING -> P.Accent
        ModelRuntimeStatus.READY -> P.Success
        ModelRuntimeStatus.NO_MODEL_LOADED -> P.Warning
        ModelRuntimeStatus.ERROR -> P.Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(
            if (uiState.isGenerating) "Generating…" else uiState.activeModelName,
            color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
