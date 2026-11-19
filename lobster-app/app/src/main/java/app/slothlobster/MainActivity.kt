package app.slothlobster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slothlobster.ui.LobsterTheme
import app.slothlobster.ui.ThemeChoice
import app.slothlobster.ui.seaColors
import app.slothlobster.ui.LobsterUi
import app.slothlobster.ui.LobsterViewModel

class MainActivity : ComponentActivity() {

    private val vm: LobsterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) takeShared(intent)
        askForNotifications()
        setContent {
            val saved by vm.saved.collectAsStateWithLifecycle()
            val colors = seaColors(ThemeChoice.of(saved.settings.theme))
            // System bar icons follow the theme: light icons on black, dark icons on white.
            LaunchedEffect(colors.isDark) {
                val style = if (colors.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Full screen: the status bar and the taskbar hide; a swipe from the edge shows them for a moment.
            val fullScreen = saved.settings.fullScreen
            LaunchedEffect(fullScreen) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    if (fullScreen) {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            LobsterTheme(colors) { LobsterUi(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShared(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    /** A link or words shared from another app become a new order, waiting for you to send. */
    private fun takeShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .distinct()
            .joinToString("\n")
        vm.share(text)
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
