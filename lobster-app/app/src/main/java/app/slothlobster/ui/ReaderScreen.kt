package app.slothlobster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slothlobster.core.Delivered
import app.slothlobster.core.KeptCatch
import app.slothlobster.core.Live
import app.slothlobster.core.Saved

/** One whole catch, as it went to SlothLlama: headings, lists and links, readable here. */
@Composable
fun ReaderScreen(
    id: String,
    now: Long,
    jobId: String?,
    load: suspend (String) -> KeptCatch?,
    onBack: () -> Unit,
    onOpenJob: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val loaded by produceState<Loaded?>(initialValue = null, id) { value = Loaded(load(id)) }
    val kept = loaded?.kept
    val blocks = remember(kept) { kept?.let { parseBlocks(it.text) }.orEmpty() }
    val uri = LocalUriHandler.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ScreenHeader(
                title = kept?.title ?: if (loaded == null) "Opening…" else "Not kept",
                subtitle = kept?.let { k ->
                    listOfNotNull(
                        kindName(k.kind),
                        ago(k.createdAt, now),
                        k.sources.size.takeIf { it > 0 }?.let { if (it == 1) "1 source" else "$it sources" },
                        k.fileName?.let { "file sent to SlothLlama: $it" }
                    ).joinToString(" · ")
                },
                onBack = onBack
            )
        }
        if (jobId != null) {
            item {
                Row(Modifier.padding(start = 16.dp, bottom = 8.dp)) { GhostButton("See the order", { onOpenJob(jobId) }) }
            }
        }
        if (loaded != null && kept == null) {
            item {
                Text(
                    "Only the last hundred catches are kept here; this one may have been cleared.",
                    modifier = Modifier.padding(16.dp),
                    color = Sea.Muted,
                    fontSize = 14.sp
                )
            }
        }
        items(blocks) { b -> Block(b) }
        val sources = kept?.sources.orEmpty()
        if (sources.isNotEmpty()) {
            item {
                Section("Sources", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    Panel {
                        sources.forEachIndexed { i, s ->
                            ListRow(
                                "[${i + 1}] ${s.title.ifBlank { s.url }}",
                                s.url,
                                onClick = { runCatching { uri.openUri(s.url) } },
                                divider = i < sources.size - 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Everything brought back, newest first, grouped by day. */
@Composable
fun BroughtBackScreen(saved: Saved, live: Live, now: Long, onBack: () -> Unit, onOpen: (Delivered) -> Unit, modifier: Modifier = Modifier) {
    val days = saved.delivered.groupBy { dayOf(it.at) }.toList()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Brought back", "The last hundred, readable here", onBack = onBack) }
        if (days.isEmpty()) {
            item { Text("Nothing yet.", Modifier.padding(horizontal = 16.dp), color = Sea.Muted, fontSize = 14.sp) }
        }
        items(days, key = { it.first }) { (_, list) ->
            Section(dayLabel(list.first().at, now), Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    list.forEachIndexed { i, d ->
                        ListRow(
                            d.title,
                            listOf(d.subtitle, clockTime(d.at)).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { onOpen(d) },
                            divider = i < list.size - 1
                        ) { DeliveredPill(d, live) }
                    }
                }
            }
        }
    }
}

private class Loaded(val kept: KeptCatch?)

private fun kindName(kind: String) = when (kind) {
    "alert" -> "Alert"
    "message" -> "Message"
    "note" -> "Note"
    else -> "Document"
}

// --- a small Markdown reader: headings, lists, quotes, links, bold ---

private sealed interface Md {
    data class Heading(val level: Int, val text: String) : Md
    data class Bullet(val text: String) : Md
    data class Quote(val text: String) : Md
    data class Para(val text: String, val quiet: Boolean = false) : Md
}

private fun parseBlocks(text: String): List<Md> = text.lines().mapNotNull { raw ->
    val line = raw.trimEnd()
    when {
        line.isBlank() -> null
        line.startsWith("#") -> Md.Heading(line.takeWhile { it == '#' }.length.coerceIn(1, 4), line.trimStart('#').trim())
        line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Md.Bullet(line.trimStart().drop(2))
        line.startsWith("> ") -> Md.Quote(line.drop(2))
        line.startsWith("_") && line.endsWith("_") && line.length > 2 -> Md.Para(line.trim('_'), quiet = true)
        else -> Md.Para(line)
    }
}

@Composable
private fun Block(b: Md) {
    when (b) {
        is Md.Heading -> Text(
            b.text,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (b.level <= 2) 18.dp else 12.dp, bottom = 6.dp),
            color = Sea.Text,
            fontSize = when (b.level) { 1 -> 21.sp; 2 -> 18.sp; else -> 16.sp },
            fontWeight = FontWeight.SemiBold
        )
        is Md.Bullet -> Row(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 6.dp)) {
            Text("•", color = Sea.Coral, fontSize = 15.sp, modifier = Modifier.width(16.dp))
            SelectionContainer { Text(richText(b.text), color = Sea.Soft, fontSize = 15.sp, lineHeight = 22.sp) }
        }
        is Md.Quote -> Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Box(Modifier.width(3.dp).height(22.dp).background(Sea.Coral))
            SelectionContainer {
                Text(richText(b.text), modifier = Modifier.padding(start = 10.dp), color = Sea.Muted, fontSize = 15.sp, fontStyle = FontStyle.Italic)
            }
        }
        is Md.Para -> SelectionContainer {
            Text(
                richText(b.text),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                color = if (b.quiet) Sea.Muted else Sea.Soft,
                fontSize = if (b.quiet) 13.sp else 15.sp,
                lineHeight = if (b.quiet) 19.sp else 23.sp,
                fontStyle = if (b.quiet) FontStyle.Italic else FontStyle.Normal,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private val INLINE = Regex("""\[([^\]]+)\]\((https?://[^)\s]+)\)|\*\*([^*]+)\*\*|(https://[^\s)]+)""")

/** Links you can tap (they open in the browser) and **bold**; everything else as written. */
@Composable
private fun richText(text: String): AnnotatedString {
    val link = Sea.Coral
    val strong = Sea.Text
    return remember(text, link, strong) { richText(text, link, strong) }
}

private fun richText(text: String, link: androidx.compose.ui.graphics.Color, strong: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
    var at = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(at, m.range.first))
        val (label, url, bold, bare) = m.destructured
        when {
            url.isNotEmpty() -> withLink(LinkAnnotation.Url(url, linkStyle)) { append(label) }
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(bold) }
            else -> withLink(LinkAnnotation.Url(bare, linkStyle)) { append(bare) }
        }
        at = m.range.last + 1
    }
    if (at < text.length) append(text.substring(at))
}
