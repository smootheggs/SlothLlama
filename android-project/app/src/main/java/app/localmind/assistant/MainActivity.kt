package app.localmind.assistant

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.localmind.assistant.ui.chat.components.LocalChineseScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.localmind.assistant.data.ask.AppShortcuts
import app.localmind.assistant.data.study.ReviewReminder
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.persona.ChineseStyleStore
import app.localmind.assistant.data.attachments.AttachmentStore
import kotlinx.coroutines.launch
import app.localmind.assistant.ui.dictionary.DictionaryScreen
import app.localmind.assistant.ui.core.CoreWordsScreen
import app.localmind.assistant.ui.core.CoreWordsViewModel
import app.localmind.assistant.ui.home.drillTutor
import app.localmind.assistant.ui.home.tutorGroups
import app.localmind.assistant.ui.library.LibraryScreen
import app.localmind.assistant.ui.library.LibraryViewModel
import app.localmind.assistant.ui.lobster.LobsterScreen
import app.localmind.assistant.ui.lobster.LobsterViewModel
import app.localmind.assistant.ui.notes.NotesScreen
import app.localmind.assistant.ui.desk.DeskScreen
import app.localmind.assistant.ui.desk.DeskViewModel
import app.localmind.assistant.ui.reader.ReaderScreen
import app.localmind.assistant.ui.reader.ReaderSource
import app.localmind.assistant.ui.reader.ReaderViewModel
import app.localmind.assistant.ui.notes.NotesViewModel
import app.localmind.assistant.ui.home.HomeRoute
import app.localmind.assistant.ui.dictionary.DictionaryViewModel
import app.localmind.assistant.ui.study.FlashcardsScreen
import app.localmind.assistant.ui.study.FlashcardsViewModel
import app.localmind.assistant.data.settings.AssistantSettingsStore
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.ChineseStyle
import app.localmind.assistant.ui.chat.ChatScreen
import app.localmind.assistant.ui.chat.ChatViewModel
import app.localmind.assistant.ui.models.ModelsScreen
import app.localmind.assistant.ui.models.ModelsViewModel
import app.localmind.assistant.ui.personas.PersonasScreen
import app.localmind.assistant.ui.personas.PersonasViewModel
import app.localmind.assistant.ui.settings.SettingsScreen
import app.localmind.assistant.ui.theme.AppearanceStore
import app.localmind.assistant.ui.theme.SlothTheme
import app.localmind.assistant.ui.theme.ThemeMode
import app.localmind.assistant.ui.theme.resolveColors
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import app.localmind.assistant.ui.theme.SlothPalette as P

@HiltAndroidApp
class LocalMindApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var assistantSettings: AssistantSettingsStore
    @Inject lateinit var attachmentStore: AttachmentStore
    @Inject lateinit var autoBackup: app.localmind.assistant.data.backup.AutoBackup

    // WorkManager starts on demand with Hilt's worker factory (the default initializer is removed
    // in the manifest), so workers such as the flashcard reminder get their dependencies injected.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Crashes are noted in private storage for Settings → Diagnostics (nothing is sent).
        app.localmind.assistant.data.diagnostics.Diagnostics.install(this)
        AppShortcuts.publish(this)
        app.localmind.assistant.data.backup.AutoBackup.schedule(this, autoBackup.state.value.enabled)
        // Keep the daily review reminder scheduled (or cancelled) to match the setting.
        ReviewReminder.schedule(this, assistantSettings.reviewReminder.value, assistantSettings.reminderHour.value, replace = false)
        // Tidy photos left behind by deleted chats, in the background.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { attachmentStore.cleanUp() }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearanceStore
    @Inject lateinit var chineseStyleStore: ChineseStyleStore
    @Inject lateinit var assistantSettings: AssistantSettingsStore
    @Inject lateinit var askInbox: AskInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleAsk(intent)
        enableEdgeToEdge()
        setContent {
            val mode by appearance.mode.collectAsStateWithLifecycle()
            val privacyScreen by appearance.privacyScreen.collectAsStateWithLifecycle()
            val fullScreen by appearance.fullScreen.collectAsStateWithLifecycle()
            val chineseStyle by chineseStyleStore.style.collectAsStateWithLifecycle()
            val thinking by assistantSettings.thinking.collectAsStateWithLifecycle()
            val colors = resolveColors(mode)

            // Privacy screen: no screenshots or recordings, and a blank card in Recents.
            DisposableEffect(privacyScreen) {
                if (privacyScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }

            // Full screen: the status bar and the tablet's taskbar hide; a swipe from the edge shows them for a moment.
            DisposableEffect(fullScreen) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    if (fullScreen) {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                onDispose { }
            }

            // Dark icons on the white theme, light icons on OLED black.
            DisposableEffect(colors.isDark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (colors.isDark) SystemBarStyle.dark(transparent)
                else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            SlothTheme(colors) {
                // Chinese characters in replies are drawn at the size chosen in Settings → Chinese.
                CompositionLocalProvider(LocalChineseScale provides chineseStyle.charScale) {
                    SlothRoot(
                        themeMode = mode,
                        onThemeModeChange = appearance::set,
                        privacyScreen = privacyScreen,
                        onPrivacyScreenChange = appearance::setPrivacyScreen,
                        fullScreen = fullScreen,
                        onFullScreenChange = appearance::setFullScreen,
                        chineseStyle = chineseStyle,
                        onChineseScriptChange = chineseStyleStore::setScript,
                        onRomanizationChange = chineseStyleStore::setRomanization,
                        onCharScaleChange = chineseStyleStore::setCharScale,
                        thinking = thinking,
                        onThinkingChange = assistantSettings::setThinking,
                        askInbox = askInbox,
                        // Read once: the setting decides where a fresh start lands.
                        startAtHome = assistantSettings.openToHome.value
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAsk(intent)
    }

    /** Text forwarded by [AskActivity], or a home-screen shortcut. */
    private fun handleAsk(intent: Intent?) {
        when (intent?.action) {
            ACTION_ASK -> intent.getStringExtra(EXTRA_ASK_TEXT)?.takeIf { it.isNotBlank() }
                ?.let { askInbox.post(LaunchRequest.Ask(it)) }
            ACTION_SHORTCUT -> {
                val target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
                when {
                    target == AppShortcuts.TARGET_FLASHCARDS -> askInbox.post(LaunchRequest.Flashcards)
                    target == AppShortcuts.TARGET_LOBSTER -> askInbox.post(LaunchRequest.Lobster)
                    target.startsWith(AppShortcuts.TARGET_PERSONA_PREFIX) ->
                        askInbox.post(LaunchRequest.StartChat(target.removePrefix(AppShortcuts.TARGET_PERSONA_PREFIX)))
                }
            }
        }
    }

    companion object {
        const val ACTION_ASK = "app.localmind.assistant.ASK"
        const val EXTRA_ASK_TEXT = "ask_text"
        const val ACTION_SHORTCUT = "app.localmind.assistant.SHORTCUT"
        const val EXTRA_TARGET = "target"
    }
}

private enum class Destination { HOME, CHAT, PERSONAS, MODELS, SETTINGS, FLASHCARDS, DICTIONARY, LIBRARY, NOTES, LOBSTER, READER, DESK, CORE }

/**
 * Home (or the last chat, per Settings) on a fresh start. Screens open on top of each other and
 * Back returns to where you came from; from the chat, Back goes Home; from Home it leaves.
 */
@Composable
private fun SlothRoot(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    privacyScreen: Boolean,
    onPrivacyScreenChange: (Boolean) -> Unit,
    fullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    chineseStyle: ChineseStyle,
    onChineseScriptChange: (ChineseScript) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onCharScaleChange: (Float) -> Unit,
    thinking: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    askInbox: AskInbox,
    startAtHome: Boolean
) {
    val first = if (startAtHome) Destination.HOME else Destination.CHAT
    // Back stack of screen names, saved as one string so it survives rotation and restarts.
    var savedStack by rememberSaveable { mutableStateOf(first.name) }
    val stack: List<String> = savedStack.split(',')
        .filter { name -> Destination.entries.any { it.name == name } }
        .ifEmpty { listOf(first.name) }
    fun setStack(value: List<String>) {
        savedStack = value.joinToString(",")
    }
    val destination = Destination.valueOf(stack.last())
    val chatViewModel: ChatViewModel = hiltViewModel()
    fun navigate(to: Destination) {
        if (to == destination) return
        // Opening something already in the stack returns to it rather than piling up copies.
        val at = stack.indexOf(to.name)
        setStack(if (at >= 0) stack.take(at + 1) else stack + to.name)
    }
    val goBack: () -> Unit = {
        when {
            stack.size > 1 -> setStack(stack.dropLast(1))
            destination != Destination.HOME -> setStack(listOf(Destination.HOME.name))
        }
    }
    // Leaving a chat for something it opened (Personas → "chat with") lands back in the chat.
    val goToChat: () -> Unit = { navigate(Destination.CHAT) }
    val goHome = goBack

    // "Ask SlothLlama" from another app, or a home-screen shortcut.
    val pendingLaunch by askInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLaunch) {
        when (val request = askInbox.take()) {
            is LaunchRequest.Ask -> {
                navigate(Destination.CHAT)
                chatViewModel.askAbout(request.text)
            }
            is LaunchRequest.StartChat -> {
                navigate(Destination.CHAT)
                chatViewModel.startConversationWith(request.personaId)
            }
            LaunchRequest.Flashcards -> navigate(Destination.FLASHCARDS)
            is LaunchRequest.Shared -> {
                navigate(Destination.CHAT)
                chatViewModel.receiveShare(request.prompt, request.photo, request.document)
            }
            LaunchRequest.Lobster -> navigate(Destination.LOBSTER)
            null -> Unit
        }
    }

    BackHandler(enabled = destination != Destination.HOME, onBack = goBack)

    // The SlothLobster shows up on Home and in the chat list once it's switched on.
    val lobster: LobsterViewModel = hiltViewModel()
    val lobsterState by lobster.state.collectAsStateWithLifecycle()
    val openLobster: (() -> Unit)? = if (lobsterState.enabled) ({ navigate(Destination.LOBSTER) }) else null
    LaunchedEffect(lobsterState.enabled) {
        if (lobsterState.enabled) lobster.refresh()
    }
    // The reader: one for the whole app, so Back from a chat it opened returns to the same text.
    val reader: ReaderViewModel = hiltViewModel()
    val read: (ReaderSource, String) -> Unit = { source, ref ->
        reader.open(source, ref)
        navigate(Destination.READER)
    }
    fun withCatch(fileName: String, action: (app.localmind.assistant.data.lobster.CaughtItem) -> Unit) {
        lobsterState.catch.firstOrNull { it.fileName == fileName }?.let(action)
    }

    // Every page stays clear of the navigation bar and a tablet's taskbar (they draw over the app
    // edge to edge); padding here also covers pages, snackbars and buttons that don't pad themselves.
    Box(Modifier.fillMaxSize().background(P.Canvas).navigationBarsPadding()) {
        Crossfade(targetState = destination, animationSpec = tween(180), label = "destination") { screen ->
            when (screen) {
                Destination.HOME -> HomeRoute(
                    chatViewModel = chatViewModel,
                    script = chineseStyle.script,
                    onOpenChat = goToChat,
                    onOpenChats = {
                        chatViewModel.setDrawerOpen(true)
                        goToChat()
                    },
                    onFlashcards = { navigate(Destination.FLASHCARDS) },
                    onDictionary = { navigate(Destination.DICTIONARY) },
                    onCoreWords = { navigate(Destination.CORE) },
                    onPersonas = { navigate(Destination.PERSONAS) },
                    onModels = { navigate(Destination.MODELS) },
                    onSettings = { navigate(Destination.SETTINGS) },
                    onLibrary = { navigate(Destination.LIBRARY) },
                    onNotes = { navigate(Destination.NOTES) },
                    onDesk = { navigate(Destination.DESK) },
                    onLater = {
                        reader.close()
                        navigate(Destination.READER)
                    },
                    onLobster = openLobster,
                    lobster = lobsterState.takeIf { it.enabled }
                )
                Destination.CHAT -> ChatScreen(
                    viewModel = chatViewModel,
                    onOpenPersonas = { navigate(Destination.PERSONAS) },
                    onOpenModels = { navigate(Destination.MODELS) },
                    onOpenSettings = { navigate(Destination.SETTINGS) },
                    onOpenFlashcards = { navigate(Destination.FLASHCARDS) },
                    onOpenDictionary = { navigate(Destination.DICTIONARY) },
                    onOpenHome = { navigate(Destination.HOME) },
                    onOpenLibrary = { navigate(Destination.LIBRARY) },
                    onOpenNotes = { navigate(Destination.NOTES) },
                    onOpenLobster = openLobster
                )
                Destination.PERSONAS -> PersonasScreen(
                    viewModel = hiltViewModel<PersonasViewModel>(),
                    onChatWith = { personaId ->
                        chatViewModel.startConversationWith(personaId)
                        goToChat()
                    },
                    onBack = goHome,
                    defaultModelName = chatViewModel.uiState.collectAsStateWithLifecycle().value.activeModelName,
                    onModels = { navigate(Destination.MODELS) },
                    script = chineseStyle.script
                )
                Destination.MODELS -> ModelsScreen(viewModel = hiltViewModel<ModelsViewModel>(), onBack = goHome)
                Destination.FLASHCARDS -> FlashcardsScreen(viewModel = hiltViewModel<FlashcardsViewModel>(), onBack = goHome)
                Destination.DICTIONARY -> DictionaryScreen(viewModel = hiltViewModel<DictionaryViewModel>(), onBack = goHome)
                Destination.CORE -> CoreWordsScreen(
                    viewModel = hiltViewModel<CoreWordsViewModel>(),
                    onBack = goHome,
                    onAsk = { language, prompt ->
                        // The language's tutor, with the question in the box to send or edit.
                        val tutor = tutorGroups(chatViewModel.uiState.value.personas)
                            .firstOrNull { it.language == language }
                            ?.let(::drillTutor)
                        if (tutor != null) chatViewModel.startConversationWith(tutor.id) else chatViewModel.createNewConversation()
                        chatViewModel.onInputTextChanged(prompt)
                        goToChat()
                    }
                )
                Destination.LIBRARY -> LibraryScreen(
                    viewModel = hiltViewModel<LibraryViewModel>(),
                    onBack = goHome,
                    onRead = { id -> read(ReaderSource.LIBRARY, id.toString()) }
                )
                Destination.NOTES -> NotesScreen(
                    viewModel = hiltViewModel<NotesViewModel>(),
                    onBack = goHome,
                    onRead = { id -> read(ReaderSource.NOTE, id) }
                )
                Destination.LOBSTER -> LobsterScreen(
                    viewModel = lobster,
                    onBack = goHome,
                    onRead = { fileName -> read(ReaderSource.CATCH, fileName) },
                    onDesk = { navigate(Destination.DESK) }
                )
                Destination.DESK -> DeskScreen(
                    viewModel = hiltViewModel<DeskViewModel>(),
                    onBack = goHome,
                    onOpenLobster = { navigate(Destination.LOBSTER) }
                )
                Destination.READER -> ReaderScreen(
                    viewModel = reader,
                    onBack = goHome,
                    onCatchToLibrary = { f -> withCatch(f, lobster::toLibrary) },
                    onCatchToNotes = { f -> withCatch(f, lobster::toNotes) },
                    onCatchDismiss = { f -> withCatch(f, lobster::dismiss) }
                )
                Destination.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    privacyScreen = privacyScreen,
                    onPrivacyScreenChange = onPrivacyScreenChange,
                    fullScreen = fullScreen,
                    onFullScreenChange = onFullScreenChange,
                    chineseStyle = chineseStyle,
                    onChineseScriptChange = onChineseScriptChange,
                    onRomanizationChange = onRomanizationChange,
                    onCharScaleChange = onCharScaleChange,
                    thinking = thinking,
                    onThinkingChange = onThinkingChange,
                    onOpenModels = { navigate(Destination.MODELS) },
                    onOpenPersonas = { navigate(Destination.PERSONAS) },
                    onOpenLobster = { navigate(Destination.LOBSTER) },
                    onBack = goHome
                )
            }
        }
    }
}
