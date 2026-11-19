package app.slothlobster.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.PowerManager
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.slothlobster.BuildConfig
import app.slothlobster.core.Diagnostics
import app.slothlobster.core.Live
import app.slothlobster.core.Saved
import app.slothlobster.core.Settings
import app.slothlobster.web.Feeds
import app.slothlobster.web.Search
import app.slothlobster.web.Web

@Composable
fun SettingsScreen(
    saved: Saved,
    live: Live,
    onBack: () -> Unit,
    onPair: (String) -> Boolean,
    onUnpair: () -> Unit,
    onFolder: (android.net.Uri) -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit,
    onSetFeeds: (String) -> Unit,
    onImportOpml: (android.net.Uri) -> Unit,
    onBriefNow: () -> Unit,
    dataKeySaved: Boolean,
    onDataKey: (String?) -> Boolean,
    onStopEverything: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = saved.settings
    var pairing by rememberSaveable { mutableStateOf(false) }
    var timing by rememberSaveable { mutableStateOf(false) }
    var feeds by rememberSaveable { mutableStateOf(false) }
    var dataKeyOpen by rememberSaveable { mutableStateOf(false) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
    var diagnosticsVersion by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val crashCount = remember(diagnosticsVersion, diagnosticsOpen) { Diagnostics.crashes(context).size }
    val unrestricted = remember(diagnosticsVersion, diagnosticsOpen) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolder(uri)
    }
    val pickOpml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportOpml(uri)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader("Settings", "Pairing, mailbox and brain", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Pairing") {
                Panel {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(LobsterIcons.Lock, null, tint = if (live.paired) Sea.Coral else Sea.Faint, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (live.paired) "Paired with SlothLlama" else "Not paired yet", color = Sea.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (live.paired) "Code ends …${live.codeHint.orEmpty()}" else "Copy the pairing code from SlothLlama's Lobster page",
                                    color = Sea.Muted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(if (live.paired) "Paste a new code" else "Paste the code", { pairing = true }, icon = LobsterIcons.Paste)
                            if (live.paired) GhostButton("Unpair", onUnpair)
                        }
                    }
                }
            }

            Section("Mailbox") {
                Panel {
                    ListRow(
                        "Mailbox folder",
                        when {
                            live.folderName != null -> live.folderName + if (live.lastSync > 0) " · synced ${ago(live.lastSync, System.currentTimeMillis())}" else ""
                            live.folderMissing -> "Can't open the folder any more: choose it again"
                            else -> "Choose the same folder as SlothLlama"
                        },
                        onClick = { pickFolder.launch(null) },
                        divider = false
                    ) { Text(if (live.folderName != null) "Change" else "Choose", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
                Text(
                    "On one tablet, both apps use one folder (for example Documents/SlothMailbox). " +
                        "On a phone, sync that folder to the tablet with Syncthing.",
                    color = Sea.Faint,
                    fontSize = 12.sp
                )
            }

            Section("Search engines") {
                Panel {
                    Search.ENGINES.forEachIndexed { i, name ->
                        val h = saved.engines[name]
                        val now = System.currentTimeMillis()
                        ListRow(
                            name,
                            when {
                                h == null -> "Not used yet"
                                h.failsInRow >= 3 -> "Not answering (${h.failsInRow} tries in a row); tried last"
                                h.lastOk > 0 -> "Working · answered ${ago(h.lastOk, now)}"
                                else -> "No answer yet"
                            },
                            divider = i < Search.ENGINES.size - 1
                        ) {
                            StatusDot(h != null && h.failsInRow < 3 && h.lastOk > 0)
                        }
                    }
                }
                Text(
                    "Free engines, no account. They're tried in this order; one that keeps failing goes last, and the Log says so.",
                    color = Sea.Faint,
                    fontSize = 12.sp
                )
            }

            Section("Look") {
                Panel {
                    ThemeChoice.entries.forEachIndexed { i, choice ->
                        if (i > 0) Divider()
                        Radio(
                            choice.label,
                            when (choice) {
                                ThemeChoice.SYSTEM -> "White by day, black at night, as the tablet is set"
                                ThemeChoice.WHITE -> "Easy to read in daylight"
                                ThemeChoice.OLED -> "True black: kind to the eyes and the battery at night"
                            },
                            selected = ThemeChoice.of(s.theme) == choice,
                            onClick = { onSettings { it.copy(theme = choice.id) } }
                        )
                    }
                }
                Panel {
                    SwitchRow(
                        "Full screen",
                        "Hide the status bar and the taskbar. Swipe in from the edge to show them for a moment.",
                        s.fullScreen,
                        { on -> onSettings { it.copy(fullScreen = on) } },
                        divider = false
                    )
                }
            }

            Section("Brain") {
                Panel {
                    Radio(
                        "Free jobs only",
                        "Fetch, watch, search and gather. SlothLlama summarises offline on the tablet. Costs nothing.",
                        selected = true
                    )
                    Divider()
                    Radio(
                        "Cloud model",
                        "Off for now. A Claude Pro or Gemini plan doesn't cover this: it needs an API key, billed separately.",
                        selected = false,
                        enabled = false
                    ) { Pill("Review later") }
                }
            }

            Section("Routine") {
                Panel {
                    SwitchRow(
                        "Daily brief",
                        "Headlines from your feeds at ${"%02d:%02d".format(s.briefHour, s.briefMinute)}",
                        s.briefOn,
                        { on -> onSettings { it.copy(briefOn = on) } }
                    )
                    ListRow("Brief time", "%02d:%02d".format(s.briefHour, s.briefMinute), onClick = { timing = true }) {
                        Text("Edit", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    ListRow("Feeds", feedsLine(s), onClick = { feeds = true }) {
                        Text("Edit", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    ListRow("Import feeds", "From a news reader's OPML file; its folders become groups", onClick = { pickOpml.launch(arrayOf("*/*")) }) {
                        Text("Choose", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    ListRow("Make a brief now", "Sent to the mailbox like the daily one", onClick = onBriefNow, divider = false)
                }
                Panel {
                    SwitchRow("Notify me", "When something is brought back or a watch changes", s.notify, { on -> onSettings { it.copy(notify = on) } })
                    SwitchRow("Only on Wi-Fi", "Waits instead of using mobile data", s.wifiOnly, { on -> onSettings { it.copy(wifiOnly = on) } })
                    SwitchRow(
                        "Stay awake",
                        "Checks the mailbox every half minute, with a quiet notification. Off: every 15 minutes.",
                        s.stayAwake,
                        { on -> onSettings { it.copy(stayAwake = on) } },
                        divider = false
                    )
                }
            }

            Section("Market data") {
                Panel {
                    ListRow(
                        "Tiingo key",
                        if (dataKeySaved) "Saved, sealed by this phone's Keystore. Used only for SlothDesk's daily prices"
                        else "Free from tiingo.com (sign up, then Account → API). Needed for ETF prices such as GLD and SLV",
                        onClick = { dataKeyOpen = true },
                        divider = false
                    ) { Text(if (dataKeySaved) "Change" else "Add", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
                Text(
                    "Rates (FRED) and gold's volatility index (Cboe) need no key. The key is sent only to Tiingo, in a " +
                        "header, and is never written to the Log or the mailbox.",
                    color = Sea.Faint,
                    fontSize = 12.sp
                )
            }

            Section("Diagnostics") {
                Panel {
                    ListRow(
                        "Battery use",
                        if (unrestricted) "Unrestricted: checks run on time"
                        else "Optimised: Android may hold checks back. Set SlothLobster to Unrestricted",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                            diagnosticsVersion++
                        }
                    ) { StatusDot(unrestricted) }
                    ListRow(
                        "Report",
                        "Version, device, recent problems and " + when (crashCount) {
                            0 -> "no crashes"
                            1 -> "1 crash"
                            else -> "$crashCount crashes"
                        } + ", to copy into a bug report",
                        onClick = { diagnosticsOpen = true },
                        divider = false
                    ) { Text("Open", color = Sea.Coral, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }

            GhostButton("Stop everything now", onStopEverything, Modifier.fillMaxWidth(), danger = true)

            Text(
                "SlothLobster ${BuildConfig.VERSION_NAME}. No account, no tracking. Every page it opens is in the Log, " +
                    "and pages are only ever read as text: nothing in them is followed as an instruction.",
                color = Sea.Faint,
                fontSize = 12.sp
            )
        }
    }

    if (pairing) PairDialog(onDismiss = { pairing = false }, onPair = { code -> onPair(code).also { if (it) pairing = false } })
    if (timing) TimeDialog(s.briefHour, s.briefMinute, onDismiss = { timing = false }) { h, m ->
        onSettings { it.copy(briefHour = h, briefMinute = m, lastBriefDay = null) }
        timing = false
    }
    if (diagnosticsOpen) {
        val report = remember(diagnosticsVersion) { Diagnostics.report(context, saved, live) }
        DiagnosticsDialog(
            report = report,
            onCopy = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("SlothLobster diagnostics", report))
            },
            onClear = {
                Diagnostics.clear(context)
                diagnosticsVersion++
            },
            onDismiss = { diagnosticsOpen = false }
        )
    }
    if (dataKeyOpen) DataKeyDialog(saved = dataKeySaved, onDismiss = { dataKeyOpen = false }, onSave = { key ->
        onDataKey(key).also { if (it) dataKeyOpen = false }
    })
    if (feeds) FeedsDialog(Feeds.formatList(s.feeds, s.feedGroups), onDismiss = { feeds = false }) { text ->
        onSetFeeds(text)
        feeds = false
    }
}

@Composable
private fun Radio(
    title: String,
    line: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick) else Modifier)
            .padding(16.dp)
            .semantics { contentDescription = "$title, ${if (selected) "selected" else if (enabled) "not selected" else "not available"}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .border(2.dp, if (selected) Sea.Coral else Sea.Off, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Sea.Coral))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (enabled) Sea.Text else Sea.Muted, fontSize = 15.sp)
            Text(line, color = Sea.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        trailing?.invoke()
    }
}

@Composable
private fun PairDialog(onDismiss: () -> Unit, onPair: (String) -> Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sea.Card,
        title = { Text("Pairing code", color = Sea.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "In SlothLlama: Lobster page, pairing code, copy. Then paste it here. It's the key that " +
                        "seals the mailbox, so keep it to yourself.",
                    color = Sea.Muted,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; wrong = false },
                    label = { Text("43 letters and numbers") },
                    isError = wrong,
                    supportingText = if (wrong) ({ Text("That isn't a pairing code. Copy it again from SlothLlama.") }) else null,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Sea.Text),
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { code = it.trim(); wrong = false } }) {
                            Icon(LobsterIcons.Paste, contentDescription = "Paste", tint = Sea.Coral)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sea.Coral, focusedLabelColor = Sea.Coral, cursorColor = Sea.Coral),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { wrong = !onPair(code) }, enabled = code.isNotBlank()) { Text("Pair", color = Sea.Coral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sea.Muted) } }
    )
}

@Composable
private fun DataKeyDialog(saved: Boolean, onDismiss: () -> Unit, onSave: (String?) -> Boolean) {
    var key by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sea.Card,
        title = { Text("Tiingo key", color = Sea.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The free plan is enough: SlothDesk asks for a few tickers once a day. Paste the key from " +
                        "tiingo.com → Account → API. It can't be read back once saved.",
                    color = Sea.Muted,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim(); wrong = false },
                    label = { Text("API key") },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) ({ Text("That doesn't look like a Tiingo key (letters and numbers).") }) else null,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Sea.Text),
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { key = it.trim(); wrong = false } }) {
                            Icon(LobsterIcons.Paste, contentDescription = "Paste", tint = Sea.Coral)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sea.Coral, focusedLabelColor = Sea.Coral, cursorColor = Sea.Coral),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { wrong = !onSave(key) }, enabled = key.isNotBlank()) { Text("Save", color = Sea.Coral) }
        },
        dismissButton = {
            Row {
                if (saved) TextButton(onClick = { onSave(null) }) { Text("Remove", color = Sea.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Sea.Muted) }
            }
        }
    )
}

@Composable
private fun DiagnosticsDialog(report: String, onCopy: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sea.Card,
        title = { Text("Diagnostics", color = Sea.Text) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Stays on this device unless you copy it. It can include links from the Log, so read it before you share it.",
                    color = Sea.Muted,
                    fontSize = 12.sp
                )
                Text(report, color = Sea.Text, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(); copied = true }) { Text(if (copied) "Copied" else "Copy", color = Sea.Coral) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear crashes", color = Sea.Muted) }
                TextButton(onClick = onDismiss) { Text("Close", color = Sea.Muted) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onSet: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sea.Card,
        title = { Text("Brief time", color = Sea.Text) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Sea.Raised,
                    selectorColor = Sea.Coral,
                    timeSelectorSelectedContainerColor = Sea.CoralSoft,
                    timeSelectorSelectedContentColor = Sea.Coral,
                    timeSelectorUnselectedContainerColor = Sea.Raised
                )
            )
        },
        confirmButton = { TextButton(onClick = { onSet(state.hour, state.minute) }) { Text("Set", color = Sea.Coral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sea.Muted) } }
    )
}

@Composable
private fun FeedsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sea.Card,
        title = { Text("Feeds for the brief", color = Sea.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "One RSS or Atom feed link per line. A line like \"# F1\" starts a group; the brief is arranged by group.",
                    color = Sea.Muted,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Sea.Text),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sea.Coral, cursorColor = Sea.Coral)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save", color = Sea.Coral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sea.Muted) } }
    )
}

private fun feedsLine(s: Settings): String {
    if (s.feeds.isEmpty()) return "None"
    val groups = s.feedGroups.values.distinct()
    return "${s.feeds.size} feeds" + if (groups.isNotEmpty()) " · " + groups.joinToString(", ") else ""
}
