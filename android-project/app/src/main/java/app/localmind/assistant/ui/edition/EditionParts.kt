package app.localmind.assistant.ui.edition

import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.dictionary.Segment
import app.localmind.assistant.domain.edition.EdStory
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.edition.MarketTile
import app.localmind.assistant.ui.theme.SlothPalette as P

internal val SERIF = FontFamily.Serif

/**
 * The masthead: the paper's name, its motto in italics, a double rule, then the dateline (edition,
 * day, stories, listening time) between rules.
 */
@Composable
internal fun Masthead(line: String, compact: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("The Sloth Express", color = P.Text, fontSize = if (compact) 30.sp else 38.sp, fontFamily = SERIF, textAlign = TextAlign.Center)
        Text(
            Editions.MOTTO, color = P.Muted, fontSize = if (compact) 14.sp else 15.sp, fontFamily = SERIF, fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = P.BorderStrong)
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = P.BorderStrong)
        Text(
            line, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.6.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 7.dp)
        )
        HorizontalDivider(color = P.BorderStrong)
    }
}

/** A section's head: a rule, its name in small capitals, a note and whatever goes on the right. */
@Composable
internal fun SectionHead(name: String, note: String? = null, modifier: Modifier = Modifier, right: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp)) {
        HorizontalDivider(color = P.BorderStrong)
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name.uppercase(), color = P.Muted, fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (note != null) Text(note, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            right()
        }
    }
}

/**
 * A news story. A tap opens it: its page, through the Lobster, or its clean text when its outlet
 * is set that way. A long press opens the menu ([onMenu]); sideways the menu is a popover beside
 * the row ([popover], while [popoverOpen]). The bookmark saves it for later.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StoryRow(
    story: EdStory,
    big: Boolean,
    read: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onLater: () -> Unit,
    shelved: Boolean = false,
    tapsClean: Boolean = false,
    /** The place the story names most (found on the tablet), and opening it in the Atlas. */
    place: String? = null,
    onPlace: () -> Unit = {},
    popoverOpen: Boolean = false,
    onPopoverDismiss: () -> Unit = {},
    popover: @Composable () -> Unit = {}
) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClickLabel = if (tapsClean) "Read it clean" else "Open the page",
                    onLongClickLabel = "More for this story",
                    onClick = onOpen,
                    onLongClick = onMenu
                )
                .padding(vertical = if (big) 12.dp else 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    story.title, color = if (read) P.Faint else P.Text, fontFamily = SERIF,
                    fontSize = if (big) 23.sp else 17.sp, lineHeight = if (big) 29.sp else 23.sp
                )
                if (story.summary.isNotBlank() && !read) {
                    Text(story.summary, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = if (big) 4 else 2, overflow = TextOverflow.Ellipsis)
                }
                val small = Editions.smallPrint(story, now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (read) Icon(Icons.Outlined.Check, contentDescription = "Read", tint = P.Faint, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                    if (small.isNotBlank()) Text(small, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    // Clean text is what a tap opens (1.45); a story that opens its page says so.
                    if (!tapsClean && story.url.isNotBlank()) Text("  ·  page", color = P.Faint, fontSize = 12.sp, maxLines = 1)
                    if (place != null) PlaceLink(place, onPlace)
                }
            }
            if (story.url.isNotBlank()) {
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !shelved, onClickLabel = "Save for later", role = Role.Button, onClick = onLater)
                        .semantics { contentDescription = if (shelved) "On your Later shelf" else "Save for later" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = if (shelved) P.Accent else P.Faint, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (popoverOpen) {
            Box(Modifier.align(Alignment.TopEnd)) {
                DropdownMenu(
                    expanded = true, onDismissRequest = onPopoverDismiss,
                    modifier = Modifier.width(380.dp).background(P.Card)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { popover() }
                }
            }
        }
    }
}

/** A place a story names: a quiet link to it in the Atlas. */
@Composable
internal fun PlaceLink(name: String, onClick: () -> Unit) {
    Text(
        "  ·  $name ›", color = P.Accent, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Show $name in the Atlas", role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

/** The markets strip: SlothDesk's latest closes and moves, worked out on the tablet. */
@Composable
internal fun MarketStrip(tiles: List<MarketTile>) {
    if (tiles.isEmpty()) {
        Text(
            "Prices show here once SlothDesk has them (SlothDesk → daily prices from the Lobster).",
            color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
        )
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { t ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(P.Card).padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(t.name, color = P.Faint, fontSize = 12.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(t.value, color = P.Text, fontSize = 15.sp, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    val arrow = when (t.up) { true -> "▲ "; false -> "▼ "; null -> "" }
                    Text(arrow + t.change, color = P.Muted, fontSize = 12.sp, maxLines = 1)
                }
                Text("${t.asOf} · ${t.label}", color = P.Faint, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** A round badge: a tutor's 普 or the Trader's Au. */
@Composable
internal fun Badge(text: String, size: Dp = 28.dp, tone: Color? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(P.AccentSoft), contentAlignment = Alignment.Center) {
        Text(text, color = tone ?: P.Accent, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Chinese text word by word, each with its reading above when [readings] is on, in simplified
 * or traditional characters. Words above your level get a faint line under them; a tap on a word
 * looks it up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RubyText(
    segments: List<Segment>,
    readings: Boolean,
    traditional: Boolean,
    size: TextUnit,
    hard: Set<String> = emptySet(),
    color: Color = P.Text,
    onWord: ((String) -> Unit)? = null
) {
    FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { seg ->
            val reading = seg.reading
            Column(
                Modifier
                    .then(
                        if (reading != null && onWord != null) Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClickLabel = "Look up ${seg.text}") { onWord(seg.text) }
                        else Modifier
                    )
                    .padding(horizontal = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (readings) {
                    Text(reading.orEmpty(), color = P.Faint, fontSize = (size.value * 0.42f).sp, maxLines = 1)
                }
                val line = P.Faint
                val underline = seg.text in hard
                Text(
                    seg.shown(traditional), color = color, fontSize = size, fontFamily = SERIF,
                    modifier = Modifier.drawBehind {
                        if (underline) {
                            drawLine(
                                line, Offset(0f, this.size.height - 1f), Offset(this.size.width, this.size.height - 1f),
                                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                            )
                        }
                    }
                )
            }
        }
    }
}
