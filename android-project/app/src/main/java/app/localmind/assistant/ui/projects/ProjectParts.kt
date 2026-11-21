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
import app.localmind.assistant.ui.common.SmallLabel
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
