package app.slothlobster.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** OLED black with a coral accent: SlothLlama's quiet dark, gone to sea. */
object Sea {
    val Canvas = Color(0xFF000000)
    val Card = Color(0xFF121214)
    val Raised = Color(0xFF1A1A1D)
    val Border = Color(0xFF26262B)
    val Divider = Color(0xFF1F1F23)
    val Text = Color(0xFFF2F2F3)
    val Soft = Color(0xFFD4D4D8)
    val Muted = Color(0xFFA1A1AA)
    val Faint = Color(0xFF8B8B93)
    val Coral = Color(0xFFFF7F66)
    val OnCoral = Color(0xFF1A0A06)
    val CoralSoft = Color(0x1FFF7F66)
    val Off = Color(0xFF3F3F46)
    val Danger = Color(0xFFFCA5A5)
    val DangerSoft = Color(0x14EF4444)
    val DangerLine = Color(0xFF7F1D1D)
    val Bubble = Color(0xFF7DD3FC)
}

@Composable
fun LobsterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sea.Coral,
            onPrimary = Sea.OnCoral,
            background = Sea.Canvas,
            onBackground = Sea.Text,
            surface = Sea.Card,
            onSurface = Sea.Text,
            surfaceVariant = Sea.Raised,
            onSurfaceVariant = Sea.Muted,
            surfaceContainerHigh = Sea.Raised,
            surfaceContainer = Sea.Card,
            outline = Sea.Off,
            outlineVariant = Sea.Border,
            error = Sea.Danger
        ),
        content = content
    )
}
