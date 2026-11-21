package app.localmind.assistant.ui.home


import androidx.compose.material.icons.outlined.TrackChanges
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.R
import app.localmind.assistant.data.home.HomeSettings
import app.localmind.assistant.domain.home.ChineseHello
import app.localmind.assistant.domain.home.DailyWord
import app.localmind.assistant.domain.home.DailyWords
import app.localmind.assistant.domain.home.GreetingFacts
import app.localmind.assistant.domain.home.GreetingOptions
import app.localmind.assistant.domain.home.HomeGreeting
import app.localmind.assistant.domain.home.Idiom
import app.localmind.assistant.domain.home.SkyCalendar
import app.localmind.assistant.domain.home.SlothMood
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.chat.components.chineseToday
import app.localmind.assistant.ui.chat.components.clockText
import app.localmind.assistant.ui.chat.components.slothHello
import app.localmind.assistant.ui.theme.SlothPalette as P
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * How a tile's value is coloured. Colour means "needs you": the accent for something to do, coral for
 * something the Lobster brought, red for a problem. QUIET is nothing to do; OK is healthy (grey, with
 * a small green dot).
 */
enum class Tone { ACCENT, LOBSTER, QUIET, OK, PROBLEM;
    val needsYou: Boolean get() = this == ACCENT || this == LOBSTER || this == PROBLEM
}

/** One shortcut on the Home page: an icon (or a small text mark), a name and a live status line. */
data class HomeTile(
    val icon: ImageVector,
    val label: String,
    val detail: String,
    val onClick: () -> Unit,
    val tone: Tone = Tone.QUIET,
    /** Drawn as a little mahjong tile instead of the icon (Ourjong's 中). */
    val mark: String? = null,
    /** Stable name for arranging ("library", "desk"…). */
    val key: String = label,
    /** A small count on the icon ("24" unread) when the tile needs you; the line under the name stays as it is. */
    val badge: String? = null
)

/**
 * (1.41) The line under Weigh: your record across every call ([detail]: calls checked, the Brier
 * score, how often you beat the reference), what's left to mark, and a tap to the whole record.
 */
data class RecordLine(val label: String, val detail: String, val toMark: Int, val onClick: () -> Unit)

/**
 * One calm fact for the line under the hello ("backup saved Wed", "nothing new from the Lobster"),
 * or a problem (in red) that needs a look. A tap goes where it's from.
 */
data class StatusNote(val text: String, val onClick: () -> Unit, val problem: Boolean = false)

/** The Home page's optional touches, as set in Settings → Home, with what they need. */
data class HomeExtrasUi(
    val settings: HomeSettings = HomeSettings(),
    /** Things the Lobster brought back, and flashcards due: for the hello. */
    val caught: Int = 0,
    val due: Int = 0,
    val practice: PracticeWeek? = null,
    /** "yesterday", per language. */
    val lastPractised: Map<SpeechLanguage, String> = emptyMap(),
    /** Languages practised in the last two weeks: their chips show; the rest fold into "+N". */
    val recentLanguages: Set<SpeechLanguage> = emptySet(),
    /** Languages practised today: their chips are filled. */
    val todayLanguages: Set<SpeechLanguage> = emptySet()
)

/** What the Home page's extras can do. */
data class HomeActions(
    val onDrill: (Persona) -> Unit = {},
    val onSpeak: (String, SpeechLanguage) -> Unit = { _, _ -> },
    val onAddWord: (DailyWord) -> Unit = {},
    val onAskWord: (DailyWord) -> Unit = {},
    val onAskIdiom: (Idiom) -> Unit = {},
    /** Arranging Home: swap two tiles of a section ("fav", "know", "do") by position. */
    val onSwap: (String, Int, Int) -> Unit = { _, _, _ -> }
)

/**
 * The main page. Top: the date, today in Chinese and the almanac line, with search and settings.
 * Then a hello with a quiet status line, the two main actions (continue the last chat, start a new
 * one), a word and an idiom for the day, then the three pillars: Learn (the tutors, one chip per
 * language with the week's practice, and the word tools), Know and Weigh, and a quiet row for the rest
 * (where the Lobster and Ourjong live since 1.37).
 * Colour is kept for the one main action and live numbers; icons stay neutral. What needs you
 * (cards due, an unread edition, a catch, tickets to mark) lights up its own tile, so nothing shows twice.
 * Everything fits on one screen: two columns in landscape, one spread over the height in portrait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    script: ChineseScript,
    conversations: List<Conversation>,
    personas: List<Persona>,
    /** The quiet line under the hello: what's calm, and any problem. What needs you lights up its own tile. */
    status: List<StatusNote> = emptyList(),
    /** Learn: word tools shown under the tutors (Core words, Flashcards, Dictionary). */
    learn: List<HomeTile>,
    /** Know: SlothBooks, Notes, Later. */
    know: List<HomeTile>,
    /** Weigh: SlothDesk, Sports and World. */
    act: List<HomeTile>,
    /** The record line under Weigh; null hides it. */
    record: RecordLine? = null,
    more: List<HomeTile>,
    /** Favourite personas, in order: a chip each that starts a chat. */
    favourites: List<Persona> = emptyList(),
    /** persona id → when you last wrote to it ("today", "yesterday"…). */
    favouriteNotes: Map<String, String> = emptyMap(),
    /** The "+" after the favourites: choose them in Personas. */
    onChooseFavourites: () -> Unit = {},
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onStartWith: (Persona) -> Unit,
    extras: HomeExtrasUi = HomeExtrasUi(),
    actions: HomeActions = HomeActions()
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    val s = extras.settings
    val traditional = script == ChineseScript.TRADITIONAL
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(now).atZone(zone)
    val day = date.toLocalDate()
    val hour = date.hour
    val lunar = remember(day) { IcuLunar() }
    // The calendar work runs once a day (and when a setting changes), not every tick.
    val dateLine = remember(day, s, traditional) { almanacLine(day, s, lunar, traditional) }
    val todayNote = remember(day, s.festivals) { if (s.festivals) SkyCalendar.next(day, lunar)?.takeIf { it.daysAway == 0 } else null }
    val birthday = remember(day, s.birthday) { s.isBirthday(day.monthValue, day.dayOfMonth, day.isLeapYear) }
    val greeting = remember(now / 3_600_000, s, extras.caught, extras.due, todayNote, birthday, traditional) {
        HomeGreeting.compose(
            GreetingFacts(
                hour = hour, epochDay = day.toEpochDay(), base = slothHello(now), name = s.name,
                birthday = birthday, today = todayNote, caught = extras.caught, due = extras.due
            ),
            GreetingOptions(context = s.contextGreeting, language = s.chineseGreeting, traditional = traditional, moods = s.slothMoods)
        )
    }
    val word = remember(day, s.wordLanguage) { DailyWords.wordFor(day.toEpochDay(), s.wordLanguage) }
    val idiom = remember(day) { DailyWords.idiomFor(day.toEpochDay()) }
    val last = conversations.firstOrNull()
    val personaOf = personas.associateBy { it.id }
    val tutorGroups = remember(personas) { tutorGroups(personas) }
    val chineseLine = chineseToday(now, script) +
        if (s.chineseZodiac) " · ${SkyCalendar.BRANCHES[SkyCalendar.doubleHour(hour)]}${if (traditional) "時" else "时"}" else ""

    val top: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            DateBlock(
                clock = clockText(now),
                chinese = chineseLine,
                almanac = dateLine,
                rows = { almanac(day, hour, s, lunar, traditional) },
                modifier = Modifier.weight(1f).padding(top = 4.dp)
            )
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = "Search chats", tint = P.Muted)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = P.Muted)
            }
        }
    }
    val greetingBlock: @Composable (Boolean) -> Unit = { compact ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlothPortrait(greeting.mood, compact)
            Spacer(Modifier.width(20.dp))
            Column {
                greeting.chinese?.let { zh -> ChineseHelloLine(zh, actions.onSpeak) }
                Text(
                    greeting.hello, color = P.Text, fontSize = if (compact) 30.sp else 34.sp, fontWeight = FontWeight.Medium,
                    lineHeight = if (compact) 36.sp else 40.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    greeting.sub, color = P.Muted, fontSize = 17.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (status.isNotEmpty()) StatusLine(status)
            }
        }
    }
    val mainActions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (last != null) {
                ContinueCard(
                    title = last.title.ifBlank { "Last chat" },
                    persona = personaOf[last.personaId]?.name.orEmpty(),
                    ago = ago(last.updatedAt, now),
                    modifier = Modifier.weight(1f)
                ) { onOpenConversation(last.id) }
            }
            NewChatCard(Modifier.weight(1f), onNewChat)
        }
    }
    // Arranging: tap a tile, then another in the same section, and they swap.
    var arranging by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Pair<String, Int>?>(null) }
    fun tap(section: String, index: Int, go: () -> Unit) {
        if (!arranging) {
            go()
            return
        }
        val p = picked
        picked = when {
            p == null -> section to index
            p.first == section && p.second != index -> {
                actions.onSwap(section, p.second, index)
                null
            }
            p.first == section -> null
            else -> section to index
        }
    }
    fun isPicked(section: String, index: Int) = picked?.let { it.first == section && it.second == index } ?: false

    val daily: @Composable (Boolean) -> Unit = { sideBySide ->
        if (s.wordOfDay || s.idiomOfDay) {
            Column {
                SectionLabel("A little Chinese")
                DailyStrip(
                    word = word.takeIf { s.wordOfDay },
                    idiom = idiom.takeIf { s.idiomOfDay },
                    traditional = traditional,
                    script = script,
                    actions = actions,
                    sideBySide = sideBySide
                )
            }
        }
    }
    val favouritesRow: @Composable () -> Unit = {
        Column {
            SectionLabel("Favourites")
            BoxWithConstraints {
                // As many chips as fit on one line, then "+".
                val fits = ((maxWidth - 66.dp) / (FAV_CHIP + 10.dp)).toInt().coerceAtLeast(1)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    favourites.take(fits).forEachIndexed { i, persona ->
                        FavouriteChip(
                            persona = persona,
                            note = favouriteNotes[persona.id],
                            active = persona.speech != null && persona.speech in extras.todayLanguages,
                            picked = isPicked("fav", i),
                            arranging = arranging,
                            onClick = { tap("fav", i) { onStartWith(persona) } },
                            onDrill = if (s.tutorDrills && persona.speech != null && !arranging) ({
                                tutorGroups.firstOrNull { it.language == persona.speech }?.let { actions.onDrill(drillTutor(it)) }
                            }) else null
                        )
                    }
                    AddFavourite(onChooseFavourites)
                }
            }
        }
    }
    val learnSection: @Composable () -> Unit = {
        if (learn.isNotEmpty()) {
            Column {
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Learn", gap = 0.dp)
                    extras.practice?.takeIf { s.practiceWeek }?.let { week ->
                        Spacer(Modifier.width(16.dp))
                        PracticeDots(week)
                        Spacer(Modifier.width(8.dp))
                        Text("${week.count} of 7", color = P.Faint, fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
                FlowRow(Modifier.offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    learn.forEach { tile -> MoreButton(tile) }
                }
            }
        }
    }
    // Know and Do: one row each, three or four tiles (four when each still gets 120 dp). The icon
    // sits beside the name whenever every name fits that way: at equal widths if it can, else with
    // widths that follow the names (The Sloth Express gets more room than Atlas). Only when they
    // can't does the icon go above the name. More than fits wraps, rather than any being left out.
    val pillar: @Composable (String, String, List<HomeTile>) -> Unit = { label, section, tiles ->
        if (tiles.isNotEmpty()) {
            Column {
                SectionLabel(label)
                BoxWithConstraints {
                    val full = maxWidth
                    val perRow = if (tiles.size >= 4 && (full - 36.dp) / 4 >= 120.dp) 4 else 3
                    val gaps = 12.dp * (perRow - 1)
                    val rows = tiles.chunked(perRow)
                    val needs = rows.map { row -> row.map { besideWidth(it) } }
                    val unit = (full - gaps) / perRow
                    val equal = needs.all { row -> row.all { it <= unit } }
                    val shaped = needs.all { row -> row.fold(0.dp) { a, b -> a + b } + MIN_TILE * (perRow - row.size) + gaps <= full }
                    val stacked = !equal && !shaped
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEachIndexed { r, row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEachIndexed { j, tile ->
                                    val i = r * perRow + j
                                    val weight = if (!equal && shaped) needs[r][j].value else 1f
                                    ThingTile(
                                        tile, stacked = stacked, arranging = arranging, picked = isPicked(section, i),
                                        modifier = Modifier.weight(weight).fillMaxHeight(),
                                        onClick = { tap(section, i, tile.onClick) }
                                    )
                                }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(if (!equal && shaped) MIN_TILE.value else 1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    // Weigh, with your record as one line under its tiles.
    val weigh: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pillar("Weigh", "do", act)
            record?.let { RecordRow(it) }
        }
    }
    val footer: @Composable () -> Unit = {
        Column {
            HorizontalDivider(color = P.Border)
            Row(Modifier.padding(top = 6.dp).offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
                if (arranging) {
                    Text(
                        "Tap a tile, then the one to swap it with.", color = P.Muted, fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    Text(
                        "Done", color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button) { arranging = false; picked = null }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                } else {
                    // One line. When the full names don't fit, step by step: Models shows just the
                    // model's name (its chip icon says what it is); then the Lobster, Ourjong and Projects
                    // keep only their marks (and a catch's count); then every link but Models does. Only a
                    // narrow phone wraps.
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val room = maxWidth
                        val shortened = more.map { t -> if (t.key == "models" && t.detail.isNotBlank()) t.copy(label = t.detail, detail = "") else t }
                        val bare = shortened.filter { it.key != "models" }.map { it.key }.toSet()
                        val iconOnly = listOf(emptySet(), emptySet(), setOf("lobster", "ourjong", "projects"), bare)
                            .zip(listOf(more, shortened, shortened, shortened))
                            .firstOrNull { (marks, tiles) -> laneWidth(tiles, marks) <= room }
                            ?: (bare to shortened)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            iconOnly.second.forEach { tile -> MoreButton(tile, iconOnly = tile.key in iconOnly.first) }
                        }
                    }
                    IconButton(onClick = { arranging = true; picked = null }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.DragIndicator, contentDescription = "Arrange Home", tint = P.Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(P.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Two pages when the screen lies on its side and has room for both: the tablet in
        // landscape, and an open foldable held sideways. Upright, one page.
        val twoPages = maxWidth >= TWO_PAGES && maxWidth > maxHeight
        val roomy = maxWidth >= WIDE
        if (twoPages) {
            // Landscape (and an open foldable): two pages, each one screen tall. Left, now: the hello,
            // the main actions and the day's Chinese. Right, places: favourites, Learn, Know,
            // Weigh and the quiet row. Both start and end on the same lines.
            Column(Modifier.fillMaxSize().padding(horizontal = if (roomy) 40.dp else 24.dp, vertical = 16.dp)) {
                top()
                Row(Modifier.weight(1f).padding(top = 12.dp)) {
                    HomePage(Modifier.weight(1f)) {
                        greetingBlock(true)
                        mainActions()
                        daily(false)
                    }
                    Box(Modifier.padding(horizontal = if (roomy) 32.dp else 20.dp, vertical = 8.dp).width(1.dp).fillMaxHeight().background(P.Card))
                    HomePage(Modifier.weight(1f)) {
                        favouritesRow()
                        learnSection()
                        pillar("Know", "know", know)
                        weigh()
                        footer()
                    }
                }
            }
        } else {
            // Portrait: one page. The sections spread over the screen's height when they fit, and
            // never sit closer than the smallest gap: past one screen the page scrolls.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                HomePage(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 24.dp), top = 16.dp, bottom = 20.dp) {
                    top()
                    greetingBlock(false)
                    mainActions()
                    daily(true)
                    favouritesRow()
                    learnSection()
                    pillar("Know", "know", know)
                    weigh()
                    footer()
                }
            }
        }
    }
}

/** A page of Home: its sections spread over the height available, scrolling only if they can't fit. */
@Composable
private fun HomePage(modifier: Modifier, top: Dp = 0.dp, bottom: Dp = 0.dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val height = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = height)
                .padding(top = top, bottom = bottom),
            verticalArrangement = Spread(20.dp, 48.dp)
        ) { content() }
    }
}

/** Narrowest sideways screen that gets Home's two pages (an open foldable is about 750–880dp). */
private val TWO_PAGES = 720.dp

/** Width a favourite chip is given when working out how many fit. */
private val FAV_CHIP = 170.dp

// --- The date, today in Chinese, and the almanac ----------------------------------------------

/**
 * The date, today in Chinese (with the double hour) and a faint third line: the festival or
 * solar term, the New Year countdown and the Sun's sign. A tap opens the almanac.
 */
@Composable
private fun DateBlock(
    clock: String,
    chinese: String,
    almanac: AlmanacLine?,
    rows: () -> List<AlmanacRow>,
    modifier: Modifier
) {
    var shown by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Open the almanac") { shown = true }
                .padding(end = 8.dp, bottom = 2.dp)
        ) {
            Text(clock, color = P.Muted, fontSize = 15.sp)
            Text(chinese, color = P.Faint, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            almanac?.let {
                Text(
                    it.text, color = if (it.special) P.Accent else P.Faint, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        DropdownMenu(expanded = shown, onDismissRequest = { shown = false }, modifier = Modifier.background(P.Elevated)) {
            Column(Modifier.widthIn(min = 280.dp, max = 420.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    "ALMANAC", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val list = rows()
                if (list.isEmpty()) Text("Turn on festivals or the zodiac in Settings → Home.", color = P.Muted, fontSize = 13.sp)
                list.forEach { r ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(r.label, color = P.Faint, fontSize = 12.sp)
                        Text(r.text, color = P.Text, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
                Text(
                    "Worked out on this tablet: the solar terms from the Sun's position, in China's time.",
                    color = P.Faint, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
            }
        }
    }
}

// --- The hello ---------------------------------------------------------------------------------

/** 早晨 · zou2 san4, small above the hello; a tap says it aloud. */
@Composable
private fun ChineseHelloLine(zh: ChineseHello, onSpeak: (String, SpeechLanguage) -> Unit) {
    val language = if (zh.cantonese) SpeechLanguage.CANTONESE else SpeechLanguage.MANDARIN
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "Say it") { onSpeak(zh.text, language) }
            .padding(bottom = 6.dp)
            .semantics { contentDescription = "${zh.text}, ${zh.reading}. ${language.label}." },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(zh.text, color = P.Accent, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(zh.reading, color = P.Faint, fontSize = 13.sp)
    }
}

/** The sloth, with a small touch for the moment: a coffee, a lantern, a party, the coral of a catch. */
@Composable
private fun SlothPortrait(mood: SlothMood, compact: Boolean) {
    val w = if (compact) 58.dp else 66.dp
    val h = if (compact) 50.dp else 58.dp
    Box(Modifier.size(width = w + 10.dp, height = h + 8.dp)) {
        Image(
            painter = painterResource(R.drawable.sloth_llama),
            contentDescription = null,
            modifier = Modifier.size(width = w, height = h).align(Alignment.BottomStart)
        )
        when (mood) {
            SlothMood.COFFEE -> Text("☕", fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomEnd).clearAndSetSemantics {})
            SlothMood.LANTERN -> Text("🏮", fontSize = 15.sp, modifier = Modifier.align(Alignment.TopEnd).clearAndSetSemantics {})
            SlothMood.BIRTHDAY -> Text("🎂", fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomEnd).clearAndSetSemantics {})
            SlothMood.CATCH -> Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(11.dp)
                    .border(2.dp, P.Canvas, CircleShape)
                    .clip(CircleShape)
                    .background(P.Lobster)
            )
            SlothMood.SLEEPY -> Sleepy(Modifier.align(Alignment.TopEnd))
            SlothMood.NONE -> {}
        }
    }
}

/** Two small z's drifting in and out (still, if animations are off on the tablet). */
@Composable
private fun Sleepy(modifier: Modifier) {
    val context = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val fade = if (still) 0.7f else {
        val t = rememberInfiniteTransition(label = "sleepy")
        val a by t.animateFloat(0.25f, 0.85f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "z")
        a
    }
    Row(modifier.alpha(fade).clearAndSetSemantics {}, verticalAlignment = Alignment.Top) {
        Text("z", color = P.Faint, fontSize = 11.sp, fontStyle = FontStyle.Italic, modifier = Modifier.offset(y = 6.dp))
        Text("z", color = P.Faint, fontSize = 14.sp, fontStyle = FontStyle.Italic)
    }
}

// --- A word and an idiom for the day ------------------------------------------------------------

/**
 * The day's word and idiom as two quiet lines (side by side in portrait). The word has Listen and
 * a menu (flashcards, ask the teacher); a tap on the idiom tells its story.
 */
@Composable
private fun DailyStrip(word: DailyWord?, idiom: Idiom?, traditional: Boolean, script: ChineseScript, actions: HomeActions, sideBySide: Boolean) {
    if (sideBySide && word != null && idiom != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            WordLine(word, traditional, script, actions, Modifier.weight(1f))
            IdiomLine(idiom, traditional, actions, Modifier.weight(1f))
        }
    } else {
        Column {
            word?.let { WordLine(it, traditional, script, actions, Modifier.fillMaxWidth()) }
            idiom?.let { IdiomLine(it, traditional, actions, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun WordLine(word: DailyWord, traditional: Boolean, script: ChineseScript, actions: HomeActions, modifier: Modifier) {
    val language = if (word.cantonese) SpeechLanguage.CANTONESE else SpeechLanguage.MANDARIN
    val written = word.written(traditional)
    var menu by remember { mutableStateOf(false) }
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(languageMark(language, script), color = P.Faint, fontSize = 11.sp, modifier = Modifier.width(22.dp))
        Text(written, color = P.Text, fontSize = 22.sp, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(word.reading, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(word.meaning, color = P.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { actions.onSpeak(written, language) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Say $written", tint = P.Muted, modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More for $written", tint = P.Muted, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.LibraryAdd, contentDescription = null, tint = P.Muted) },
                    text = { Text("Add to flashcards", color = P.Text) },
                    onClick = { menu = false; actions.onAddWord(word) }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null, tint = P.Muted) },
                    text = { Text("Ask the ${language.label} teacher", color = P.Text) },
                    onClick = { menu = false; actions.onAskWord(word) }
                )
            }
        }
    }
}

@Composable
private fun IdiomLine(idiom: Idiom, traditional: Boolean, actions: HomeActions, modifier: Modifier) {
    var story by remember(idiom) { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Tell its story") { story = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("成", color = P.Faint, fontSize = 11.sp, modifier = Modifier.width(22.dp).clearAndSetSemantics {})
            Text(idiom.written(traditional), color = P.Text, fontSize = 22.sp, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(idiom.pinyin, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(idiom.meaning, color = P.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = story, onDismissRequest = { story = false }, modifier = Modifier.background(P.Elevated)) {
            Column(Modifier.widthIn(min = 260.dp, max = 420.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(idiom.written(traditional), color = P.Text, fontSize = 22.sp)
                Text(idiom.pinyin, color = P.Muted, fontSize = 13.sp)
                Text(idiom.meaning, color = P.Text, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Literally: ${idiom.literal}", color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (idiom.story.isNotBlank()) {
                    Text(idiom.story, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null, tint = P.Muted) },
                text = { Text("Ask the teacher about it", color = P.Text) },
                onClick = { story = false; actions.onAskIdiom(idiom) }
            )
        }
    }
}

// --- Practice -------------------------------------------------------------------------------------

/** Seven quiet dots, today last: filled for a day with any practice. */
@Composable
private fun PracticeDots(week: PracticeWeek) {
    Row(
        Modifier
            .padding(top = 2.dp)
            .semantics { contentDescription = "Practised ${week.count} of the last 7 days" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        week.days.forEachIndexed { i, done ->
            val todayDot = i == week.days.lastIndex
            Box(
                Modifier
                    .size(if (todayDot) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .then(if (done) Modifier.background(P.Accent) else Modifier.border(1.dp, if (todayDot) P.Muted else P.BorderStrong, CircleShape))
            )
        }
    }
}

/** The tutors for one language: a partner, a teacher, custom ones. */
data class TutorGroup(val language: SpeechLanguage, val tutors: List<Persona>)

/** One group per language, in the languages' order; each group's partner first. */
fun tutorGroups(personas: List<Persona>): List<TutorGroup> =
    personas.filter { it.speech != null }
        .groupBy { it.speech!! }
        .toSortedMap(compareBy { it.ordinal })
        .map { (language, list) -> TutorGroup(language, list.sortedBy { if (roleOf(it, language) == "Partner") 0 else 1 }) }

/** "Mandarin Teacher" → "Teacher"; a tutor named just for its language is the partner. */
fun roleOf(tutor: Persona, language: SpeechLanguage): String {
    val rest = tutor.name.removePrefix(language.label).trim()
    return if (rest.isEmpty()) "Partner" else rest
}

/** The tutor who runs a drill: the teacher if there is one. */
fun drillTutor(group: TutorGroup): Persona =
    group.tutors.firstOrNull { roleOf(it, group.language) == "Teacher" } ?: group.tutors.first()

/** A short mark for a language: its own script for Chinese and Japanese, a code otherwise. */
fun languageMark(language: SpeechLanguage, script: ChineseScript): String = when (language) {
    SpeechLanguage.MANDARIN -> "中"
    SpeechLanguage.CANTONESE -> if (script == ChineseScript.TRADITIONAL) "粵" else "粤"
    SpeechLanguage.JAPANESE -> "日"
    SpeechLanguage.FRENCH -> "FR"
    SpeechLanguage.GERMAN -> "DE"
    SpeechLanguage.SPANISH -> "ES"
    SpeechLanguage.ITALIAN -> "IT"
}

private fun ago(time: Long, now: Long): String =
    if (time <= 0 || now - time < 60_000) "just now"
    else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.ACCENT -> P.Accent
    Tone.LOBSTER -> P.Lobster
    Tone.QUIET, Tone.OK -> P.Muted
    Tone.PROBLEM -> P.Danger
}

@Composable
private fun SectionLabel(text: String, gap: Dp = SECTION_LABEL_GAP) {
    Text(
        text.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = gap).semantics { heading() }
    )
}

/** Space between the page's sections, and under a section's label. */
private val SECTION_GAP = 32.dp
private val SECTION_LABEL_GAP = 14.dp

/**
 * Like SpaceBetween while everything fits (gaps up to [max]), but never closer than [min]: once the
 * page is taller than the screen it scrolls with steady gaps instead of squeezing them to nothing.
 */
private class Spread(private val min: Dp, private val max: Dp) : Arrangement.Vertical {
    override val spacing: Dp = min

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        if (sizes.isEmpty()) return
        val gaps = (sizes.size - 1).coerceAtLeast(1)
        val gap = ((totalSize - sizes.sum()) / gaps).coerceIn(min.roundToPx(), max.roundToPx())
        var y = 0
        sizes.forEachIndexed { i, size ->
            outPositions[i] = y
            y += size + gap
        }
    }
}

@Composable
private fun ContinueCard(title: String, persona: String, ago: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 124.dp)
            .clip(shape)
            .background(P.Elevated)
            .clickable(role = Role.Button, onClickLabel = "Continue this chat", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = P.Faint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Continue · $ago", color = P.Faint, fontSize = 13.sp)
        }
        Text(title, color = P.Text, fontSize = 19.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (persona.isNotBlank()) Text(persona, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The page's one filled action. */
@Composable
private fun NewChatCard(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 124.dp)
            .clip(shape)
            .background(P.Accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(P.OnAccent), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = P.Accent, modifier = Modifier.size(24.dp))
        }
        Text("New chat", color = P.OnAccent, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Ask anything · private, on this tablet", color = P.OnAccent, fontSize = 14.sp, maxLines = 2)
    }
}

/**
 * The quiet line under the hello: a green dot and what's calm ("Nothing new from the Lobster ·
 * backup saved Wed"), or a red dot with the problem first. A tap opens the first thing it names.
 */
@Composable
private fun StatusLine(notes: List<StatusNote>) {
    val ordered = notes.sortedByDescending { it.problem }
    val problem = ordered.first().problem
    val text = buildAnnotatedString {
        ordered.forEachIndexed { i, note ->
            if (i > 0) append(" · ")
            val words = if (i == 0) note.text.replaceFirstChar { it.uppercase() } else note.text
            if (note.problem) withStyle(SpanStyle(color = P.Danger)) { append(words) } else append(words)
        }
    }
    Row(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { ordered.first().onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (problem) P.Danger else P.Success))
        Spacer(Modifier.width(10.dp))
        Text(text, color = P.Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * How wide a tile must be to put its icon beside its name. A short line ("2 to read · 2 min")
 * gets room for one line; a long one may take two.
 */
private fun besideWidth(tile: HomeTile): Dp {
    val name = (tile.label.length * 7.6f).dp
    val line = when {
        tile.detail.isBlank() -> 0.dp
        tile.detail.length <= 22 -> (tile.detail.length * 6.6f).dp
        else -> (tile.detail.length * 6.2f / 2f + 10f).dp
    }
    return maxOf(MIN_TILE, BESIDE_CHROME + maxOf(name, line))
}

/** Padding, icon and gap around a tile's name when the icon sits beside it. */
private val BESIDE_CHROME = 62.dp

/** About how wide the quiet row's links are side by side (14 sp text, icon, padding); [iconOnly] keys show just a mark. */
private fun laneWidth(tiles: List<HomeTile>, iconOnly: Set<String> = emptySet()): Dp = tiles.fold(0.dp) { sum, t ->
    if (t.key in iconOnly) sum + 44.dp + (t.badge?.let { (it.length * 7.3f + 6f).dp } ?: 0.dp)
    else {
        val text = if (t.detail.isBlank()) t.label else "${t.label} · ${t.detail}"
        sum + 50.dp + (text.length * 7.3f).dp
    }
} + 4.dp * (tiles.size - 1).coerceAtLeast(0)

/** The narrowest a Know or Weigh tile gets. */
private val MIN_TILE = 120.dp

/**
 * A place to go: an outlined tile with an icon, a name and a live line. Narrow: the icon above the
 * name. Arranging: a dashed outline, and a filled moss one on the tile picked to swap.
 */
@Composable
private fun ThingTile(tile: HomeTile, stacked: Boolean, arranging: Boolean, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val lit = tile.badge != null && tile.tone.needsYou
    val litColor = if (tile.tone == Tone.LOBSTER) P.Lobster else P.Accent
    val frame = when {
        picked -> Modifier.background(P.AccentSoft).border(2.dp, P.Accent, shape)
        arranging -> Modifier.dashedBorder(P.BorderStrong, 16.dp)
        lit -> Modifier.border(1.dp, litColor.copy(alpha = 0.55f), shape)
        else -> Modifier.border(1.dp, P.Border, shape)
    }
    val icon: @Composable () -> Unit = {
        Box {
            if (tile.mark != null) TileMark(tile.mark)
            else Icon(tile.icon, contentDescription = null, tint = if (lit || tile.tone == Tone.LOBSTER) litColor else P.Muted, modifier = Modifier.size(22.dp))
            if (lit && !picked && !arranging) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-7).dp)
                        .heightIn(min = 16.dp)
                        .widthIn(min = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(litColor)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tile.badge.orEmpty(), color = P.OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp, maxLines = 1)
                }
            }
        }
    }
    val text: @Composable () -> Unit = {
        Column {
            Text(tile.label, color = P.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = if (picked) "Picked" else tile.detail
            if (line.isNotBlank()) {
                // A lit tile's line keeps its own colour (the badge carries the count); a status line takes the accent.
                Text(
                    line, color = if (picked || (tile.tone == Tone.ACCENT && tile.badge == null)) P.Accent else P.Muted, fontSize = 13.sp,
                    lineHeight = 17.sp, maxLines = if (stacked) 1 else 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    val base = modifier
        .heightIn(min = 76.dp)
        .clip(shape)
        .then(frame)
        .clickable(role = Role.Button, onClickLabel = if (arranging) "Pick to swap" else null, onClick = onClick)
    if (stacked) {
        Column(base.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            text()
        }
    } else {
        Row(base.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { text() }
        }
    }
}

/** A dashed rounded outline, for tiles while Home is being arranged. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
    val inset = stroke.width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke.width, size.height - stroke.width),
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke
    )
}

/**
 * A favourite persona: its badge, its name and when you last wrote to it. A tap starts a chat;
 * a long press on a tutor starts a five-minute drill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteChip(
    persona: Persona,
    note: String?,
    active: Boolean,
    picked: Boolean,
    arranging: Boolean,
    onClick: () -> Unit,
    onDrill: (() -> Unit)?
) {
    val shape = RoundedCornerShape(28.dp)
    val frame = when {
        picked -> Modifier.background(P.AccentSoft).border(2.dp, P.Accent, shape)
        arranging -> Modifier.dashedBorder(P.BorderStrong, 28.dp)
        else -> Modifier.border(1.dp, P.Border, shape)
    }
    Row(
        Modifier
            .widthIn(max = FAV_CHIP)
            .heightIn(min = 56.dp)
            .clip(shape)
            .then(frame)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (arranging) "Pick to swap" else "Chat with ${persona.name}",
                onLongClickLabel = if (onDrill != null) "Five-minute drill" else null,
                onLongClick = onDrill,
                onClick = onClick
            )
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (active) P.AccentSoft else P.Elevated),
            contentAlignment = Alignment.Center
        ) {
            Text(persona.badge, color = if (persona.badge == "Au") GOLD else P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(persona.name, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = if (picked) "Picked" else note
            if (!line.isNullOrBlank()) Text(line, color = if (picked) P.Accent else P.Faint, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** The Trader's Au, in gold. */
private val GOLD = Color(0xFFE2C26B)

/** After the favourites: choose them in Personas (the star on each). */
@Composable
private fun AddFavourite(onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .dashedBorder(P.BorderStrong, 28.dp)
            .clickable(role = Role.Button, onClickLabel = "Choose favourites", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "Choose favourites", tint = P.Muted, modifier = Modifier.size(20.dp))
    }
}

/** A tiny mahjong tile, the red dragon's 中, in the space an icon takes. */
@Composable
private fun TileMark(mark: String) {
    Box(
        Modifier
            .size(width = 20.dp, height = 26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(P.Elevated)
            .border(1.dp, P.BorderStrong, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(mark, color = P.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoreButton(tile: HomeTile, iconOnly: Boolean = false) {
    val color = when (tile.tone) {
        Tone.ACCENT -> P.Accent
        Tone.LOBSTER -> P.Lobster
        else -> P.Muted
    }
    val text = if (tile.detail.isBlank()) tile.label else "${tile.label} · ${tile.detail}"
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = if (iconOnly) text else null, onClick = tile.onClick)
            .semantics { if (iconOnly) contentDescription = text }
            .padding(horizontal = if (iconOnly) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tile.mark != null) Text(tile.mark, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        else Icon(tile.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        if (!iconOnly) {
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (tile.badge != null) {
            Spacer(Modifier.width(4.dp))
            Text(tile.badge, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Screens at least this wide get two panes (a Y700 in landscape is about 1,280 dp). */
val WIDE = 840.dp

/** The record line under Weigh: a quiet bordered row, the count to mark in the accent. */
@Composable
private fun RecordRow(r: RecordLine) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, P.Border, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = r.onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.TrackChanges, contentDescription = null, tint = P.Accent, modifier = Modifier.size(18.dp))
        Text(r.label, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(r.detail, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (r.toMark > 0) Text("${r.toMark} to mark", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
    }
}
