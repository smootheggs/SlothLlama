package app.localmind.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.localmind.assistant.data.bench.BenchDevice
import app.localmind.assistant.data.models.LocalModel
import app.localmind.assistant.data.models.ModelFormat
import app.localmind.assistant.domain.bench.BenchRun
import app.localmind.assistant.domain.edition.MarketTile
import app.localmind.assistant.ui.edition.MarketStrip
import app.localmind.assistant.ui.models.BenchActions
import app.localmind.assistant.ui.models.BenchUiState
import app.localmind.assistant.ui.models.BenchmarkContent
import app.localmind.assistant.ui.theme.OledColors
import app.localmind.assistant.ui.theme.SlothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Device previews (1.54): screens drawn at each of the owner's devices' sizes, upright and
 * sideways, as PNGs under app/build/outputs/roborazzi/<device>/. Only built with
 * -PdevicePreviews (CI's "previews" job); the numbers below are example data for the drawing.
 * Each device is its own class because the screen size is fixed before the test's activity starts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class DevicePreviews(private val device: String) {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(screen: String, content: @Composable () -> Unit) {
        compose.setContent {
            SlothTheme(OledColors) {
                Box(Modifier.fillMaxSize().background(OledColors.canvas)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$device/$screen.png")
    }

    @Test
    fun benchmark() = shoot("benchmark") { BenchmarkContent(sampleBench, noActions) }

    @Test
    fun marketStrip() = shoot("market-strip") { Box(Modifier.padding(16.dp)) { MarketStrip(sampleTiles) } }

    private companion object {
        val sampleModel = LocalModel(File("/example/example-4b-q4_0.gguf"), "Example 4B", ModelFormat.GGUF, 2_600_000_000, "Q4_0")
        val sampleRun = BenchRun(
            id = "a", at = 0, model = "example-4b-q4_0.gguf", modelName = "Example 4B", backend = "GPU", threads = 4,
            promptTokens = 600, readTps = 100.0, writeTps = 10.0, loadSeconds = 3.0, memoryMb = 3_000, spreadPct = 4.0,
            device = "Example device"
        )
        val sampleBench = BenchUiState(
            device = BenchDevice("Example device", "Example chip", 12, 8),
            gpuName = "Example GPU",
            models = listOf(sampleModel),
            selected = sampleModel.path,
            threadChoices = listOf(4, 6, 8),
            runs = listOf(sampleRun, sampleRun.copy(id = "b", backend = "CPU"))
        )
        val noActions = BenchActions({}, {}, {}, {}, {}, {}, {})
        val sampleTiles = listOf(
            MarketTile("GLD", "100.00", "1.00%", true, "date", "close"),
            MarketTile("SLV", "10.00", "1.00%", false, "date", "close"),
            MarketTile("USDJPY", "150.00", "0.10%", true, "date", "close"),
            MarketTile("US 3M", "4.00%", "+0 bp", null, "date", "rate")
        )
    }
}

@Config(sdk = [34], qualifiers = "w412dp-h891dp-port-xxhdpi")
class GalaxyS25PlusUpright : DevicePreviews("galaxy-s25plus-upright")

@Config(sdk = [34], qualifiers = "w891dp-h412dp-land-xxhdpi")
class GalaxyS25PlusSideways : DevicePreviews("galaxy-s25plus-sideways")

@Config(sdk = [34], qualifiers = "w800dp-h1280dp-port-xhdpi")
class LenovoY700Upright : DevicePreviews("lenovo-y700-upright")

@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
class LenovoY700Sideways : DevicePreviews("lenovo-y700-sideways")

@Config(sdk = [34], qualifiers = "w870dp-h1393dp-port-xhdpi")
class TabS11UltraUpright : DevicePreviews("tab-s11-ultra-upright")

@Config(sdk = [34], qualifiers = "w1393dp-h870dp-land-xhdpi")
class TabS11UltraSideways : DevicePreviews("tab-s11-ultra-sideways")
