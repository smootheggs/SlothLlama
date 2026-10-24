package app.localmind.assistant

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val tabs = listOf("Chat", "Personas", "Knowledge", "Models", "Settings")

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B0C0E)) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {},
                        label = { Text(label) }
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
            Text(
                text = "LocalMind — ${tabs[selectedTab]} (Offline-First OLED Ready)",
                color = Color(0xFFF4F4F6)
            )
        }
    }
}
