package app.localmind.assistant.ui.reader

import app.localmind.assistant.domain.edition.Editions
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.localmind.assistant.ui.theme.LocalSlothColors
import app.localmind.assistant.ui.theme.LightColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.library.LibraryKind
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * The reader. On a wide screen the shelf (the Lobster's catch, the library, notes) sits on the
 * left and the text on the right; in portrait it's one or the other. Tap a link to have the
 * Lobster fetch it; press and hold a paragraph to save it or ask the chat about it.
 *
 * [onCatchToLibrary], [onCatchToNotes] and [onCatchDismiss] file a catch the way the Lobster
 * page does (and take it out of the catch), given its file name.
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onCatchToLibrary: (String) -> Unit,
    onCatchToNotes: (String) -> Unit,
    onCatchDismiss: (String) -> Unit,
    /** A place the text names, opened in the Atlas (by its country). */
    onPlace: (app.localmind.assistant.domain.atlas.PlaceHit) -> Unit = {}
) {
    val doc by viewModel.doc.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val white by viewModel.whitePage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Opened from the shelf (not from the Lobster page or the library): Back returns to the shelf.
    var viaShelf by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    WhitePage(white, statusBar = true) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE
            val current = doc
            BackHandler(enabled = current != null && viaShelf && !wide) {
                viewModel.close()
                viaShelf = false
            }
            val openFromShelf: (ReaderSource, String) -> Unit = { s, r ->
                viaShelf = true
                viewModel.open(s, r)
            }
            val leave: () -> Unit = {
                if (current != null && viaShelf && !wide) {
                    viewModel.close()
                    viaShelf = false
                } else {
                    viewModel.close()
                    onBack()
                }
            }
            val filed: (String) -> Unit = {
                viewModel.close()
                if (!viaShelf && !wide) onBack()
            }
            Column(Modifier.fillMaxSize()) {
                if (current == null) {
                    SlothTopBar(title = "Reader", subtitle = "Later, catch, library and notes · offline", onBack = leave)
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(340.dp).fillMaxHeight()) {
                            if (current != null) {
                                SlothTopBar(title = "Reader", subtitle = "Catch, library and notes", onBack = leave)
                            }
                            Shelf(viewModel, selectedKey = current?.key, onOpen = openFromShelf)
                        }
                        VerticalDivider(color = P.Border)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (current != null) {
                                key(current.key) {
                                    Reading(
                                        viewModel, current, onBack = { viewModel.close() },
                                        onCatchToLibrary = { onCatchToLibrary(it); filed(it) },
                                        onCatchToNotes = { onCatchToNotes(it); filed(it) },
                                        onCatchDismiss = { onCatchDismiss(it); filed(it) },
                                        onPlace = onPlace
                                    )
                                }
                            } else {
                                Empty(if (loading) null else "Pick something on the left to read it here.")
                            }
                        }
                    }
                } else if (current != null) {
                    key(current.key) {
                        Reading(
                            viewModel, current, onBack = leave,
                            onCatchToLibrary = { onCatchToLibrary(it); filed(it) },
                            onCatchToNotes = { onCatchToNotes(it); filed(it) },
                            onCatchDismiss = { onCatchDismiss(it); filed(it) },
                            onPlace = onPlace
                        )
                    }
                } else if (loading) {
                    Empty(null)
                } else {
                    Shelf(viewModel, selectedKey = null, onOpen = openFromShelf)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A reading page on white when that's chosen, on the app's own colours otherwise. With [statusBar]
 * the page has the screen's top edge, so the status bar's icons turn dark to stay readable; a page
 * beside the Express leaves the status bar to the paper.
 */
@Composable
internal fun WhitePage(white: Boolean, statusBar: Boolean, content: @Composable BoxScope.() -> Unit) {
    val outer = LocalSlothColors.current
    if (statusBar) {
        val view = LocalView.current
        DisposableEffect(white, outer.isDark) {
            val window = view.context.findActivity()?.window
            val bars = window?.let { WindowInsetsControllerCompat(it, view) }
            val before = bars?.isAppearanceLightStatusBars
            bars?.isAppearanceLightStatusBars = white || !outer.isDark
            onDispose { if (before != null) bars?.isAppearanceLightStatusBars = before }
        }
    }
    CompositionLocalProvider(LocalSlothColors provides if (white) LightColors else outer) {
        Box(Modifier.fillMaxSize().background(P.Canvas), content = content)
    }
}

/** The activity behind a context, if there is one. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

// --- the shelf ---

private enum class ShelfTab(val label: String) { LATER("Later"), CATCH("Catch"), LIBRARY("Library"), NOTES("Notes") }

private data class ShelfRow(
    val source: ReaderSource,
    val ref: String,
    val key: String,
    val title: String,
    val detail: String,
    /** How far it's been read (Later only); null hides the bar. */
    val progress: Float? = null
)

@Composable
private fun Shelf(viewModel: ReaderViewModel, selectedKey: String?, onOpen: (ReaderSource, String) -> Unit) {
    val mailbox by viewModel.mailboxState.collectAsStateWithLifecycle()
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val notes by viewModel.noteList.collectAsStateWithLifecycle()
    val later by viewModel.laterItems.collectAsStateWithLifecycle()
    val toRead = later.filter { !it.archived }
    var tabName by rememberSaveable {
        mutableStateOf(
            when {
                toRead.isNotEmpty() -> ShelfTab.LATER.name
                mailbox.enabled && mailbox.catch.isNotEmpty() -> ShelfTab.CATCH.name
                else -> ShelfTab.LIBRARY.name
            }
        )
    }
    val tab = ShelfTab.valueOf(tabName)
    var filter by rememberSaveable { mutableStateOf("") }
    var archive by rememberSaveable { mutableStateOf(false) }

    val rows = when (tab) {
        ShelfTab.LATER -> later.filter { it.archived == archive }.map { item ->
            ShelfRow(
                ReaderSource.LATER, item.id, "later:${item.id}",
                (if (item.favourite) "★ " else "") + item.title,
                listOfNotNull(
                    item.site.takeIf { it.isNotBlank() },
                    if (!item.readable) "no readable copy" else if (item.progress > 0f && !item.archived) "${item.minutesLeft} min left" else "${item.minutes} min"
                ).joinToString(" · "),
                progress = item.progress.takeIf { !item.archived && it > 0f }
            )
        }
        ShelfTab.CATCH -> mailbox.catch.mapNotNull { ci ->
            ci.item?.takeIf { it.kind != "prices" && it.kind != "sports" && it.kind != "world" }?.let { c -> ShelfRow(ReaderSource.CATCH, ci.fileName, "catch:${c.id}", c.title, c.kind.replaceFirstChar { it.uppercase() }) }
        }
        ShelfTab.LIBRARY -> library.orEmpty().filter { it.kind == LibraryKind.DOCUMENT }.map {
            ShelfRow(ReaderSource.LIBRARY, it.id.toString(), "lib:${it.id}", it.name, it.detail)
        }
        ShelfTab.NOTES -> notes.map { n ->
            val (title, body) = ReaderViewModel.splitNote(n.text)
            ShelfRow(ReaderSource.NOTE, n.id, "note:${n.id}", title, body.replace('\n', ' ').take(90))
        }
    }.filter { filter.isBlank() || it.title.contains(filter.trim(), true) || it.detail.contains(filter.trim(), true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShelfTab.entries.forEach { t ->
                val count = when (t) {
                    ShelfTab.LATER -> toRead.size
                    ShelfTab.CATCH -> mailbox.catch.count { it.item != null && it.item.kind != "prices" && it.item.kind != "sports" && it.item.kind != "world" }
                    ShelfTab.LIBRARY -> library.orEmpty().count { it.kind == LibraryKind.DOCUMENT }
                    ShelfTab.NOTES -> notes.size
                }
                Chip(if (count > 0) "${t.label} · $count" else t.label, t == tab) { tabName = t.name; archive = false }
            }
        }
        if (tab == ShelfTab.LATER) {
            val archived = later.count { it.archived }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (toRead.isEmpty()) "To read" else "To read · ${toRead.size} · ${toRead.sumOf { it.minutesLeft }} min", !archive) { archive = false }
                Chip(if (archived > 0) "Archive · $archived" else "Archive", archive) { archive = true }
            }
        }
        Field(
            value = filter, onChange = { filter = it }, hint = "Filter by title",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        if (rows.isEmpty()) {
            Text(
                when {
                    filter.isNotBlank() -> "Nothing matches."
                    tab == ShelfTab.LATER && archive -> "Nothing archived yet. Finish an article and it lands here."
                    tab == ShelfTab.LATER -> "Nothing saved yet. In any app, share a link and pick \"Save for later\": " +
                        "the Lobster brings back a clean copy to read here, offline."
                    tab == ShelfTab.CATCH && !mailbox.enabled -> "The Lobster is off. Turn it on from its page to have it fetch things for you."
                    tab == ShelfTab.CATCH -> "Nothing brought back yet. Briefs and fetched pages land here."
                    tab == ShelfTab.LIBRARY -> "No documents yet. Add them on the Library page."
                    else -> "No notes yet."
                },
                color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 16.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            itemsIndexed(rows, key = { _, r -> r.key }) { _, r ->
                val selected = r.key == selectedKey
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) P.AccentSoft else Color.Transparent)
                        .clickable { onOpen(r.source, r.ref) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(r.title, color = P.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r.detail.isNotBlank()) {
                        Text(r.detail, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    r.progress?.let { p -> ProgressLine(p, Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}

// --- reading ---

/**
 * One text open for reading. Also the Express's clean view (1.43): [onOpenPage] opens an Express
 * story's page through the Lobster.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun Reading(
    viewModel: ReaderViewModel,
    doc: ReaderDoc,
    onBack: () -> Unit,
    onCatchToLibrary: (String) -> Unit,
    onCatchToNotes: (String) -> Unit,
    onCatchDismiss: (String) -> Unit,
    onPlace: (app.localmind.assistant.domain.atlas.PlaceHit) -> Unit,
    onOpenPage: ((String) -> Unit)? = null,
    /** Whether this page has the screen's top edge (so the status bar follows a white page). */
    topOfScreen: Boolean = false
) {
    // The page's own colours, wherever it's shown: on the Reader screen and in the Express alike.
    val white by viewModel.whitePage.collectAsStateWithLifecycle()
    WhitePage(white, statusBar = topOfScreen) {
        ReadingPage(viewModel, doc, onBack, onCatchToLibrary, onCatchToNotes, onCatchDismiss, onPlace, onOpenPage)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ReadingPage(
    viewModel: ReaderViewModel,
    doc: ReaderDoc,
    onBack: () -> Unit,
    onCatchToLibrary: (String) -> Unit,
    onCatchToNotes: (String) -> Unit,
    onCatchDismiss: (String) -> Unit,
    onPlace: (app.localmind.assistant.domain.atlas.PlaceHit) -> Unit,
    onOpenPage: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val scale by viewModel.scale.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    val mailbox by viewModel.mailboxState.collectAsStateWithLifecycle()
    val lobsterOn = mailbox.enabled && mailbox.folderName != null
    val blocks = doc.blocks
    val start = remember(doc.key) { viewModel.position(doc.key).coerceIn(0, blocks.size) }
    // Item 0 is the title; block i is item i + 1.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val scope = rememberCoroutineScope()

    var finding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hit by rememberSaveable { mutableStateOf(0) }
    val matches = remember(blocks, query) { findMatches(blocks, query) }
    var linkMenu by remember { mutableStateOf<String?>(null) }
    var passageMenu by remember { mutableStateOf<String?>(null) }
    val extraSources = remember(doc) { doc.sources.filter { s -> s.url.isNotBlank() && !doc.text.contains(s.url) } }
    val laterItems by viewModel.laterItems.collectAsStateWithLifecycle()
    val laterItem = if (doc.source == ReaderSource.LATER) laterItems.firstOrNull { it.id == doc.ref } else null
    val express = doc.source == ReaderSource.EXPRESS
    val onShelf = express && laterItems.any { it.url == doc.url && !it.archived }

    // Read aloud: the page follows the paragraph being read.
    var listening by remember(doc.key) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var spokenBlock by remember { mutableStateOf(-1) }
    var rateIndex by rememberSaveable { mutableStateOf(1) }
    // The speech engine starts only when you first ask to listen.
    val voiceHolder = remember(doc.key) { arrayOfNulls<ReadAloud>(1) }
    fun voice(): ReadAloud = voiceHolder[0]
        ?: ReadAloud(context, onBlock = { spokenBlock = it }, onEnd = { playing = false; spokenBlock = -1 }).also { voiceHolder[0] = it }
    DisposableEffect(doc.key) { onDispose { voiceHolder[0]?.shutdown() } }
    LaunchedEffect(spokenBlock) {
        if (spokenBlock >= 0) {
            val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
            if (spokenBlock + 1 !in visible.drop(1).dropLast(1)) list.animateScrollToItem(spokenBlock + 1)
        }
    }

    // Remembers where reading stopped, a moment after scrolling settles and on leaving. A Later
    // article also keeps how far through it you are.
    LaunchedEffect(doc.key) {
        snapshotFlow { list.firstVisibleItemIndex to list.canScrollForward }.debounce(800).collect { (first, more) ->
            viewModel.savePosition(doc.key, first)
            if (doc.source == ReaderSource.LATER && blocks.isNotEmpty()) {
                viewModel.laterProgress(doc.ref, if (!more) 1f else first.toFloat() / blocks.size)
            }
        }
    }
    DisposableEffect(doc.key) {
        onDispose { viewModel.savePosition(doc.key, list.firstVisibleItemIndex) }
    }
    // A new search goes to its first match.
    LaunchedEffect(matches) {
        if (matches.isNotEmpty()) list.animateScrollToItem(matches[0] + 1)
    }
    fun jump(to: Int) {
        if (matches.isEmpty()) return
        hit = ((to % matches.size) + matches.size) % matches.size
        scope.launch { list.animateScrollToItem(matches[hit] + 1) }
    }

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = if (express) "Read clean" else "Reader",
            subtitle = doc.meta,
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    if (listening) {
                        voiceHolder[0]?.pause(); playing = false; listening = false; spokenBlock = -1
                    } else {
                        listening = true; playing = true
                        voice().apply { setRate(ReadAloud.RATES[rateIndex]) }.play(blocks.map { it.text }, (list.firstVisibleItemIndex - 1).coerceAtLeast(0))
                    }
                }) {
                    Icon(Icons.Outlined.Headphones, contentDescription = if (listening) "Stop reading aloud" else "Read aloud", tint = if (listening) P.Accent else P.Muted)
                }
                val white by viewModel.whitePage.collectAsStateWithLifecycle()
                IconButton(onClick = viewModel::toggleWhitePage) {
                    Icon(
                        if (white) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = if (white) "Black page" else "White page", tint = P.Muted
                    )
                }
                SizeButton("A−", enabled = scale > ReaderViewModel.MIN_SCALE) { viewModel.smaller() }
                SizeButton("A+", enabled = scale < ReaderViewModel.MAX_SCALE) { viewModel.bigger() }
                IconButton(onClick = { finding = !finding }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Find in text", tint = if (finding) P.Accent else P.Muted)
                }
                IconButton(onClick = {
                    viewModel.markdown()?.let { md ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, doc.title)
                            putExtra(Intent.EXTRA_TEXT, md.take(MAX_SHARE_CHARS))
                        }
                        context.startActivity(Intent.createChooser(send, "Share as Markdown"))
                    }
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share as Markdown", tint = P.Muted)
                }
            }
        )
        if (finding) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Field(value = query, onChange = { query = it; hit = 0 }, hint = "Find in text", modifier = Modifier.weight(1f))
                Text(
                    when {
                        query.isBlank() -> ""
                        matches.isEmpty() -> "none"
                        else -> "${hit + 1} of ${matches.size}"
                    },
                    color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { jump(hit - 1) }, enabled = matches.isNotEmpty()) {
                    Icon(Icons.Outlined.ExpandLess, contentDescription = "Previous", tint = P.Muted)
                }
                IconButton(onClick = { jump(hit + 1) }, enabled = matches.isNotEmpty()) {
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "Next", tint = P.Muted)
                }
                IconButton(onClick = { finding = false; query = "" }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close find", tint = P.Muted)
                }
            }
        }
        HorizontalDivider(color = P.Border)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = list,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
            ) {
                item(key = "title") {
                    Column {
                        Text(doc.title, color = P.Text, fontSize = (24 * scale).sp, lineHeight = (31 * scale).sp, fontWeight = FontWeight.SemiBold)
                        Text("${doc.meta} · about ${readingMinutes(Editions.withoutAsides(doc.text))} min", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        if (express) {
                            ExpressActions(doc, onShelf, onOpenPage, onSave = viewModel::keepClean)
                            doc.picture?.let { StoryPicture(viewModel, it) }
                        }
                        if (places.isNotEmpty()) {
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Places", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                                places.forEach { hit ->
                                    Text(
                                        hit.named + " ›", color = P.Accent, fontSize = 13.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Show ${hit.named} in the Atlas") { onPlace(hit) }
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        if (blocks.isEmpty()) {
                            Text("This one has no text to show.", color = P.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
                itemsIndexed(blocks) { i, b ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (listening && spokenBlock == i) P.AccentSoft else Color.Transparent)
                    ) {
                        BlockView(
                            block = b,
                            scale = scale,
                            query = if (finding) query else "",
                            current = finding && matches.getOrNull(hit) == i,
                            picked = picked,
                            onLink = { linkMenu = it },
                            onPick = viewModel::togglePick,
                            onHold = { passageMenu = b.text }
                        )
                    }
                }
                if (laterItem != null && blocks.isNotEmpty()) {
                    item(key = "finished") {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            HorizontalDivider(color = P.Border, modifier = Modifier.padding(bottom = 20.dp))
                            if (laterItem.archived) {
                                Text("In your archive", color = P.Faint, fontSize = 13.sp)
                                Action("Put it back on the list", muted = true) { viewModel.restoreLater(laterItem.id) }
                            } else {
                                Text("The end", color = P.Faint, fontSize = 13.sp)
                                Action("Finished · archive it") { viewModel.finishLater(laterItem.id) }
                            }
                        }
                    }
                }
                if (express && blocks.isNotEmpty()) {
                    item(key = "express-end") {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            HorizontalDivider(color = P.Border, modifier = Modifier.padding(bottom = 20.dp))
                            Text("The end of the clean text", color = P.Faint, fontSize = 13.sp)
                            ExpressActions(doc, onShelf, onOpenPage, onSave = viewModel::keepClean)
                        }
                    }
                }
                if (extraSources.isNotEmpty() && !express) {
                    item(key = "sources") {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("SOURCES", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
                            extraSources.forEachIndexed { n, s ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${n + 1}. ${s.title.ifBlank { s.url }}",
                                        color = P.Accent, fontSize = (14 * scale).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).clickable { linkMenu = s.url }
                                    )
                                    PickMark(s.url in picked) { viewModel.togglePick(s.url) }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = P.Border)
        if (listening) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (playing) { voiceHolder[0]?.pause(); playing = false } else { voice().resume(); playing = true }
                }) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play", tint = P.Accent
                    )
                }
                Text(
                    if (spokenBlock >= 0) "Reading aloud · ${spokenBlock + 1} of ${blocks.size}" else if (playing) "Starting…" else "Paused",
                    color = P.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                Action("${ReadAloud.RATES[rateIndex].let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}×", muted = true) {
                    rateIndex = (rateIndex + 1) % ReadAloud.RATES.size
                    voice().setRate(ReadAloud.RATES[rateIndex])
                }
                IconButton(onClick = { voiceHolder[0]?.pause(); playing = false; listening = false; spokenBlock = -1 }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Stop reading aloud", tint = P.Muted)
                }
            }
        } else if (picked.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${picked.size} link${if (picked.size == 1) "" else "s"} picked",
                    color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Action("Clear", muted = true) { viewModel.clearPicks() }
                if (lobsterOn) Action("Fetch picked") { viewModel.fetchPicked() }
                else Text("Turn on the Lobster to fetch", color = P.Faint, fontSize = 12.sp)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Action("Ask about this") { viewModel.askAboutAll() }
                Action("Ask the trader", muted = true) { viewModel.askTrader() }
                if (laterItem != null) {
                    if (!laterItem.archived) Action("Done") { viewModel.finishLater(laterItem.id) }
                    Action(if (laterItem.favourite) "★ Favourite" else "☆ Favourite", muted = !laterItem.favourite) {
                        viewModel.favouriteLater(laterItem.id, !laterItem.favourite)
                    }
                    Action("To Library", muted = true) { viewModel.laterToLibrary(laterItem.id) }
                    Spacer(Modifier.width(12.dp))
                    Action("Delete", muted = true) { viewModel.removeLater(laterItem.id) }
                }
                if (doc.source == ReaderSource.CATCH) {
                    Action("Library") { onCatchToLibrary(doc.ref) }
                    Action("Notes") { onCatchToNotes(doc.ref) }
                    Spacer(Modifier.width(12.dp))
                    Action("Dismiss", muted = true) { onCatchDismiss(doc.ref) }
                }
            }
        }
    }

    linkMenu?.let { url ->
        Menu(
            title = url.substringAfter("://").take(80),
            onDismiss = { linkMenu = null },
            hint = if (lobsterOn) "SlothLlama stays offline: the Lobster fetches the page and brings it back to your catch."
            else "SlothLlama stays offline. Turn on the Lobster (its page) to have it fetch links for you.",
            options = buildList<Pair<String, () -> Unit>> {
                if (lobsterOn) add("Fetch with the Lobster" to { viewModel.fetch(url) })
                if (lobsterOn) add("Save for later" to { viewModel.saveForLater(url) })
                add((if (url in picked) "Unpick" else "Pick, to fetch with others") to { viewModel.togglePick(url) })
                add("Copy link" to { copy(context, "Link", url) })
            }
        )
    }
    passageMenu?.let { passage ->
        Menu(
            title = passage.take(140) + if (passage.length > 140) "…" else "",
            onDismiss = { passageMenu = null },
            hint = "Asking opens a new chat with this passage, ready to send or edit.",
            options = buildList<Pair<String, () -> Unit>> {
                add("Save to Notes" to { viewModel.highlightToNotes(passage) })
                ParagraphAsk.entries.forEach { a -> add(a.label to { viewModel.ask(a, passage) }) }
                add("Copy" to { copy(context, "Passage", passage) })
            }
        )
    }
}

/** One block: tap a link for its menu, press and hold anywhere for the passage menu. */
@Composable
private fun BlockView(
    block: ReaderBlock,
    scale: Float,
    query: String,
    current: Boolean,
    picked: Set<String>,
    onLink: (String) -> Unit,
    onPick: (String) -> Unit,
    onHold: () -> Unit
) {
    val accent = P.Accent
    val mark = if (current) P.Warning.copy(alpha = 0.45f) else P.AccentSoft
    val text = remember(block, query, accent, mark) { styled(block.body, query, accent, mark) }
    val serif = FontFamily.Serif
    val body = (17 * scale).sp
    when (block) {
        is ReaderBlock.Heading -> {
            val size = when (block.level) { 1 -> 22; 2 -> 20; else -> 17 }
            Tappable(
                text, block.links, P.Text, (size * scale).sp, (size * 1.35f * scale).sp, FontWeight.SemiBold, null,
                Modifier.padding(top = (8 * scale).dp), onLink, onHold
            )
        }
        is ReaderBlock.Paragraph -> Tappable(text, block.links, P.Text, body, body * 1.55f, null, serif, Modifier, onLink, onHold)
        is ReaderBlock.Quote -> Row {
            Box(Modifier.width(3.dp).height((22 * scale).dp).background(P.BorderStrong))
            Spacer(Modifier.width(12.dp))
            Tappable(text, block.links, P.Muted, body, body * 1.55f, null, serif, Modifier.weight(1f), onLink, onHold)
        }
        is ReaderBlock.Bullet, is ReaderBlock.Numbered -> Row(verticalAlignment = Alignment.Top) {
            Text(
                if (block is ReaderBlock.Numbered) "${block.number}." else "•",
                color = P.Muted, fontSize = body, lineHeight = body * 1.55f, modifier = Modifier.width((22 * scale).dp)
            )
            Tappable(text, block.links, P.Text, body, body * 1.55f, null, serif, Modifier.weight(1f), onLink, onHold)
            // Headlines in a brief: pick several, then fetch them together.
            block.links.firstOrNull()?.let { l -> PickMark(l.url in picked) { onPick(l.url) } }
        }
        is ReaderBlock.Code -> Text(
            block.text, color = P.CodeText, fontSize = (14 * scale).sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(P.CodeBackground)
                .horizontalScroll(rememberScrollState()).padding(12.dp)
                .pointerInput(block) { detectTapGestures(onLongPress = { onHold() }) }
        )
        is ReaderBlock.Aside -> AsideView(block, scale, picked, onLink, onPick)
    }
}

/**
 * The page's own lines around the article, folded to one faint row: nothing of the page is thrown
 * away, so a line the Lobster took for a link list or a date is one tap from coming back.
 */
@Composable
private fun AsideView(
    block: ReaderBlock.Aside,
    scale: Float,
    picked: Set<String>,
    onLink: (String) -> Unit,
    onPick: (String) -> Unit
) {
    var unfolded by remember(block) { mutableStateOf(false) }
    val accent = P.Accent
    val n = block.lines.size
    Column(Modifier.fillMaxWidth()) {
        Text(
            (if (unfolded) "Fold the page's lines" else "$n ${if (n == 1) "line" else "lines"} from the page · links, dates, captions") + (if (unfolded) " ˄" else " ˅"),
            color = P.Faint, fontSize = (13 * scale).sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = if (unfolded) "Fold" else "Show") { unfolded = !unfolded }
                .padding(vertical = 6.dp)
        )
        if (unfolded) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Box(Modifier.width(2.dp).height((18 * scale).dp).background(P.Border))
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.lines.forEach { line ->
                        Row(verticalAlignment = Alignment.Top) {
                            val text = remember(line, accent) { styled(line, "", accent, accent) }
                            Tappable(text, line.links, P.Muted, (14 * scale).sp, (20 * scale).sp, null, null, Modifier.weight(1f), onLink) {}
                            line.links.firstOrNull()?.let { l -> PickMark(l.url in picked) { onPick(l.url) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tappable(
    text: AnnotatedString,
    links: List<LinkSpan>,
    color: Color,
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight?,
    family: FontFamily?,
    modifier: Modifier,
    onLink: (String) -> Unit,
    onHold: () -> Unit
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = color,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = weight,
        fontFamily = family,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text, links) {
            detectTapGestures(
                onTap = { pos ->
                    val l = layout ?: return@detectTapGestures
                    val at = l.getOffsetForPosition(pos)
                    links.firstOrNull { at >= it.start && at < it.end || at - 1 >= it.start && at - 1 < it.end }?.let { onLink(it.url) }
                },
                onLongPress = { onHold() }
            )
        }
    )
}

/** Bold runs, links in the accent colour, and matches for "find" marked. */
private fun styled(body: Inline, query: String, accent: Color, mark: Color): AnnotatedString = buildAnnotatedString {
    append(body.text)
    body.bold.forEach { addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), it.start, it.end) }
    body.links.forEach { addStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline), it.start, it.end) }
    val q = query.trim()
    if (q.isNotEmpty()) {
        var from = body.text.indexOf(q, ignoreCase = true)
        while (from >= 0) {
            addStyle(SpanStyle(background = mark), from, from + q.length)
            from = body.text.indexOf(q, from + q.length, ignoreCase = true)
        }
    }
}

// --- small pieces ---

@Composable
private fun Menu(title: String, hint: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text(title, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                options.forEach { (label, run) ->
                    Text(
                        label, color = P.Accent, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss(); run() }
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
                Text(hint, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = P.Muted) } }
    )
}

@Composable
private fun PickMark(on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        if (on) "✓" else "+",
        color = if (on) P.OnAccent else P.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(26.dp)
            .clip(shape)
            .background(if (on) P.Accent else P.Canvas)
            .border(1.dp, if (on) P.Accent else P.Border, shape)
            .clickable(onClick = onClick)
            .padding(top = 3.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SizeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) P.Muted else P.Faint,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(P.Elevated).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, color = P.Faint, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = P.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(P.Accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        label,
        color = if (selected) P.Accent else P.Muted,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) P.AccentSoft else P.Canvas)
            .border(1.dp, if (selected) P.Accent else P.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** An Express story's two ways on: its page (through the Lobster), or the Later shelf. */
@Composable
private fun ExpressActions(doc: ReaderDoc, onShelf: Boolean, onOpenPage: ((String) -> Unit)?, onSave: () -> Unit) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onOpenPage != null && doc.url.isNotBlank()) Action("Open the page") { onOpenPage(doc.url) }
        if (onShelf) Text("On your Later shelf", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
        else Action("Save for later", onClick = onSave)
    }
}

/** A story's lead picture, kept beside its paper, decoded off the main thread at the width it's shown. */
@Composable
private fun StoryPicture(viewModel: ReaderViewModel, name: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val px = with(LocalDensity.current) { maxWidth.toPx() }.toInt().coerceIn(200, 1_440)
        val bmp by produceState<android.graphics.Bitmap?>(null, name, px) {
            value = withContext(Dispatchers.IO) { runCatching { viewModel.pictures.load(name, px) }.getOrNull() }
        }
        bmp?.let { b ->
            Image(
                b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
private fun Action(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (muted) P.Muted else P.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/** A thin bar: how far through an article you are. */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(P.Border)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(3.dp).background(P.Accent))
    }
}

@Composable
private fun Empty(text: String?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        if (text == null) CircularProgressIndicator(Modifier.size(24.dp), color = P.Accent, strokeWidth = 2.dp)
        else Text(text, color = P.Muted, fontSize = 15.sp)
    }
}

private fun copy(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Share intents carry text in a Binder transaction, which fails past about 1 MB. */
private const val MAX_SHARE_CHARS = 200_000
