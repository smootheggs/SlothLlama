package app.localmind.assistant.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.library.LibraryItem
import app.localmind.assistant.data.library.LibraryKind
import app.localmind.assistant.data.library.MeaningIndex
import app.localmind.assistant.data.meaning.MeaningModelState
import app.localmind.assistant.domain.attachments.LibraryPassage
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * The private library: books and documents indexed on the tablet, Wikipedia (Kiwix) files read
 * in place, and a test search. In landscape the list and the search sit side by side.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onBack: () -> Unit, onRead: (Long) -> Unit = {}) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val meaningState by viewModel.meaningState.collectAsStateWithLifecycle()
    val meaningIndex by viewModel.meaningIndex.collectAsStateWithLifecycle()
    var confirmRemoveMeaning by remember { mutableStateOf(false) }
    // .gguf files have no registered type either.
    val meaningPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.importMeaningModel(uri)
    }
    val snackbar = remember { SnackbarHostState() }
    var pendingRemove by remember { mutableStateOf<LibraryItem?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addDocuments(uris)
    }
    // .zim files have no registered type, so any file can be picked; the name is checked after.
    val wikiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addWiki)
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(title = "Library", subtitle = "Private · offline", onBack = onBack)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth >= WIDE
                val shelf: @Composable (Modifier) -> Unit = { m ->
                    Shelf(
                        items = items,
                        working = working,
                        onAddDocuments = {
                            documentPicker.launch(
                                arrayOf("application/pdf", "application/epub+zip", "text/html", "application/xhtml+xml", "text/*")
                            )
                        },
                        onAddWiki = { wikiPicker.launch(arrayOf("*/*")) },
                        onToggle = viewModel::setEnabled,
                        onRemove = { pendingRemove = it },
                        onRead = { onRead(it.id) },
                        modifier = m,
                        meaningCard = {
                            MeaningCard(
                                state = meaningState,
                                index = meaningIndex,
                                onImport = { meaningPicker.launch(arrayOf("*/*")) },
                                onRemove = { confirmRemoveMeaning = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            )
                        }
                    )
                }
                val search: @Composable (Modifier) -> Unit = { m ->
                    TestSearch(
                        enabled = items.orEmpty().any { it.enabled },
                        searching = searching,
                        results = results,
                        onSearch = viewModel::search,
                        modifier = m
                    )
                }
                if (wide) {
                    Row(Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                        shelf(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
                        VerticalDivider(color = P.Border, modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp))
                        search(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(
                        Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    ) {
                        shelf(Modifier.fillMaxWidth())
                        HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 16.dp))
                        search(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmRemoveMeaning) {
        AlertDialog(
            onDismissRequest = { confirmRemoveMeaning = false },
            containerColor = P.Elevated,
            title = { Text("Turn off search by meaning?", color = P.Text) },
            text = { Text("The model and the library's meaning index are deleted. Searches go back to words only.", color = P.Muted) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeMeaningModel(); confirmRemoveMeaning = false }) { Text("Turn off", color = P.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveMeaning = false }) { Text("Cancel", color = P.Muted) } }
        )
    }

    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = P.Elevated,
            title = { Text("Remove ${item.name}?", color = P.Text) },
            text = {
                Text(
                    if (item.kind == LibraryKind.WIKI) "The file itself stays where it is on the tablet."
                    else "Its index is deleted; the original file isn't touched.",
                    color = P.Muted
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(item); pendingRemove = null }) { Text("Remove", color = P.Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel", color = P.Muted) } }
        )
    }
}

@Composable
private fun Shelf(
    items: List<LibraryItem>?,
    working: String?,
    onAddDocuments: () -> Unit,
    onAddWiki: () -> Unit,
    onToggle: (LibraryItem, Boolean) -> Unit,
    onRemove: (LibraryItem) -> Unit,
    onRead: (LibraryItem) -> Unit,
    modifier: Modifier,
    meaningCard: @Composable () -> Unit = {}
) {
    Column(modifier.padding(top = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddButton(Icons.Outlined.NoteAdd, "Books & documents", "PDF, EPUB, HTML, text", Modifier.weight(1f), working == null, onAddDocuments)
            AddButton(Icons.Outlined.Language, "Wikipedia", "Kiwix .zim file", Modifier.weight(1f), working == null, onAddWiki)
        }
        meaningCard()
        if (working != null) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = P.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(working, color = P.Accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val list = items.orEmpty()
        if (items != null && list.isEmpty()) {
            Hint(
                "Add books and documents and they are indexed here, on the tablet. Then switch on the library " +
                    "(the book button next to + in a chat) and each question goes with the passages that answer it.\n\n" +
                    "For Wikipedia, download a .zim file from library.kiwix.org (the \"mini\" or \"nopic\" editions " +
                    "of Wikipedia in your language are a few hundred MB to a few GB) and add it here. It is read " +
                    "where it is, not copied."
            )
        }
        list.forEach { item ->
            ItemRow(item, onToggle = { onToggle(item, it) }, onRemove = { onRemove(item) }, onRead = { onRead(item) })
        }
    }
}

@Composable
private fun AddButton(icon: ImageVector, label: String, detail: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) P.Accent else P.Faint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(detail, color = P.Faint, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ItemRow(item: LibraryItem, onToggle: (Boolean) -> Unit, onRemove: () -> Unit, onRead: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(P.Elevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.kind == LibraryKind.WIKI) Icons.Outlined.Language else Icons.Outlined.MenuBook,
                contentDescription = null, tint = if (item.enabled) P.Accent else P.Faint, modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        // Documents open in the reader; a Wikipedia file is searched, not read through.
        val readable = item.kind == LibraryKind.DOCUMENT
        Column(Modifier.weight(1f).clickable(enabled = readable, onClick = onRead)) {
            Text(item.name, color = if (item.enabled) P.Text else P.Muted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.detail, color = P.Faint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = P.OnAccent,
                checkedTrackColor = P.Accent,
                uncheckedThumbColor = P.Muted,
                uncheckedTrackColor = P.Elevated,
                uncheckedBorderColor = P.BorderStrong
            )
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${item.name}", tint = P.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TestSearch(
    enabled: Boolean,
    searching: Boolean,
    results: List<LibraryPassage>?,
    onSearch: (String) -> Unit,
    modifier: Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.padding(top = 12.dp)) {
        Text("TRY A QUESTION", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Text(
            "See which passages a question would bring along to the chat.",
            color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(P.Elevated)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = P.Faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(color = P.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(P.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                if (enabled) "e.g. What causes inflation?" else "Add something to the library first",
                                color = P.Faint, fontSize = 16.sp, maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            if (searching) {
                CircularProgressIndicator(Modifier.size(16.dp), color = P.Accent, strokeWidth = 2.dp)
            } else if (query.isNotBlank()) {
                Text(
                    "Search", color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSearch(query) }.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        when {
            results == null -> Unit
            results.isEmpty() -> Hint("Nothing matched. Try the key words of the question, or check the item is switched on.")
            else -> results.forEachIndexed { i, p -> PassageCard(i + 1, p) }
        }
    }
}

@Composable
private fun PassageCard(n: Int, p: LibraryPassage) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .padding(14.dp)
    ) {
        Text(
            "[$n] ${p.title}" + if (p.where.isNotBlank()) " — ${p.where}" else "",
            color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(p.text, color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 12, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/** Search by meaning: set it up, watch it read the library, or turn it off. */
@Composable
private fun MeaningCard(state: MeaningModelState, index: MeaningIndex, onImport: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = if (state is MeaningModelState.Ready) P.Accent else P.Faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Search by meaning", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            when (state) {
                is MeaningModelState.Ready -> TextButton(onClick = onRemove) { Text("Turn off", color = P.Muted, fontSize = 13.sp) }
                is MeaningModelState.Importing -> Unit
                else -> TextButton(onClick = onImport) { Text("Import model", color = P.Accent, fontSize = 13.sp) }
            }
        }
        val line = when (state) {
            MeaningModelState.Missing ->
                "Finds passages that say the same thing in other words, or in another language. Needs the " +
                    "EmbeddingGemma file (embeddinggemma-300M-Q8_0.gguf, 334 MB from huggingface.co/ggml-org/embeddinggemma-300M-GGUF), imported once."
            is MeaningModelState.Importing -> "Importing… ${(state.fraction * 100).toInt()}%"
            is MeaningModelState.Failed -> state.message
            is MeaningModelState.Ready -> when {
                index.total == 0 -> "On. Add books or documents and they'll be read for meaning too."
                index.done < index.total ->
                    "Reading your library for meaning: %,d of %,d passages%s".format(
                        index.done, index.total, if (index.running) "" else " (continues next time you search)"
                    )
                else -> "On · all %,d passages. Word search and meaning search work together.".format(index.total)
            }
        }
        Text(line, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        if (state is MeaningModelState.Ready && index.running && index.total > 0) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { index.done.toFloat() / index.total },
                color = P.Accent,
                trackColor = P.Elevated,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = P.Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 16.dp))
}
