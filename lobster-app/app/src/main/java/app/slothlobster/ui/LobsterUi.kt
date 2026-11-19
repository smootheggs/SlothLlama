package app.slothlobster.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slothlobster.R
import app.slothlobster.core.Job
import app.slothlobster.core.JobState
import app.slothlobster.core.Saved

enum class Tab(val label: String, val icon: ImageVector) {
    DECK("Deck", LobsterIcons.Deck),
    ORDERS("Orders", LobsterIcons.Orders),
    WATCH("Watch", LobsterIcons.Watch),
    LOG("Log", LobsterIcons.Log)
}

/**
 * The whole app. On a phone: one screen at a time with a bar of four tabs at the bottom. On a
 * tablet (or a phone sideways): a rail on the left, and on wide screens the Deck and Orders
 * show the chosen job beside the list.
 */
@Composable
fun LobsterUi(vm: LobsterViewModel) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val dataKeySaved by vm.dataKey.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val shared by vm.shared.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.DECK) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var openJob by rememberSaveable { mutableStateOf<String?>(null) }
    var readerId by rememberSaveable { mutableStateOf<String?>(null) }
    var listOpen by rememberSaveable { mutableStateOf(false) }

    // Something shared from another app goes to the order box.
    LaunchedEffect(shared) {
        if (shared != null) {
            tab = Tab.ORDERS
            settingsOpen = false
            openJob = null
            readerId = null
            listOpen = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Sea.Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val railed = maxWidth >= 600.dp
        val twoPane = maxWidth >= 840.dp

        BackHandler(enabled = settingsOpen || readerId != null || listOpen || (!twoPane && openJob != null) || tab != Tab.DECK) {
            when {
                settingsOpen -> settingsOpen = false
                readerId != null -> readerId = null
                listOpen -> listOpen = false
                !twoPane && openJob != null -> openJob = null
                else -> tab = Tab.DECK
            }
        }

        val pickTab: (Tab) -> Unit = { t ->
            tab = t
            settingsOpen = false
            readerId = null
            listOpen = false
            if (!twoPane) openJob = null
        }

        val content: @Composable (Modifier) -> Unit = { mod ->
            when {
                settingsOpen -> SettingsScreen(
                    saved = saved,
                    live = live,
                    onBack = { settingsOpen = false },
                    onPair = vm::pair,
                    onUnpair = vm::unpair,
                    onFolder = vm::setFolder,
                    onSettings = vm::settings,
                    onSetFeeds = vm::setFeeds,
                    onImportOpml = vm::importOpml,
                    onBriefNow = vm::briefNow,
                    dataKeySaved = dataKeySaved,
                    onDataKey = vm::setDataKey,
                    onStopEverything = vm::stopEverything,
                    modifier = mod
                )
                readerId != null -> {
                    val id = readerId.orEmpty()
                    ReaderScreen(
                        id = id,
                        now = now,
                        jobId = saved.delivered.find { it.id == id }?.jobId?.takeIf { j -> saved.jobs.any { it.id == j } },
                        load = vm::loadCatch,
                        onBack = { readerId = null },
                        onOpenJob = { j ->
                            readerId = null
                            listOpen = false
                            tab = Tab.ORDERS
                            openJob = j
                        },
                        modifier = mod
                    )
                }
                listOpen -> BroughtBackScreen(
                    saved, live, now,
                    onBack = { listOpen = false },
                    onOpen = { d -> readerId = d.id },
                    modifier = mod
                )
                tab == Tab.WATCH -> WatchScreen(
                    saved, now,
                    onAdd = vm::addWatch, onFilter = vm::setWatchFilter, onToggle = vm::setWatchOn, onEvery = vm::setWatchEvery,
                    onCheckNow = vm::checkWatchNow, onRemove = vm::removeWatch, modifier = mod
                )
                tab == Tab.LOG -> LogScreen(saved, now, mod)
                else -> {
                    val selected = selectedJob(saved, openJob, live.workingId, tab)
                    val detail: @Composable (Modifier, (() -> Unit)?) -> Unit = { m, back ->
                        JobDetail(
                            job = saved.jobs.find { it.id == selected },
                            now = now,
                            onBack = back,
                            onCancel = vm::cancel,
                            onSendNow = vm::sendNow,
                            onAgain = vm::again,
                            onRemove = { id -> vm.remove(id); openJob = null },
                            catchId = saved.delivered.firstOrNull { it.jobId == selected }?.id,
                            onRead = { c -> readerId = c },
                            modifier = m
                        )
                    }
                    val list: @Composable (Modifier) -> Unit = { m ->
                        if (tab == Tab.ORDERS) {
                            OrdersScreen(
                                saved, now, twoPane, selected, shared,
                                onSharedUsed = vm::sharedUsed,
                                onSend = vm::send,
                                onOpenJob = { openJob = it },
                                onRepeatOn = vm::setRepeatOn,
                                onRepeatRemove = vm::removeRepeat,
                                modifier = m
                            )
                        } else {
                            DeckScreen(
                                saved, live, now, railed, selected,
                                onSettings = { settingsOpen = true },
                                onOpenJob = { openJob = it },
                                onOpenCatch = { readerId = it },
                                onSeeAll = { listOpen = true },
                                onPause = vm::setPaused,
                                onPair = { settingsOpen = true },
                                onSend = { kind, text -> vm.send(kind, text, false, null) },
                                onSendNow = vm::sendNow,
                                onWatches = { pickTab(Tab.WATCH) },
                                onOrders = { pickTab(Tab.ORDERS) },
                                modifier = m
                            )
                        }
                    }
                    when {
                        twoPane -> Row(mod) {
                            list(Modifier.width(500.dp).fillMaxHeight())
                            Box(Modifier.width(1.dp).fillMaxHeight().background(Sea.Divider))
                            detail(Modifier.weight(1f), null)
                        }
                        openJob != null && saved.jobs.any { it.id == openJob } -> detail(mod, { openJob = null })
                        else -> list(mod)
                    }
                }
            }
        }

        if (railed) {
            Row(Modifier.fillMaxSize()) {
                Rail(tab, settingsOpen, onTab = pickTab, onSettings = { settingsOpen = true })
                Box(Modifier.width(1.dp).fillMaxHeight().background(Sea.Divider))
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth())
                BottomBar(if (settingsOpen) null else tab, onTab = pickTab)
            }
        }
    }
}

/** The job shown beside the list on wide screens: the one picked, else the one in hand, else the latest. */
private fun selectedJob(saved: Saved, picked: String?, working: String?, tab: Tab): String? {
    if (picked != null && saved.jobs.any { it.id == picked }) return picked
    if (working != null) return working
    val candidates: List<Job> = if (tab == Tab.DECK) saved.jobs.filter { it.state == JobState.WORKING || it.state == JobState.QUEUED } else saved.jobs
    return (candidates.firstOrNull() ?: saved.jobs.firstOrNull())?.id
}

@Composable
private fun BottomBar(tab: Tab?, onTab: (Tab) -> Unit) {
    Column {
        Divider()
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Tab) { onTab(t) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (on) Sea.Coral else Sea.Muted, modifier = Modifier.size(22.dp))
                    Text(t.label, color = if (on) Sea.Coral else Sea.Muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun Rail(tab: Tab, settingsOpen: Boolean, onTab: (Tab) -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .width(96.dp)
            .fillMaxHeight()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painterResource(R.drawable.sloth_lobster),
            contentDescription = "SlothLobster: a sloth riding a lobster",
            modifier = Modifier.size(width = 64.dp, height = 56.dp)
        )
        Spacer(Modifier.height(14.dp))
        Tab.entries.forEach { t ->
            RailItem(t.icon, t.label, selected = t == tab && !settingsOpen) { onTab(t) }
        }
        Spacer(Modifier.weight(1f))
        RailItem(LobsterIcons.Settings, "Settings", selected = settingsOpen, onClick = onSettings)
    }
}

/** A rail entry: icon over label; the chosen one gets a coral mark at the edge. */
@Composable
private fun RailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(96.dp)
            .heightIn(min = 64.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(Sea.Coral)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = if (selected) Sea.Coral else Sea.Muted, modifier = Modifier.size(24.dp))
            Text(label, color = if (selected) Sea.Text else Sea.Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}
