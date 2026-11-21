package app.localmind.assistant.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.projects.Rag
import app.localmind.assistant.ui.chat.components.MarkdownContent
import app.localmind.assistant.ui.theme.SlothPalette as P

/** The colour of a status you gave: green, amber, red. */
@Composable
internal fun ragColor(rag: Rag): Color = when (rag) {
    Rag.ON_TRACK -> P.Success
    Rag.AT_RISK -> P.Warning
    Rag.OFF_TRACK -> P.Danger
}

@Composable
internal fun StatusMark(rag: Rag?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(9.dp).clip(CircleShape)
                .background(if (rag == null) Color.Transparent else ragColor(rag))
                .border(1.dp, if (rag == null) P.BorderStrong else ragColor(rag), CircleShape)
        )
        Text(rag?.label ?: "No status yet", color = if (rag == null) P.Faint else ragColor(rag), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SmallLabel(text: String, modifier: Modifier = Modifier, color: Color = P.Faint) {
    Text(text.uppercase(), modifier = modifier, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
}

/** A thin bar for done ÷ total, drawn from the percent the code worked out. */
@Composable
internal fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.Elevated)) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.Accent))
    }
}

/** A pill you can choose; the chosen one is filled. */
@Composable
internal fun ChoicePill(text: String, chosen: Boolean, onClick: () -> Unit, color: Color = P.Accent, lead: ImageVector? = null) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (chosen) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (chosen) color else P.Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (lead != null) Icon(lead, contentDescription = null, tint = if (chosen) color else P.Muted, modifier = Modifier.size(16.dp))
        Text(text, color = if (chosen) P.Text else P.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** One part of a project page: a label, an Add button, and its rows. */
@Composable
internal fun Section(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "Add",
    content: @Composable () -> Unit
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel(title, Modifier.weight(1f).padding(vertical = 12.dp))
            if (onAdd != null) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = P.Accent, modifier = Modifier.size(18.dp))
                    Text(" $addLabel", color = P.Accent, fontSize = 14.sp)
                }
            }
        }
        if (note != null) Text(note, color = P.Faint, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
        content()
    }
}

/** A tappable row in a section: what it is, a line under it, and room for marks on the right. */
@Composable
internal fun ItemRow(
    title: String,
    line: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lineColor: Color = P.Muted,
    struck: Boolean = false,
    lead: @Composable (() -> Unit)? = null,
    trail: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (lead != null) lead()
        Column(Modifier.weight(1f)) {
            Text(
                title, color = if (struck) P.Muted else P.Text, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (struck) TextDecoration.LineThrough else null
            )
            if (!line.isNullOrBlank()) Text(line, color = lineColor, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trail()
    }
}

@Composable
internal fun EmptyLine(text: String) {
    Text(text, color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp, bottom = 6.dp))
}

/**
 * A document: "# " is its title, "## " a heading, and the rest is the chat's markdown (lists,
 * bold). Model replies that use either style read the same way.
 */
@Composable
internal fun DocText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { docBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { (kind, body) ->
            when (kind) {
                1 -> Text(body, color = P.Text, fontSize = 26.sp, lineHeight = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                2 -> SmallLabel(body, Modifier.padding(top = 8.dp), color = P.Muted)
                else -> MarkdownContent(body)
            }
        }
    }
}

/** (1 = title, 2 = heading, 0 = markdown body) in order. */
internal fun docBlocks(text: String): List<Pair<Int, String>> {
    val out = mutableListOf<Pair<Int, String>>()
    val body = StringBuilder()
    fun flush() {
        val b = body.toString().trim('\n')
        if (b.isNotBlank()) out += 0 to b
        body.clear()
    }
    text.lines().forEach { line ->
        val t = line.trim()
        when {
            t.startsWith("# ") -> { flush(); out += 1 to t.removePrefix("# ").trim() }
            t.startsWith("## ") || t.startsWith("### ") -> { flush(); out += 2 to t.trimStart('#').trim() }
            else -> body.appendLine(line)
        }
    }
    flush()
    return out
}
