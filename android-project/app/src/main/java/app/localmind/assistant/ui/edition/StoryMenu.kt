package app.localmind.assistant.ui.edition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.edition.EdStory
import app.localmind.assistant.domain.edition.EditionSetup
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.ui.reader.readingMinutes
import app.localmind.assistant.ui.theme.SlothPalette as P

/** What can be done with a news story: from a tap, the long-press menu, or the clean view. */
internal class StoryActions(
    /** A tap: the page, or the clean text when its outlet is set that way. */
    val tap: (EdStory) -> Unit,
    val openPage: (EdStory) -> Unit,
    val readClean: (EdStory) -> Unit,
    val later: (EdStory) -> Unit,
    val ask: (EdStory) -> Unit,
    val setRead: (EdStory, Boolean) -> Unit,
    val setTap: (EdStory, Boolean) -> Unit,
    val place: (EdStory) -> Unit,
    /** A new idea on SlothDesk, its headline carried over (1.49). */
    val newIdea: ((EdStory) -> Unit)? = null
)

/** The long-press menu upright: a sheet from the bottom. (Sideways it's a popover beside the row.) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorySheet(
    story: EdStory,
    read: Boolean,
    shelved: Boolean,
    setup: EditionSetup,
    place: String?,
    now: Long,
    actions: StoryActions,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = P.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()
        ) {
            StoryMenu(story, read, shelved, setup, place, now, actions, onDismiss)
        }
    }
}

/**
 * The menu itself: the story's head, then open the page, read it clean (or why it's page only),
 * save for later, ask about it, start an idea from it, its place on the Atlas, read or unread,
 * and what a tap on this outlet does.
 */
@Composable
internal fun StoryMenu(
    story: EdStory,
    read: Boolean,
    shelved: Boolean,
    setup: EditionSetup,
    place: String?,
    now: Long,
    actions: StoryActions,
    onDismiss: () -> Unit
) {
    val clean = Editions.hasClean(story)
    val minutes = if (clean) readingMinutes(Editions.cleanText(story)) else 0
    fun go(f: (EdStory) -> Unit): () -> Unit = { onDismiss(); f(story) }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(story.title, color = P.Text, fontFamily = SERIF, fontSize = 18.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val small = listOf(Editions.smallPrint(story, now), if (clean) "$minutes min read" else "").filter { it.isNotBlank() }.joinToString(" · ")
        if (small.isNotBlank()) Text(small, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = P.Border)
    if (story.url.isNotBlank()) {
        MenuRow(Icons.Outlined.Public, "Open the page", "In Chrome, through the Lobster · ads and all", onClick = go(actions.openPage))
    }
    MenuRow(
        Icons.Outlined.Description, "Read it clean",
        when {
            clean -> "No ads · kept here, offline"
            story.pageOnly.isNotBlank() -> "Page only: ${story.pageOnly}"
            else -> "Page only: this paper came before the Lobster read stories clean"
        },
        enabled = clean, onClick = go(actions.readClean)
    )
    if (story.url.isNotBlank()) {
        MenuRow(
            Icons.Outlined.BookmarkBorder, if (shelved) "On your Later shelf" else "Save for later",
            when {
                shelved -> "Saved"
                clean -> "On the Later shelf at once"
                else -> "The Lobster fetches the whole article"
            },
            enabled = !shelved, onClick = go(actions.later)
        )
    }
    MenuRow(Icons.Outlined.ChatBubbleOutline, "Ask about it in a chat", if (clean) "Its text goes with it" else "Its summary goes with it", onClick = go(actions.ask))
    actions.newIdea?.let { f -> MenuRow(Icons.Outlined.Lightbulb, "New idea from this story", "On SlothDesk, the headline with it", onClick = go(f)) }
    if (place != null) MenuRow(Icons.Outlined.Place, "Show it on the Atlas", place, onClick = go(actions.place))
    MenuRow(
        if (read) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.Check,
        if (read) "Mark as unread" else "Mark as read", null,
        onClick = { onDismiss(); actions.setRead(story, !read) }
    )
    if (clean && story.outlet.isNotBlank()) {
        HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 6.dp))
        val tapsClean = Editions.tapsClean(setup, story)
        Text("A tap on ${story.outlet} opens", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("the page", !tapsClean) { actions.setTap(story, false) }
            Choice("clean text", tapsClean) { actions.setTap(story, true) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, detail: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) P.Muted else P.Faint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) P.Text else P.Faint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (!detail.isNullOrBlank()) Text(detail, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) P.AccentSoft else P.Elevated)
            .border(1.dp, if (on) P.Accent else P.Border, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp)
            .height(34.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (on) P.Accent else P.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
