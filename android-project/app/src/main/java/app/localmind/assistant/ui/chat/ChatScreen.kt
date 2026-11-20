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
import app.localmind.assistant.ui.chat.components.QuickAction
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
import app.localmind.assistant.ui.chat.components.ChatActions
import app.localmind.assistant.ui.chat.components.LocalChatActions
import app.localmind.assistant.ui.chat.components.StartPage
import app.localmind.assistant.ui.chat.components.QuickActions
import app.localmind.assistant.domain.model.MessageStatus
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.ui.chat.components.defaultStartTiles
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.practice.SpeakingCheck
import app.localmind.assistant.ui.chat.components.LocalWordTapAction
import app.localmind.assistant.ui.dictionary.DictionaryViewModel
import app.localmind.assistant.ui.dictionary.WordLookupSheet
import app.localmind.assistant.ui.practice.PracticeSentencePicker
import app.localmind.assistant.ui.practice.SpeakingPracticeSheet
import app.localmind.assistant.ui.speech.TutorSpeaker
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.outlined.Home
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import app.localmind.assistant.ui.home.WIDE
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.localmind.assistant.ui.chat.components.LocalPhotoFile
import app.localmind.assistant.ui.theme.SlothPalette as P

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenPersonas: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenDictionary: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenLobster: (() -> Unit)? = null,
    /** An idea card from a Trader reply, to the Trading book. */
    onSaveIdea: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Landscape on a tablet: the chat list stays open on the left instead of sliding over.
    val wide = LocalConfiguration.current.screenWidthDp.dp >= WIDE
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceLevel by viewModel.voiceLevel.collectAsStateWithLifecycle()
    val chineseStyle by viewModel.chineseStyle.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
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
    val saveWords: ((String) -> Unit)? = if (persona.speech != null) ({ text: String -> saveWordsFrom = text }) else null

    // Dictionary: tap a character in a reply. Practice: repeat a sentence after the tutor.
    val dictionary: DictionaryViewModel = hiltViewModel()
    val lookup by dictionary.lookup.collectAsStateWithLifecycle()
    val dictionaryMessage by dictionary.message.collectAsStateWithLifecycle()
    val studyLanguage = persona.speech ?: SpeechLanguage.MANDARIN
    var practiseSentences by remember { mutableStateOf<List<String>>(emptyList()) }
    var practiseTarget by remember { mutableStateOf<String?>(null) }
    val handsFree by viewModel.handsFree.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val hasLibrary by viewModel.hasLibrary.collectAsStateWithLifecycle()
    val libraryOn by viewModel.libraryOn.collectAsStateWithLifecycle()
    val lookingUp by viewModel.lookingUp.collectAsStateWithLifecycle()
    val toolPreview by viewModel.toolPreview.collectAsStateWithLifecycle()
    // The system photo picker needs no permission and only shares the photo chosen.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attachPhoto)
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::attachDocument)
    }
    LaunchedEffect(dictionaryMessage) {
        dictionaryMessage?.let {
            snackbarHostState.showSnackbar(it)
            dictionary.clearMessage()
        }
    }
    // Leaving the chat ends a hands-free conversation (the speaker goes with the screen).
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopHandsFree()
            dictionary.dismiss()
        }
    }
    // Chinese dictionary: for the Chinese tutors and the general assistant (not Japanese kanji).
    val wordTap: ((String, Int) -> Unit)? =
        dictionary::lookUpAt.takeIf { dictionary.isAvailable && persona.speech?.isChinese != false }
    val openPractice: (String) -> Unit = { reply ->
        viewModel.stopHandsFree()
        val language = persona.speech ?: SpeechLanguage.MANDARIN
        val kind = when {
            language.isChinese -> SpeakingCheck.Units.HAN
            language == SpeechLanguage.JAPANESE -> SpeakingCheck.Units.JAPANESE
            else -> SpeakingCheck.Units.WORDS
        }
        val sentences = SpeakingCheck.sentencesFrom(TutorSpeaker.spokenText(reply, language), kind)
        when (sentences.size) {
            0 -> Unit
            1 -> practiseTarget = sentences.first()
            else -> practiseSentences = sentences
        }
    }
    LaunchedEffect(flashcardMessage) {
        flashcardMessage?.let {
            snackbarHostState.showSnackbar(it)
            flashcards.clearMessage()
        }
    }

    // The slide-over drawer only exists in portrait; in landscape the list is always showing.
    LaunchedEffect(uiState.isDrawerOpen, wide) {
        when {
            wide -> if (uiState.isDrawerOpen) viewModel.setDrawerOpen(false)
            uiState.isDrawerOpen && drawerState.isClosed -> drawerState.open()
            !uiState.isDrawerOpen && drawerState.isOpen -> drawerState.close()
        }
    }
    LaunchedEffect(drawerState.isOpen) {
        if (!wide && drawerState.isOpen != uiState.isDrawerOpen) viewModel.setDrawerOpen(drawerState.isOpen)
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    fun closeDrawerThen(action: () -> Unit) {
        if (!wide) scope.launch { drawerState.close() }
        action()
    }

    val drawerContent: @Composable () -> Unit = {
                ConversationDrawerContent(
                    conversations = searchResults?.let { ids ->
                        (uiState.conversations + uiState.archivedConversations).filter { it.id in ids }
                    } ?: uiState.conversations,
                    archivedConversations = if (searchResults == null) uiState.archivedConversations else emptyList(),
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
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
                    onOpenDictionary = { closeDrawerThen(onOpenDictionary) },
                    onOpenHome = { closeDrawerThen(onOpenHome) },
                    onOpenLibrary = { closeDrawerThen(onOpenLibrary) },
                    onOpenNotes = { closeDrawerThen(onOpenNotes) },
                    onOpenLobster = onOpenLobster?.let { go -> { closeDrawerThen(go) } },
                    flashcardsDue = flashcardState.dueCount,
                    modelStatus = uiState.activeModelName
                )
    }

    val chatContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalSpeakAction provides speakAction,
            // Tutors only: "save words" makes no sense for the general assistant.
            LocalSaveWordAction provides saveWords,
            LocalChatActions provides ChatActions(
                contextSize = uiState.contextSize,
                onContinue = viewModel::continueReply,
                onPractise = if (persona.speech != null) openPractice else null,
                onSaveIdea = if (persona.id == BuiltInPersonas.TRADER.id) onSaveIdea else null
            ),
            LocalWordTapAction provides wordTap,
            LocalPhotoFile provides viewModel::photoFile
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
                                if (wide) {
                                    IconButton(onClick = onOpenHome) {
                                        Icon(Icons.Outlined.Home, contentDescription = "Home", tint = P.Text)
                                    }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = P.Text)
                                    }
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
                        onCancelVoice = viewModel::cancelVoiceInput,
                        // Follow-ups make sense once the persona has replied at least once.
                        quickActions = uiState.messages
                            .lastOrNull { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETE }
                            ?.let { last -> QuickActions.forPersona(persona, last.content) }
                            .orEmpty(),
                        onQuickAction = { action ->
                            when (action.kind) {
                                QuickAction.Kind.SAVE_NOTE -> viewModel.saveLastReplyAsNote()
                                QuickAction.Kind.SEND -> viewModel.sendQuickAction(action.prompt)
                            }
                        },
                        handsFree = handsFree,
                        onStartHandsFree = {
                            speaker.stop()
                            val voice = persona.speech
                            viewModel.startHandsFree { reply -> speaker.speakAndWait(reply, voice) }
                        },
                        onStopHandsFree = {
                            viewModel.stopHandsFree()
                            speaker.stop()
                        },
                        attachments = attachments,
                        onAttachPhoto = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onAttachDocument = {
                            documentPicker.launch(arrayOf("application/pdf", "application/epub+zip", "text/*", "application/json", "application/x-subrip"))
                        },
                        onRemovePhoto = viewModel::removePhoto,
                        onRemoveDocument = viewModel::removeDocument,
                        hasLibrary = hasLibrary,
                        libraryOn = libraryOn,
                        lookingUp = lookingUp,
                        onToggleLibrary = viewModel::toggleLibrary,
                        toolPreview = toolPreview
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
                        showMascot = persona.id == BuiltInPersonas.GENERAL_ASSISTANT.id,
                        emptyContent = {
                            StartPage(
                                persona = persona,
                                script = chineseStyle.script,
                                tiles = defaultStartTiles(
                                    lastConversation = uiState.conversations.firstOrNull { it.id != uiState.activeConversationId },
                                    flashcardsDue = flashcardState.dueCount,
                                    onContinue = viewModel::selectConversation,
                                    onMandarin = { viewModel.startConversationWith(BuiltInPersonas.MANDARIN_PARTNER.id) },
                                    onCantonese = { viewModel.startConversationWith(BuiltInPersonas.CANTONESE_PARTNER.id) },
                                    onFlashcards = onOpenFlashcards
                                ),
                                suggestions = persona.starterPrompts.ifEmpty { DefaultStarterPrompts },
                                onSuggestion = { prompt ->
                                    viewModel.onInputTextChanged(prompt)
                                    viewModel.sendMessage()
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().background(P.Canvas)) {
            Box(
                Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(P.Card)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) { drawerContent() }
            VerticalDivider(color = P.Border)
            Box(Modifier.weight(1f).fillMaxHeight()) { chatContent() }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = P.Card,
                    drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    modifier = Modifier.width(320.dp)
                ) { drawerContent() }
            },
            content = chatContent
        )
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
    lookup?.let { found ->
        WordLookupSheet(
            lookup = found,
            script = chineseStyle.script,
            language = studyLanguage,
            onSpeak = { speaker.speak(it, studyLanguage) },
            onPractise = {
                dictionary.dismiss()
                viewModel.stopHandsFree()
                practiseTarget = it
            },
            onSave = { dictionary.save(it, studyLanguage) },
            onDismiss = dictionary::dismiss
        )
    }
    if (practiseSentences.isNotEmpty()) {
        PracticeSentencePicker(
            sentences = practiseSentences,
            onPick = {
                practiseSentences = emptyList()
                practiseTarget = it
            },
            onDismiss = { practiseSentences = emptyList() }
        )
    }
    practiseTarget?.let { target ->
        SpeakingPracticeSheet(target = target, language = studyLanguage, speaker = speaker, onDismiss = { practiseTarget = null })
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
