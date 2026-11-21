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
import app.localmind.assistant.data.atlas.AtlasStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

/**
 * The map's colours, one set per look. Sea and land first; then the coast (a soft halo round the
 * land, or on Paper an old atlas's two water lines), the hairline between countries, labels, and
 * what the picked country, pins and quiz use. [reliefAlpha]: how strongly the shaded relief shows.
 */
data class MapColors(
    val sea: Color,
    val graticule: Color,
    val globe: Color,
    val coast: Color,
    /** Paper's water lines along the coast; null: a soft halo in the coast's colour instead. */
    val waterLines: Color?,
    val border: Color,
    val tones: List<Color>,
    val label: Color,
    val water: Color,
    val faint: Color,
    val capital: Color,
    val picked: Color,
    val pickedLine: Color,
    val subLine: Color,
    val story: Color,
    val book: Color,
    val dim: Color,
    val reliefAlpha: Float
) {
    companion object {
        /** Muted land on an ink-blue sea. */
        val Night = MapColors(
            sea = Color(0xFF0C1822), graticule = Color(0xFF16242F), globe = Color(0xFF22323F), coast = Color(0xFF5E7383), waterLines = null,
            border = Color(0xFF0C1822),
            tones = listOf(0xFF36443A, 0xFF433C2E, 0xFF45362F, 0xFF323C4A, 0xFF3A4433, 0xFF40343F, 0xFF2F4243).map(::Color),
            label = Color(0xFFD6DDD4), water = Color(0xFF7896A8), faint = Color(0xFFA3ADA6), capital = Color(0xFFE8E2CF),
            picked = Color(0xFF44583B), pickedLine = Color(0xFF9CC98A), subLine = Color(0xFF5E7356),
            story = Color(0xFFF0937A), book = Color(0xFF9CC98A), dim = Color(0xFF141E26), reliefAlpha = 0.6f
        )

        /** Pale land on a light sea, flat and quick to read. */
        val White = MapColors(
            sea = Color(0xFFE3EBF0), graticule = Color(0xFFD3DEE5), globe = Color(0xFFB9C6CF), coast = Color(0xFF8C9AA4), waterLines = null,
            border = Color(0xFFFFFFFF),
            tones = listOf(0xFFF4F1E9, 0xFFECF0E4, 0xFFF3EAE1, 0xFFE8ECF2, 0xFFEFF1E6, 0xFFF1E9ED, 0xFFE6EFEC).map(::Color),
            label = Color(0xFF2B333A), water = Color(0xFF557083), faint = Color(0xFF4F5A53), capital = Color(0xFF2B333A),
            picked = Color(0xFFDCE9D2), pickedLine = Color(0xFF3D6B2C), subLine = Color(0xFF9DB08F),
            story = Color(0xFFB4492E), book = Color(0xFF3D6B2C), dim = Color(0xFFEEF2F4), reliefAlpha = 0.55f
        )

        /** An old school atlas: cream land, sepia ink, grey-green seas with water lines. */
        val Paper = MapColors(
            sea = Color(0xFFD9E0D6), graticule = Color(0xFFC6BEA7), globe = Color(0xFF8C7A5E), coast = Color(0xFF6B5640),
            waterLines = Color(0xFF7A8C86), border = Color(0xFFF3EBD8),
            tones = listOf(0xFFEBDFC3, 0xFFE7D7B5, 0xFFEFD8BF, 0xFFE2DBC4, 0xFFE9DFBE, 0xFFEBD5C7, 0xFFDEDCC2).map(::Color),
            label = Color(0xFF4A3826), water = Color(0xFF4F6670), faint = Color(0xFF6E5C47), capital = Color(0xFF4A3826),
            picked = Color(0xFFE9C9AE), pickedLine = Color(0xFF8A3B22), subLine = Color(0xFFB89C7C),
            story = Color(0xFFB4492E), book = Color(0xFF4E6B3A), dim = Color(0xFFE4DCC8), reliefAlpha = 0.7f
        )

        fun of(style: AtlasStyle): MapColors = when (style) {
            AtlasStyle.NIGHT -> Night
            AtlasStyle.WHITE -> White
            AtlasStyle.PAPER -> Paper
        }
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
    /** Natural Earth's shaded relief, projected to Equal Earth; null: flat. */
    relief: ImageBitmap? = null,
    quiz: Boolean = false,
    onTap: (String?) -> Unit = {},
    onTapSub: ((Int) -> Unit)? = null
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer(cacheSize = 300)
    // All the land as one path, for the coast's strokes; and the globe's grid.
    val land = remember(paths) { Path().apply { paths.forEach { addPath(it) } } }
    val grid = remember { gridPaths() }

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
        drawRect(colors.sea)
        if (scale == 0f || paths.size != world.countries.size) return@Canvas
        val k = scale / minScale(size) // 1.25 at the whole world, more as you zoom in
        val dimmed = picked != null && !quiz
        withTransform({
            translate(pan.x, pan.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // The globe's grid (every 30°, every 15° closer in) and its edge.
            drawPath(if (k >= 3f) grid.fine else grid.coarse, colors.graticule, style = Stroke(width = 0.8f / scale))
            drawPath(grid.edge, colors.globe, style = Stroke(width = 1.2f / scale))
            // The coast: an old atlas's water lines on Paper, else a soft halo; then the coastline.
            if (colors.waterLines != null) {
                drawPath(land, colors.waterLines.copy(alpha = 0.16f), style = Stroke(width = 16f / scale, join = StrokeJoin.Round))
                drawPath(land, colors.waterLines.copy(alpha = 0.22f), style = Stroke(width = 7f / scale, join = StrokeJoin.Round))
            } else {
                drawPath(land, colors.coast.copy(alpha = 0.18f), style = Stroke(width = 7f / scale, join = StrokeJoin.Round))
            }
            drawPath(land, colors.coast, style = Stroke(width = 1.6f / scale, join = StrokeJoin.Round))
            val hair = Stroke(width = 0.7f / scale)
            for (i in paths.indices) {
                val c = world.countries[i]
                if (!visible(world.outlines[i].bounds, pan, scale)) continue
                val fill = when {
                    quiz && c.a3 != picked -> colors.dim
                    c.a3 == picked -> colors.picked
                    else -> colors.tones[(c.color - 1).coerceIn(0, colors.tones.size - 1)]
                }
                drawPath(paths[i], fill, alpha = if (dimmed && c.a3 != picked) 0.8f else 1f)
                drawPath(paths[i], colors.border, style = hair)
            }
        }
        if (relief != null && !quiz) drawRelief(relief, colors.reliefAlpha, pan, scale)
        withTransform({
            translate(pan.x, pan.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            if (regions != null && regions.a3 == picked && regionPaths.size == regions.outlines.size) {
                val thin = Stroke(width = 0.7f / scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f / scale, 2.5f / scale)))
                regionPaths.forEachIndexed { i, p ->
                    if (i == pickedSub) drawPath(p, colors.pickedLine.copy(alpha = if (quiz) 0.85f else 0.35f))
                    drawPath(p, colors.subLine, style = thin)
                }
            }
            world.indexOf[picked ?: ""]?.let { i ->
                drawPath(paths[i], colors.pickedLine.copy(alpha = 0.25f), style = Stroke(width = 7f / scale, join = StrokeJoin.Round))
                drawPath(paths[i], colors.pickedLine, style = Stroke(width = 1.8f / scale, join = StrokeJoin.Round))
            }
        }
        if (!quiz) {
            drawLabels(world, regions, picked, labels, colors, measurer, k, pan, scale)
            drawPins(world, pins, colors, pan, scale)
            drawScale(colors, measurer, pan, scale)
        }
    }
}

/** The globe's grid lines and edge, in projection units: drawn under the transform, made once. */
private class Grid(val coarse: Path, val fine: Path, val edge: Path)

private fun gridPaths(): Grid {
    fun lines(step: Int) = Path().apply {
        var lon = -180
        while (lon <= 180) {
            var lat = -90.0
            moveTo(EqualEarth.x(lon.toDouble(), lat).toFloat(), EqualEarth.y(lat).toFloat())
            while (lat < 90.0) {
                lat += 2.0
                lineTo(EqualEarth.x(lon.toDouble(), lat).toFloat(), EqualEarth.y(lat).toFloat())
            }
            lon += step
        }
        var lat = -90 + step
        while (lat < 90) {
            moveTo(EqualEarth.x(-180.0, lat.toDouble()).toFloat(), EqualEarth.y(lat.toDouble()).toFloat())
            var lon2 = -180
            while (lon2 < 180) {
                lon2 += 3
                lineTo(EqualEarth.x(lon2.toDouble(), lat.toDouble()).toFloat(), EqualEarth.y(lat.toDouble()).toFloat())
            }
            lat += step
        }
    }
    val edge = Path().apply {
        var lat = 90.0
        moveTo(EqualEarth.x(-180.0, lat).toFloat(), EqualEarth.y(lat).toFloat())
        while (lat > -90.0) {
            lat -= 1.0
            lineTo(EqualEarth.x(-180.0, lat).toFloat(), EqualEarth.y(lat).toFloat())
        }
        while (lat < 90.0) {
            lat += 1.0
            lineTo(EqualEarth.x(180.0, lat).toFloat(), EqualEarth.y(lat).toFloat())
        }
        close()
    }
    return Grid(lines(30), lines(15), edge)
}

/**
 * The shaded relief over the land: only the part of the image in view, drawn to the screen
 * (a whole 5120-pixel image scaled up a hundred times would be wasted work).
 */
private fun DrawScope.drawRelief(image: ImageBitmap, alpha: Float, pan: Offset, scale: Float) {
    val hw = EqualEarth.HALF_WIDTH.toFloat()
    val hh = EqualEarth.Y_MAX.toFloat()
    val x0 = max(-pan.x / scale, -hw)
    val x1 = min((size.width - pan.x) / scale, hw)
    val y0 = max(-pan.y / scale, -hh)
    val y1 = min((size.height - pan.y) / scale, hh)
    if (x1 <= x0 || y1 <= y0) return
    val iw = image.width
    val ih = image.height
    val sx0 = floor((x0 + hw) / (2 * hw) * iw).toInt().coerceIn(0, iw - 1)
    val sx1 = ceil((x1 + hw) / (2 * hw) * iw).toInt().coerceIn(sx0 + 1, iw)
    val sy0 = floor((y0 + hh) / (2 * hh) * ih).toInt().coerceIn(0, ih - 1)
    val sy1 = ceil((y1 + hh) / (2 * hh) * ih).toInt().coerceIn(sy0 + 1, ih)
    // Back from whole pixels to the screen, so the image sits exactly on the coastlines.
    val left = pan.x + (sx0.toFloat() / iw * 2 * hw - hw) * scale
    val top = pan.y + (sy0.toFloat() / ih * 2 * hh - hh) * scale
    val right = pan.x + (sx1.toFloat() / iw * 2 * hw - hw) * scale
    val bottom = pan.y + (sy1.toFloat() / ih * 2 * hh - hh) * scale
    drawImage(
        image,
        srcOffset = IntOffset(sx0, sy0), srcSize = IntSize(sx1 - sx0, sy1 - sy0),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize((right - left).roundToInt().coerceAtLeast(1), (bottom - top).roundToInt().coerceAtLeast(1)),
        alpha = alpha, filterQuality = FilterQuality.Low
    )
}

/** A scale bar for the middle of the view (bottom left) and the data's credit (bottom right). */
private fun DrawScope.drawScale(colors: MapColors, measurer: TextMeasurer, pan: Offset, scale: Float) {
    val lat = EqualEarth.latOf(((size.height / 2 - pan.y) / scale).toDouble())
    val kmPerPx = EqualEarth.kmPerUnit(lat) / scale
    if (kmPerPx <= 0 || kmPerPx.isNaN()) return
    val nice = listOf(5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000)
        .firstOrNull { it / kmPerPx >= 64 * density } ?: return
    val w = (nice / kmPerPx).toFloat()
    val left = 16 * density
    val base = size.height - 14 * density
    val tick = 5 * density
    val line = Stroke(width = 1.5f * density)
    val bar = Path().apply {
        moveTo(left, base - tick)
        lineTo(left, base)
        lineTo(left + w, base)
        lineTo(left + w, base - tick)
    }
    drawPath(bar, colors.faint, style = line)
    val text = measurer.measure("%,d km".format(java.util.Locale.ENGLISH, nice), TextStyle(color = colors.faint, fontSize = 11.sp))
    drawText(text, topLeft = Offset(left, base - tick - 3 * density - text.size.height))
    val credit = measurer.measure("Natural Earth · Equal Earth", TextStyle(color = colors.faint, fontSize = 10.sp))
    drawText(credit, topLeft = Offset(size.width - credit.size.width - 14 * density, size.height - credit.size.height - 10 * density))
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
 * The lettering, as an atlas sets it: sea names in italics first, then countries in spaced serif
 * capitals (fewer as you zoom out: Natural Earth's label rank decides; the picked one always),
 * capitals as small rings from a little closer, and the picked country's subdivisions. Each sits
 * on a soft halo of the sea's colour so it reads over the relief. A label that would overlap one
 * already placed is left out.
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
    fun place(text: String, at: Offset, style: TextStyle, above: Boolean = false, side: Boolean = false): Boolean {
        val layout = measurer.measure(text, style)
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val left = if (side) at.x + 6 * density else at.x - w / 2
        val top = when {
            above -> at.y - h - 3 * density
            else -> at.y - h / 2
        }
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
    val halo = Shadow(colors.sea.copy(alpha = 0.9f), blurRadius = 4f * density)
    val english = lang == "en"
    fun countryStyle(rank: Int, isPicked: Boolean) = TextStyle(
        color = if (isPicked) colors.pickedLine else colors.label,
        fontFamily = FontFamily.Serif,
        fontSize = (if (rank <= 2) 13f else if (rank <= 3) 12f else 11f).let { if (isPicked) it + 2f else it }.sp,
        letterSpacing = if (english) 1.6.sp else 0.5.sp,
        fontWeight = if (isPicked) FontWeight.SemiBold else FontWeight.Medium,
        shadow = halo
    )
    val oceanStyle = TextStyle(color = colors.water, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 12.sp, letterSpacing = 3.sp, shadow = halo)
    val seaStyle = oceanStyle.copy(fontSize = 11.sp, letterSpacing = 0.6.sp)
    val capitalStyle = TextStyle(color = colors.faint, fontSize = 11.sp, shadow = halo)
    val subStyle = TextStyle(color = colors.faint, fontSize = 10.sp, fontFamily = FontFamily.Serif, shadow = halo)

    // Oceans from far out, seas from a little closer (English names: they're the map's own).
    for (sea in AtlasFacts.SEAS) {
        if (!sea.ocean && k < 2.2f) continue
        if (sea.ocean && k >= 9f) continue
        place(if (sea.ocean) sea.name.uppercase() else sea.name, screen(sea.lon, sea.lat, pan, scale), if (sea.ocean) oceanStyle else seaStyle)
    }
    // The picked country first, so it always gets its label; then its capital.
    picked?.let { world.byA3[it] }?.takeIf { it.label.size >= 2 }?.let { c ->
        val name = AtlasFacts.label(c.english, c.names, lang).let { if (english) it.uppercase() else it }
        place(name, screen(c.label[0], c.label[1], pan, scale), countryStyle(c.labelRank, true))
    }
    val order = world.countries.sortedBy { it.labelRank }
    if (k >= 2.2f) {
        val ring = 3f * density
        val capitals = world.capitals.sortedWith(compareBy({ it.a3 != picked }, { world.byA3[it.a3]?.labelRank ?: 9 }))
        for (p in capitals) {
            val at = screen(p.lon, p.lat, pan, scale)
            if (at.x < 0 || at.y < 0 || at.x > size.width || at.y > size.height) continue
            val rank = world.byA3[p.a3]?.labelRank ?: 9
            if (rank > rankLimit + 1 && p.a3 != picked) continue
            drawCircle(colors.sea, radius = ring, center = at)
            drawCircle(colors.capital, radius = ring, center = at, style = Stroke(width = 1.4f * density))
            if (k >= 3.2f && (rank <= rankLimit || p.a3 == picked)) place(AtlasFacts.label(p.name, p.names, lang), at, capitalStyle, side = true)
        }
    }
    for (c in order) {
        if (c.a3 == picked || c.label.size < 2 || c.labelRank > rankLimit) continue
        val name = AtlasFacts.label(c.english, c.names, lang).let { if (english) it.uppercase() else it }
        place(name, screen(c.label[0], c.label[1], pan, scale), countryStyle(c.labelRank, false))
    }
    if (regions != null && regions.a3 == picked && k >= 2.5f) {
        regions.subdivisions.forEach { s ->
            val l = s.label ?: return@forEach
            place(AtlasQuiz.subName(s, lang), screen(l[0], l[1], pan, scale), subStyle)
        }
    }
}
