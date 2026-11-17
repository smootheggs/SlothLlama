package app.localmind.assistant

import android.app.Application
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
class LocalMindApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearanceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by appearance.mode.collectAsStateWithLifecycle()
            val colors = resolveColors(mode)

            // Dark icons on the white theme, light icons on OLED black.
            DisposableEffect(colors.isDark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (colors.isDark) SystemBarStyle.dark(transparent)
                else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            SlothTheme(colors) {
                SlothRoot(themeMode = mode, onThemeModeChange = appearance::set)
            }
        }
    }
}

private enum class Destination { CHAT, PERSONAS, MODELS, SETTINGS }

/**
 * Chat is home. Personas, Models and Settings open from the drawer as full screens;
 * Back always returns to the chat.
 */
@Composable
private fun SlothRoot(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }
    val chatViewModel: ChatViewModel = hiltViewModel()
    val goHome = { destination = Destination.CHAT }

    BackHandler(enabled = destination != Destination.CHAT, onBack = goHome)

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Crossfade(targetState = destination, animationSpec = tween(180), label = "destination") { screen ->
            when (screen) {
                Destination.CHAT -> ChatScreen(
                    viewModel = chatViewModel,
                    onOpenPersonas = { destination = Destination.PERSONAS },
                    onOpenModels = { destination = Destination.MODELS },
                    onOpenSettings = { destination = Destination.SETTINGS }
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
                Destination.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenModels = { destination = Destination.MODELS },
                    onOpenPersonas = { destination = Destination.PERSONAS },
                    onBack = goHome
                )
            }
        }
    }
}
