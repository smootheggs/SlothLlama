package app.localmind.assistant.ui.books

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.domain.books.DefaultShelf
import app.localmind.assistant.domain.books.Entry
import app.localmind.assistant.domain.books.GroupBy
import app.localmind.assistant.domain.books.Grouping
import app.localmind.assistant.domain.books.Section
import app.localmind.assistant.domain.books.ShelfPick
import app.localmind.assistant.domain.books.Status
import app.localmind.assistant.domain.books.UserShelf
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.ui.trading.Field
import app.localmind.assistant.ui.trading.FilledButton
import app.localmind.assistant.ui.trading.Pill
import app.localmind.assistant.ui.trading.QuietButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads a book's kept cover (off the main thread), provided once by the SlothBooks screen. */
val LocalCovers = staticCompositionLocalOf<(Book, Int) -> Bitmap?> { { _, _ -> null } }

/** A few hundred small covers stay decoded while you scroll. */
private object CoverCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

private val SERIF = FontFamily.Serif

/** A book's cover: the kept one if there is one, else one made from its title and genre. */
@Composable
fun BookCover(book: Book, width: Dp, modifier: Modifier = Modifier) {
    val loader = LocalCovers.current
    val px = with(LocalDensity.current) { width.roundToPx() }
    val key = "${book.cover}@${book.updatedAt}@${(px / 64) * 64}"
    val image by produceState(CoverCache.get(key), key) {
        if (book.cover.isNotBlank() && value == null) {
            value = withContext(Dispatchers.IO) { loader(book, px)?.asImageBitmap() }?.also { CoverCache.put(key, it) }
        }
    }
    val shape = RoundedCornerShape(if (width < 48.dp) 3.dp else 5.dp)
    Box(
        modifier.size(width, width * 1.5f).clip(shape)
            .semantics { contentDescription = "${book.title}${if (book.author.isNotBlank()) " by ${book.author}" else ""}" }
    ) {
        val img = image
        if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else MadeCover(book, width)
    }
}

/** Title and author set in serif on the genre's colour: never a hole in the grid. */
@Composable
private fun MadeCover(book: Book, width: Dp) {
    val tone = toneOf(book)
    Box(Modifier.fillMaxSize().background(tone)) {
        // A faint spine edge, like a real cover.
        Box(Modifier.width((width.value / 24f).coerceAtLeast(2f).dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))
        if (width < 56.dp) {
            Text(
                initial(book), color = Color(0xFFF2F2EF).copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = SERIF,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clearAndSetSemantics {}
            )
        } else {
            val density = LocalDensity.current
            val titleSize = with(density) { (width.toPx() / 7.6f).toSp() }
            val authorSize = with(density) { (width.toPx() / 11.5f).toSp() }
            Column(
                Modifier.fillMaxSize().padding(width / 11).clearAndSetSemantics {},
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(book.title, color = Color(0xFFF2F2EF), fontSize = titleSize, lineHeight = titleSize * 1.18f, fontFamily = SERIF, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Column {
                    Box(Modifier.fillMaxWidth(0.4f).height(1.dp).background(Color(0xFFF2F2EF).copy(alpha = 0.35f)))
                    Text(
                        book.author, color = Color(0xFFF2F2EF).copy(alpha = 0.78f), fontSize = authorSize, lineHeight = authorSize * 1.25f,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun initial(b: Book): String =
    if (b.isPaper) "¶" else Grouping.surname(b.author).take(1).uppercase().ifBlank { b.title.take(1).uppercase() }

/** Dark, quiet colours by genre (white text reads on all of them); anything else by its genre's name. */
private fun toneOf(b: Book): Color {
    val g = (b.genre + " " + b.subgenre + " " + b.form).lowercase()
    val hex = when {
        b.isPaper -> 0xFF2C3238
        "mystery" in g || "detective" in g || "thriller" in g || "crime" in g -> 0xFF284034
        "fantasy" in g -> 0xFF3B2D52
        "science fiction" in g || "post-apocalyptic" in g || "dystopian" in g -> 0xFF1C3540
        "drama" in g || "tragedy" in g || "comedy" in g -> 0xFF4A2A2A
        "poetry" in g || "epic" in g -> 0xFF3C2E40
        "history" in g || "biography" in g || "memoir" in g -> 0xFF463726
        "politic" in g || "sociology" in g -> 0xFF3A2B3E
        "economic" in g || "finance" in g || "business" in g -> 0xFF283A4C
        "science" in g || "mathematic" in g || "medicine" in g -> 0xFF1F3C46
        "philosoph" in g || "religion" in g -> 0xFF3A3A2E
        "literary" in g || "modernist" in g || "novel" in g || "romance" in g -> 0xFF36364D
        else -> listOf(0xFF2F3A2A, 0xFF33343F, 0xFF3D3328, 0xFF2A3A3A)[(b.genre.hashCode() and 0x7fffffff) % 4]
    }
    return Color(hex)
}

// --- the browser ---

/**
 * The shelf: default and your own shelves on top, then the books grouped (by author, genre…) as
 * a list or a grid of covers. Prolific authors fold into a stack you open in place; copies of
 * one book are one row. Picking a book opens its card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfBrowser(
    all: List<Book>, shelves: List<UserShelf>, pick: ShelfPick, group: GroupBy, view: BookView,
    picked: String?, wide: Boolean, vm: BooksViewModel, modifier: Modifier,
    onPick: (Book) -> Unit, onImport: () -> Unit, onAdd: () -> Unit
) {
    val books = all.filter { !it.isPaper }
    var unfolded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    if (books.isEmpty()) {
        Empty(modifier, onImport, onAdd)
        return
    }
    val shown = books.filter { pick.holds(it) }
    val sections = remember(shown, group) { Grouping.sections(shown, group) }
    val readingNow = if (pick == ShelfPick.Default(DefaultShelf.ALL)) books.filter { it.status == Status.READING }.sortedByDescending { it.updatedAt } else emptyList()
    val scope = rememberCoroutineScope()

    Column(modifier) {
        ShelvesRow(books, shelves, pick, onPick = vm::choose, onNew = { naming = Naming.New }, onRename = { naming = Naming.Rename(it) }, onDelete = vm::deleteShelf)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Group by", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupBy.entries.forEach { g -> Pill(g.label, selected = g == group, quiet = true, small = true) { vm.setGroup(g) } }
            }
            ViewSwitch(view, vm::setView)
        }
        Spacer(Modifier.height(6.dp))
        if (shown.isEmpty()) {
            Text(
                when (pick) {
                    is ShelfPick.Mine -> "Nothing on this shelf yet. Add books to it from their cards (You → Shelves)."
                    else -> "Nothing here."
                },
                color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        // Where each section starts, for the A–Z strip.
        val starts = HashMap<String, Int>()
        var index = readingNow.size
        sections.forEach { s ->
            starts[s.title] = index
            index += 1
            s.entries.forEach { e -> index += 1 + if (e is Entry.Stack && e.id in unfolded) e.books.size else 0 }
        }
        val letters = if (group == GroupBy.AUTHOR) sections.map { it.title } else emptyList()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val toggle: (Entry.Stack) -> Unit = { st -> unfolded = if (st.id in unfolded) unfolded - st.id else unfolded + st.id }
            if (view == BookView.LIST) {
                val state = rememberLazyListState()
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = if (letters.isNotEmpty()) 22.dp else 0.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)) {
                    readingNow.forEach { b -> item(key = "now-${b.id}") { NowCard(b, onClick = { onPick(b) }) } }
                    sections.forEach { s ->
                        stickyHeader(key = "h-${s.key}") { Header(s) }
                        s.entries.forEach { e ->
                            when (e) {
                                is Entry.One -> item(key = e.id) { BookRow(e.book, picked = e.book.id == picked, copies = e.copies.size, onClick = { onPick(e.book) }) }
                                is Entry.Stack -> {
                                    item(key = e.id) { StackRow(e, opened = e.id in unfolded, onClick = { toggle(e) }) }
                                    if (e.id in unfolded) e.books.forEach { b ->
                                        item(key = "${e.id}/${b.id}") {
                                            BookRow(b, picked = b.id == picked, inStack = true, onClick = { onPick(b) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (letters.isNotEmpty()) Letters(letters, Modifier.align(Alignment.CenterEnd)) { l -> starts[l]?.let { scope.launch { state.scrollToItem(it) } } }
                ScrollEdge(state.canScrollBackward)
            } else {
                val large = view == BookView.LARGE
                val state = rememberLazyGridState()
                val cell = if (large) (if (wide) 132.dp else 140.dp) else 88.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cell + 14.dp), state = state,
                    modifier = Modifier.fillMaxSize().padding(end = if (letters.isNotEmpty()) 22.dp else 0.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(if (large) 22.dp else 18.dp)
                ) {
                    readingNow.forEach { b -> item(key = "now-${b.id}", span = { GridItemSpan(maxLineSpan) }) { NowCard(b, onClick = { onPick(b) }) } }
                    sections.forEach { s ->
                        item(key = "h-${s.key}", span = { GridItemSpan(maxLineSpan) }) { Header(s) }
                        s.entries.forEach { e ->
                            when (e) {
                                is Entry.One -> item(key = e.id) { CoverTile(e.book, cell, large, picked = e.book.id == picked, copies = e.copies.size) { onPick(e.book) } }
                                is Entry.Stack -> {
                                    item(key = e.id) { StackTile(e, cell, large, opened = e.id in unfolded) { toggle(e) } }
                                    if (e.id in unfolded) e.books.forEach { b ->
                                        item(key = "${e.id}/${b.id}") { CoverTile(b, cell, large, picked = b.id == picked) { onPick(b) } }
                                    }
                                }
                            }
                        }
                    }
                }
                if (letters.isNotEmpty()) Letters(letters, Modifier.align(Alignment.CenterEnd)) { l -> starts[l]?.let { scope.launch { state.scrollToItem(it) } } }
                ScrollEdge(state.canScrollBackward)
            }
        }
    }

    naming?.let { n ->
        NameDialog(
            title = when (n) { is Naming.Rename -> "Rename ${n.shelf.name}"; else -> "New shelf" },
            start = (n as? Naming.Rename)?.shelf?.name.orEmpty(),
            onDone = { name ->
                when (n) {
                    is Naming.Rename -> vm.renameShelf(n.shelf, name)
                    is Naming.ForBook -> vm.createShelf(name, addTo = n.book)
                    Naming.New -> vm.createShelf(name)
                }
                naming = null
            },
            onDismiss = { naming = null }
        )
    }
}

/**
 * Where the shelf controls end and the books begin: once the books scroll under the controls, a
 * hairline and a short fade, so covers don't look cut off.
 */
@Composable
private fun ScrollEdge(scrolled: Boolean) {
    if (!scrolled) return
    val canvas = P.Canvas
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = P.Border)
        Box(Modifier.fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(canvas, canvas.copy(alpha = 0f)))))
    }
}

/** What the name dialog is for. */
sealed interface Naming {
    data object New : Naming
    data class Rename(val shelf: UserShelf) : Naming
    data class ForBook(val book: Book) : Naming
}

@Composable
private fun Empty(modifier: Modifier, onImport: () -> Unit, onAdd: () -> Unit) {
    Column(modifier.padding(vertical = 24.dp)) {
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
}

@Composable
private fun ShelvesRow(
    books: List<Book>, shelves: List<UserShelf>, pick: ShelfPick,
    onPick: (ShelfPick) -> Unit, onNew: () -> Unit, onRename: (UserShelf) -> Unit, onDelete: (UserShelf) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        DefaultShelf.entries.forEach { d ->
            val n = books.count { d.holds(it) }
            if (d == DefaultShelf.ALL || n > 0) Pill("${d.label} $n", selected = pick == ShelfPick.Default(d), quiet = true, small = true) { onPick(ShelfPick.Default(d)) }
        }
        if (shelves.isNotEmpty()) Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(P.Border))
        shelves.forEach { s ->
            val on = pick == ShelfPick.Mine(s.id)
            Pill("${s.name} ${books.count { s.id in it.shelves }}", selected = on, quiet = true, small = true) { onPick(ShelfPick.Mine(s.id)) }
            if (on) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "More for ${s.name}", tint = P.Muted)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                        DropdownMenuItem(text = { Text("Rename", color = P.Text) }, onClick = { menu = false; onRename(s) })
                        DropdownMenuItem(text = { Text("Remove this shelf (the books stay)", color = P.Danger) }, onClick = { menu = false; onDelete(s) })
                    }
                }
            }
        }
        Box(
            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, P.BorderStrong, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onNew).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ New shelf", color = P.Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun ViewSwitch(view: BookView, onView: (BookView) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(21.dp)).border(1.dp, P.Border, RoundedCornerShape(21.dp)).padding(3.dp)) {
        listOf(
            Triple(BookView.LIST, Icons.AutoMirrored.Outlined.ViewList, "List"),
            Triple(BookView.SMALL, Icons.Outlined.Apps, "Small covers"),
            Triple(BookView.LARGE, Icons.Outlined.GridView, "Large covers")
        ).forEach { (v, icon, label) ->
            val on = v == view
            Box(
                Modifier.size(width = 44.dp, height = 36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) P.AccentSoft else Color.Transparent)
                    .clickable(role = Role.Tab) { onView(v) }.semantics { selected = on },
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = label, tint = if (on) P.Text else P.Faint, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun Header(s: Section) {
    Row(Modifier.fillMaxWidth().background(P.Canvas).padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.title.uppercase(), color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${s.count}", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Letters(letters: List<String>, modifier: Modifier, onLetter: (String) -> Unit) {
    Column(modifier.width(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        letters.forEach { l ->
            Text(
                l, color = P.Faint, fontSize = 11.sp,
                modifier = Modifier.heightIn(min = 20.dp).clickable(role = Role.Button) { onLetter(l) }.semantics { contentDescription = "Jump to $l" }
            )
        }
    }
}

@Composable
private fun NowCard(b: Book, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(22.dp)).background(P.Card).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(b, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("READING NOW", color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp)
            Text(b.title, color = P.Text, fontSize = 19.sp, fontFamily = SERIF, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text(listOfNotNull(b.author.ifBlank { null }, b.upTo.ifBlank { null }).joinToString(" · "), color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(P.Elevated)) {
                Box(Modifier.fillMaxWidth(b.progress.coerceIn(0, 100) / 100f).height(4.dp).clip(CircleShape).background(P.Accent))
            }
        }
        Text("${b.progress}%", color = P.Accent, fontSize = 19.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun BookRow(b: Book, picked: Boolean, copies: Int = 0, inStack: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (inStack) 24.dp else 0.dp).clip(RoundedCornerShape(14.dp))
            .background(if (picked) P.Card else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(b, 30.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                (if (inStack && b.seriesNo != null) "${b.seriesNo} · " else "") + b.title,
                color = P.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val meta = if (inStack) listOfNotNull(b.yearText.ifBlank { null }, b.form.takeIf { it != "Novel" && it.isNotBlank() })
            else listOfNotNull(b.author.ifBlank { null }, b.yearText.ifBlank { null }, b.readingIn.ifBlank { null }, (b.series.ifBlank { null } ?: b.form.ifBlank { null }))
            Text(meta.joinToString(" · "), color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (copies > 0) Badge("${copies + 1} copies")
        if (b.notes.contains("collection", ignoreCase = true) || b.notes.contains("anthology", ignoreCase = true)) Badge("Collection")
        if (b.upNext != null && b.status != Status.READ) Text("Up next", color = P.Accent, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        StatusDot(b.status)
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text, color = P.Muted, fontSize = 11.sp,
        modifier = Modifier.padding(end = 10.dp).border(1.dp, P.Border, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
internal fun StatusDot(status: String) {
    val d = Modifier.size(10.dp).clip(CircleShape)
    val label = Status.label(status)
    Box(Modifier.semantics { contentDescription = label }) {
        when (status) {
            Status.READ -> Box(d.background(P.Accent))
            Status.READING -> Box(d.border(1.5.dp, P.Accent, CircleShape)) { Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(P.Accent)) }
            Status.ASIDE, Status.REFERENCE -> Box(d.border(1.5.dp, P.Faint, CircleShape)) {
                Box(Modifier.align(Alignment.Center).size(width = 6.dp, height = 1.5.dp).background(P.Faint))
            }
            else -> Box(d.border(1.5.dp, P.Faint, CircleShape))
        }
    }
}

@Composable
private fun StackRow(st: Entry.Stack, opened: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(if (opened) P.Card else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { stateDescription = if (opened) "Open" else "Folded" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width((30 + 7 * (minOf(st.books.size, 6) - 1)).dp).height(45.dp)) {
            st.books.take(6).forEachIndexed { i, b -> BookCover(b, 30.dp, Modifier.offset(x = (7 * i).dp)) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(st.author, color = P.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val what = st.books.map { it.form }.distinct().singleOrNull()?.let { forms(it) } ?: "books"
            val series = st.books.map { it.series }.filter { it.isNotBlank() }.distinct().singleOrNull()?.takeIf { s -> st.books.all { it.series == s } }
            Text(
                listOfNotNull("${st.books.size} $what", series, "${st.read} read").joinToString(" · "),
                color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(if (opened) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = P.Faint)
    }
}

private fun forms(form: String): String = when (form) {
    "Novel" -> "novels"
    "Drama" -> "plays"
    "Poetry" -> "books of poetry"
    "Stories" -> "story collections"
    "Comics" -> "comics"
    else -> "books"
}

@Composable
private fun CoverTile(b: Book, cell: Dp, large: Boolean, picked: Boolean, copies: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            BookCover(b, cell, if (picked) Modifier.border(2.dp, P.Accent, RoundedCornerShape(5.dp)) else Modifier)
            if (b.status == Status.READ) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp).clip(CircleShape).background(P.Accent).semantics { contentDescription = "Read" })
            if (b.upNext != null && b.status != Status.READ) {
                Text(
                    "Up next", color = P.Accent, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            if (copies > 0) {
                Text(
                    "${copies + 1}", color = P.Text, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        .semantics { contentDescription = "${copies + 1} copies" }
                )
            }
        }
        Box(Modifier.width(cell).padding(top = 6.dp).height(3.dp).clip(CircleShape).background(if (b.status == Status.READING) P.Elevated else Color.Transparent)) {
            if (b.status == Status.READING) Box(Modifier.fillMaxWidth(b.progress.coerceIn(0, 100) / 100f).height(3.dp).background(P.Accent))
        }
        if (large) {
            Column(Modifier.width(cell).padding(top = 4.dp)) {
                Text(b.title, color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(b.author, color = P.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StackTile(st: Entry.Stack, cell: Dp, large: Boolean, opened: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (opened) P.Card else Color.Transparent).clickable(onClick = onClick).padding(4.dp)
            .semantics { stateDescription = if (opened) "Open" else "Folded" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(cell, cell * 1.5f)) {
            val shown = st.books.take(3)
            shown.reversed().forEachIndexed { i, b ->
                val back = shown.size - 1 - i
                BookCover(b, cell * 0.86f, Modifier.offset(x = cell * 0.07f * back, y = cell * 0.07f * back))
            }
        }
        Column(Modifier.width(cell).padding(top = 8.dp)) {
            Text(st.author, color = P.Text, fontSize = if (large) 14.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${st.books.size} · ${if (opened) "tap to fold" else "tap to open"}", color = P.Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun NameDialog(title: String, start: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text(title, color = P.Text, fontSize = 18.sp) },
        text = { Field("Name", name, "Rereads 2027, For the trip, Club picks…") { name = it.take(40) } },
        confirmButton = { FilledButton("Save", enabled = name.isNotBlank()) { onDone(name) } },
        dismissButton = { QuietButton("Cancel", onClick = onDismiss) }
    )
}
