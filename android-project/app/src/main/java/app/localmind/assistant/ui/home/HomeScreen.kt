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

/** How a tile's number or icon is coloured: the accent, the Lobster's coral, quiet, or a problem. */
enum class Tone { ACCENT, LOBSTER, QUIET, PROBLEM }

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
    val onClick: () -> Unit
)

/** The Home page's optional touches, as set in Settings → Home, with what they need. */
data class HomeExtrasUi(
    val settings: HomeSettings = HomeSettings(),
    /** Things the Lobster brought back, and flashcards due: for the hello. */
    val caught: Int = 0,
    val due: Int = 0,
    val practice: PracticeWeek? = null,
    /** "yesterday", per language. */
    val lastPractised: Map<SpeechLanguage, String> = emptyMap()
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
 * or new), a word and an idiom for the day, the tutors one chip per language with the week's
 * practice, your things with live status, and a quiet row for the rest.
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
    things: List<HomeTile>,
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
            Spacer(Modifier.width(18.dp))
            Column {
                greeting.chinese?.let { zh -> ChineseHelloLine(zh, actions.onSpeak) }
                Text(
                    greeting.hello, color = P.Text, fontSize = if (compact) 30.sp else 34.sp, fontWeight = FontWeight.Medium,
                    lineHeight = if (compact) 36.sp else 40.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Text(greeting.sub, color = P.Muted, fontSize = 17.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                // Up to three in a row; four make a tidy two-by-two.
                today.chunked(if (today.size > 3) 2 else 3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item -> TodayTile(item, Modifier.weight(1f)) }
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
    val tutorSection: @Composable () -> Unit = {
        if (tutorGroups.isNotEmpty()) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    SectionLabel("Tutors")
                    Spacer(Modifier.weight(1f))
                    extras.practice?.takeIf { s.practiceWeek }?.let { PracticeDots(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tutorGroups.forEach { group ->
                        TutorChip(
                            group, script,
                            lastPractised = extras.lastPractised[group.language]?.takeIf { s.practiceWeek },
                            drills = s.tutorDrills,
                            onStartWith = onStartWith,
                            onDrill = actions.onDrill
                        )
                    }
                }
            }
        }
    }
    val thingsGrid: @Composable () -> Unit = {
        if (things.isNotEmpty()) {
            Column {
                SectionLabel("Your things")
                things.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile -> ThingTile(tile, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
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
            // Landscape: hello, main actions, Today and the day's Chinese on the left; tutors and
            // your things on the right.
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp)) {
                top()
                Row(Modifier.weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        greetingBlock(true)
                        mainActions()
                        todayStrip()
                        daily()
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        tutorSection()
                        thingsGrid()
                        moreRow()
                    }
                }
            }
        } else {
            // One page: the sections spread over the screen's height. It only scrolls if they truly
            // can't fit (very large text, split screen).
            val screen = maxHeight
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = screen)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    top()
                    greetingBlock(false)
                    mainActions()
                    todayStrip()
                    daily()
                    tutorSection()
                    thingsGrid()
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
            Text(chinese, color = P.Faint, fontSize = 15.sp, modifier = Modifier.padding(top = 3.dp))
            almanac?.let {
                Text(
                    it.text, color = if (it.special) P.Accent else P.Faint, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)
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
            .padding(bottom = 2.dp)
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
    Column(modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 6.dp)) {
        Text(
            "WORD · ${languageMark(language, script)}", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium
        )
        Text(written, color = P.Text, fontSize = 24.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 4.dp))
        Text(word.reading, color = P.Muted, fontSize = 13.sp)
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
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Text("IDIOM", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        Text(idiom.written(traditional), color = P.Text, fontSize = 24.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 4.dp))
        Text(idiom.pinyin, color = P.Muted, fontSize = 13.sp)
        Text(idiom.meaning, color = P.Text, fontSize = 14.sp, lineHeight = 19.sp, maxLines = if (story) 4 else 2, overflow = TextOverflow.Ellipsis)
        if (story) {
            Text("Literally: ${idiom.literal}.", color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
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
            Text("Tap for its story", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
    Tone.QUIET -> P.Muted
    Tone.PROBLEM -> P.Danger
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 10.dp).semantics { heading() }
    )
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

@Composable
private fun TodayTile(item: TodayItem, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 100.dp)
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = if (item.tone == Tone.LOBSTER) P.Lobster else P.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
        }
        Text(item.value, color = toneColor(item.tone), fontSize = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.title, color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.detail, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Box(Modifier.size(36.dp).clip(CircleShape).background(P.AccentSoft), contentAlignment = Alignment.Center) {
                Text(languageMark(group.language, script), color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
            Text(
                tile.detail, color = if (tile.tone == Tone.ACCENT) P.Accent else P.Muted, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
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
