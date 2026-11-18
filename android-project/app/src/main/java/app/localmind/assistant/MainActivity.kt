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

    // WorkManager starts on demand with Hilt's worker factory (the default initializer is removed
    // in the manifest), so workers such as the flashcard reminder get their dependencies injected.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        AppShortcuts.publish(this)
        // Keep the daily review reminder scheduled (or cancelled) to match the setting.
        ReviewReminder.schedule(this, assistantSettings.reviewReminder.value, assistantSettings.reminderHour.value, replace = false)
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
            val chineseStyle by chineseStyleStore.style.collectAsStateWithLifecycle()
            val thinking by assistantSettings.thinking.collectAsStateWithLifecycle()
            val colors = resolveColors(mode)

            // Privacy screen: no screenshots or recordings, and a blank card in Recents.
            DisposableEffect(privacyScreen) {
                if (privacyScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
                        chineseStyle = chineseStyle,
                        onChineseScriptChange = chineseStyleStore::setScript,
                        onRomanizationChange = chineseStyleStore::setRomanization,
                        onCharScaleChange = chineseStyleStore::setCharScale,
                        thinking = thinking,
                        onThinkingChange = assistantSettings::setThinking,
                        askInbox = askInbox
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

private enum class Destination { CHAT, PERSONAS, MODELS, SETTINGS, FLASHCARDS }

/**
 * Chat is home. Personas, Models and Settings open from the drawer as full screens;
 * Back always returns to the chat.
 */
@Composable
private fun SlothRoot(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    privacyScreen: Boolean,
    onPrivacyScreenChange: (Boolean) -> Unit,
    chineseStyle: ChineseStyle,
    onChineseScriptChange: (ChineseScript) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onCharScaleChange: (Float) -> Unit,
    thinking: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    askInbox: AskInbox
) {
    var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }
    val chatViewModel: ChatViewModel = hiltViewModel()
    val goHome = { destination = Destination.CHAT }

    // "Ask SlothLlama" from another app, or a home-screen shortcut.
    val pendingLaunch by askInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLaunch) {
        when (val request = askInbox.take()) {
            is LaunchRequest.Ask -> {
                destination = Destination.CHAT
                chatViewModel.askAbout(request.text)
            }
            is LaunchRequest.StartChat -> {
                destination = Destination.CHAT
                chatViewModel.startConversationWith(request.personaId)
            }
            LaunchRequest.Flashcards -> destination = Destination.FLASHCARDS
            null -> Unit
        }
    }

    BackHandler(enabled = destination != Destination.CHAT, onBack = goHome)

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Crossfade(targetState = destination, animationSpec = tween(180), label = "destination") { screen ->
            when (screen) {
                Destination.CHAT -> ChatScreen(
                    viewModel = chatViewModel,
                    onOpenPersonas = { destination = Destination.PERSONAS },
                    onOpenModels = { destination = Destination.MODELS },
                    onOpenSettings = { destination = Destination.SETTINGS },
                    onOpenFlashcards = { destination = Destination.FLASHCARDS }
                )
                Destination.PERSONAS -> PersonasScreen(
                    viewModel = hiltViewModel<PersonasViewModel>(),
                    onChatWith = { personaId ->
                        chatViewModel.startConversationWith(personaId)
                        goHome()
                    },
                    onBack = goHome
                )
                Destination.MODELS -> ModelsScreen(viewModel = hiltViewModel<ModelsViewModel>(), onBack = goHome)
                Destination.FLASHCARDS -> FlashcardsScreen(viewModel = hiltViewModel<FlashcardsViewModel>(), onBack = goHome)
                Destination.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    privacyScreen = privacyScreen,
                    onPrivacyScreenChange = onPrivacyScreenChange,
                    chineseStyle = chineseStyle,
                    onChineseScriptChange = onChineseScriptChange,
                    onRomanizationChange = onRomanizationChange,
                    onCharScaleChange = onCharScaleChange,
                    thinking = thinking,
                    onThinkingChange = onThinkingChange,
                    onOpenModels = { destination = Destination.MODELS },
                    onOpenPersonas = { destination = Destination.PERSONAS },
                    onBack = goHome
                )
            }
        }
    }
}
