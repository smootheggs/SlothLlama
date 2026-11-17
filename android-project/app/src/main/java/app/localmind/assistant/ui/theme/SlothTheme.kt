package app.localmind.assistant.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) {
    SYSTEM("Match system"),
    LIGHT("White"),
    OLED("OLED black")
}

/**
 * Every colour the UI uses. Screens read these through [SlothPalette] (or LocalSlothColors);
 * nothing in the UI should hard-code a hex value.
 */
@Immutable
data class SlothColors(
    val isDark: Boolean,
    val canvas: Color,        // app background
    val surface: Color,       // cards, drawer
    val elevated: Color,      // composer, user bubble, inputs
    val border: Color,        // hairlines
    val borderStrong: Color,  // focused / emphasized outlines
    val text: Color,
    val muted: Color,         // secondary text
    val faint: Color,         // tertiary text, inactive icons
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,    // selected states, chips
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerSoft: Color,
    val codeBackground: Color,
    val codeText: Color,
    val scrim: Color
)

val OledColors = SlothColors(
    isDark = true,
    canvas = Color(0xFF000000),
    surface = Color(0xFF0B0C0E),
    elevated = Color(0xFF16181D),
    border = Color(0xFF22252C),
    borderStrong = Color(0xFF33363F),
    text = Color(0xFFF2F2F4),
    muted = Color(0xFF9EA2AD),
    faint = Color(0xFF6B7080),
    accent = Color(0xFF38BDF8),
    onAccent = Color(0xFF00121C),
    accentSoft = Color(0xFF0B2533),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0xFF2A1517),
    codeBackground = Color(0xFF0E1014),
    codeText = Color(0xFFE4E7EC),
    scrim = Color(0x99000000)
)

val LightColors = SlothColors(
    isDark = false,
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F7F8),
    elevated = Color(0xFFF1F2F4),
    border = Color(0xFFE3E5E8),
    borderStrong = Color(0xFFCDD1D6),
    text = Color(0xFF131418),
    muted = Color(0xFF5A606B),
    faint = Color(0xFF8B909A),
    accent = Color(0xFF0369A1),       // sky-700: 5.9:1 on white (the dark theme's sky-400 is too pale)
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE6F3FB),
    success = Color(0xFF047857),
    warning = Color(0xFFB45309),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFDECEC),
    codeBackground = Color(0xFFF5F6F8),
    codeText = Color(0xFF1F2328),
    scrim = Color(0x66000000)
)

val LocalSlothColors = staticCompositionLocalOf { OledColors }

private val SlothTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun resolveColors(mode: ThemeMode): SlothColors = when (mode) {
    ThemeMode.LIGHT -> LightColors
    ThemeMode.OLED -> OledColors
    ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) OledColors else LightColors
}

@Composable
fun SlothTheme(colors: SlothColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accent,
            background = colors.canvas, onBackground = colors.text,
            surface = colors.canvas, onSurface = colors.text,
            surfaceVariant = colors.elevated, onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.elevated,
            surfaceContainerHighest = colors.elevated, surfaceContainerLow = colors.surface,
            outline = colors.borderStrong, outlineVariant = colors.border,
            error = colors.danger, onError = colors.canvas, scrim = colors.scrim
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accent,
            background = colors.canvas, onBackground = colors.text,
            surface = colors.canvas, onSurface = colors.text,
            surfaceVariant = colors.elevated, onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.elevated,
            surfaceContainerHighest = colors.elevated, surfaceContainerLow = colors.surface,
            outline = colors.borderStrong, outlineVariant = colors.border,
            error = colors.danger, onError = colors.canvas, scrim = colors.scrim
        )
    }
    CompositionLocalProvider(LocalSlothColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = SlothTypography, content = content)
    }
}

/** Remembers the chosen theme across launches. */
@Singleton
class AppearanceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: ThemeMode.OLED
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private val _privacyScreen = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY, false))
    /** Blocks screenshots / screen recording and blanks the app in Recents. */
    val privacyScreen: StateFlow<Boolean> = _privacyScreen.asStateFlow()

    fun setPrivacyScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY, enabled).apply()
        _privacyScreen.value = enabled
    }

    private companion object {
        const val KEY = "theme_mode"
        const val KEY_PRIVACY = "privacy_screen"
    }
}
