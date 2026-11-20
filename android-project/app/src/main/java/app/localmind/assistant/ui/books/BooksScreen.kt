package app.localmind.assistant.ui.books

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.books.PaperFiles
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.domain.books.FORMATS
import app.localmind.assistant.domain.books.Forms
import app.localmind.assistant.domain.books.GistSource
import app.localmind.assistant.domain.books.MOODS
import app.localmind.assistant.domain.books.PACES
import app.localmind.assistant.domain.books.READING_LANGUAGES
import app.localmind.assistant.domain.books.Shelf
import app.localmind.assistant.domain.books.ShelfImport
import app.localmind.assistant.domain.books.ShelfStats
import app.localmind.assistant.domain.books.Status
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.Field
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton

/** SlothBooks' three tabs. */
enum class BooksTab(val label: String) { SHELF("Shelf"), PAPERS("Papers"), INSIGHTS("Insights") }

private enum class Filter(val label: String) {
    ALL("All"), UNREAD("To read"), READING("Reading"), READ("Read"), FICTION("Fiction"), NONFICTION("Nonfiction"), SERIES("Series")
}

private enum class Sort(val label: String) { AUTHOR("By author"), TITLE("By title"), YEAR("By year"), ADDED("Newest first") }

private val SERIF = FontFamily.Serif

/**
 * SlothBooks: your books and papers, with a card each for your reading, the Librarian a tap
 * away, and counts worked out on the tablet. Papers and books kept as files live in
 * Documents/SlothBooks; the shelf itself is in SlothLlama (and its backups).
 */
@Composable
fun BooksScreen(viewModel: BooksViewModel, onBack: () -> Unit) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(BooksTab.SHELF) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Book?>(null) }

    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::readSheet) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.addFile(it, paper = true) } }
    val pickSheet = {
        sheetPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "*/*"))
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    reading?.let { book ->
        PdfViewer(book, onClose = viewModel::closeViewer, onAsk = { viewModel.closeViewer(); viewModel.talk(book) })
        return
    }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE
            val inTab = books.filter { if (tab == BooksTab.PAPERS) it.isPaper else !it.isPaper }
            val selected = books.firstOrNull { it.id == selectedId }?.takeIf { tab != BooksTab.INSIGHTS && (it.isPaper == (tab == BooksTab.PAPERS)) }
            val shown = selected ?: if (wide) (inTab.firstOrNull { it.status == Status.READING } ?: inTab.firstOrNull()) else null
            if (!wide && selected != null) BackHandler { selectedId = null }

            Column(Modifier.fillMaxSize()) {
                SlothTopBar(
                    title = if (!wide && selected != null) selected.title else "SlothBooks",
                    subtitle = busy ?: if (!wide && selected != null) selected.author.ifBlank { null } else null,
                    onBack = if (!wide && selected != null) ({ selectedId = null }) else onBack,
                    actions = {
                        when (tab) {
                            BooksTab.PAPERS -> QuietButton("Add a PDF") { pdfPicker.launch(arrayOf("application/pdf")) }
                            else -> QuietButton("Add a book") { editing = viewModel.newBook() }
                        }
                        ShelfMenu(onImport = pickSheet, onAskShelf = { viewModel.askShelf() })
                    }
                )
                if (wide || selected == null) {
                    BooksTabs(tab, { tab = it; selectedId = null }, Modifier.padding(start = 24.dp, top = 4.dp))
                }
                val list: @Composable (Modifier) -> Unit = { m ->
                    when (tab) {
                        BooksTab.SHELF -> ShelfList(
                            all = books, picked = shown?.id, wide = wide, modifier = m,
                            onPick = { selectedId = it.id },
                            onImport = pickSheet, onAdd = { editing = viewModel.newBook() }
                        )
                        BooksTab.PAPERS -> PapersList(
                            papers = inTab, picked = shown?.id, modifier = m,
                            onPick = { selectedId = it.id; viewModel.seen(it) },
                            onAdd = { pdfPicker.launch(arrayOf("application/pdf")) }
                        )
                        BooksTab.INSIGHTS -> Unit
                    }
                }
                when {
                    tab == BooksTab.INSIGHTS -> Insights(stats, Modifier.fillMaxSize(), wide, onAsk = { viewModel.askShelf(it) })
                    wide -> Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        list(Modifier.weight(1f).fillMaxHeight())
                        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).width(1.dp).fillMaxHeight().background(P.Card))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (shown != null) BookCard(shown, books, viewModel, onEdit = { editing = it }, onPick = { selectedId = it.id })
                            else EmptyCard(tab)
                        }
                    }
                    selected != null -> BookCard(selected, books, viewModel, onEdit = { editing = it }, onPick = { selectedId = it.id }, modifier = Modifier.padding(horizontal = 24.dp))
                    else -> list(Modifier.fillMaxSize().padding(horizontal = 24.dp))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    editing?.let { b ->
        BookEditor(b, isNew = books.none { it.id == b.id }, onSave = { viewModel.save(it); editing = null }, onDismiss = { editing = null })
    }
    preview?.let { p ->
        ImportSheet(p, onConfirm = viewModel::confirmImport, onDismiss = viewModel::cancelImport)
    }
}

@Composable
private fun ShelfMenu(onImport: () -> Unit, onAskShelf: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = P.Muted) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(P.Elevated)) {
            DropdownMenuItem(text = { Text("Bring in a spreadsheet", color = P.Text) }, onClick = { expanded = false; onImport() })
            DropdownMenuItem(text = { Text("Ask the Librarian about the shelf", color = P.Text) }, onClick = { expanded = false; onAskShelf() })
        }
    }
}

/** Plain text tabs with a moss underline, like SlothDesk's. */
@Composable
private fun BooksTabs(tab: BooksTab, onTab: (BooksTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        BooksTab.entries.forEach { t ->
            val on = t == tab
            Column(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab, onClick = { onTab(t) })
                    .semantics { this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    t.label, fontSize = 15.sp, color = if (on) P.Text else P.Muted,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(Modifier.width(28.dp).height(2.dp).clip(CircleShape).background(if (on) P.Accent else Color.Transparent))
            }
        }
    }
}

// --- the shelf ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfList(
    all: List<Book>, picked: String?, wide: Boolean, modifier: Modifier,
    onPick: (Book) -> Unit, onImport: () -> Unit, onAdd: () -> Unit
) {
    val books = all.filter { !it.isPaper }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var sort by rememberSaveable { mutableStateOf(Sort.AUTHOR) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    if (books.isEmpty()) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).padding(20.dp)) {
                Text("Your shelf is empty", color = P.Text, fontSize = 21.sp, fontFamily = SERIF)
                Text(
                    "Bring in a spreadsheet of your books once (a Title column is all it needs; Author, Year, Genre and the rest are read if they're there), or add books one at a time.",
                    color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledButton("Choose a spreadsheet", onClick = onImport)
                    Spacer(Modifier.width(8.dp))
                    QuietButton("Add a book", onClick = onAdd)
                }
            }
        }
        return
    }
    val readingNow = books.filter { it.status == Status.READING }.sortedByDescending { it.updatedAt }
    val queue = books.filter { it.upNext != null }.sortedBy { it.upNext }
    val counts = mapOf(
        Filter.ALL to books.size, Filter.UNREAD to books.count { it.status == Status.UNREAD },
        Filter.READING to readingNow.size, Filter.READ to books.count { it.status == Status.READ },
        Filter.FICTION to books.count { it.isFiction }, Filter.NONFICTION to books.count { !it.isFiction },
        Filter.SERIES to books.count { it.series.isNotBlank() }
    )
    val rows = books.asSequence()
        .filter { b ->
            when (filter) {
                Filter.ALL -> true
                Filter.UNREAD -> b.status == Status.UNREAD
                Filter.READING -> b.status == Status.READING
                Filter.READ -> b.status == Status.READ
                Filter.FICTION -> b.isFiction
                Filter.NONFICTION -> !b.isFiction
                Filter.SERIES -> b.series.isNotBlank()
            }
        }
        .filter { Shelf.matches(it, query) }
        .let { seq ->
            when {
                filter == Filter.SERIES -> seq.sortedWith(compareBy({ it.series }, { it.seriesNo ?: Int.MAX_VALUE }, { it.year ?: 0 }))
                sort == Sort.TITLE -> seq.sortedBy { Book.keyOf(it.title, "") }
                sort == Sort.YEAR -> seq.sortedWith(compareBy({ it.year ?: Int.MAX_VALUE }, { it.title }))
                sort == Sort.ADDED -> seq.sortedByDescending { it.addedAt }
                else -> seq.sortedWith(compareBy({ surname(it.author) }, { it.year ?: 0 }, { it.title }))
            }
        }
        .toList()

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { Spacer(Modifier.height(16.dp)) }
        readingNow.forEach { b ->
            item(key = "now-${b.id}") {
                ReadingNow(b, onClick = { onPick(b) })
                Spacer(Modifier.height(12.dp))
            }
        }
        if (queue.isNotEmpty()) {
            item(key = "queue") {
                Column(Modifier.padding(top = 10.dp, bottom = 14.dp)) {
                    Label("Up next", "your queue, in order")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        queue.forEach { b -> Pill(b.title, selected = b.id == picked, quiet = true, small = true) { onPick(b) } }
                    }
                }
            }
        }
        item(key = "filters") {
            Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Filter.entries.forEach { f ->
                        Pill("${f.label} ${counts[f] ?: 0}", selected = f == filter, quiet = true, small = true) { filter = f }
                    }
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        Box(Modifier.weight(1f)) { Field("", query, "Title, author, series or genre") { query = it } }
                        QuietButton("Done", muted = true) { searching = false; query = "" }
                    } else {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search the shelf", tint = P.Muted) }
                        if (filter != Filter.SERIES) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Sort.entries.forEach { s ->
                                    Text(
                                        s.label, fontSize = 13.sp, color = if (s == sort) P.Text else P.Faint,
                                        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { sort = s }
                                            .padding(horizontal = 10.dp, vertical = 13.dp)
                                    )
                                }
                            }
                        } else Spacer(Modifier.weight(1f))
                        Text("○ to read · ◐ reading · ● read", color = P.Faint, fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            item { Text("Nothing here.", color = P.Faint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp)) }
        }
        items(rows, key = { it.id }) { b ->
            BookRow(b, picked = b.id == picked, seriesView = filter == Filter.SERIES, onClick = { onPick(b) })
        }
        item { Spacer(Modifier.height(if (wide) 24.dp else 72.dp)) }
    }
}

private fun surname(author: String): String =
    author.split(",", " and ", "&").first().trim().split(' ').lastOrNull().orEmpty().lowercase()

@Composable
private fun ReadingNow(b: Book, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("READING NOW", color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spine(b)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(b.title, color = P.Text, fontSize = 20.sp, fontFamily = SERIF, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(b.author.ifBlank { null }, b.upTo.ifBlank { null }).joinToString(" · "),
                    color = P.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text("${b.progress}%", color = P.Accent, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Progress(b.progress, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Progress(percent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(P.Elevated)
            .semantics { contentDescription = "$percent percent read" }
    ) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(4.dp).clip(CircleShape).background(P.Accent))
    }
}

/** A book's mark: a small spine with the author's initial (there are no covers: the app is offline). */
@Composable
private fun Spine(b: Book) {
    Box(
        Modifier.size(width = 30.dp, height = 42.dp).clip(RoundedCornerShape(4.dp)).background(P.Elevated).border(1.dp, P.BorderStrong, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            (if (b.isPaper) "¶" else surname(b.author).take(1).uppercase().ifBlank { b.title.take(1) }),
            color = P.Muted, fontSize = 12.sp, fontFamily = SERIF, modifier = Modifier.padding(bottom = 4.dp).clearAndSetSemantics {}
        )
    }
}

@Composable
private fun StatusMark(status: String) {
    val d = Modifier.size(10.dp).clip(CircleShape)
    val label = Status.label(status)
    Box(Modifier.semantics { contentDescription = label }) {
        when (status) {
            Status.READ -> Box(d.background(P.Accent))
            Status.READING -> Box(d.border(1.5.dp, P.Accent, CircleShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(P.Accent))
            }
            Status.ASIDE, Status.REFERENCE -> Box(d.border(1.5.dp, P.Faint, CircleShape)) {
                Box(Modifier.align(Alignment.Center).size(width = 6.dp, height = 1.5.dp).background(P.Faint))
            }
            else -> Box(d.border(1.5.dp, P.Faint, CircleShape))
        }
    }
}

@Composable
private fun BookRow(b: Book, picked: Boolean, seriesView: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
            .background(if (picked) P.Card else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spine(b)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(b.title, color = P.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = if (seriesView) listOfNotNull(b.series + (b.seriesNo?.let { " · $it" } ?: ""), b.author.ifBlank { null })
            else listOfNotNull(b.author.ifBlank { null }, b.yearText.ifBlank { null }, b.readingIn.ifBlank { null }, (b.series.ifBlank { null } ?: b.form.ifBlank { null }))
            Text(meta.joinToString(" · "), color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.upNext != null) Text("Up next", color = P.Accent, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        StatusMark(b.status)
    }
}

// --- papers ---

@Composable
private fun PapersList(papers: List<Book>, picked: String?, modifier: Modifier, onPick: (Book) -> Unit, onAdd: () -> Unit) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Text(
                "PDFs you keep: the Lobster's fetches land here by themselves, and the files are in Documents/${PaperFiles.ROOT}/${PaperFiles.PAPERS} on the tablet.",
                color = P.Faint, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
        }
        if (papers.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.Card).padding(20.dp)) {
                    Text("No papers yet", color = P.Text, fontSize = 19.sp, fontFamily = SERIF)
                    Text(
                        "Ask the Lobster to fetch a PDF link, share one to SlothLlama, or add a PDF that's already on the tablet.",
                        color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )
                    FilledButton("Add a PDF", onClick = onAdd)
                }
            }
        }
        items(papers.sortedByDescending { it.addedAt }, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (p.id == picked) P.Card else Color.Transparent).clickable { onPick(p) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spine(p)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(p.title, color = P.Text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.author.ifBlank { null }, host(p.source), day(p.addedAt)).joinToString(" · "),
                        color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (p.fresh) Text("New", color = P.Accent, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                StatusMark(p.status)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

private fun host(url: String): String? =
    runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "from $it" }

private fun day(millis: Long): String? = if (millis <= 0) null else
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK))

@Composable
private fun EmptyCard(tab: BooksTab) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (tab == BooksTab.PAPERS) "Pick a paper to see its card." else "Pick a book to see its card.", color = P.Faint, fontSize = 14.sp)
    }
}

// --- the card ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCard(
    book: Book, all: List<Book>, vm: BooksViewModel, onEdit: (Book) -> Unit, onPick: (Book) -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var reveal by remember(book.id) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 48.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(book.title, color = P.Text, fontSize = 26.sp, lineHeight = 32.sp, fontFamily = SERIF)
                val meta = listOfNotNull(
                    book.author.ifBlank { null }, book.yearText.ifBlank { null },
                    book.language.ifBlank { null }?.let { "written in $it" }, book.form.ifBlank { null },
                    listOf(book.genre, book.subgenre.substringAfter('/', "").trim()).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
                )
                Text(meta.joinToString(" · "), color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                if (book.about.isNotBlank()) Text(book.about, color = P.Faint, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                if (book.notes.isNotBlank()) Text(book.notes, color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More for this book", tint = P.Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                    DropdownMenuItem(text = { Text("Edit details", color = P.Text) }, onClick = { menu = false; onEdit(book) })
                    DropdownMenuItem(
                        text = { Text(if (book.upNext != null) "Take out of Up next" else "Put in Up next", color = P.Text) },
                        onClick = { menu = false; vm.queue(book, book.upNext == null) }
                    )
                    if (book.hasFile && book.fileName.endsWith(".epub", ignoreCase = true)) {
                        DropdownMenuItem(text = { Text("Open in another reader", color = P.Text) }, onClick = {
                            menu = false
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(book.file), PaperFiles.mimeOf(book.fileName))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        })
                    }
                    if (book.gist != null) DropdownMenuItem(text = { Text("Clear the gist", color = P.Text) }, onClick = { menu = false; vm.clearGist(book) })
                    DropdownMenuItem(text = { Text("Take off the shelf", color = P.Danger) }, onClick = { menu = false; vm.delete(book) })
                }
            }
        }
        if (book.series.isNotBlank()) {
            val series = ShelfStats.seriesOf(all.filter { !it.isPaper }).firstOrNull { it.name == book.series }
            if (series != null) {
                Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.series, color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                    series.books.forEach { b ->
                        Pill((b.seriesNo?.let { "$it · " } ?: "") + b.title, selected = b.id == book.id, quiet = true, small = true) { onPick(b) }
                    }
                }
            }
        }

        // You
        Label("You")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Status.ALL.forEach { s -> Pill(Status.label(s), selected = book.status == s, quiet = true, small = true) { vm.setStatus(book, s) } }
        }
        val dates = listOfNotNull(
            book.started.ifBlank { null }?.let { "Started $it" }, book.finished.ifBlank { null }?.let { "Finished $it" },
            if (book.reads > 1) "Read ${book.reads} times in the app" else null
        )
        if (dates.isNotEmpty()) Text(dates.joinToString(" · "), color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        if (book.status == Status.READING) ProgressEditor(book) { p, upTo -> vm.setProgress(book, p, upTo) }

        Text("Reading in", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val langs = (READING_LANGUAGES + listOf(book.language, book.readIn)).filter { it.isNotBlank() }.distinct()
            langs.forEach { l -> Pill(l, selected = book.readingIn == l, quiet = true, small = true) { vm.edit(book) { it.copy(readIn = if (l == it.language) "" else l) } } }
        }
        Text("Your copy", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FORMATS.forEach { f -> Pill(f, selected = book.format == f, quiet = true, small = true) { vm.edit(book) { it.copy(format = if (it.format == f) "" else f) } } }
        }
        if (book.status == Status.READ || book.status == Status.READING || book.rating > 0) {
            Stars(book.rating) { r -> vm.edit(book) { it.copy(rating = if (it.rating == r) 0 else r) } }
        }
        Text("Tags", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (MOODS + PACES).forEach { t ->
                Pill(t, selected = t in book.tags, quiet = true, small = true) {
                    vm.edit(book) { b ->
                        val others = if (t in PACES) b.tags.filterNot { it in PACES } else b.tags
                        b.copy(tags = if (t in b.tags) b.tags - t else others + t)
                    }
                }
            }
        }
        NoteField("Why now", book.why, "Why you picked it up (again)", book.id) { v -> vm.edit(book) { it.copy(why = v) } }
        if (book.status == Status.READ || book.verdict.isNotBlank()) {
            NoteField("Your verdict", book.verdict, "One line: what you'd tell a friend", book.id) { v -> vm.edit(book) { it.copy(verdict = v) } }
        }
        Quotes(book) { quotes -> vm.edit(book) { it.copy(quotes = quotes) } }

        // Gist
        Label("Gist", book.gist?.let { GistSource.label(it.source) })
        val g = book.gist
        when {
            g == null || g.isEmpty -> {
                Text(
                    if (book.isFiction && book.status != Status.READ) "None yet. When you've finished, the Librarian can draft one."
                    else "None yet. The Librarian can draft one from what it knows of the book; you keep or change it.",
                    color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
                )
                QuietButton("Draft with the Librarian") { vm.draftGist(book) }
            }
            book.isFiction && book.status != Status.READ && !reveal -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, P.BorderStrong, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Folded while you read, so nothing is spoiled. It opens when you mark the book Read.", color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                QuietButton("Show anyway", muted = true) { reveal = true }
            }
            else -> Column {
                Text(g.thesis, color = P.Text, fontSize = 16.sp, lineHeight = 24.sp)
                g.claims.forEachIndexed { i, c ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Text("${i + 1}.", color = P.Faint, fontSize = 14.sp, modifier = Modifier.width(24.dp))
                        Text(c, color = P.Text, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
                if (g.pushback.isNotBlank()) {
                    Row(Modifier.padding(top = 10.dp)) {
                        Text("Pushback", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
                        Text(g.pushback, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        // Connections
        val links = Shelf.connections(book, all)
        if (links.isNotEmpty()) {
            Label("On your shelf too")
            links.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(l.book) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spine(l.book)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(l.book.title, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${l.book.author} · ${l.why}", color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusMark(l.book.status)
                }
            }
        }

        // Recall
        if (book.recall.isNotEmpty()) {
            Label("Recall")
            book.recall.forEach { q ->
                Text(
                    q, color = P.Text, fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, P.Border, RoundedCornerShape(14.dp)).padding(14.dp)
                )
            }
            QuietButton("Quiz me") { vm.quizMe(book) }
        }

        HorizontalDivider(color = P.Border, modifier = Modifier.padding(top = 28.dp, bottom = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledButton("Talk it through") { vm.talk(book) }
            Spacer(Modifier.width(8.dp))
            if (book.hasFile && !book.fileName.endsWith(".epub", ignoreCase = true)) QuietButton("Open the PDF") { vm.open(book) }
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    book.hasFile -> book.fileName.ifBlank { "On this tablet" }
                    book.format.isNotBlank() -> "No file · ${book.format}"
                    else -> ""
                },
                color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)
            )
        }
    }
}

@Composable
private fun Label(text: String, right: String? = null) {
    Row(Modifier.padding(top = 30.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp)
        if (right != null) {
            Text(
                right, color = P.Faint, fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp).border(1.dp, P.Border, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun ProgressEditor(book: Book, onSave: (Int, String) -> Unit) {
    var value by remember(book.id) { mutableFloatStateOf(book.progress.toFloat()) }
    var upTo by remember(book.id) { mutableStateOf(book.upTo) }
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${value.toInt()}% read", color = P.Text, fontSize = 14.sp, modifier = Modifier.width(96.dp))
            Slider(
                value = value, onValueChange = { value = it }, valueRange = 0f..100f,
                onValueChangeFinished = { onSave(value.toInt(), upTo) },
                colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Border),
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { Field("Up to", upTo, "chapter 20, p. 212… (the Librarian keeps to it)") { upTo = it } }
            if (upTo != book.upTo) QuietButton("Save") { onSave(value.toInt(), upTo) }
        }
    }
}

@Composable
private fun Stars(rating: Int, onRate: (Int) -> Unit) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Rating", color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        (1..5).forEach { r ->
            IconButton(onClick = { onRate(r) }, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (r <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "$r of 5", tint = if (r <= rating) P.Accent else P.Faint
                )
            }
        }
    }
}

/** A text note on the card, saved when you tap Save (so typing never fights the shelf). */
@Composable
private fun NoteField(label: String, value: String, hint: String, bookId: String, onSave: (String) -> Unit) {
    var text by remember(bookId, value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { Field(label, text, hint, lines = 2) { text = it } }
        if (text != value) QuietButton("Save") { onSave(text.trim()) }
    }
}

@Composable
private fun Quotes(book: Book, onChange: (List<String>) -> Unit) {
    var draft by remember(book.id) { mutableStateOf("") }
    Text(
        if (book.quotes.isEmpty()) "Quotes you kept" else "Quotes you kept · ${book.quotes.size}",
        color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
    book.quotes.forEachIndexed { i, q ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, P.Border, RoundedCornerShape(14.dp)).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(q, color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = SERIF, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            QuietButton("Remove", muted = true) { onChange(book.quotes.filterIndexed { j, _ -> j != i }) }
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { Field("", draft, "A passage, with its chapter or page", lines = 2) { draft = it } }
        if (draft.isNotBlank()) QuietButton("Keep") { onChange(book.quotes + draft.trim()); draft = "" }
    }
}

// --- insights ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Insights(s: ShelfStats, modifier: Modifier, wide: Boolean, onAsk: (String) -> Unit) {
    val left: @Composable () -> Unit = {
        Column {
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("Read in the app", s.read.toString(), if (s.readThisYear > 0) "${s.readThisYear} this year" else "a fresh start", Modifier.weight(1f))
                Figure("Reading", s.reading.size.toString(), s.reading.firstOrNull()?.let { "${it.title} · ${it.progress}%" } ?: "nothing yet", Modifier.weight(1f), accent = true)
                Figure("On the shelf", s.books.toString(), "${s.fiction} fiction · ${s.nonfiction} nonfiction", Modifier.weight(1f))
            }
            if (s.series.isNotEmpty()) {
                Label("Series and cycles")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                    s.series.take(8).forEach { sp ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, P.Border, RoundedCornerShape(16.dp)).padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sp.name, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${sp.read} of ${sp.books.size}", color = P.Muted, fontSize = 13.sp)
                            }
                            Row(Modifier.padding(vertical = 10.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                sp.books.forEach { b -> Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (b.status == Status.READ) P.Accent else P.Elevated)) }
                            }
                            Text(sp.next?.let { "Next: ${it.title}" } ?: "All read", color = P.Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Label("Worth noticing")
            val notes = buildList {
                val translated = s.originals.filterKeys { it in READING_LANGUAGES }.entries.sortedByDescending { it.value }
                if (translated.isNotEmpty()) add(
                    translated.joinToString(", ") { "${it.value} written in ${it.key}" } +
                        ". If you read one in the original, set Reading in on its card."
                )
                val few = s.regions.filter { it.second in 1..10 }
                if (few.isNotEmpty()) add(few.joinToString(", ") { "${it.first}: ${it.second}" } + ". Ask the Librarian for picks from the quieter corners.")
            }
            notes.forEach { Text(it, color = P.Text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            Row {
                QuietButton("What should I read next?") { onAsk("What should I read next from my shelf, and why?") }
                QuietButton("Picks from my gaps") { onAsk("Looking at the regions and languages my shelf is thin on, which unread books on it should I read first?") }
            }
        }
    }
    val right: @Composable () -> Unit = {
        Column {
            Label("When they were written")
            val top = s.eras.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            s.eras.forEach { (name, n) -> Bar(name, n, top) }
            Label("Written in")
            val langTop = s.languages.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
            s.languages.take(8).forEach { (name, n) -> Bar(name, n, langTop) }
            Label("Where from")
            val regionTop = s.regions.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
            s.regions.forEach { (name, n) -> Bar(name, n, regionTop) }
            Text(
                "Every number here is counted on the tablet from your shelf. The model never does the sums.",
                color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
    if (wide) {
        Row(modifier.padding(horizontal = 24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) { left() }
            Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).width(1.dp).fillMaxHeight().background(P.Card))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) { right() }
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 72.dp)) {
            left()
            right()
        }
    }
}

@Composable
private fun Figure(label: String, value: String, detail: String, modifier: Modifier, accent: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(P.Card).padding(14.dp)) {
        Text(label, color = P.Faint, fontSize = 12.sp)
        Text(value, color = if (accent) P.Accent else P.Text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Bar(name: String, n: Int, top: Int) {
    Row(Modifier.fillMaxWidth().height(28.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = P.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(130.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(P.Elevated)) {
            Box(Modifier.fillMaxWidth((n.toFloat() / top).coerceIn(0.01f, 1f)).height(8.dp).clip(CircleShape).background(P.Accent))
        }
        Text("$n", color = P.Text, fontSize = 14.sp, modifier = Modifier.width(48.dp).padding(start = 10.dp))
    }
}

// --- sheets ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookEditor(start: Book, isNew: Boolean, onSave: (Book) -> Unit, onDismiss: () -> Unit) {
    var b by remember(start.id) { mutableStateOf(start) }
    var year by remember(start.id) { mutableStateOf(start.yearText.removePrefix("c. ").let { if (it.endsWith(" BC")) "-" + it.removeSuffix(" BC") else it }) }
    var number by remember(start.id) { mutableStateOf(start.seriesNo?.toString().orEmpty()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = P.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(if (isNew) (if (b.isPaper) "New paper" else "New book") else "Edit details", color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("The catalogue half of the card. Your reading is kept separately.", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Field("Title", b.title, "") { b = b.copy(title = it) }
            Field("Author", b.author, "As you'd file it: Jane Austen, Ezra Klein and Derek Thompson") { b = b.copy(author = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("Year", year, "First published; -440 for 440 BC", number = true) { year = it } }
                Box(Modifier.weight(2f)) { Field("Written in", b.language, "English, French, Classical Chinese…") { b = b.copy(language = it) } }
            }
            Text("Form", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Forms.ALL.forEach { f -> Pill(f, selected = b.form == f, quiet = true, small = true) { b = b.copy(form = f) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("Genre", b.genre, "History, Mystery, Economics…") { b = b.copy(genre = it) } }
                Box(Modifier.weight(1f)) { Field("Country", b.country, "The author's") { b = b.copy(country = it) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(3f)) { Field("Series or cycle", b.series, "Optional") { b = b.copy(series = it) } }
                Box(Modifier.weight(1f)) { Field("Number", number, "", number = true) { number = it } }
            }
            Field("Region", b.region, "Europe, Asia, North America…") { b = b.copy(region = it) }
            Field("About", b.about, "One line on what it is", lines = 2) { b = b.copy(about = it) }
            Field("Notes", b.notes, "Awards, edition…") { b = b.copy(notes = it) }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledButton(if (isNew) "Add to the shelf" else "Save", enabled = b.title.isNotBlank()) {
                    onSave(b.copy(year = ShelfImport.year(year), seriesNo = number.trim().toIntOrNull()))
                }
                Spacer(Modifier.width(12.dp))
                QuietButton("Cancel", onClick = onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(p: ShelfImport.Preview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = P.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.fresh.size}", color = P.Text, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(if (p.fresh.size == 1) "book to bring in" else "books to bring in", color = P.Text, fontSize = 16.sp)
                    Text("All start as not read yet. Read on this tablet; nothing is sent.", color = P.Muted, fontSize = 13.sp)
                }
            }
            val skipped = listOfNotNull(
                if (p.known > 0) "${p.known} already on your shelf" else null,
                if (p.blank > 0) "${p.blank} rows without a title" else null
            )
            if (skipped.isNotEmpty()) Text("Skipped: ${skipped.joinToString(", ")}.", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text(
                "Kept: title, author, year, form, genre, languages, country, region, series and the about line. Left out: ISBNs, links and anything else.",
                color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp)
            )
            if (p.twins.isNotEmpty()) {
                Label("Two editions")
                p.twins.forEach { (a, b) ->
                    Text(
                        "${a.title} (${a.readingIn.ifBlank { "?" }}) and ${b.title} (${b.readingIn.ifBlank { "?" }}) by ${a.author}: kept as two cards.",
                        color = P.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledButton(if (p.fresh.size == 1) "Bring in 1 book" else "Bring in ${p.fresh.size} books", enabled = p.fresh.isNotEmpty(), onClick = onConfirm)
                Spacer(Modifier.width(12.dp))
                QuietButton("Cancel", onClick = onDismiss)
            }
        }
    }
}
