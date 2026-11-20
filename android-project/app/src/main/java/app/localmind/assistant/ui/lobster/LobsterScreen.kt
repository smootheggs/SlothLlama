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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.lobster.CaughtItem
import app.localmind.assistant.data.lobster.LobsterCatch
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
fun LobsterScreen(viewModel: LobsterViewModel, onBack: () -> Unit, onRead: (String) -> Unit = {}, onDesk: () -> Unit = {}) {
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

    var menu by remember { mutableStateOf(false) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    val ready = state.enabled && state.folderName != null

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(
                title = "SlothLobster",
                subtitle = "Goes online for you",
                onBack = onBack,
                actions = {
                    if (ready) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = P.Muted)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                                DropdownMenuItem(
                                    text = { Text(if (state.paused) "Let it carry on" else "Pause the Lobster", color = P.Text) },
                                    onClick = { menu = false; viewModel.setPaused(!state.paused) },
                                    enabled = !busy
                                )
                                DropdownMenuItem(
                                    text = { Text("Mailbox and pairing", color = P.Text) },
                                    onClick = { menu = false; pairing = true }
                                )
                            }
                        }
                    }
                }
            )
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
                        val catch: @Composable () -> Unit = {
                            Catch(
                                state.catch,
                                onLibrary = viewModel::toLibrary,
                                onNotes = viewModel::toNotes,
                                onChat = viewModel::toChat,
                                onDismiss = viewModel::dismiss,
                                onRead = { onRead(it.fileName) },
                                onDesk = onDesk
                            )
                        }
                        if (wide) {
                            // Asking on the left, what came back on the right.
                            Row(
                                Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                                    StatusBar(state)
                                    OrderBox(enabled = !busy, onSend = viewModel::send, onDraft = viewModel::draftOrder)
                                    Orders(state.orders.filter { it.sent.order.kind != OrderKind.EDITION.id }, onCancel = viewModel::cancel)
                                    Spacer(Modifier.heightIn(min = 32.dp))
                                }
                                Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                                    catch()
                                    Spacer(Modifier.heightIn(min = 32.dp))
                                }
                            }
                        } else {
                            Column(
                                Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp)
                            ) {
                                StatusBar(state)
                                OrderBox(enabled = !busy, onSend = viewModel::send, onDraft = viewModel::draftOrder)
                                catch()
                                Orders(state.orders.filter { it.sent.order.kind != OrderKind.EDITION.id }, onCancel = viewModel::cancel)
                                Spacer(Modifier.heightIn(min = 40.dp))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (pairing && ready) {
        AlertDialog(
            onDismissRequest = { pairing = false },
            containerColor = P.Elevated,
            title = { Text("Mailbox and pairing", color = P.Text) },
            text = {
                Pairing(
                    folderName = state.folderName.orEmpty(),
                    code = { viewModel.pairingCode() },
                    onNewKey = viewModel::newKey,
                    onChangeFolder = { pairing = false; folderPicker.launch(null) },
                    onTurnOff = { pairing = false; viewModel.setEnabled(false) }
                )
            },
            confirmButton = { TextButton(onClick = { pairing = false }) { Text("Done", color = P.Lobster) } }
        )
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

/** One slim line: awake or not, and what it's doing. Pausing is in the ⋯ menu. */
@Composable
private fun StatusBar(state: MailboxState) {
    val now = System.currentTimeMillis()
    val s = state.status
    val awake = !state.paused && s != null && now - s.at < AWAKE_MS
    val title = when {
        state.paused -> "Paused · it waits until you let it carry on"
        s == null -> "Hasn't checked in yet · orders wait in the mailbox"
        !awake -> "Asleep · last checked in ${ago(s.at)}"
        s.state == "busy" -> "Busy · checked ${ago(s.at)}"
        else -> "Awake · checked ${ago(s.at)}"
    }
    val doing = s?.takeIf { awake }?.let { st -> st.working ?: if (st.queue > 0) "${st.queue} in its queue" else null }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (awake) LobsterSoft else P.Card)
            .border(1.dp, if (awake) P.Lobster.copy(alpha = 0.25f) else P.Border, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (awake) P.Lobster else P.Faint))
        Spacer(Modifier.width(12.dp))
        Text(title, color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = doing == null))
        doing?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, color = P.Lobster, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderBox(enabled: Boolean, onSend: (OrderKind, String, Boolean, String) -> Unit, onDraft: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableStateOf(OrderKind.RESEARCH.id) }
    var deep by rememberSaveable { mutableStateOf(false) }
    var deliver by rememberSaveable { mutableStateOf("library") }
    val kind = OrderKind.of(kindId)
    var tips by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.padding(top = 16.dp), padding = 18.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OrderKind.forOrderBox.forEach { k -> Chip(k.label, k == kind) { kindId = k.id } }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(P.Canvas)
                .border(1.dp, P.Border, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    when (kind) {
                        OrderKind.FETCH -> "Paste a link"
                        OrderKind.WATCH -> "e.g. new roles on a company's careers page"
                        OrderKind.BRIEF -> "e.g. markets and tech news, every morning"
                        else -> "What should it find out?"
                    },
                    color = P.Faint, fontSize = 15.sp
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = P.Text, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(P.Lobster),
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(kind.hint, color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val places = listOf("library" to "To Library", "notes" to "To Notes") +
                if (kind == OrderKind.FETCH) listOf("later" to "To Later") else emptyList()
            places.forEach { (id, label) ->
                Chip(label, deliver == id, small = true) { deliver = id }
            }
            if (kind == OrderKind.RESEARCH) Chip("Deep · overnight", deep, small = true) { deep = !deep }
        }
        if (tips) {
            val tip = LobsterTips.forKind(kind)
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(P.Canvas).padding(14.dp)
            ) {
                Text(LobsterTips.HOW_IT_WORKS, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                if (tip.rule.isNotBlank()) {
                    Text(tip.rule, color = P.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                }
                tip.examples.forEach { ex ->
                    Text(
                        ex, color = P.Lobster, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { text = ex }.padding(6.dp)
                    )
                }
                Text("Tap an example to use it.", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Link(if (tips) "Hide tips" else "How to ask") { tips = !tips }
            Text("·", color = P.Faint, fontSize = 12.sp)
            Link("Draft it in a chat") { onDraft(LobsterTips.draftPrompt(kind, text)) }
            Spacer(Modifier.weight(1f))
            SendButton(enabled = enabled && text.isNotBlank()) {
                onSend(kind, text, deep && kind == OrderKind.RESEARCH, if (deliver == "later" && kind != OrderKind.FETCH) "library" else deliver)
                text = ""
            }
        }
    }
}

@Composable
private fun Orders(orders: List<OrderView>, onCancel: (app.localmind.assistant.data.lobster.SentOrder) -> Unit) {
    if (orders.isEmpty()) return
    var showDone by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    // Brought back more than a day ago: folded away, so the catch stays near the top.
    val (recent, old) = orders.partition { it.state != OrderState.DONE || now - it.sent.order.createdAt < DAY_MS }
    Label("ORDERS")
    if (recent.isEmpty()) Text("Nothing in progress.", color = P.Muted, fontSize = 13.sp)
    (if (showDone) recent + old else recent).take(20).forEach { o ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${OrderKind.of(o.sent.order.kind).label}${if (o.sent.order.depth == "deep") " · deep" else ""} · ${ago(o.sent.order.createdAt)}",
                    color = P.Faint, fontSize = 11.sp
                )
                Text(o.sent.order.text, color = P.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(o.state.label, color = if (o.state == OrderState.DONE) P.Muted else P.Lobster, fontSize = 12.sp)
            }
            if (o.state == OrderState.WAITING) {
                TextButton(onClick = { onCancel(o.sent) }) { Text("Take back", color = P.Muted, fontSize = 13.sp) }
            }
        }
        HorizontalDivider(color = P.Border)
    }
    if (old.isNotEmpty()) {
        Action(if (showDone) "Hide earlier orders" else "Done earlier · ${old.size}", muted = true) { showDone = !showDone }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

@Composable
private fun Catch(
    items: List<CaughtItem>,
    onLibrary: (CaughtItem) -> Unit,
    onNotes: (CaughtItem) -> Unit,
    onChat: (CaughtItem) -> Unit,
    onDismiss: (CaughtItem) -> Unit,
    onRead: (CaughtItem) -> Unit,
    onDesk: () -> Unit
) {
    Label(if (items.isEmpty()) "THE CATCH" else "THE CATCH · ${items.size} NEW")
    if (items.isEmpty()) {
        Text("Nothing brought back yet.", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
        return
    }
    val open: (CaughtItem) -> Unit = { ci -> if (ci.item?.kind == "prices") onDesk() else onRead(ci) }
    val menu: @Composable (CaughtItem) -> Unit = { ci ->
        CatchMenu(ci, onLibrary = onLibrary, onNotes = onNotes, onChat = onChat, onDismiss = onDismiss, onDesk = onDesk)
    }
    HeroCatch(items.first(), onOpen = open, menu = menu)
    if (items.size > 1) {
        Column(Modifier.padding(top = 10.dp)) {
            items.drop(1).forEach { ci ->
                HorizontalDivider(color = P.Border)
                CatchRow(ci, onOpen = open, menu = menu)
            }
        }
    }
}

/** How a catch is shown: its kind ("Research"), its title without the kind, and a line about it. */
internal data class CatchLook(val kind: String, val title: String, val detail: String)

internal fun catchLook(c: LobsterCatch): CatchLook {
    val prefix = c.title.substringBefore(" · ", "")
    val kind = when {
        c.kind == "prices" -> "Prices"
        c.kind == "message" || c.kind == "alert" -> "Message"
        prefix == "Research" -> "Research"
        prefix == "Watching" || c.title.endsWith(" changed") -> "Watch"
        c.title.contains("brief", ignoreCase = true) -> "Brief"
        c.kind == "note" -> "Note"
        else -> "Page"
    }
    val title = if (prefix == "Research" || prefix == "Watching" || prefix == "Brief") c.title.substringAfter(" · ") else c.title
    val preview = plainPreview(c.text, c.title).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val detail = when {
        c.kind == "prices" -> "For SlothDesk: it takes them in when you open it"
        kind == "Research" -> "${c.sources.size} source${if (c.sources.size == 1) "" else "s"} · ready to read or ask about offline"
        preview.isNotBlank() -> preview
        c.sources.isNotEmpty() -> c.sources.first().url.substringAfter("://").substringBefore('/')
        else -> ""
    }
    return CatchLook(kind, title, detail)
}

/** The newest thing brought back, with room to read a little of it. */
@Composable
private fun HeroCatch(ci: CaughtItem, onOpen: (CaughtItem) -> Unit, menu: @Composable (CaughtItem) -> Unit) {
    val c = ci.item
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(P.Lobster))
        Column(Modifier.weight(1f).padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp)) {
            if (c == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("A file that can't be opened", color = P.Text, fontSize = 16.sp)
                        Text("It may be damaged, or sealed with an older key.", color = P.Muted, fontSize = 13.sp)
                    }
                    menu(ci)
                }
                return@Column
            }
            val look = remember(c.id) { catchLook(c) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(look.kind, ago(c.createdAt), c.sources.size.takeIf { it > 0 }?.let { "$it sources" }).filterNotNull().joinToString(" · "),
                    color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                menu(ci)
            }
            Column(Modifier.padding(end = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Read it") { onOpen(ci) }) {
                Text(
                    look.title, color = P.Text, fontSize = 21.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val lines = remember(c.id) { heroLines(c) }
                lines.forEach { line ->
                    Text(
                        line, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Row(Modifier.padding(top = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SoftButton(if (c.kind == "prices") "Open SlothDesk" else "Read", Icons.AutoMirrored.Outlined.MenuBook) { onOpen(ci) }
            }
        }
    }
}

/** A brief's first headlines, or the first lines of anything else. */
private fun heroLines(c: LobsterCatch): List<String> {
    val preview = plainPreview(c.text, c.title).lines().map { it.trim() }.filter { it.isNotEmpty() }
    val bullets = preview.filter { it.startsWith("· ") }
    return if (bullets.size >= 2) bullets.take(3) else preview.take(2)
}

/** One line per catch: a kind mark, its title, a line about it, and ⋯. */
@Composable
private fun CatchRow(ci: CaughtItem, onOpen: (CaughtItem) -> Unit, menu: @Composable (CaughtItem) -> Unit) {
    val c = ci.item
    val look = remember(c?.id) { c?.let(::catchLook) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = c != null, onClickLabel = "Open it") { onOpen(ci) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KindMark(look?.kind ?: "Message")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (c == null || look == null) {
                Text("A file that can't be opened", color = P.Text, fontSize = 15.sp)
                Text("It may be damaged, or sealed with an older key.", color = P.Muted, fontSize = 13.sp)
            } else {
                Text("${look.kind} · ${ago(c.createdAt)}", color = P.Faint, fontSize = 12.sp)
                Text(look.title, color = P.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (look.detail.isNotBlank()) Text(look.detail, color = P.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        menu(ci)
    }
}

/** A small square with the kind's icon: green for research, amber for prices, coral for messages. */
@Composable
private fun KindMark(kind: String) {
    val (icon, tint) = when (kind) {
        "Research" -> Icons.Outlined.Search to P.Accent
        "Prices" -> Icons.Outlined.ShowChart to P.Warning
        "Message" -> Icons.Outlined.Info to P.Lobster
        "Watch" -> Icons.Outlined.Visibility to P.Lobster
        "Brief" -> Icons.Outlined.Newspaper to P.Lobster
        "Note" -> Icons.Outlined.StickyNote2 to P.Muted
        else -> Icons.Outlined.Description to P.Muted
    }
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MenuLine(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = color) }, onClick = onClick)
}

/** ⋯ for a catch: where to file it, or let it go. */
@Composable
private fun CatchMenu(
    ci: CaughtItem,
    onLibrary: (CaughtItem) -> Unit,
    onNotes: (CaughtItem) -> Unit,
    onChat: (CaughtItem) -> Unit,
    onDismiss: (CaughtItem) -> Unit,
    onDesk: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    val c = ci.item
    Box {
        IconButton(onClick = { shown = true }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "File or dismiss", tint = P.Muted)
        }
        DropdownMenu(expanded = shown, onDismissRequest = { shown = false }, modifier = Modifier.background(P.Elevated)) {
            val close = { shown = false }
            if (c != null && c.kind == "prices") {
                MenuLine("Open SlothDesk", P.Text) { close(); onDesk() }
            } else if (c != null && c.kind != "message" && c.kind != "alert") {
                // A message or an alert is something to read, not to file.
                MenuLine("Save to Library", P.Text) { close(); onLibrary(ci) }
                MenuLine("Save to Notes", P.Text) { close(); onNotes(ci) }
                MenuLine("Ask about it in a chat", P.Text) { close(); onChat(ci) }
            }
            MenuLine("Dismiss", P.Muted) { close(); onDismiss(ci) }
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
private fun Card(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .padding(padding)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(
        text, color = P.Faint, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp).semantics { heading() }
    )
}

/** The Lobster's coral, faintly. */
private val LobsterSoft: androidx.compose.ui.graphics.Color @Composable get() = P.Lobster.copy(alpha = 0.12f)

@Composable
private fun Chip(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        label,
        color = if (selected) P.Lobster else P.Muted,
        fontSize = if (small) 12.sp else 14.sp,
        modifier = Modifier
            .heightIn(min = if (small) 32.dp else 38.dp)
            .clip(shape)
            .background(if (selected) LobsterSoft else P.Canvas)
            .border(1.dp, if (selected) P.Lobster.copy(alpha = 0.5f) else P.Border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 7.dp else 9.dp)
    )
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Text(
        label, color = P.Faint, fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
    )
}

/** The order box's one filled button, in the Lobster's coral. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) P.Lobster else P.Elevated)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Sailing, contentDescription = null, tint = if (enabled) OnCoral else P.Faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Send", color = if (enabled) OnCoral else P.Faint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** A quiet coral button: a tint, not a fill. */
@Composable
private fun SoftButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(LobsterSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = P.Lobster, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = P.Lobster, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Text on coral: near-black reads well on it in both themes. */
private val OnCoral = androidx.compose.ui.graphics.Color(0xFF1C0F0C)

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
