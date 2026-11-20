package app.localmind.assistant.ui.atlas

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.atlas.AtlasWorld
import app.localmind.assistant.domain.atlas.AtlasFacts
import app.localmind.assistant.domain.atlas.AtlasQuiz
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton

/** Which tutor a country's chat goes to, by badge (null: the assistant). */
private fun tutorBadge(id: String?): String? = when (id) {
    "persona-mandarin-partner" -> "普"
    "persona-cantonese-partner" -> "粵"
    "persona-japanese-partner" -> "日"
    "persona-french-partner" -> "Fr"
    "persona-german-partner" -> "De"
    "persona-spanish-partner" -> "Es"
    "persona-italian-partner" -> "It"
    else -> null
}

/**
 * A country: its flag and names (English first, then the tutors' languages), its subdivisions on a
 * map of their own and in a list, its facts, and where it turns up in today's edition and your
 * books. A tap on a neighbour opens that one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountryPage(info: CountryInfo, world: AtlasWorld, vm: AtlasViewModel, colors: MapColors, onBack: () -> Unit, onQuiz: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val paths by vm.paths.collectAsStateWithLifecycle()
    val regions by vm.regions.collectAsStateWithLifecycle()
    val regionPaths by vm.regionPaths.collectAsStateWithLifecycle()
    val pickedSub by vm.pickedSub.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val c = info.country
    val lang = prefs.labels
    val subs = regions?.takeIf { it.a3 == c.a3 }?.subdivisions.orEmpty()
    LaunchedEffect(c.a3) { vm.framePicked() }

    SlothTopBar(title = c.english, subtitle = c.formal.takeIf { it.isNotBlank() && it != c.english } ?: "Country card", onBack = onBack)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE
        val pad = if (wide) 36.dp else 20.dp
        val header: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Flag(vm, c.flag, 112.dp, "Flag of ${c.english}")
                Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.english, color = P.Text, fontSize = 32.sp, lineHeight = 36.sp, fontFamily = SERIF)
                    Text(listOf(c.type, c.subregion, c.continent).filter { it.isNotBlank() }.distinct().joinToString(" · "), color = P.Faint, fontSize = 13.sp)
                }
            }
            NamesBlock(c, info.reading)
        }
        val map: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth().height(if (wide) 440.dp else 340.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, P.Border, RoundedCornerShape(18.dp))) {
                AtlasMap(
                    world, paths, regions, regionPaths, c.a3, pickedSub, emptyMap(), lang, colors, frame,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { a3 -> if (a3 != null && a3 != c.a3) vm.pick(a3, frame = true) },
                    onTapSub = { i -> vm.pickSub(if (i == pickedSub) null else i) }
                )
            }
            subs.getOrNull(pickedSub ?: -1)?.let { s ->
                Text(
                    listOfNotNull(s.english, s.names.zh ?: s.names.ja, s.reading.ifBlank { null }, s.type.ifBlank { null }).joinToString(" · "),
                    color = P.Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "Borders as held on the ground (Natural Earth). Tap a subdivision for its names.",
                color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }
        val facts: @Composable () -> Unit = {
            c.capital?.let { cap ->
                Fact(
                    if (cap.seat == "capital") "Capital" else "Main city", cap.name,
                    listOfNotNull(cap.names.zh, cap.names.ja, cap.names.fr, cap.names.de, cap.names.es, cap.names.it).distinct().filter { it != cap.name }.take(4).joinToString(" · ")
                )
            }
            Fact("Population", AtlasFacts.population(c), "Natural Earth's estimate")
            Fact("Area", info.area, "worked out from the map's borders")
            Fact("Time", info.time, c.tz)
            Fact("Income group", c.income, "World Bank, as Natural Earth gives it")
            if (subs.isNotEmpty()) Fact("Subdivisions", subs.size.toString(), info.subdivisions)
            if (info.neighbours.isNotEmpty() || info.alsoBorders.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(bottom = 6.dp))
                    Text(
                        if (info.neighbours.isEmpty()) "Neighbours: none by land" else "Neighbours: ${info.neighbours.size} by land",
                        color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        info.neighbours.forEach { n -> Pill(n.english, selected = false, small = true) { vm.pick(n.a3, frame = true) } }
                        info.alsoBorders.forEach { n -> Pill("${n.english} (${n.sov})", selected = false, quiet = true, small = true) { vm.pick(n.a3, frame = true) } }
                    }
                }
            }
            if (info.stories.isNotEmpty()) Fact("Today", "${info.stories.size} in the edition", info.stories.take(3).joinToString(" · "))
            if (info.books.isNotEmpty()) Fact("Your books", "${info.books.size} set here", info.books.take(5).joinToString(" · "))
            val tutor = AtlasFacts.tutor(c.a3)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledButton(tutorBadge(tutor)?.let { "Talk about it with $it" } ?: "Ask about it") { vm.talkAbout(info) }
                if (subs.size >= 4) QuietButton("Quiz me on its ${subs.firstOrNull()?.type?.lowercase()?.let { if (it.isBlank()) "subdivisions" else it + "s" } ?: "subdivisions"}", onClick = onQuiz)
            }
        }
        val list: @Composable () -> Unit = {
            if (subs.isNotEmpty()) {
                Text(
                    "${subs.size} ${if (subs.size == 1) "SUBDIVISION" else "SUBDIVISIONS"}", color = P.Faint, fontSize = 12.sp,
                    letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                )
                subs.withIndex().sortedBy { it.value.english }.forEach { (i, s) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.pickSub(i) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.english, color = if (i == pickedSub) P.Accent else P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        val local = AtlasQuiz.subName(s, if (lang == "en") "zh" else lang).takeIf { it != s.english }
                            ?: s.names.ja?.takeIf { c.a3 == "JPN" }
                        local?.let { Text(it, color = P.Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp)) }
                        if (s.reading.isNotBlank()) Text(s.reading, color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                        Text(s.type.lowercase(), color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp).width(110.dp))
                    }
                    HorizontalDivider(color = P.Border)
                }
            }
            Text(
                "Natural Earth (public domain) for borders, names, capitals and population · flags: flag-icons (MIT).",
                color = P.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp)
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header()
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1.2f)) { map() }
                    Column(Modifier.weight(1f)) { facts() }
                }
            } else {
                map()
                facts()
            }
            list()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** English first (the page's title), then the names in the tutors' languages, each marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamesBlock(c: app.localmind.assistant.domain.atlas.AtlasCountry, reading: String) {
    val rows = listOf(
        "中文" to listOfNotNull(c.names.zh, reading.ifBlank { null }).joinToString("  "),
        "繁體" to c.names.zht.orEmpty().takeIf { it != c.names.zh }.orEmpty(),
        "日本語" to c.names.ja.orEmpty(),
        "Fr" to c.names.fr.orEmpty(),
        "De" to c.names.de.orEmpty(),
        "Es" to c.names.es.orEmpty(),
        "It" to c.names.it.orEmpty()
    ).filter { it.second.isNotBlank() }
    if (rows.isEmpty()) return
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (tag, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tag, color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                Text(name, color = P.Muted, fontSize = 15.sp)
            }
        }
    }
}
