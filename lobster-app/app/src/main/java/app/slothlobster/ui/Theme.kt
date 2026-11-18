package app.slothlobster.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Settings → Look. Stored by name in the Lobster's settings. */
enum class ThemeChoice(val id: String, val label: String) {
    SYSTEM("system", "Match the tablet"),
    WHITE("white", "White"),
    OLED("oled", "OLED black");

    companion object {
        fun of(id: String?): ThemeChoice = entries.firstOrNull { it.id == id } ?: OLED
    }
}

/**
 * Every colour the Lobster's screens use. Coral is kept for Send, new things and progress; sea
 * teal says it's awake; problems are a soft red. Neutrals lean very slightly to the sea.
 * Text contrast is at least 4.5:1 in both themes.
 */
@Immutable
data class SeaColors(
    val isDark: Boolean,
    val canvas: Color,
    val card: Color,
    val raised: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val text: Color,
    val soft: Color,
    val muted: Color,
    val faint: Color,
    val coral: Color,
    val onCoral: Color,
    val coralSoft: Color,
    val off: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerLine: Color,
    val bubble: Color,
    val awake: Color
)

/** OLED black: on black, text 19:1, muted 9.3:1, faint 6.0:1, coral 8.5:1, teal 10.3:1. */
val OledSea = SeaColors(
    isDark = true,
    canvas = Color(0xFF000000),
    card = Color(0xFF111314),
    raised = Color(0xFF171B1C),
    border = Color(0xFF232829),
    borderStrong = Color(0xFF2E3536),
    divider = Color(0xFF1C2021),
    text = Color(0xFFF2F4F3),
    soft = Color(0xFFD3D8D7),
    muted = Color(0xFFA8AEAD),
    faint = Color(0xFF848B8A),
    coral = Color(0xFFFF7F66),
    onCoral = Color(0xFF1A0A06),
    coralSoft = Color(0xFF2A1714),
    off = Color(0xFF3A4243),
    danger = Color(0xFFF59B9B),
    dangerSoft = Color(0xFF2A1416),
    dangerLine = Color(0xFF7F1D1D),
    bubble = Color(0xFF7DD3FC),
    awake = Color(0xFF5FC7B8)
)

/** White: a deeper coral (5.6:1 on white) and darker greys so everything still reads. */
val WhiteSea = SeaColors(
    isDark = false,
    canvas = Color(0xFFFFFFFF),
    card = Color(0xFFF6F7F7),
    raised = Color(0xFFEFF1F1),
    border = Color(0xFFE1E5E5),
    borderStrong = Color(0xFFC9CFCF),
    divider = Color(0xFFE8ECEC),
    text = Color(0xFF121516),
    soft = Color(0xFF2E3436),
    muted = Color(0xFF5A6264),
    faint = Color(0xFF646C6E),
    coral = Color(0xFFB83F27),
    onCoral = Color(0xFFFFFFFF),
    coralSoft = Color(0xFFFCEEEA),
    off = Color(0xFFC9CFCF),
    danger = Color(0xFFB42318),
    dangerSoft = Color(0xFFFDECEA),
    dangerLine = Color(0xFFF3B5AE),
    bubble = Color(0xFF0369A1),
    awake = Color(0xFF0F766E)
)

val LocalSea = staticCompositionLocalOf { OledSea }

/** The current theme's colours, by name: `Sea.Coral`, `Sea.Text`, … (inside composables). */
object Sea {
    val Canvas: Color @Composable @ReadOnlyComposable get() = LocalSea.current.canvas
    val Card: Color @Composable @ReadOnlyComposable get() = LocalSea.current.card
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalSea.current.raised
    val Border: Color @Composable @ReadOnlyComposable get() = LocalSea.current.border
    val BorderStrong: Color @Composable @ReadOnlyComposable get() = LocalSea.current.borderStrong
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalSea.current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalSea.current.text
    val Soft: Color @Composable @ReadOnlyComposable get() = LocalSea.current.soft
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalSea.current.muted
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalSea.current.faint
    val Coral: Color @Composable @ReadOnlyComposable get() = LocalSea.current.coral
    val OnCoral: Color @Composable @ReadOnlyComposable get() = LocalSea.current.onCoral
    val CoralSoft: Color @Composable @ReadOnlyComposable get() = LocalSea.current.coralSoft
    val Off: Color @Composable @ReadOnlyComposable get() = LocalSea.current.off
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalSea.current.danger
    val DangerSoft: Color @Composable @ReadOnlyComposable get() = LocalSea.current.dangerSoft
    val DangerLine: Color @Composable @ReadOnlyComposable get() = LocalSea.current.dangerLine
    val Bubble: Color @Composable @ReadOnlyComposable get() = LocalSea.current.bubble
    val Awake: Color @Composable @ReadOnlyComposable get() = LocalSea.current.awake
}

@Composable
fun seaColors(choice: ThemeChoice): SeaColors = when (choice) {
    ThemeChoice.WHITE -> WhiteSea
    ThemeChoice.OLED -> OledSea
    ThemeChoice.SYSTEM -> if (isSystemInDarkTheme()) OledSea else WhiteSea
}

@Composable
fun LobsterTheme(colors: SeaColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.coral, onPrimary = colors.onCoral,
            background = colors.canvas, onBackground = colors.text,
            surface = colors.card, onSurface = colors.text,
            surfaceVariant = colors.raised, onSurfaceVariant = colors.muted,
            surfaceContainerHigh = colors.raised, surfaceContainer = colors.card,
            outline = colors.off, outlineVariant = colors.border,
            error = colors.danger
        )
    } else {
        lightColorScheme(
            primary = colors.coral, onPrimary = colors.onCoral,
            background = colors.canvas, onBackground = colors.text,
            surface = colors.card, onSurface = colors.text,
            surfaceVariant = colors.raised, onSurfaceVariant = colors.muted,
            surfaceContainerHigh = colors.raised, surfaceContainer = colors.card,
            outline = colors.off, outlineVariant = colors.border,
            error = colors.danger
        )
    }
    CompositionLocalProvider(LocalSea provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
