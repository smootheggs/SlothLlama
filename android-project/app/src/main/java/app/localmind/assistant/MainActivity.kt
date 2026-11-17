package app.localmind.assistant

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import app.localmind.assistant.ui.chat.ChatScreen
import app.localmind.assistant.ui.chat.ChatViewModel
import app.localmind.assistant.ui.models.ModelsScreen
import app.localmind.assistant.ui.models.ModelsViewModel
import app.localmind.assistant.ui.personas.PersonasScreen
import app.localmind.assistant.ui.personas.PersonasViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LocalMindApp : Application()

private val TrueOledDarkColorScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF0B0C0E),
    surfaceVariant = Color(0xFF121418),
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F6),
    onSurface = Color(0xFFF4F4F6),
    onSurfaceVariant = Color(0xFF8E929F),
    error = Color(0xFFF87171)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = TrueOledDarkColorScheme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    color = Color.Black
                ) {
                    LocalMindRootScaffold()
                }
            }
        }
    }
}

@Composable
fun LocalMindRootScaffold() {
    var selectedTab by remember { mutableIntStateOf(0) }
    // Activity-scoped, so the Personas tab can start a chat on the Chat tab.
    val chatViewModel: ChatViewModel = hiltViewModel()
    val tabItems = listOf(
        Pair("Chat", Icons.Default.ChatBubbleOutline),
        Pair("Personas", Icons.Default.PersonOutline),
        Pair("Knowledge", Icons.Default.MenuBook),
        Pair("Models", Icons.Default.Memory),
        Pair("Settings", Icons.Default.Settings)
    )

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0B0C0E),
                tonalElevation = 0.dp
            ) {
                tabItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = item.second,
                                contentDescription = item.first,
                                tint = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF6B7280)
                            )
                        },
                        label = {
                            Text(
                                text = item.first,
                                color = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF6B7280)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFF161922)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when (selectedTab) {
                0 -> ChatScreen(viewModel = chatViewModel)
                1 -> PersonasScreen(
                    viewModel = hiltViewModel<PersonasViewModel>(),
                    onChatWith = { personaId ->
                        chatViewModel.startConversationWith(personaId)
                        selectedTab = 0
                    }
                )
                2 -> TabPlaceholderScreen("Knowledge", "Manage local indexed document collections, SHA-256 verified text chunks, and offline vector embeddings.")
                3 -> ModelsScreen(viewModel = hiltViewModel<ModelsViewModel>())
                4 -> TabPlaceholderScreen("Settings", "On-device privacy controls, secure hardware key management, and complete local database wipe.")
            }
        }
    }
}

@Composable
fun TabPlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = Color(0xFFF4F4F6),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = Color(0xFF8E929F),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
