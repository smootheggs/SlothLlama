package app.localmind.assistant.ui.atlas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.atlas.AtlasWorld
import app.localmind.assistant.domain.atlas.AtlasCountry
import app.localmind.assistant.domain.atlas.AtlasFacts
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.LightColors
import app.localmind.assistant.ui.theme.LocalSlothColors
import app.localmind.assistant.ui.theme.SlothTheme
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton
import app.localmind.assistant.ui.theme.SlothColors
import app.localmind.assistant.ui.theme.OledColors
import app.localmind.assistant.data.atlas.AtlasStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.selection.selectable

private enum class AtlasPage { MAP, COUNTRY, SEARCH, QUIZ }

internal val SERIF = FontFamily.Serif

/**
 * Paper: the Atlas's old-atlas look, cream and sepia. Contrast on the cream: text 11.9:1,
 * muted 7.0:1, faint 5.0:1, the sepia-red accent 7.3:1.
 */
internal val PaperColors = SlothColors(
    isDark = false,
    canvas = Color(0xFFF1E8D4),
    surface = Color(0xFFF8F1E1),
    elevated = Color(0xFFEFE4CB),
    border = Color(0xFFD6C8AB),
    borderStrong = Color(0xFFBFAF8F),
    text = Color(0xFF3B2D1E),
    muted = Color(0xFF5E4C38),
    faint = Color(0xFF6E5C47),
    accent = Color(0xFF8A3B22),
    onAccent = Color(0xFFFFF8EE),
    accentSoft = Color(0xFFEFD9C6),
    success = Color(0xFF2F6B3A),
    warning = Color(0xFF9A5B0B),
    danger = Color(0xFFB42318),
    dangerSoft = Color(0xFFF6DCD3),
    codeBackground = Color(0xFFEFE4CB),
    codeText = Color(0xFF3B2D1E),
    scrim = Color(0x66000000),
    lobster = Color(0xFFB4492E)
)

/**
 * The Atlas: a world map on the tablet, its countries, their subdivisions and flags, a search
 * across them, and quizzes. English names always; the map's labels can be in a tutor's language.
 * Its look is its own, whatever the app's theme: Night, White or Paper, with or without the
 * shaded relief.
 */
@Composable
fun AtlasScreen(viewModel: AtlasViewModel, onBack: () -> Unit) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val outer = LocalSlothColors.current
    val colors = when (prefs.style) {
        AtlasStyle.NIGHT -> if (outer.isDark) outer else OledColors
        AtlasStyle.WHITE -> LightColors
        AtlasStyle.PAPER -> PaperColors
    }
    // The status bar's icons follow the page: dark on White and Paper.
    val view = LocalView.current
    DisposableEffect(prefs.style, outer.isDark) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { WindowInsetsControllerCompat(it, view) }
        val before = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = !colors.isDark
        onDispose { if (before != null) bars?.isAppearanceLightStatusBars = before }
    }
    SlothTheme(colors) {
        AtlasContent(viewModel, onBack, mapColors = MapColors.of(prefs.style))
    }
}

/** Night, White or Paper: three small toggles in the top bar. */
@Composable
private fun LookSwitch(style: AtlasStyle, onPick: (AtlasStyle) -> Unit) {
    Row(
        Modifier.padding(end = 4.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, P.Border, RoundedCornerShape(20.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AtlasStyle.entries.forEach { s ->
            val on = s == style
            Box(
                Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) P.AccentSoft else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onPick(s) }
                    .semantics { contentDescription = "${s.label} map" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (s) {
                        AtlasStyle.NIGHT -> Icons.Outlined.DarkMode
                        AtlasStyle.WHITE -> Icons.Outlined.LightMode
                        AtlasStyle.PAPER -> Icons.Outlined.Description
                    },
                    contentDescription = null, tint = if (on) P.Accent else P.Muted, modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AtlasContent(vm: AtlasViewModel, onBack: () -> Unit, mapColors: MapColors) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val world by vm.world.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val paths by vm.paths.collectAsStateWithLifecycle()
    val picked by vm.picked.collectAsStateWithLifecycle()
    val regions by vm.regions.collectAsStateWithLifecycle()
    val regionPaths by vm.regionPaths.collectAsStateWithLifecycle()
    val pickedSub by vm.pickedSub.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val region by vm.region.collectAsStateWithLifecycle()
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    val relief by vm.relief.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(AtlasPage.MAP) }
    var labelsMenu by remember { mutableStateOf(false) }

    BackHandler(page != AtlasPage.MAP) {
        if (page == AtlasPage.QUIZ) vm.endQuiz()
        page = AtlasPage.MAP
    }

    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        val w = world
        when (page) {
            AtlasPage.MAP -> {
                SlothTopBar(
                    title = "Atlas",
                    subtitle = "Natural Earth · borders as held on the ground",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { page = AtlasPage.SEARCH }) { Icon(Icons.Outlined.Search, contentDescription = "Search places", tint = P.Text) }
                        IconButton(onClick = { page = AtlasPage.QUIZ }) { Icon(Icons.Outlined.Quiz, contentDescription = "Quiz", tint = P.Text) }
                        LookSwitch(prefs.style) { vm.setStyle(it) }
                    }
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Pill("Your places", selected = places, quiet = true, small = true) { vm.setPlaces(!places) }
                    Box {
                        Pill("Names: " + (AtlasFacts.LABELS.firstOrNull { it.first == prefs.labels }?.second ?: "English"), selected = prefs.labels != "en", quiet = true, small = true) { labelsMenu = true }
                        DropdownMenu(expanded = labelsMenu, onDismissRequest = { labelsMenu = false }) {
                            AtlasFacts.LABELS.forEach { (code, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { vm.setLabels(code); labelsMenu = false })
                            }
                        }
                    }
                    Pill(if (prefs.relief) "Relief on" else "Relief off", selected = prefs.relief, quiet = true, small = true) { vm.setRelief(!prefs.relief) }
                    Spacer(Modifier.width(12.dp))
                    AtlasFacts.REGIONS.forEach { r -> Pill(r, selected = region == r, quiet = true, small = true) { vm.showRegion(r) } }
                }
                if (w == null) {
                    Text(
                        failed?.let { "The map's data didn't open: $it" } ?: "Opening the map…",
                        color = if (failed != null) P.Danger else P.Faint, fontSize = 14.sp, modifier = Modifier.padding(24.dp)
                    )
                } else {
                    // The map edge to edge, and under it (beside it on a wide screen) the picked
                    // country's card, or the world's: search, your places and the quizzes.
                    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp)) {
                        val wide = maxWidth >= WIDE
                        val mapHeight = maxHeight * 0.5f
                        val map: @Composable (Modifier) -> Unit = { m ->
                            Box(m) {
                                AtlasMap(
                                    w, paths, regions, regionPaths, picked, pickedSub, if (places) pins else emptyMap(), prefs.labels, mapColors, frame,
                                    modifier = Modifier.fillMaxSize(),
                                    relief = if (prefs.relief) relief else null,
                                    onTap = { a3 -> vm.pick(a3) },
                                    onTapSub = { i -> vm.pickSub(if (i == pickedSub) null else i) }
                                )
                                if (places) PlacesKey(mapColors, Modifier.align(Alignment.TopStart).padding(12.dp))
                            }
                        }
                        val panel: @Composable (Modifier, Int) -> Unit = { m, columns ->
                            Column(m.background(P.Card).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
                                val i = info
                                if (i != null && i.country.a3 == picked) {
                                    CountryPanel(
                                        i, regions?.takeIf { it.a3 == picked }?.subdivisions?.getOrNull(pickedSub ?: -1), prefs.labels, vm, columns,
                                        onOpen = { page = AtlasPage.COUNTRY },
                                        onQuiz = { vm.startQuiz("subdivision"); page = AtlasPage.QUIZ }
                                    )
                                } else {
                                    WorldPanel(
                                        region, places, columns, vm,
                                        onSearch = { page = AtlasPage.SEARCH },
                                        onQuiz = { kind -> if (kind != null) vm.startQuiz(kind, region); page = AtlasPage.QUIZ }
                                    )
                                }
                            }
                        }
                        if (wide) {
                            Row(Modifier.fillMaxSize()) {
                                map(Modifier.weight(1f).fillMaxHeight())
                                Box(Modifier.width(1.dp).fillMaxHeight().background(P.Border))
                                panel(Modifier.width(420.dp).fillMaxHeight(), 2)
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                map(Modifier.fillMaxWidth().height(mapHeight))
                                HorizontalDivider(color = P.Border)
                                panel(Modifier.fillMaxWidth().weight(1f), 3)
                            }
                        }
                    }
                }
            }
            AtlasPage.COUNTRY -> {
                val i = info
                if (w == null || i == null) {
                    LaunchedEffect(Unit) { page = AtlasPage.MAP }
                } else {
                    CountryPage(i, w, vm, mapColors, onBack = { page = AtlasPage.MAP }, onQuiz = { vm.startQuiz("subdivision"); page = AtlasPage.QUIZ })
                }
            }
            AtlasPage.SEARCH -> AtlasSearch(vm, onBack = { page = AtlasPage.MAP }) { a3 ->
                vm.focus(a3)
                page = AtlasPage.MAP
            }
            AtlasPage.QUIZ -> {
                if (w != null) AtlasQuizPage(vm, w, quiz, mapColors, onBack = { vm.endQuiz(); page = AtlasPage.MAP })
            }
        }
    }
}

@Composable
private fun PlacesKey(colors: MapColors, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(P.Canvas.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(colors.story)
        Text("In today's edition", color = P.Muted, fontSize = 12.sp)
        Dot(colors.book)
        Text("Books set there", color = P.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(10.dp).border(1.6.dp, color, RoundedCornerShape(5.dp)))
}

/** A flag from the Atlas's own PNGs; a quiet box while it loads or where there's none. */
@Composable
internal fun Flag(vm: AtlasViewModel, code: String, width: Dp, label: String) {
    val bitmap by produceState<ImageBitmap?>(null, code) { value = if (code.isBlank()) null else vm.flag(code)() }
    val height = width * 0.75f
    val b = bitmap
    if (b != null) {
        Image(
            b, contentDescription = label, contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(width, height).clip(RoundedCornerShape(4.dp)).border(1.dp, P.Border, RoundedCornerShape(4.dp))
        )
    } else {
        Box(Modifier.size(width, height).clip(RoundedCornerShape(4.dp)).background(P.Elevated))
    }
}

@Composable
internal fun Fact(name: String, value: String, small: String = "") {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        HorizontalDivider(color = P.Border, modifier = Modifier.padding(bottom = 6.dp))
        Row {
            Text(name, color = P.Faint, fontSize = 13.sp, modifier = Modifier.width(104.dp))
            Column(Modifier.weight(1f)) {
                Text(value, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp)
                if (small.isNotBlank()) Text(small, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** The names in the chosen label language (and the tutors' others), English first. */
internal fun localNames(c: AtlasCountry, lang: String): String {
    val first = if (lang != "en") c.names.of(lang) else null
    val rest = listOfNotNull(c.names.zh, c.names.ja, c.names.fr, c.names.de, c.names.es, c.names.it).filter { it != first && it != c.english }.distinct()
    return (listOfNotNull(first) + rest).take(4).joinToString(" · ")
}

/** A small fact: its name in capitals, the value, and a line about it. */
@Composable
private fun FactTile(name: String, value: String, note: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(P.Elevated).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(name.uppercase(), color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        Text(value, color = P.Text, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        if (note.isNotBlank()) Text(note, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text.uppercase(), color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/**
 * The picked country under (or beside) the map: flag, English name and its others, the facts in
 * small tiles, neighbours to tap through, where it turns up in today's edition and your books,
 * and the way to talk about it, quiz its subdivisions or open its page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountryPanel(
    info: CountryInfo,
    sub: app.localmind.assistant.domain.atlas.AtlasSubdivision?,
    lang: String,
    vm: AtlasViewModel,
    columns: Int,
    onOpen: () -> Unit,
    onQuiz: () -> Unit
) {
    val c = info.country
    Row(verticalAlignment = Alignment.CenterVertically) {
        Flag(vm, c.flag, 64.dp, "Flag of ${c.english}")
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(c.english, color = P.Text, fontSize = 28.sp, lineHeight = 32.sp, fontFamily = SERIF)
            val line = listOf(c.formal.takeIf { it != c.english }.orEmpty(), c.type.takeIf { it != "Sovereign country" && it != "Country" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) Text(line, color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = { vm.pick(null) }) { Icon(Icons.Outlined.Close, contentDescription = "Close the card", tint = P.Muted) }
    }
    val names = localNames(c, lang)
    if (names.isNotBlank()) Text(names, color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
    if (info.reading.isNotBlank()) Text(info.reading, color = P.Faint, fontSize = 13.sp)
    sub?.let { s ->
        Text(
            listOfNotNull(s.english, s.type.ifBlank { null }, s.names.zh ?: s.names.ja, s.reading.ifBlank { null }).joinToString(" · "),
            color = P.Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
    val population = AtlasFacts.population(c)
    val facts = buildList {
        c.capital?.let { cap ->
            add(Triple(if (cap.seat == "capital") "Capital" else "Main city", cap.name, (cap.names.of(lang) ?: cap.names.zh).orEmpty().takeIf { it != cap.name }.orEmpty()))
        }
        if (population.isNotBlank()) add(Triple("Population", population.substringBefore(" ("), if (c.popYear > 0) "Natural Earth's estimate, ${c.popYear}" else "Natural Earth's estimate"))
        add(Triple("Area", info.area.removePrefix("about "), "from the borders"))
        if (info.time.isNotBlank()) add(Triple("Local time", info.time.substringBefore(" · "), info.time.substringAfter(" · ", "")))
        if (info.subdivisionCount > 0) add(Triple("Subdivisions", info.subdivisionCount.toString(), info.subdivisions))
        if (c.subregion.isNotBlank()) add(Triple("Region", c.subregion, c.continent))
    }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (name, value, note) -> FactTile(name, value, note, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (info.neighbours.isNotEmpty() || info.alsoBorders.isNotEmpty()) {
        PanelLabel(
            if (info.neighbours.isEmpty()) "Neighbours by land · none"
            else "Neighbours by land · ${info.neighbours.size}" + if (info.alsoBorders.isNotEmpty()) " and ${info.alsoBorders.joinToString(" and ") { it.english }} apart" else ""
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            info.neighbours.forEach { n -> Pill(n.english, selected = false, small = true) { vm.focus(n.a3) } }
            info.alsoBorders.forEach { n -> Pill(n.english, selected = false, quiet = true, small = true) { vm.focus(n.a3) } }
        }
    } else {
        PanelLabel("Neighbours by land · none")
    }
    if (info.stories.isNotEmpty()) {
        PanelLabel("In today's edition · ${info.stories.size}")
        info.stories.take(3).forEach { t -> Text(t, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = SERIF, modifier = Modifier.padding(bottom = 6.dp)) }
    }
    if (info.books.isNotEmpty()) {
        PanelLabel("Your books set here · ${info.books.size}")
        Text(info.books.take(5).joinToString(" · "), color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
    FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledButton(tutorBadge(AtlasFacts.tutor(c.a3))?.let { "Talk about it with $it" } ?: "Ask about it") { vm.talkAbout(info) }
        if (info.subdivisionCount >= 4) QuietButton("Quiz its subdivisions", onClick = onQuiz)
        QuietButton("Open its page", onClick = onOpen)
    }
}

/**
 * Nothing picked: the world's card. Find a place, pin your places (today's edition and your books),
 * and the quizzes.
 */
@Composable
private fun WorldPanel(region: String, places: Boolean, columns: Int, vm: AtlasViewModel, onSearch: () -> Unit, onQuiz: (String?) -> Unit) {
    Text(if (region == "World") "The world" else region, color = P.Text, fontSize = 28.sp, lineHeight = 32.sp, fontFamily = SERIF)
    Text("Tap a country for its card. Pinch to zoom, drag to move, double-tap to come closer.", color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(P.Elevated)
            .clickable(role = Role.Button, onClickLabel = "Search the Atlas", onClick = onSearch).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = P.Faint)
        Spacer(Modifier.width(10.dp))
        Text("Countries, capitals, provinces · 广东, Bavaria, Lyon", color = P.Faint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    PanelLabel("Your places")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Elevated).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = P.Accent)
        Text(
            "Pins the countries named in today's edition and the ones your books are set in.",
            color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Switch(
            checked = places, onCheckedChange = { vm.setPlaces(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = P.OnAccent, checkedTrackColor = P.Accent,
                uncheckedThumbColor = P.Muted, uncheckedTrackColor = P.Elevated, uncheckedBorderColor = P.BorderStrong
            )
        )
    }
    PanelLabel("Quizzes")
    val quizzes = listOf(
        Triple("Flags", if (region == "World") "From the whole world" else "From $region", "flag"),
        Triple("Capitals", if (region == "World") "From the whole world" else "From $region", "capital"),
        Triple("Subdivisions", "Pick a country, then quiz its provinces or states", null)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        quizzes.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (title, note, kind) ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).border(1.dp, P.Border, RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { onQuiz(kind) }.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(title, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(note, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Search across countries, capitals, cities and subdivisions, in any of the names the Atlas has. */
@Composable
private fun AtlasSearch(vm: AtlasViewModel, onBack: () -> Unit, onPick: (String) -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { vm.search(query) }
    SlothTopBar(title = "Search the Atlas", subtitle = "Countries, capitals, cities, provinces · any language", onBack = onBack)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(P.Elevated).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = P.Faint)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("广东, Lyon, Bavaria, 日本…", color = P.Faint, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                    textStyle = TextStyle(color = P.Text, fontSize = 15.sp), cursorBrush = SolidColor(P.Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(results, key = { it.a3 + "|" + it.title + "|" + it.detail }) { hit ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(hit.a3) }.padding(vertical = 8.dp)
                ) {
                    Text(hit.title, color = P.Text, fontSize = 16.sp)
                    if (hit.detail.isNotBlank()) Text(hit.detail, color = P.Faint, fontSize = 12.sp)
                }
                HorizontalDivider(color = P.Border)
            }
        }
    }
}

/** Quizzes: flags and capitals by region, and the subdivisions of the picked country. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AtlasQuizPage(vm: AtlasViewModel, world: AtlasWorld, quiz: QuizState?, colors: MapColors, onBack: () -> Unit) {
    val region by vm.region.collectAsStateWithLifecycle()
    val picked by vm.picked.collectAsStateWithLifecycle()
    SlothTopBar(title = "Atlas quiz", subtitle = "Asked from the map and its data, on the tablet", onBack = onBack)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Flags", selected = quiz?.kind == "flag", quiet = true, small = true) { vm.startQuiz("flag", region) }
            Pill("Capitals", selected = quiz?.kind == "capital", quiet = true, small = true) { vm.startQuiz("capital", region) }
            Pill(
                "Subdivisions of " + (picked?.let { world.byA3[it]?.name } ?: "China"),
                selected = quiz?.kind == "subdivision", quiet = true, small = true
            ) { vm.startQuiz("subdivision") }
            Spacer(Modifier.width(12.dp))
            AtlasFacts.REGIONS.forEach { r ->
                Pill(r, selected = region == r, quiet = true, small = true) {
                    vm.showRegion(r)
                    quiz?.takeIf { it.kind != "subdivision" }?.let { vm.startQuiz(it.kind, r) }
                }
            }
        }
        val q = quiz?.question
        when {
            quiz == null -> Text("Pick a quiz above: ten questions, the missed ones again at the end.", color = P.Muted, fontSize = 15.sp)
            quiz.questions.isEmpty() -> Text("Not enough here to ask about: try another region or country.", color = P.Muted, fontSize = 15.sp)
            q == null -> {
                Text("${quiz.right} of ${quiz.questions.size} right", color = P.Text, fontSize = 24.sp, fontFamily = SERIF)
                Row {
                    if (quiz.missed.isNotEmpty()) FilledButton("Again, the ${quiz.missed.size} missed") { vm.retryMissed() }
                    QuietButton("A new round") { vm.startQuiz(quiz.kind, quiz.region) }
                }
            }
            else -> QuizQuestion(vm, world, quiz, q, colors)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizQuestion(vm: AtlasViewModel, world: AtlasWorld, quiz: QuizState, q: app.localmind.assistant.domain.atlas.AtlasQuestion, colors: MapColors) {
    val picked by vm.picked.collectAsStateWithLifecycle()
    val regions by vm.regions.collectAsStateWithLifecycle()
    val regionPaths by vm.regionPaths.collectAsStateWithLifecycle()
    val pickedSub by vm.pickedSub.collectAsStateWithLifecycle()
    val paths by vm.paths.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${quiz.at + 1} of ${quiz.questions.size} · ${quiz.right} right", color = P.Faint, fontSize = 12.sp)
        when (q.kind) {
            "flag" -> Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                Flag(vm, world.byA3[q.a3]?.flag.orEmpty(), 240.dp, "A flag to name")
            }
            "subdivision" -> Box(
                Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, P.Border, RoundedCornerShape(18.dp))
            ) {
                AtlasMap(world, paths, regions, regionPaths, picked, pickedSub, emptyMap(), "en", colors, frame, Modifier.fillMaxSize(), quiz = true)
            }
        }
        Text(q.prompt, color = P.Text, fontSize = 20.sp, fontFamily = SERIF)
        BoxWithConstraints {
            val full = maxWidth
            val two = full > 520.dp
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, option ->
                    val chosen = quiz.chosen
                    val isRight = chosen != null && i == q.answer
                    val isWrong = chosen == i && i != q.answer
                    Box(
                        Modifier
                            .width(if (two) (full - 10.dp) / 2 else full)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isRight) P.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (isRight) P.Accent else if (isWrong) P.Danger else P.Border, RoundedCornerShape(16.dp))
                            .clickable(enabled = chosen == null) { vm.answer(i) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(option, color = P.Text, fontSize = 16.sp, fontWeight = if (isRight) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
        if (quiz.chosen != null) {
            val right = quiz.chosen == q.answer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (right) "Right." else "It's ${q.options[q.answer]}.",
                    color = if (right) P.Accent else P.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f)
                )
                FilledButton("Next") { vm.next() }
            }
        }
    }
}
