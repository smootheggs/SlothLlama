package app.localmind.assistant.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.R
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.ui.chat.components.chineseToday
import app.localmind.assistant.ui.chat.components.clockText
import app.localmind.assistant.ui.chat.components.slothHello
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.delay

/** One shortcut on the Home page. */
data class HomeTile(val icon: ImageVector, val label: String, val detail: String, val onClick: () -> Unit)

/**
 * The main page: time and today in Chinese, a hello, the last chat, the tutors, recent chats,
 * and shortcuts to everything else. Two columns in landscape, one in portrait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    script: ChineseScript,
    conversations: List<Conversation>,
    personas: List<Persona>,
    tiles: List<HomeTile>,
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
    val tutors = personas.filter { it.speech != null }
    val personaOf = personas.associateBy { it.id }

    val header: @Composable () -> Unit = {
        Text(clockText(now), color = P.Muted, fontSize = 14.sp)
        Text(chineseToday(now, script), color = P.Faint, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.sloth_llama),
                contentDescription = null,
                modifier = Modifier.size(width = 58.dp, height = 50.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(hello, color = P.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
                Text(sub, color = P.Muted, fontSize = 16.sp)
            }
        }
    }
    val continueAndTutors: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (last != null) {
                BigCard(
                    label = "Continue",
                    text = last.title.ifBlank { "Last chat" },
                    detail = personaOf[last.personaId]?.let { "${it.badge} ${it.name}" }.orEmpty(),
                    modifier = Modifier.weight(1f)
                ) { onOpenConversation(last.id) }
            }
            BigCard(
                label = "New chat",
                text = "Ask anything",
                detail = "Private · on this tablet",
                modifier = Modifier.weight(1f),
                onClick = onNewChat
            )
        }
        if (tutors.isNotEmpty()) {
            SectionLabel("TUTORS")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tutors.forEach { tutor -> TutorChip(tutor) { onStartWith(tutor) } }
            }
        }
    }
    val shortcuts: @Composable () -> Unit = {
        SectionLabel("SHORTCUTS")
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile -> SmallTile(tile, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    val recent: @Composable () -> Unit = {
        val others = conversations.drop(1).take(5)
        if (others.isNotEmpty()) {
            SectionLabel("RECENT")
            others.forEach { conv ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenConversation(conv.id) }
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(personaOf[conv.personaId]?.badge ?: "💬", fontSize = 15.sp, modifier = Modifier.width(28.dp))
                    Text(
                        conv.title.ifBlank { "Chat" }, color = P.Text, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
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
        val wide = maxWidth >= WIDE
        if (wide) {
            // Landscape: greeting, continue and tutors on the left; shortcuts and recent on the right.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState())) {
                    header()
                    Spacer(Modifier.height(28.dp))
                    continueAndTutors()
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    shortcuts()
                    recent()
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    header()
                    Spacer(Modifier.height(24.dp))
                    continueAndTutors()
                    Spacer(Modifier.height(8.dp))
                    shortcuts()
                    recent()
                }
            }
        }
    }
}

/** Shortcuts every Home page has; patches can add more (e.g. Library). */
fun defaultHomeTiles(
    flashcardsDue: Int,
    modelStatus: String,
    onFlashcards: () -> Unit,
    onDictionary: () -> Unit,
    onPersonas: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onChats: () -> Unit,
    libraryDetail: String,
    onLibrary: () -> Unit
): List<HomeTile> = listOf(
    HomeTile(Icons.Outlined.Style, "Flashcards", if (flashcardsDue > 0) "$flashcardsDue due" else "Review words", onFlashcards),
    HomeTile(Icons.Outlined.Translate, "Dictionary", "Chinese · offline", onDictionary),
    HomeTile(Icons.Outlined.LocalLibrary, "Library", libraryDetail, onLibrary),
    HomeTile(Icons.Outlined.ChatBubbleOutline, "Chats", "All conversations", onChats),
    HomeTile(Icons.Outlined.PersonOutline, "Personas", "Tutors and assistants", onPersonas),
    HomeTile(Icons.Outlined.Memory, "Models", modelStatus.ifBlank { "Choose a model" }, onModels),
    HomeTile(Icons.Outlined.Settings, "Settings", "Look, voice, backups", onSettings)
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun BigCard(label: String, text: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label == "New chat") {
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = P.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = P.Faint, fontSize = 12.sp)
        }
        Text(text, color = P.Text, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail.isNotBlank()) Text(detail, color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TutorChip(tutor: Persona, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tutor.badge, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(tutor.speech?.label ?: tutor.name, color = P.Text, fontSize = 14.sp)
    }
}

@Composable
private fun SmallTile(tile: HomeTile, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(1.dp, P.Border, shape)
            .clickable(onClick = tile.onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tile.icon, contentDescription = null, tint = P.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(tile.label, color = P.Text, fontSize = 15.sp)
            Text(tile.detail, color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Screens at least this wide get two panes (a Y700 in landscape is about 1,280 dp). */
val WIDE = 840.dp
