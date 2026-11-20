package app.localmind.assistant.ui.atlas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.atlas.AtlasRegions
import app.localmind.assistant.data.atlas.AtlasWorld
import app.localmind.assistant.domain.atlas.AtlasFacts
import app.localmind.assistant.domain.atlas.AtlasQuiz
import app.localmind.assistant.domain.atlas.EqualEarth
import kotlin.math.max
import kotlin.math.min

/** The map's colours: dark or the white map. */
data class MapColors(
    val ocean: Color,
    val tones: List<Color>,
    val line: Color,
    val subLine: Color,
    val picked: Color,
    val pickedLine: Color,
    val label: Color,
    val faint: Color,
    val story: Color,
    val book: Color,
    val dim: Color
) {
    companion object {
        val Dark = MapColors(
            ocean = Color(0xFF040706),
            tones = listOf(0xFF1A221D, 0xFF211F19, 0xFF1A1F27, 0xFF231B21, 0xFF1D2419, 0xFF1C1B25, 0xFF221D18).map(::Color),
            line = Color(0xFF3A403B), subLine = Color(0xFF2C312D), picked = Color(0xFF1F2E19), pickedLine = Color(0xFF9CC98A),
            label = Color(0xFFD7DAD4), faint = Color(0xFF858A83), story = Color(0xFFF0937A), book = Color(0xFF9CC98A), dim = Color(0xFF101311)
        )
        val Light = MapColors(
            ocean = Color(0xFFE8F0F3),
            tones = listOf(0xFFEDF1E6, 0xFFF4EEDF, 0xFFE6ECF3, 0xFFF2E6EC, 0xFFE8F0DF, 0xFFEAE7F2, 0xFFF5E9DE).map(::Color),
            line = Color(0xFF9AA39B), subLine = Color(0xFFC3C9C3), picked = Color(0xFFDCEBD3), pickedLine = Color(0xFF3D6B2C),
            label = Color(0xFF2A2E2A), faint = Color(0xFF5E655F), story = Color(0xFFB4492E), book = Color(0xFF3D6B2C), dim = Color(0xFFF1F3F0)
        )
    }
}

/**
 * The world map: Equal Earth, drawn from the Atlas's outlines. Pinch to zoom, drag to move, tap a
 * country (or, with [onTapSub], a subdivision of the picked one). Labels thin out as you zoom out;
 * capitals show from a little closer in. [quiz]: the picked country's subdivisions only, one lit.
 */
@Composable
fun AtlasMap(
    world: AtlasWorld,
    paths: List<Path>,
    regions: AtlasRegions?,
    regionPaths: List<Path>,
    picked: String?,
    pickedSub: Int?,
    pins: Map<String, Pin>,
    labels: String,
    colors: MapColors,
    frame: Frame?,
    modifier: Modifier = Modifier,
    quiz: Boolean = false,
    onTap: (String?) -> Unit = {},
    onTapSub: ((Int) -> Unit)? = null
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer(cacheSize = 300)

    fun fit(box: FloatArray, margin: Float = 0.86f) {
        if (size.width == 0 || size.height == 0) return
        val bw = max(box[2] - box[0], 0.002f)
        val bh = max(box[3] - box[1], 0.002f)
        val s = min(size.width * margin / bw, size.height * margin / bh).coerceIn(minScale(size), maxScale(size))
        scale = s
        pan = Offset(size.width / 2f - (box[0] + box[2]) / 2 * s, size.height / 2f - (box[1] + box[3]) / 2 * s)
    }

    LaunchedEffect(size, frame?.nonce) {
        if (size.width == 0) return@LaunchedEffect
        when {
            frame != null -> fit(frame.box)
            scale == 0f -> fit(EqualEarth.boxOf(-180.0, -58.0, 180.0, 84.0), 0.98f)
        }
    }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .semantics { contentDescription = "World map" + (picked?.let { a3 -> ", " + (world.byA3[a3]?.english ?: "") + " picked" } ?: "") }
            .pointerInput(world) {
                detectTransformGestures { centroid, delta, zoom, _ ->
                    if (scale == 0f) return@detectTransformGestures
                    val next = (scale * zoom).coerceIn(minScale(size), maxScale(size))
                    pan = centroid - (centroid - pan) * (next / scale) + delta
                    scale = next
                }
            }
            .pointerInput(world, paths, regions, onTapSub != null) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        val next = (scale * 2f).coerceIn(minScale(size), maxScale(size))
                        pan = p - (p - pan) * (next / scale)
                        scale = next
                    },
                    onTap = { p ->
                        if (scale == 0f) return@detectTapGestures
                        val x = (p.x - pan.x) / scale
                        val y = (p.y - pan.y) / scale
                        val r = regions
                        if (onTapSub != null && r != null) {
                            val hit = r.outlines.indices.filter { inBox(r.outlines[it].bounds, x, y) && EqualEarth.contains(r.outlines[it].rings, x, y) }
                                .minByOrNull { r.outlines[it].areaKm2 }
                            if (hit != null) {
                                onTapSub(hit)
                                return@detectTapGestures
                            }
                        }
                        val hit = world.outlines.indices.filter { inBox(world.outlines[it].bounds, x, y) && EqualEarth.contains(world.outlines[it].rings, x, y) }
                            .minByOrNull { world.outlines[it].areaKm2 }
                        onTap(hit?.let { world.countries[it].a3 })
                    }
                )
            }
    ) {
        drawRect(colors.ocean)
        if (scale == 0f || paths.size != world.countries.size) return@Canvas
        val k = scale / minScale(size) // 1.25 at the whole world, more as you zoom in
        withTransform({
            translate(pan.x, pan.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val hair = Stroke(width = 0.7f / scale)
            for (i in paths.indices) {
                val c = world.countries[i]
                if (!visible(world.outlines[i].bounds, pan, scale)) continue
                val fill = when {
                    quiz && c.a3 != picked -> colors.dim
                    c.a3 == picked -> colors.picked
                    else -> colors.tones[(c.color - 1).coerceIn(0, colors.tones.size - 1)]
                }
                drawPath(paths[i], fill)
                drawPath(paths[i], colors.line, style = hair)
            }
            if (regions != null && regions.a3 == picked && regionPaths.size == regions.outlines.size) {
                val thin = Stroke(width = 0.6f / scale)
                regionPaths.forEachIndexed { i, p ->
                    if (i == pickedSub) drawPath(p, colors.pickedLine.copy(alpha = if (quiz) 0.85f else 0.35f))
                    drawPath(p, colors.subLine, style = thin)
                }
            }
            world.indexOf[picked ?: ""]?.let { i -> drawPath(paths[i], colors.pickedLine, style = Stroke(width = 1.6f / scale)) }
        }
        if (!quiz) {
            drawLabels(world, regions, picked, labels, colors, measurer, k, pan, scale)
            drawPins(world, pins, colors, pan, scale)
        }
    }
}

private fun inBox(b: FloatArray, x: Float, y: Float) = x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]

/** The whole world fills the view at this scale (a little less is allowed). */
private fun minScale(size: IntSize): Float {
    if (size.width == 0) return 1f
    return min(size.width / (2 * EqualEarth.HALF_WIDTH).toFloat(), size.height / 2.64f) * 0.8f
}

private fun maxScale(size: IntSize): Float = minScale(size) * 120f

/** Inside the current view: [b] is a projected box, mapped to the screen by [pan] and [scale]. */
private fun DrawScope.visible(b: FloatArray, pan: Offset, scale: Float): Boolean =
    pan.x + b[2] * scale >= 0 && pan.x + b[0] * scale <= size.width && pan.y + b[3] * scale >= 0 && pan.y + b[1] * scale <= size.height

private fun screen(lon: Double, lat: Double, pan: Offset, scale: Float) =
    Offset(pan.x + EqualEarth.x(lon, lat).toFloat() * scale, pan.y + EqualEarth.y(lat).toFloat() * scale)

/** Today's stories (coral) and your books (moss): a ring at the country's label point. */
private fun DrawScope.drawPins(world: AtlasWorld, pins: Map<String, Pin>, colors: MapColors, pan: Offset, scale: Float) {
    val r = 6.dp2px(this)
    for ((a3, pin) in pins) {
        val c = world.byA3[a3] ?: continue
        if (c.label.size < 2) continue
        val at = screen(c.label[0], c.label[1], pan, scale)
        if (at.x < -r || at.y < -r || at.x > size.width + r || at.y > size.height + r) continue
        val color = if (pin.stories > 0) colors.story else colors.book
        drawCircle(color, radius = r, center = at, style = Stroke(width = 1.6f * density))
        drawCircle(color, radius = r / 3, center = at)
        if (pin.stories > 0 && pin.books > 0) drawCircle(colors.book, radius = r + 3 * density, center = at, style = Stroke(width = 1.2f * density))
    }
}

private fun Int.dp2px(scope: DrawScope): Float = this * scope.density

/**
 * Country names (fewer as you zoom out: Natural Earth's label rank decides), capitals from a
 * little closer, and the picked country's subdivisions. A label that would overlap one already
 * placed is left out.
 */
private fun DrawScope.drawLabels(
    world: AtlasWorld,
    regions: AtlasRegions?,
    picked: String?,
    lang: String,
    colors: MapColors,
    measurer: TextMeasurer,
    k: Float,
    pan: Offset,
    scale: Float
) {
    val placed = ArrayList<Rect>()
    fun place(text: String, at: Offset, style: TextStyle, above: Boolean = false): Boolean {
        val layout = measurer.measure(text, style)
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val left = at.x - w / 2
        val top = if (above) at.y - h - 3 * density else at.y - h / 2
        if (left < 0 || top < 0 || left + w > size.width || top + h > size.height) return false
        val rect = Rect(left - 2, top - 1, left + w + 2, top + h + 1)
        if (placed.any { it.overlaps(rect) }) return false
        placed += rect
        drawText(layout, topLeft = Offset(left, top))
        return true
    }
    val rankLimit = when {
        k < 1.6f -> 2
        k < 2.6f -> 3
        k < 4f -> 4
        k < 7f -> 5
        k < 12f -> 6
        else -> 9
    }
    val countryStyle = TextStyle(color = colors.label, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
    val pickedStyle = countryStyle.copy(color = colors.pickedLine)
    val capitalStyle = TextStyle(color = colors.faint, fontSize = 11.sp)
    val subStyle = TextStyle(color = colors.faint, fontSize = 10.sp)

    // The picked country first, so it always gets its label.
    val order = world.countries.sortedWith(compareBy({ it.a3 != picked }, { it.labelRank }))
    for (c in order) {
        if (c.label.size < 2) continue
        if (c.a3 != picked && c.labelRank > rankLimit) continue
        val name = AtlasFacts.label(c.english, c.names, lang).let { if (lang == "en") it.uppercase() else it }
        place(name, screen(c.label[0], c.label[1], pan, scale), if (c.a3 == picked) pickedStyle else countryStyle)
    }
    if (k >= 2.2f) {
        val dot = 2.2f * density
        for (p in world.capitals) {
            val at = screen(p.lon, p.lat, pan, scale)
            if (at.x < 0 || at.y < 0 || at.x > size.width || at.y > size.height) continue
            drawCircle(colors.faint, radius = dot, center = at)
            val rank = world.byA3[p.a3]?.labelRank ?: 9
            if (k >= 4f && rank <= rankLimit) place(AtlasFacts.label(p.name, p.names, lang), at, capitalStyle, above = true)
        }
    }
    if (regions != null && regions.a3 == picked && k >= 2.5f) {
        regions.subdivisions.forEach { s ->
            val l = s.label ?: return@forEach
            place(AtlasQuiz.subName(s, lang), screen(l[0], l[1], pan, scale), subStyle)
        }
    }
}
