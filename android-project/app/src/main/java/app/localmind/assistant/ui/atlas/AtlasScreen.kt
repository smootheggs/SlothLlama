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

private enum class AtlasPage { MAP, COUNTRY, SEARCH, QUIZ }

internal val SERIF = FontFamily.Serif

/**
 * The Atlas: a world map on the tablet, its countries, their subdivisions and flags, a search
 * across them, and quizzes. English names always; the map's labels can be in a tutor's language.
 * The white map is a switch of its own, whatever the app's theme.
 */
@Composable
fun AtlasScreen(viewModel: AtlasViewModel, onBack: () -> Unit) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val outer = LocalSlothColors.current
    val colors = if (prefs.light) LightColors else outer
    // The status bar's icons follow the page: dark on the white map.
    val view = LocalView.current
    DisposableEffect(prefs.light, outer.isDark) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { WindowInsetsControllerCompat(it, view) }
        val before = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = !colors.isDark
        onDispose { if (before != null) bars?.isAppearanceLightStatusBars = before }
    }
    SlothTheme(colors) {
        AtlasContent(viewModel, onBack, mapColors = if (colors.isDark) MapColors.Dark else MapColors.Light)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AtlasContent(vm: AtlasViewModel, onBack: () -> Unit, mapColors: MapColors) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val world by vm.world.collectAsStateWithLifecycle()
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
                    subtitle = "On the tablet · Natural Earth · borders as held on the ground",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { page = AtlasPage.SEARCH }) { Icon(Icons.Outlined.Search, contentDescription = "Search places", tint = P.Text) }
                        IconButton(onClick = { page = AtlasPage.QUIZ }) { Icon(Icons.Outlined.Quiz, contentDescription = "Quiz", tint = P.Text) }
                        IconButton(onClick = { vm.setLight(!prefs.light) }) {
                            Icon(
                                if (prefs.light) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                contentDescription = if (prefs.light) "Dark map" else "White map", tint = P.Text
                            )
                        }
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
                    Spacer(Modifier.width(12.dp))
                    AtlasFacts.REGIONS.forEach { r -> Pill(r, selected = region == r, quiet = true, small = true) { vm.showRegion(r) } }
                }
                if (w == null) {
                    Text("Opening the map…", color = P.Faint, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp)) {
                        val wide = maxWidth >= WIDE
                        val map: @Composable (Modifier) -> Unit = { m ->
                            Box(m.clip(RoundedCornerShape(18.dp)).border(1.dp, P.Border, RoundedCornerShape(18.dp))) {
                                AtlasMap(
                                    w, paths, regions, regionPaths, picked, pickedSub, if (places) pins else emptyMap(), prefs.labels, mapColors, frame,
                                    modifier = Modifier.fillMaxSize(),
                                    onTap = { a3 -> vm.pick(a3) },
                                    onTapSub = { i -> vm.pickSub(if (i == pickedSub) null else i) }
                                )
                                if (places) PlacesKey(mapColors, Modifier.align(Alignment.BottomStart).padding(12.dp))
                            }
                        }
                        val card: @Composable (Modifier) -> Unit = { m ->
                            val i = info
                            if (i != null && i.country.a3 == picked) {
                                CountryCard(i, w, regions?.takeIf { it.a3 == picked }?.subdivisions?.getOrNull(pickedSub ?: -1), prefs.labels, vm, m) { page = AtlasPage.COUNTRY }
                            } else if (wide) {
                                Column(m.clip(RoundedCornerShape(22.dp)).background(P.Card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Tap a country", color = P.Text, fontSize = 18.sp, fontFamily = SERIF)
                                    Text(
                                        "Pinch to zoom, drag to move, double-tap to come closer. Its card shows here: flag, names, capital, subdivisions and neighbours, with its stories in today's edition and your books set there.",
                                        color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                        if (wide) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                map(Modifier.weight(1f).fillMaxHeight())
                                card(Modifier.width(340.dp).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                map(Modifier.fillMaxWidth().weight(1f))
                                card(Modifier.fillMaxWidth().heightIn(max = 380.dp))
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

/** The picked country beside (or under) the map. */
@Composable
private fun CountryCard(
    info: CountryInfo,
    world: AtlasWorld,
    sub: app.localmind.assistant.domain.atlas.AtlasSubdivision?,
    lang: String,
    vm: AtlasViewModel,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    val c = info.country
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(P.Card).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Flag(vm, c.flag, 60.dp, "Flag of ${c.english}")
            Column(Modifier.padding(start = 14.dp)) {
                Text(c.english, color = P.Text, fontSize = 22.sp, fontFamily = SERIF, lineHeight = 26.sp)
                Text(listOf(c.type.takeIf { it != "Sovereign country" && it != "Country" }.orEmpty(), c.subregion).filter { it.isNotBlank() }.joinToString(" · "), color = P.Faint, fontSize = 12.sp)
            }
        }
        val names = localNames(c, lang)
        if (names.isNotBlank()) Text(names, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.reading.isNotBlank()) Text(info.reading, color = P.Faint, fontSize = 12.sp)
        sub?.let { s ->
            Fact("Picked", s.english, listOfNotNull(s.type.ifBlank { null }, s.names.zh ?: s.names.ja, s.reading.ifBlank { null }).joinToString(" · "))
        }
        c.capital?.let { cap ->
            Fact(if (cap.seat == "capital") "Capital" else "Main city", cap.name, listOfNotNull(cap.names.of(lang), cap.names.zh.takeIf { lang == "en" }).distinct().joinToString(" · "))
        }
        if (info.subdivisionCount > 0) Fact("Subdivisions", info.subdivisionCount.toString(), info.subdivisions)
        if (info.neighbours.isNotEmpty() || info.alsoBorders.isNotEmpty()) {
            Fact(
                "Neighbours", if (info.neighbours.isEmpty()) "None by land" else "${info.neighbours.size} by land",
                (info.neighbours.map { it.english } + info.alsoBorders.map { "${it.english} (${it.sov})" }).joinToString(", ")
            )
        }
        Fact("Time", info.time)
        if (info.stories.isNotEmpty()) Fact("Today", "${info.stories.size} in the edition", info.stories.take(2).joinToString(" · "))
        if (info.books.isNotEmpty()) Fact("Your books", "${info.books.size} set here", info.books.take(3).joinToString(" · "))
        Row(Modifier.padding(top = 6.dp)) {
            FilledButton("Open ${c.name}", onClick = onOpen)
            QuietButton("Close", muted = true) { vm.pick(null) }
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
            val two = maxWidth > 520.dp
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, option ->
                    val chosen = quiz.chosen
                    val isRight = chosen != null && i == q.answer
                    val isWrong = chosen == i && i != q.answer
                    Box(
                        Modifier
                            .width(if (two) (maxWidth - 10.dp) / 2 else maxWidth)
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
