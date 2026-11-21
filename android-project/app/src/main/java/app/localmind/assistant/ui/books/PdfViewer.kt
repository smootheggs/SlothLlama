package app.localmind.assistant.ui.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The pages of a PDF on the tablet, drawn by Android's own PDF renderer: nothing is sent
 * anywhere and no other app is needed. Pages are drawn as you scroll and a few are kept.
 */
private class PdfPages(context: Context, uri: Uri) : AutoCloseable {
    private val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Couldn't open the file.")
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    private val cache = LruCache<Int, Bitmap>(6)
    private var closed = false

    val count: Int = renderer.pageCount

    /** Width over height of each page, read once. */
    val ratios: List<Float> = (0 until count).map { i ->
        renderer.openPage(i).use { p -> if (p.height > 0) p.width.toFloat() / p.height else 0.75f }
    }

    suspend fun page(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        cache.get(index)?.takeIf { it.width == widthPx }?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { p ->
                    val w = widthPx.coerceIn(200, 2400)
                    val h = (w / ratios[index]).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }?.also { cache.put(index, it) }
    }

    override fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

@Composable
fun PdfViewer(book: Book, onClose: () -> Unit, onAsk: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val pages: Result<PdfPages>? by produceState<Result<PdfPages>?>(null, book.file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfPages(context, Uri.parse(book.file)) } }
    }
    // Close the PDF this effect was set up with, never whatever `pages` holds by then: reading
    // the state in onDispose closed the document the moment it finished opening, so every page
    // stayed blank.
    val opened = pages?.getOrNull()
    DisposableEffect(opened) {
        onDispose { opened?.close() }
    }
    val list = rememberLazyListState()
    val current by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        val doc = pages?.getOrNull()
        SlothTopBar(
            title = book.title,
            subtitle = doc?.let { "Page $current of ${it.count} · on this tablet" } ?: book.fileName,
            onBack = onClose,
            actions = { app.localmind.assistant.ui.trading.QuietButton("Ask the Librarian", onClick = onAsk) }
        )
        when {
            pages == null -> Centered("Opening…")
            doc == null -> Centered("This PDF won't open here. It may be damaged or protected.")
            else -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val width = minOf(maxWidth, 900.dp)
                val px = with(LocalDensity.current) { width.roundToPx() }
                LazyColumn(
                    state = list,
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(doc.count) { i ->
                        PdfPage(doc, i, px)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfPages, index: Int, widthPx: Int) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(index, widthPx) { mutableStateOf(false) }
    LaunchedEffect(index, widthPx) {
        bitmap = doc.page(index, widthPx)
        failed = bitmap == null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(doc.ratios[index].coerceIn(0.3f, 3f))
            .background(androidx.compose.ui.graphics.Color.White)
            .semantics { contentDescription = "Page ${index + 1}" }
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
        if (failed) {
            Text(
                "Page ${index + 1} couldn't be drawn", color = androidx.compose.ui.graphics.Color(0xFF6E736C), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = P.Muted, fontSize = 15.sp)
    }
}
