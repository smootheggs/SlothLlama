package app.slothlobster.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons drawn like the mockups (24-unit grid, 1.7 strokes, round ends). */
object LobsterIcons {
    private fun line(name: String, vararg paths: String, width: Float = 1.7f): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private const val RING3 = "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0"

    val Deck = line("deck", "M3 18h18M5 18l2-8h10l2 8M9 10V6h6v4")
    val Orders = line("orders", "M6 5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M4 7l8 6 8-6")
    val Watch = line("watch", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", RING3)
    val Log = line("log", "M6 3h9l4 4v14H6z", "M9 11h7M9 15h7")
    val Settings = line("settings", RING3, "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val Back = line("back", "M15 5l-7 7 7 7", width = 1.8f)
    val Plus = line("plus", "M12 5v14M5 12h14", width = 1.8f)
    val Lock = line("lock", "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Check = line("check", "M5 12l5 5 9-10", width = 2f)
    val Close = line("close", "M6 6l12 12M18 6L6 18", width = 1.8f)
    val Refresh = line("refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    val Info = line("info", "M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0", "M12 8v5M12 16h.01")
    val Trash = line("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val Folder = line("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Paste = line("paste", "M9 4h6v3H9z", "M15 5h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2")
    val Send = line("send", "M4 12l16-8-6 16-2.5-6.5z", "M11.5 13.5L20 4")
    val Link = line("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
}
