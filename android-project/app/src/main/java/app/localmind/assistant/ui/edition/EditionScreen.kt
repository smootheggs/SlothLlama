package app.localmind.assistant.ui.edition

import app.localmind.assistant.ui.reader.Reading
import app.localmind.assistant.ui.reader.ReaderDoc
import app.localmind.assistant.ui.reader.ReaderSource
import app.localmind.assistant.ui.reader.ReaderViewModel
import app.localmind.assistant.data.lobster.LobsterHandOff
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.VerticalDivider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import app.localmind.assistant.domain.edition.EdStory
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.edition.EditionEntry
import app.localmind.assistant.domain.edition.CornerLanguage
import app.localmind.assistant.domain.edition.EdCorner
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.edition.MarketTile
import app.localmind.assistant.domain.edition.SavedEdition
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.reader.ReadAloud
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EditionPage { PAPER, ARCHIVE, SETTINGS }

/**
 * The Sloth Express: the edition the Lobster brought, laid out like a paper (two pages side by
 * side on a wide screen), with its markets strip, the desk, the language corners, the archive and
 * the settings that are sent to the Lobster.
 */
@Composable
fun EditionScreen(
    viewModel: EditionViewModel,
    /** The Express's own reader (not the Reader screen's): a story read clean. */
    reader: ReaderViewModel,
    onBack: () -> Unit,
    onPlace: (String) -> Unit = {}
) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val story by viewModel.story.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val shelved by viewModel.shelved.collectAsStateWithLifecycle()
    val openAsk by viewModel.openAsk.collectAsStateWithLifecycle()
    val readerDoc by reader.doc.collectAsStateWithLifecycle()
    val readerMessage by reader.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(EditionPage.PAPER) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(readerMessage) {
        readerMessage?.let {
            snackbar.showSnackbar(it)
            reader.clearMessage()
        }
    }
    LaunchedEffect(Unit) { viewModel.start() }

    val context = LocalContext.current

    // Reading a story: its page through the Lobster (SlothLlama never goes online), or its clean
    // text here. If the Lobster can't open it, the clean text is the way in when there is one.
    fun readClean(st: EdStory) {
        val ref = viewModel.cleanRef(st)
        if (ref == null) {
            viewModel.say(st.pageOnly.ifBlank { "This one can only be read on its page" }.replaceFirstChar { it.uppercase() })
            return
        }
        viewModel.setRead(st, true)
        page = EditionPage.PAPER
        reader.open(ReaderSource.EXPRESS, ref)
    }
    fun openPage(st: EdStory) {
        viewModel.setRead(st, true)
        val problem = LobsterHandOff.open(context, st.url, Editions.hasClean(st)) ?: return
        if (viewModel.cleanRef(st) != null) {
            readClean(st)
            viewModel.say("$problem. Here's its clean text.")
        } else {
            viewModel.say(problem)
        }
    }
    val actions = StoryActions(
        tap = { st -> if (Editions.tapsClean(setup, st)) readClean(st) else openPage(st) },
        openPage = ::openPage,
        readClean = ::readClean,
        later = viewModel::keepForLater,
        ask = viewModel::askAbout,
        setRead = { st, r -> viewModel.setRead(st, r) },
        setTap = { st, clean -> viewModel.setTap(st, clean) },
        place = { st -> viewModel.places.value[Editions.storyKey(st)]?.let { onPlace(it.a3) } }
    )

    // "Read it clean in SlothLlama" from the Lobster's Chrome tab.
    LaunchedEffect(openAsk) {
        val url = openAsk ?: return@LaunchedEffect
        viewModel.openAnswered()
        val ref = viewModel.cleanRefFor(url)
        if (ref != null) {
            page = EditionPage.PAPER
            reader.open(ReaderSource.EXPRESS, ref)
        } else {
            viewModel.say("That story isn't in a paper here any more")
        }
    }

    // The listen version: Android's own voices, offline, each corner in its language.
    var playing by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val voice = remember { arrayOfNulls<ReadAloud>(1) }
    DisposableEffect(Unit) {
        onDispose { voice[0]?.shutdown() }
    }
    val listen: () -> Unit = {
        val e = shown?.edition
        if (e != null) {
            val aloud = voice[0] ?: ReadAloud(context, onBlock = {}, onEnd = { playing = false; paused = false }).also { voice[0] = it }
            if (playing) {
                aloud.pause()
                playing = false
                paused = true
            } else if (paused) {
                aloud.resume()
                playing = true
                paused = false
            } else {
                val blocks = Editions.listen(e)
                aloud.play(
                    blocks.map { it.first }, 0,
                    blocks.map { (_, lang) -> CornerLanguage.of(lang)?.let { Locale.forLanguageTag(it.locale) } }
                )
                playing = true
            }
        }
    }

    story?.let { view ->
        CornerStoryScreen(view, viewModel, onBack = viewModel::closeStory)
        return
    }

    val clean = readerDoc?.takeIf { it.source == ReaderSource.EXPRESS && page == EditionPage.PAPER }
    when (page) {
        EditionPage.ARCHIVE -> BackHandler { page = EditionPage.PAPER }
        EditionPage.SETTINGS -> BackHandler { page = EditionPage.PAPER }
        EditionPage.PAPER -> BackHandler(enabled = clean != null) { reader.close() }
    }
    val cleanView: @Composable (ReaderDoc) -> Unit = { doc ->
        Reading(
            reader, doc, onBack = reader::close,
            onCatchToLibrary = {}, onCatchToNotes = {}, onCatchDismiss = {},
            onPlace = onPlace,
            onOpenPage = { url ->
                val st = shown?.edition?.let { Editions.findNews(it, url) }
                if (st != null) openPage(st) else LobsterHandOff.open(context, url, true)?.let(viewModel::say)
            }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(P.Canvas)) {
        // Sideways on a wide screen the clean story sits beside the paper; upright it has the screen.
        // (Worked out here: the Row below hides this scope's maxWidth.)
        val beside = maxWidth >= WIDE && maxWidth > maxHeight
        if (clean != null && !beside) {
            cleanView(clean)
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                when (page) {
                    EditionPage.PAPER -> {
                        val e = shown
                        val older = e != null && latest != null && e.edition.id != latest?.edition?.id
                        SlothTopBar(
                            title = e?.let { "${it.edition.name} edition" } ?: "The Sloth Express",
                            subtitle = e?.let { received(it, older) } ?: "From the Lobster",
                            onBack = if (older) viewModel::showLatest else onBack,
                            actions = {
                                if (e != null) {
                                    IconButton(onClick = listen) {
                                        Icon(
                                            if (playing) Icons.Outlined.Pause else Icons.Outlined.Headphones,
                                            contentDescription = if (playing) "Pause" else "Listen to the edition", tint = P.Text
                                        )
                                    }
                                }
                                IconButton(onClick = { page = EditionPage.ARCHIVE }) { Icon(Icons.Outlined.Archive, contentDescription = "Past editions", tint = P.Text) }
                                IconButton(onClick = { page = EditionPage.SETTINGS }) { Icon(Icons.Outlined.Settings, contentDescription = "Edition settings", tint = P.Text) }
                            }
                        )
                        if (e == null) {
                            NoEdition(setup, onSend = { viewModel.send(now = true) }, onSettings = { page = EditionPage.SETTINGS })
                        } else {
                            Paper(e, tiles, setup, personas, viewModel, actions, shelved, onSettings = { page = EditionPage.SETTINGS }, onPlace = onPlace)
                        }
                    }
                    EditionPage.ARCHIVE -> {
                        SlothTopBar(title = "Past editions", subtitle = "Kept 30 days", onBack = { page = EditionPage.PAPER })
                        Archive(entries, shown?.edition?.id, onOpen = { viewModel.openEdition(it); page = EditionPage.PAPER }, onDelete = viewModel::delete)
                    }
                    EditionPage.SETTINGS -> {
                        SlothTopBar(title = "Edition settings", subtitle = "Sent to the SlothLobster through the mailbox, sealed", onBack = { page = EditionPage.PAPER })
                        EditionSettings(setup, personas, viewModel)
                    }
                }
            }
            if (clean != null) {
                VerticalDivider(color = P.Border)
                Box(Modifier.weight(1.1f).fillMaxHeight()) { cleanView(clean) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun received(s: SavedEdition, older: Boolean): String {
    val at = Instant.ofEpochMilli(s.edition.made).atZone(ZoneId.systemDefault())
    val time = at.format(DateTimeFormatter.ofPattern(if (older) "EEE d MMM · HH:mm" else "HH:mm", Locale.getDefault()))
    return if (older) "Past edition · $time" else "From the Lobster · $time"
}

@Composable
private fun NoEdition(setup: EditionSetup, onSend: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Masthead("YOUR MORNING PAPER, MADE BY THE LOBSTER", compact = false)
        Column(Modifier.widthIn(max = 560.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (setup.sentAt == 0L) "No edition yet. The Lobster makes one at the times you choose, from the sections and language corners in the settings."
                else "The Lobster has your settings; the first edition comes at ${setup.plan.times.firstOrNull()?.let { "%02d:%02d".format(it.h, it.m) } ?: "the next time"}.",
                color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp
            )
            Text("It needs SlothLobster 1.8 or newer. SlothLlama itself never goes online.", color = P.Faint, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledButton("Send settings and make one now", onClick = onSend)
                QuietButton("Edition settings", muted = true, onClick = onSettings)
            }
        }
    }
}

/** A part of the paper the contents bar can jump to, with what's unread in it. */
private data class PaperPart(val key: String, val label: String, val unread: Int)

/**
 * The paper: the masthead and the markets, then the top stories and the sections, the desk and
 * the language corners. A contents bar stays under the top bar: each part with what's unread in
 * it, the part you're in picked, a tap to jump there. Wide: news on the left, the desk and the
 * corners on the right.
 */
@Composable
private fun Paper(
    s: SavedEdition,
    tiles: List<MarketTile>,
    setup: EditionSetup,
    personas: List<Persona>,
    vm: EditionViewModel,
    actions: StoryActions,
    shelved: Set<String>,
    onSettings: () -> Unit,
    onPlace: (String) -> Unit
) {
    val places by vm.places.collectAsStateWithLifecycle()
    // The long-press menu: a popover beside the row on a wide screen lying on its side, else a sheet.
    val config = LocalConfiguration.current
    val sideMenu = config.screenWidthDp >= WIDE.value && config.screenWidthDp > config.screenHeightDp
    var menuKey by remember(s.edition.id) { mutableStateOf<String?>(null) }
    val e = s.edition
    val now = remember(e.id) { System.currentTimeMillis() }
    val read = s.read.toSet()
    val line = Editions.masthead(e, ZoneId.systemDefault(), Editions.listenMinutes(e))
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Where each part starts in the scrolled column, measured as it's laid out.
    val marks = remember(e.id) { mutableStateMapOf<String, Float>() }
    var content by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun anchor(key: String): Modifier = Modifier.onGloballyPositioned { c ->
        val root = content
        if (root != null && root.isAttached && c.isAttached) {
            val y = root.localPositionOf(c, Offset.Zero).y
            if (marks[key] != y) marks[key] = y
        }
    }
    fun unread(list: List<EdStory>) = list.count { Editions.storyKey(it) !in read }
    val sections = e.sections.filter { it.stories.isNotEmpty() }
    val hasCorners = e.corners.any { it.stories.isNotEmpty() }
    val parts = buildList {
        if (e.top.isNotEmpty()) add(PaperPart("top", "Top stories", unread(e.top)))
        sections.forEach { add(PaperPart("s:" + it.name, it.name, unread(it.stories))) }
        add(PaperPart("briefing", "Briefing", 0))
        add(PaperPart("desk", "The desk", 0))
        if (hasCorners) add(PaperPart("corners", "Corners", 0))
    }
    val keys = parts.map { it.key }
    val current by remember(e.id, keys) {
        derivedStateOf {
            val y = scroll.value + 48f
            keys.lastOrNull { k -> (marks[k] ?: Float.MAX_VALUE) <= y } ?: keys.firstOrNull()
        }
    }

    /** A run of stories under its head: unread count and "mark read" on the right, hairlines between. */
    val block: @Composable (String, String, List<EdStory>, Boolean) -> Unit = { key, name, list, lead ->
        val left = unread(list)
        SectionHead(name, modifier = anchor(key)) {
            if (left == 0) {
                Text("All read", color = P.Faint, fontSize = 12.sp)
            } else {
                Text("$left unread", color = P.Faint, fontSize = 12.sp)
                Text(
                    "Mark read", color = P.Accent, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "Mark $name read") { vm.setReadAll(list, true) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
        }
        list.forEachIndexed { i, st ->
            if (i > 0) HorizontalDivider(color = P.Border)
            val k = Editions.storyKey(st)
            val at = places[k]
            val isRead = k in read
            val onShelf = st.url in shelved
            StoryRow(
                st, big = lead && i == 0, read = isRead, now = now,
                onOpen = { actions.tap(st) }, onMenu = { menuKey = k }, onLater = { actions.later(st) },
                shelved = onShelf, tapsClean = Editions.tapsClean(setup, st),
                place = at?.named, onPlace = { at?.let { onPlace(it.a3) } },
                popoverOpen = sideMenu && menuKey == k,
                onPopoverDismiss = { menuKey = null }
            ) {
                StoryMenu(st, isRead, onShelf, setup, at?.named, now, actions, onDismiss = { menuKey = null })
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE
        val pad = if (wide) 40.dp else 20.dp
        Column(Modifier.fillMaxSize()) {
            // Contents: always in view, so a long paper never feels like one endless list.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                parts.forEach { part ->
                    Pill(
                        if (part.unread > 0) "${part.label} · ${part.unread}" else part.label,
                        selected = part.key == current, quiet = true, small = true
                    ) {
                        marks[part.key]?.let { y -> scope.launch { scroll.animateScrollTo(y.toInt()) } }
                    }
                }
            }
            HorizontalDivider(color = P.Border)
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).onGloballyPositioned { content = it }
                    .padding(horizontal = pad).padding(top = 12.dp, bottom = 48.dp)
            ) {
                Masthead(line, compact = !wide)
                Spacer(Modifier.height(14.dp))
                MarketStrip(tiles)
                val news: @Composable () -> Unit = {
                    if (e.top.isNotEmpty()) block("top", "Top stories", e.top, true)
                    sections.forEach { sec -> block("s:" + sec.name, sec.name, sec.stories, false) }
                    BriefingPart(anchor("briefing"), onPlace)
                    if (e.notes.isNotEmpty()) Notes(e.notes)
                }
                val desk: @Composable () -> Unit = { Desk(s, setup, personas, vm, onSettings, anchor("desk")) }
                val corners: @Composable () -> Unit = { Corners(e.corners, vm, now, places, onPlace, anchor("corners")) }
                if (wide) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1.4f)) { news() }
                        Box(Modifier.padding(horizontal = 28.dp, vertical = 24.dp).width(1.dp).heightIn(min = 400.dp).background(P.Card))
                        Column(Modifier.weight(1f)) {
                            // Watch items due today: asked at the top of the right-hand page.
                            DueCalls(Modifier.padding(top = 18.dp))
                            desk()
                            corners()
                        }
                    }
                } else {
                    DueCalls(Modifier.padding(top = 14.dp))
                    news()
                    desk()
                    corners()
                }
            }
        }
    }
    if (!sideMenu) {
        val key = menuKey
        val st = key?.let { k -> Editions.news(e).firstOrNull { Editions.storyKey(it) == k } }
        if (st != null) {
            StorySheet(
                st, read = Editions.storyKey(st) in read, shelved = st.url in shelved, setup = setup,
                place = places[Editions.storyKey(st)]?.named, now = now,
                actions = actions, onDismiss = { menuKey = null }
            )
        }
    }
}

@Composable
private fun Notes(notes: List<String>) {
    var shown by remember { mutableStateOf(false) }
    Text(
        if (shown) notes.joinToString("\n") else "${notes.size} thing${if (notes.size == 1) "" else "s"} didn't come through · show",
        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).clickable { shown = !shown }.padding(vertical = 6.dp)
    )
}

/** The desk: each desk persona's notes on this edition, or the way to ask for them. */
@Composable
private fun Desk(s: SavedEdition, setup: EditionSetup, personas: List<Persona>, vm: EditionViewModel, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val seated = setup.desk.mapNotNull { id -> personas.firstOrNull { it.id == id } }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val persona = seated.firstOrNull { it.id == chosen } ?: seated.firstOrNull()
    SectionHead("The desk", modifier = modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            seated.forEach { p -> Pill("${p.badge} · ${p.name}", selected = p.id == persona?.id, quiet = true, small = true) { chosen = p.id } }
            Pill("+ Add a desk", selected = false, small = true, onClick = onSettings)
        }
    }
    if (persona == null) {
        Text("No one sits on the desk. Add the Trader, or a persona of your own, in the settings.", color = P.Faint, fontSize = 13.sp)
        return
    }
    val note = s.desk.firstOrNull { it.personaId == persona.id }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.Card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(persona.badge, 30.dp, if (persona.id == Editions.TRADER_ID) Color(0xFFE2C26B) else null)
            Text(
                "What ${persona.name.lowercase()}'s desk would watch today", color = P.Text, fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (note != null) Text("model-written · not advice", color = P.Faint, fontSize = 11.sp)
        }
        if (note != null) {
            Text(note.text, color = P.Text, fontSize = 15.sp, lineHeight = 22.sp)
            Row {
                QuietButton("Ask ${persona.badge} again") { vm.askDesk(persona) }
                QuietButton("Take it off", muted = true) { vm.dropDesk(persona.id) }
            }
        } else {
            val count = Editions.deskStories(s.edition).size
            Text(
                "A card with today's $count headlines and the prices above goes to ${persona.name} in a chat. Keep the reply here with " +
                    "\"Keep on today's desk\" under it.",
                color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
            )
            Row { FilledButton("Ask ${persona.badge} for the desk notes") { vm.askDesk(persona) } }
        }
    }
}

/** The language corners: one tab per language, the day's biggest stories in it. */
@Composable
private fun Corners(
    corners: List<EdCorner>,
    vm: EditionViewModel,
    now: Long,
    places: Map<String, app.localmind.assistant.domain.atlas.PlaceHit>,
    onPlace: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filled = corners.filter { it.stories.isNotEmpty() }
    if (filled.isEmpty()) return
    var pick by rememberSaveable { mutableStateOf(filled.first().lang) }
    val corner = filled.firstOrNull { it.lang == pick } ?: filled.first()
    val lang = CornerLanguage.of(corner.lang)
    val heads by vm.heads.collectAsStateWithLifecycle()
    val readings by vm.readings.collectAsStateWithLifecycle()
    val traditional by vm.traditional.collectAsStateWithLifecycle()
    SectionHead("Language corners", "the day's biggest stories in each", modifier = modifier)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        filled.forEach { c ->
            val l = CornerLanguage.of(c.lang)
            Pill(listOfNotNull(l?.badge, l?.label ?: c.name).distinct().joinToString(" "), selected = c.lang == corner.lang, quiet = true, small = true) { pick = c.lang }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        lang?.let { Badge(it.badge, 26.dp) }
        Text(
            listOf(corner.source, "${corner.stories.size} ${if (corner.stories.size == 1) "story" else "stories"}").filter { it.isNotBlank() }.joinToString(" · "),
            color = P.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (lang?.isChinese == true) {
            Text(
                (if (readings) "${if (lang == CornerLanguage.MANDARIN) "Pinyin" else "Jyutping"} on" else "Readings off") + " · " + if (traditional) "繁" else "简",
                color = P.Faint, fontSize = 12.sp
            )
        }
    }
    corner.stories.forEachIndexed { i, st ->
        if (i > 0) HorizontalDivider(color = P.Border)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Read the story") { vm.openStory(corner, st) }
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val segs = heads[Editions.storyKey(st)]
            if (lang?.isChinese == true && segs != null) RubyText(segs, readings, traditional, 24.sp)
            else Text(st.title, color = P.Text, fontSize = 18.sp, lineHeight = 24.sp, fontFamily = SERIF)
            if (st.summary.isNotBlank()) Text(st.summary, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val small = listOf(Editions.smallPrint(st, now), if (st.paras.isNotEmpty()) "read it here ›" else "headline only").filter { it.isNotBlank() }.joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(small, color = P.Faint, fontSize = 12.sp)
                places[Editions.storyKey(st)]?.let { hit -> PlaceLink(hit.named) { onPlace(hit.a3) } }
            }
        }
    }
}

@Composable
private fun Archive(entries: List<EditionEntry>, shownId: String?, onOpen: (EditionEntry) -> Unit, onDelete: (EditionEntry) -> Unit) {
    if (entries.isEmpty()) {
        Text("No editions yet.", color = P.Faint, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        return
    }
    val day = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        items(entries, key = { it.file }) { en ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(en) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${en.name} edition · " + Instant.ofEpochMilli(en.made).atZone(ZoneId.systemDefault()).format(day),
                        color = if (en.id == shownId) P.Accent else P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium
                    )
                    Text("${en.read} of ${en.stories} read", color = P.Faint, fontSize = 12.sp)
                    en.headlines.take(2).forEach { h -> Text(h, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                QuietButton("Delete", muted = true) { onDelete(en) }
            }
            HorizontalDivider(color = P.Border)
        }
    }
}
