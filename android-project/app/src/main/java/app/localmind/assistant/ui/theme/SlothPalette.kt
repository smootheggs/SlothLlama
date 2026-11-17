package app.localmind.assistant.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Short names for the current theme's colours, usable anywhere inside a composable:
 * `import app.localmind.assistant.ui.theme.SlothPalette as P` then `P.Text`, `P.Accent`, …
 */
object SlothPalette {
    val Canvas: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.canvas
    val Card: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.surface
    val Elevated: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.elevated
    val Border: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.border
    val BorderStrong: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.borderStrong
    val Text: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.muted
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.faint
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.onAccent
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.accentSoft
    val Success: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.warning
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.danger
    val DangerSoft: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.dangerSoft
    val CodeBackground: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.codeBackground
    val CodeText: Color @Composable @ReadOnlyComposable get() = LocalSlothColors.current.codeText
}
