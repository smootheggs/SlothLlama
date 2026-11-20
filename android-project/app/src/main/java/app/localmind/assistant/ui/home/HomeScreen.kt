package app.localmind.assistant.ui.home

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
    val mark: String? = null
)

/** One tile in the Today strip: a short live value ("12 due") over its name and a next step. */
data class TodayItem(
    val icon: ImageVector,
    val value: String,
    val title: String,
    val detail: String,
    val tone: Tone,
    val onClick: () -> Unit,
    /** How it reads on the "All caught up" line ("backup saved yesterday"); null = leave it off. */
    val calm: String? = null
)

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
    val onAskIdiom: (Idiom) -> Unit = {}
)

/**
 * The main page. Top: the date, today in Chinese and the almanac line, with search and settings.
 * Then a hello, the two main actions (continue the last chat, start a new one), Today (what's due
 * or new), a word and an idiom for the day, then the three pillars: Learn (the tutors, one chip per
 * language with the week's practice, and the word tools), Know and Do, and a quiet row for the rest.
 * Colour is kept for the one main action and live numbers; icons stay neutral.
 * Everything fits on one screen: two columns in landscape, one spread over the height in portrait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    script: ChineseScript,
    conversations: List<Conversation>,
    personas: List<Persona>,
    today: List<TodayItem>,
    /** Learn: word tools shown under the tutors (Core words, Flashcards, Dictionary). */
    learn: List<HomeTile>,
    /** Know: Library, Notes, Later. */
    know: List<HomeTile>,
    /** Do: SlothDesk, the Lobster, Ourjong. */
    act: List<HomeTile>,
    more: List<HomeTile>,
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
    val todayStrip: @Composable () -> Unit = {
        if (today.isNotEmpty()) {
            Column {
                SectionLabel("Today")
                if (today.none { it.tone.needsYou }) {
                    CaughtUp(today)
                } else {
                    BoxWithConstraints {
                        // All in one row when each tile gets a comfortable width; otherwise two by two.
                        val oneRow = (maxWidth - 12.dp * (today.size - 1)) / today.size >= 200.dp
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            today.chunked(if (oneRow) today.size else 2).forEach { row ->
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { item -> TodayTile(item, Modifier.weight(1f).fillMaxHeight()) }
                                    repeat((if (oneRow) today.size else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val daily: @Composable () -> Unit = {
        if (s.wordOfDay || s.idiomOfDay) {
            Column {
                SectionLabel("A little Chinese")
                DailyCard(
                    word = word.takeIf { s.wordOfDay },
                    idiom = idiom.takeIf { s.idiomOfDay },
                    traditional = traditional,
                    script = script,
                    actions = actions
                )
            }
        }
    }
    val learnSection: @Composable () -> Unit = {
        if (tutorGroups.isNotEmpty() || learn.isNotEmpty()) {
            var allLanguages by remember { mutableStateOf(false) }
            // Languages practised lately; with none yet, the first two.
            val recent = tutorGroups.filter { it.language in extras.recentLanguages }.ifEmpty { tutorGroups.take(2) }
            val shownGroups = if (allLanguages) tutorGroups else recent
            val hidden = tutorGroups.size - recent.size
            Column {
                Row(Modifier.padding(bottom = SECTION_LABEL_GAP), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Learn", gap = 0.dp)
                    extras.practice?.takeIf { s.practiceWeek }?.let { week ->
                        Spacer(Modifier.width(16.dp))
                        PracticeDots(week)
                        Spacer(Modifier.width(8.dp))
                        Text("${week.count} of 7", color = P.Faint, fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shownGroups.forEach { group ->
                        TutorChip(
                            group, script,
                            lastPractised = extras.lastPractised[group.language]?.takeIf { s.practiceWeek },
                            active = group.language in extras.todayLanguages,
                            drills = s.tutorDrills,
                            onStartWith = onStartWith,
                            onDrill = actions.onDrill
                        )
                    }
                    if (hidden > 0) {
                        MoreLanguages(if (allLanguages) "Fewer" else "+$hidden", if (allLanguages) "Show fewer languages" else "Show $hidden more languages") {
                            allLanguages = !allLanguages
                        }
                    }
                }
                if (learn.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        learn.forEach { tile -> MoreButton(tile) }
                    }
                }
            }
        }
    }
    // Know and Do: three tiles a row when there's room (a tablet), two on a phone.
    val pillar: @Composable (String, List<HomeTile>) -> Unit = { label, tiles ->
        if (tiles.isNotEmpty()) {
            Column {
                SectionLabel(label)
                BoxWithConstraints {
                    val perRow = if ((maxWidth - 24.dp) / 3 >= 170.dp) 3 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(perRow).forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { tile -> ThingTile(tile, Modifier.weight(1f).fillMaxHeight()) }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    val moreRow: @Composable () -> Unit = {
        Column {
            HorizontalDivider(color = P.Border)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                more.forEach { tile -> MoreButton(tile) }
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
        if (maxWidth >= WIDE) {
            // Landscape: hello, main actions, Today and the day's Chinese on the left; Learn (the
            // tutors and word tools), Know and Do on the right.
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp)) {
                top()
                Row(Modifier.weight(1f).padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(SECTION_GAP)
                    ) {
                        greetingBlock(true)
                        mainActions()
                        todayStrip()
                        daily()
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(SECTION_GAP)
                    ) {
                        learnSection()
                        pillar("Know", know)
                        pillar("Do", act)
                        moreRow()
                    }
                }
            }
        } else {
            // The sections spread over the screen's height when they fit, and never sit closer than
            // SECTION_GAP: past one screen the page scrolls with room to breathe.
            val screen = maxHeight
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = screen)
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
                    verticalArrangement = Spread(SECTION_GAP, 56.dp)
                ) {
                    top()
                    greetingBlock(false)
                    mainActions()
                    todayStrip()
                    daily()
                    learnSection()
                    pillar("Know", know)
                    pillar("Do", act)
                    moreRow()
                }
            }
        }
    }
}

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

@Composable
private fun DailyCard(word: DailyWord?, idiom: Idiom?, traditional: Boolean, script: ChineseScript, actions: HomeActions) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .animateContentSize()
    ) {
        word?.let { WordHalf(it, traditional, script, actions, Modifier.weight(1f)) }
        if (word != null && idiom != null) VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 14.dp), color = P.Border)
        idiom?.let { IdiomHalf(it, traditional, actions, Modifier.weight(1f)) }
    }
}

@Composable
private fun WordHalf(word: DailyWord, traditional: Boolean, script: ChineseScript, actions: HomeActions, modifier: Modifier) {
    val language = if (word.cantonese) SpeechLanguage.CANTONESE else SpeechLanguage.MANDARIN
    val written = word.written(traditional)
    Column(modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Text(
            "WORD · ${languageMark(language, script)}", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium
        )
        Text(written, color = P.Text, fontSize = 24.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 8.dp))
        Text(word.reading, color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text(word.meaning, color = P.Text, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 2.dp)) {
            IconButton(onClick = { actions.onSpeak(written, language) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Say $written", tint = P.Muted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { actions.onAddWord(word) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.LibraryAdd, contentDescription = "Add to flashcards", tint = P.Muted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { actions.onAskWord(word) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.School, contentDescription = "Ask the ${language.label} teacher about it", tint = P.Muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The idiom; a tap tells its story. */
@Composable
private fun IdiomHalf(idiom: Idiom, traditional: Boolean, actions: HomeActions, modifier: Modifier) {
    var story by remember(idiom) { mutableStateOf(false) }
    Column(
        modifier
            .clickable(role = Role.Button, onClickLabel = if (story) "Hide the story" else "Tell its story") { story = !story }
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Text("IDIOM", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        Text(idiom.written(traditional), color = P.Text, fontSize = 24.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 8.dp))
        Text(idiom.pinyin, color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text(idiom.meaning, color = P.Text, fontSize = 14.sp, lineHeight = 19.sp, maxLines = if (story) 4 else 2, overflow = TextOverflow.Ellipsis)
        Text(
            "Literally: ${idiom.literal}", color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
        )
        if (story) {
            if (idiom.story.isNotBlank()) Text(idiom.story, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Ask the teacher →", color = P.Accent, fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { actions.onAskIdiom(idiom) }
                    .padding(vertical = 6.dp)
            )
        } else {
            Text(
                if (idiom.story.isNotBlank()) "Tap for its story" else "Tap to ask the teacher about it",
                color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
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

/** A slim Today tile: an icon, the value and its name on one line, and a line about it. */
@Composable
private fun TodayTile(item: TodayItem, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = null, tint = if (item.tone == Tone.LOBSTER) P.Lobster else P.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.tone == Tone.OK) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(P.Success))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    item.value, color = toneColor(item.tone), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(" · ${item.title}", color = P.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.detail.isNotBlank()) {
                Text(
                    item.detail, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = if (item.tone == Tone.LOBSTER) 2 else 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Nothing needs you: one quiet line instead of the tiles. A tap opens the first thing it names. */
@Composable
private fun CaughtUp(items: List<TodayItem>) {
    val calm = items.filter { it.calm != null }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = calm.isNotEmpty(), role = Role.Button) { calm.firstOrNull()?.onClick?.invoke() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(P.Success))
        Spacer(Modifier.width(12.dp))
        Text(
            (listOf("All caught up") + calm.mapNotNull { it.calm }).joinToString(" · "),
            color = P.Muted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** The "+4" chip that shows the rest of the languages. */
@Composable
private fun MoreLanguages(label: String, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = P.Muted, fontSize = 15.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * One chip per language; with more than one tutor it asks which. A long press (or the menu's
 * last line) starts a five-minute drill with the teacher.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TutorChip(
    group: TutorGroup,
    script: ChineseScript,
    lastPractised: String?,
    active: Boolean,
    drills: Boolean,
    onStartWith: (Persona) -> Unit,
    onDrill: (Persona) -> Unit
) {
    var choosing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    val roles = group.tutors.map { roleOf(it, group.language) }
    val detail = (roles + listOfNotNull(lastPractised)).joinToString(" · ")
    Box {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .clip(shape)
                .border(1.dp, P.Border, shape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = "Talk with the ${group.language.label} tutor",
                    onLongClickLabel = if (drills) "Five-minute drill" else null,
                    onLongClick = if (drills) ({ onDrill(drillTutor(group)) }) else null,
                    onClick = { if (group.tutors.size == 1) onStartWith(group.tutors.first()) else choosing = true }
                )
                .padding(start = 7.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filled for a language practised today; an outline otherwise, so the page isn't all green.
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(if (active) Modifier.background(P.AccentSoft) else Modifier.border(1.dp, P.Border, CircleShape)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    languageMark(group.language, script), color = if (active) P.Accent else P.Muted,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(group.language.label, color = P.Text, fontSize = 16.sp)
                Text(detail, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }, modifier = Modifier.background(P.Elevated)) {
            group.tutors.forEachIndexed { i, tutor ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(roles[i], color = P.Text, fontSize = 15.sp)
                            if (tutor.tagline.isNotBlank()) Text(tutor.tagline, color = P.Muted, fontSize = 12.sp, maxLines = 2)
                        }
                    },
                    onClick = {
                        choosing = false
                        onStartWith(tutor)
                    }
                )
            }
            if (drills) {
                HorizontalDivider(color = P.Border)
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, tint = P.Muted, modifier = Modifier.size(18.dp)) },
                    text = {
                        Column {
                            Text("Five-minute drill", color = P.Text, fontSize = 15.sp)
                            Text("Quick questions at your level", color = P.Muted, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        choosing = false
                        onDrill(drillTutor(group))
                    }
                )
            }
        }
    }
}

@Composable
private fun ThingTile(tile: HomeTile, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .clickable(role = Role.Button, onClick = tile.onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tile.mark != null) TileMark(tile.mark)
        else Icon(tile.icon, contentDescription = null, tint = if (tile.tone == Tone.LOBSTER) P.Lobster else P.Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tile.label, color = P.Text, fontSize = 16.sp)
            if (tile.detail.isNotBlank()) {
                Text(
                    tile.detail, color = if (tile.tone == Tone.ACCENT) P.Accent else P.Muted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
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
private fun MoreButton(tile: HomeTile) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = tile.onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tile.icon, contentDescription = null, tint = P.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (tile.detail.isBlank()) tile.label else "${tile.label} · ${tile.detail}",
            color = P.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Screens at least this wide get two panes (a Y700 in landscape is about 1,280 dp). */
val WIDE = 840.dp
