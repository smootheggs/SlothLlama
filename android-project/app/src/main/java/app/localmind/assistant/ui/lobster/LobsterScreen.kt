package app.localmind.assistant.ui.lobster

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.lobster.CaughtItem
import app.localmind.assistant.data.lobster.MailboxState
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.lobster.OrderState
import app.localmind.assistant.data.lobster.OrderView
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.delay

/**
 * The SlothLobster's page: whether it's awake, orders for it, what it brought back, and pairing.
 * The Lobster is a separate app that goes online; SlothLlama only shares a mailbox folder with it.
 */
@Composable
fun LobsterScreen(viewModel: LobsterViewModel, onBack: () -> Unit, onRead: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::chooseFolder)
    }
    // For "the Lobster brought something back"; the Lobster works the same without it.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current

    // Checks the mailbox while the page is open.
    LaunchedEffect(Unit) {
        viewModel.opened()
        while (true) {
            viewModel.refresh()
            delay(20_000)
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(title = "SlothLobster", subtitle = "Goes online for you · 🦞", onBack = onBack)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth >= WIDE
                when {
                    !state.enabled -> Intro(onTurnOn = {
                        viewModel.setEnabled(true)
                        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                    state.folderName == null -> FolderStep(
                        missing = state.folderMissing,
                        onChoose = { folderPicker.launch(null) },
                        onTurnOff = { viewModel.setEnabled(false) }
                    )
                    else -> {
                        val left: @Composable (Modifier) -> Unit = { m ->
                            Column(m) {
                                StatusCard(state, busy, onPause = viewModel::setPaused)
                                OrderBox(enabled = !busy, onSend = viewModel::send)
                                Orders(state.orders, onCancel = viewModel::cancel)
                            }
                        }
                        val right: @Composable (Modifier) -> Unit = { m ->
                            Column(m) {
                                Catch(
                                    state.catch,
                                    onLibrary = viewModel::toLibrary,
                                    onNotes = viewModel::toNotes,
                                    onChat = viewModel::toChat,
                                    onDismiss = viewModel::dismiss,
                                    onRead = { onRead(it.fileName) }
                                )
                                Pairing(
                                    folderName = state.folderName.orEmpty(),
                                    code = { viewModel.pairingCode() },
                                    onNewKey = viewModel::newKey,
                                    onChangeFolder = { folderPicker.launch(null) },
                                    onTurnOff = { viewModel.setEnabled(false) }
                                )
                            }
                        }
                        if (wide) {
                            Row(Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                                left(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
                                VerticalDivider(color = P.Border, modifier = Modifier.padding(16.dp))
                                right(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
                            }
                        } else {
                            Column(
                                Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp)
                            ) {
                                left(Modifier.fillMaxWidth())
                                right(Modifier.fillMaxWidth())
                                Spacer(Modifier.heightIn(min = 32.dp))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Intro(onTurnOn: () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).padding(20.dp)) {
        Text("🦞", fontSize = 40.sp)
        Text("The Lobster goes where the sloth doesn't.", color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp))
        Text(
            "SlothLlama never goes online. The SlothLobster is a separate app that does: it researches, fetches " +
                "articles and watches pages for you, and brings back what it finds. The two only share a mailbox " +
                "folder, and everything in it is encrypted with a key that only they hold.\n\n" +
                "What comes back is shown to you, never acted on: you decide whether it goes into your Library, " +
                "your Notes or a chat.",
            color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp)
        )
        Button("Set up the mailbox", onTurnOn, Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun FolderStep(missing: Boolean, onChoose: () -> Unit, onTurnOff: () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).padding(20.dp)) {
        Text(if (missing) "The mailbox folder can't be opened" else "Choose the mailbox folder",
            color = P.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (missing) "It may have been moved or deleted. Choose it again (or a new one)."
            else "Make a new folder (for example Documents/SlothLobster) and choose it. The Lobster uses the same " +
                "folder: on this tablet, or synced to your phone with Syncthing.",
            color = P.Muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp)
        )
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button("Choose folder", onChoose)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onTurnOff) { Text("Turn off", color = P.Muted) }
        }
    }
}

@Composable
private fun StatusCard(state: MailboxState, busy: Boolean, onPause: (Boolean) -> Unit) {
    val now = System.currentTimeMillis()
    val s = state.status
    val (title: String, detail: String) = if (state.paused) {
        "Paused" to "It finishes what it's doing, then waits until you switch it back on."
    } else if (s == null) {
        "Hasn't checked in yet" to "Orders wait in the mailbox until it does."
    } else if (now - s.at >= AWAKE_MS) {
        "Asleep" to "Last checked in ${ago(s.at)}. Orders wait in the mailbox."
    } else {
        val working = s.working
        if (working != null) "Working" to working
        else (if (s.state == "busy") "Busy" else "Awake") to (if (s.queue > 0) "${s.queue} in its queue" else "Ready for orders")
    }
    Card(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🦞", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = P.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = !state.paused,
                    onCheckedChange = { on -> onPause(!on) },
                    enabled = !busy,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = P.OnAccent, checkedTrackColor = P.Accent,
                        uncheckedThumbColor = P.Muted, uncheckedTrackColor = P.Elevated, uncheckedBorderColor = P.BorderStrong
                    )
                )
                Text(if (state.paused) "Paused" else "On", color = P.Faint, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderBox(enabled: Boolean, onSend: (OrderKind, String, Boolean, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableStateOf(OrderKind.RESEARCH.id) }
    var deep by rememberSaveable { mutableStateOf(false) }
    var deliver by rememberSaveable { mutableStateOf("library") }
    val kind = OrderKind.of(kindId)
    Label("NEW ORDER")
    Card {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OrderKind.entries.forEach { k -> Chip(k.label, k == kind) { kindId = k.id } }
        }
        Text(kind.hint, color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(P.Canvas)
                .padding(12.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    when (kind) {
                        OrderKind.FETCH -> "Paste a link"
                        OrderKind.WATCH -> "e.g. new Senior PM roles on a company's careers page"
                        OrderKind.BRIEF -> "e.g. Hong Kong markets and tech news, every morning"
                        else -> "What should it find out?"
                    },
                    color = P.Faint, fontSize = 15.sp
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = P.Text, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(P.Accent),
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bring it to", color = P.Muted, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            listOf("library" to "Library", "notes" to "Notes").forEach { (id, label) ->
                Chip(label, deliver == id) { deliver = id }
                Spacer(Modifier.width(6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (kind == OrderKind.RESEARCH) {
                Chip(if (deep) "Deep (overnight) ✓" else "Deep (overnight)", deep) { deep = !deep }
            }
            Spacer(Modifier.weight(1f))
            Button("Send to the Lobster", onClick = {
                onSend(kind, text, deep && kind == OrderKind.RESEARCH, deliver)
                text = ""
            }, enabled = enabled && text.isNotBlank())
        }
    }
}

@Composable
private fun Orders(orders: List<OrderView>, onCancel: (app.localmind.assistant.data.lobster.SentOrder) -> Unit) {
    if (orders.isEmpty()) return
    Label("ORDERS")
    orders.take(12).forEach { o ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${OrderKind.of(o.sent.order.kind).label}${if (o.sent.order.depth == "deep") " · deep" else ""} · ${ago(o.sent.order.createdAt)}",
                    color = P.Faint, fontSize = 11.sp
                )
                Text(o.sent.order.text, color = P.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(o.state.label, color = if (o.state == OrderState.DONE) P.Accent else P.Muted, fontSize = 12.sp)
            }
            if (o.state == OrderState.WAITING) {
                TextButton(onClick = { onCancel(o.sent) }) { Text("Take back", color = P.Muted, fontSize = 13.sp) }
            }
        }
        HorizontalDivider(color = P.Border)
    }
}

@Composable
private fun Catch(
    items: List<CaughtItem>,
    onLibrary: (CaughtItem) -> Unit,
    onNotes: (CaughtItem) -> Unit,
    onChat: (CaughtItem) -> Unit,
    onDismiss: (CaughtItem) -> Unit,
    onRead: (CaughtItem) -> Unit
) {
    Label(if (items.isEmpty()) "THE CATCH" else "THE CATCH · ${items.size}")
    if (items.isEmpty()) {
        Text("Nothing brought back yet.", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
        return
    }
    items.forEach { ci ->
        Card(Modifier.padding(bottom = 10.dp)) {
            val c = ci.item
            if (c == null) {
                Text("A file that can't be opened", color = P.Text, fontSize = 15.sp)
                Text("It may be damaged, or sealed with an older key.", color = P.Muted, fontSize = 13.sp)
                Row { Spacer(Modifier.weight(1f)); Action("Dismiss") { onDismiss(ci) } }
                return@Card
            }
            Text("${c.kind.replaceFirstChar { it.uppercase() }} · ${ago(c.createdAt)}", color = P.Faint, fontSize = 11.sp)
            // Tap the title or the preview to read it all.
            Column(Modifier.clickable { onRead(ci) }) {
                Text(c.title, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (c.text.isNotBlank()) {
                    Text(c.text, color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 5, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (c.sources.isNotEmpty()) {
                Text("${c.sources.size} source${if (c.sources.size == 1) "" else "s"}: " +
                    c.sources.take(3).joinToString(" · ") { it.title.ifBlank { it.url.substringAfter("://").substringBefore('/') } },
                    color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 6.dp)) {
                Action("Read") { onRead(ci) }
                Action("Library") { onLibrary(ci) }
                Action("Notes") { onNotes(ci) }
                Action("Chat") { onChat(ci) }
                Spacer(Modifier.weight(1f))
                Action("Dismiss", muted = true) { onDismiss(ci) }
            }
        }
    }
}

@Composable
private fun Pairing(
    folderName: String,
    code: () -> String,
    onNewKey: () -> Unit,
    onChangeFolder: () -> Unit,
    onTurnOff: () -> Unit
) {
    var shownCode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Label("MAILBOX AND PAIRING")
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Folder: $folderName", color = P.Text, fontSize = 14.sp)
                Text("Encrypted; the Lobster needs the pairing code once.", color = P.Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { shownCode = !shownCode }) { Text(if (shownCode) "Hide" else "Pairing code", color = P.Accent) }
        }
        if (shownCode) {
            val shown = remember(shownCode) { code() }
            SelectionContainer {
                Text(shown.chunked(4).joinToString(" "), color = P.Text, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            Text("Keep it private: anyone with it could read the mailbox.", color = P.Faint, fontSize = 12.sp)
            Row(Modifier.padding(top = 4.dp)) {
                Action("Copy") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("SlothLobster pairing code", shown)
                    // Marked sensitive: Android hides it from the clipboard preview and keyboard suggestions.
                    clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    clipboard.setPrimaryClip(clip)
                }
                Action("New key", muted = true) { onNewKey() }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Action("Change folder", muted = true) { onChangeFolder() }
            Spacer(Modifier.weight(1f))
            Action("Turn off", muted = true) { onTurnOff() }
        }
    }
}

// --- small pieces ---

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
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

@Composable
private fun Button(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        label,
        color = if (enabled) P.OnAccent else P.Faint,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) P.Accent else P.Elevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

private fun ago(time: Long): String =
    if (time <= 0) "just now"
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** A heartbeat younger than this means the Lobster is up. */
private const val AWAKE_MS = 3 * 60 * 1000L
