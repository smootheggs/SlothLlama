package app.localmind.assistant.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.R
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.chat.components.chineseToday
import app.localmind.assistant.ui.chat.components.clockText
import app.localmind.assistant.ui.chat.components.slothHello
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.delay

/** How a tile's number or icon is coloured: the accent, the Lobster's coral, quiet, or a problem. */
enum class Tone { ACCENT, LOBSTER, QUIET, PROBLEM }

/** One shortcut on the Home page: an icon, a name and a live status line. */
data class HomeTile(
    val icon: ImageVector,
    val label: String,
    val detail: String,
    val onClick: () -> Unit,
    val tone: Tone = Tone.QUIET
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

/**
 * The main page. Top: the date (and today in Chinese) with search and settings. Then a hello,
 * the two main actions (continue the last chat, start a new one), Today (what's due or new),
 * the tutors one chip per language, your things with live status, and a quiet row for the rest.
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
    onStartWith: (Persona) -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    val (hello, sub) = remember(now / 3_600_000) { slothHello(now) }
    val last = conversations.firstOrNull()
    val personaOf = personas.associateBy { it.id }
    val tutorGroups = remember(personas) { tutorGroups(personas) }

    val top: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(clockText(now), color = P.Muted, fontSize = 15.sp)
                Text(chineseToday(now, script), color = P.Faint, fontSize = 15.sp, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = "Search chats", tint = P.Muted)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = P.Muted)
            }
        }
    }
    val greeting: @Composable (Boolean) -> Unit = { compact ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.sloth_llama),
                contentDescription = null,
                modifier = Modifier.size(width = if (compact) 58.dp else 66.dp, height = if (compact) 50.dp else 58.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    hello, color = P.Text, fontSize = if (compact) 30.sp else 34.sp, fontWeight = FontWeight.Medium,
                    lineHeight = if (compact) 36.sp else 40.sp, modifier = Modifier.semantics { heading() }
                )
                Text(sub, color = P.Muted, fontSize = 17.sp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    today.forEach { item -> TodayTile(item, Modifier.weight(1f)) }
                }
            }
        }
    }
    val tutorSection: @Composable () -> Unit = {
        if (tutorGroups.isNotEmpty()) {
            Column {
                SectionLabel("Tutors")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tutorGroups.forEach { group -> TutorChip(group, script, onStartWith) }
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
            // Landscape: hello, main actions and Today on the left; tutors and your things on the right.
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp)) {
                top()
                Row(Modifier.weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        greeting(true)
                        mainActions()
                        todayStrip()
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
                    greeting(false)
                    mainActions()
                    todayStrip()
                    tutorSection()
                    thingsGrid()
                    moreRow()
                }
            }
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

/** One chip per language; with more than one tutor it asks which. */
@Composable
private fun TutorChip(group: TutorGroup, script: ChineseScript, onStartWith: (Persona) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    val roles = group.tutors.map { roleOf(it, group.language) }
    Box {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .clip(shape)
                .border(1.dp, P.Border, shape)
                .clickable(role = Role.Button, onClickLabel = "Talk with the ${group.language.label} tutor") {
                    if (group.tutors.size == 1) onStartWith(group.tutors.first()) else choosing = true
                }
                .padding(start = 7.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(P.AccentSoft), contentAlignment = Alignment.Center) {
                Text(languageMark(group.language, script), color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(group.language.label, color = P.Text, fontSize = 16.sp)
                Text(roles.joinToString(" · "), color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Icon(tile.icon, contentDescription = null, tint = if (tile.tone == Tone.LOBSTER) P.Lobster else P.Muted, modifier = Modifier.size(24.dp))
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
