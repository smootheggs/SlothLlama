package app.localmind.assistant.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.persona.TutorLevel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.localmind.assistant.data.dictionary.ChineseDictionary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.BuildConfig
import app.localmind.assistant.R
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.ChineseStyle
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.LightColors
import app.localmind.assistant.ui.theme.OledColors
import app.localmind.assistant.ui.theme.SlothColors
import app.localmind.assistant.ui.theme.ThemeMode
import app.localmind.assistant.ui.theme.SlothPalette as P

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    privacyScreen: Boolean,
    onPrivacyScreenChange: (Boolean) -> Unit,
    chineseStyle: ChineseStyle,
    onChineseScriptChange: (ChineseScript) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onCharScaleChange: (Float) -> Unit,
    thinking: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    onOpenModels: () -> Unit,
    onOpenPersonas: () -> Unit,
    onOpenLobster: () -> Unit,
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = hiltViewModel()
    val reminderOn by vm.reviewReminder.collectAsStateWithLifecycle()
    val reminderHour by vm.reminderHour.collectAsStateWithLifecycle()
    val openToHome by vm.openToHome.collectAsStateWithLifecycle()
    val toolsOn by vm.toolsOn.collectAsStateWithLifecycle()
    val toolsForTutors by vm.toolsForTutors.collectAsStateWithLifecycle()
    val calendarOn by vm.calendarOn.collectAsStateWithLifecycle()
    val levels by vm.levels.collectAsStateWithLifecycle()
    val backupDialog by vm.dialog.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    val context = LocalContext.current
    var dictionarySources by remember { mutableStateOf<String?>(null) }
    val dictionaryLicense = remember { ChineseDictionary.licenseText(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReviewReminder(granted)
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setCalendarOn(granted)
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        vm.export(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.restore(uri)
    }
    val auto by vm.autoBackupState.collectAsStateWithLifecycle()
    val toImport by vm.toImport.collectAsStateWithLifecycle()
    var autoPassword by remember { mutableStateOf(false) }
    // Switching weekly backups on walks through what's missing: folder, then password.
    var pendingEnable by remember { mutableStateOf(false) }
    val continueEnable: () -> Unit = {
        val now = vm.autoBackupState.value
        when {
            !pendingEnable -> Unit
            now.folder == null -> pendingEnable = false
            !now.hasPassword -> autoPassword = true
            else -> {
                pendingEnable = false
                vm.setAutoBackup(true)
            }
        }
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.setAutoBackupFolder(uri)
        if (uri == null) pendingEnable = false else continueEnable()
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val crashCount = remember(diagnosticsOpen) { vm.crashCount() }
    val loadDiagnostics: () -> Unit = {
        diagnostics = null
        scope.launch { diagnostics = vm.diagnosticsReport() }
    }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(title = "Settings", onBack = onBack)
        // Landscape: two columns of sections instead of one narrow strip.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val twoColumns = maxWidth >= WIDE
            val leftSections: @Composable () -> Unit = {
                Section("Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeOption(ThemeMode.LIGHT, LightColors, themeMode, onThemeModeChange, Modifier.weight(1f))
                        ThemeOption(ThemeMode.OLED, OledColors, themeMode, onThemeModeChange, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingRow(
                        title = ThemeMode.SYSTEM.label,
                        detail = "Follow the tablet's light / dark setting",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                    )
                    HorizontalDivider(color = P.Border)
                    ToggleRow(
                        title = "Open on the Home page",
                        detail = "Off: SlothLlama opens straight into your last chat. Home is always in the menu.",
                        checked = openToHome,
                        onCheckedChange = vm::setOpenToHome
                    )
                }

                Section("Chinese") {
                    Text(
                        "How the Mandarin and Cantonese tutors write. Applies from their next reply.",
                        color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    ChineseScript.entries.forEach { script ->
                        SettingRow(
                            title = script.label,
                            detail = script.detail,
                            selected = chineseStyle.script == script,
                            onClick = { onChineseScriptChange(script) }
                        )
                    }
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 6.dp))
                    CharSizeRow(chineseStyle.charScale, onCharScaleChange)
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 6.dp))
                    ToggleRow(
                        title = "Pinyin & Jyutping",
                        detail = "Pinyin with tone marks under Mandarin, Jyutping with tone numbers under Cantonese.",
                        checked = chineseStyle.romanization,
                        onCheckedChange = onRomanizationChange
                    )
                }

                Section("Tutors") {
                    Text(
                        "Your level in each language. Tutors pitch their replies to it; from C1 up they " +
                            "drop the English and romaji lines.",
                        color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    SpeechLanguage.entries.forEach { language ->
                        LevelRow(language, levels[language] ?: TutorLevel.defaultFor(language)) { vm.setLevel(language, it) }
                    }
                }

                Section("Privacy") {
                    val context = LocalContext.current
                    val networkBlocked = remember {
                        context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                    }
                    StatusRow(
                        ok = networkBlocked,
                        title = if (networkBlocked) "No network access" else "Network access allowed",
                        detail = if (networkBlocked) "This app has no internet permission, so Android blocks every connection. Chats and models can't leave the tablet."
                        else "This build can reach the network."
                    )
                    StatusRow(
                        ok = true,
                        title = "Stays out of backups",
                        detail = "Chats, personas and models are excluded from cloud backup and device transfer."
                    )
                    HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 6.dp))
                    ToggleRow(
                        title = "Privacy screen",
                        detail = "Block screenshots and screen recording, and hide chats in Recents.",
                        checked = privacyScreen,
                        onCheckedChange = onPrivacyScreenChange
                    )
                }

                Section("Assistant") {
                    ToggleRow(
                        title = "Thinking",
                        detail = "For reasoning models such as Qwen3.5: think before answering. Better on hard " +
                            "questions, but adds a 20–60 s pause per reply on the tablet. Off is best for conversation practice.",
                        checked = thinking,
                        onCheckedChange = onThinkingChange
                    )
                    HorizontalDivider(color = P.Border)
                    ToggleRow(
                        title = "Offline tools",
                        detail = "Sums, unit conversions, the time in other cities, dates, timers and alarms, notes, " +
                            "your calendar and the Chinese dictionary: worked out on the tablet when a message asks, " +
                            "and handed to the assistant so it doesn't guess.",
                        checked = toolsOn,
                        onCheckedChange = vm::setToolsOn
                    )
                    if (toolsOn) {
                        ToggleRow(
                            title = "Tools in tutor chats too",
                            detail = "Off: tutors teach without them (\"what is 学习?\" stays a lesson).",
                            checked = toolsForTutors,
                            onCheckedChange = vm::setToolsForTutors
                        )
                        ToggleRow(
                            title = "Read my calendar",
                            detail = "For \"what's on my calendar tomorrow?\". Read only, and only when you ask; nothing leaves the tablet.",
                            checked = calendarOn && context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
                            onCheckedChange = { on ->
                                val granted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                if (on && !granted) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                                else vm.setCalendarOn(on)
                            }
                        )
                    }
                    HorizontalDivider(color = P.Border)
                    LinkRow("Models", "Import, choose and delete on-device models", onOpenModels)
                    HorizontalDivider(color = P.Border)
                    LinkRow("Personas", "Built-in and custom personas", onOpenPersonas)
                    HorizontalDivider(color = P.Border)
                    LinkRow("SlothLobster 🦞", "The separate app that goes online for you: mailbox and pairing", onOpenLobster)
                }

            }
            val rightSections: @Composable () -> Unit = {
                Section("Flashcards") {
                    ToggleRow(
                        title = "Daily review reminder",
                        detail = "A notification at ${hourLabel(reminderHour)} when cards are due. Nothing if there's nothing to review.",
                        checked = reminderOn,
                        onCheckedChange = { on ->
                            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (on && !granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else vm.setReviewReminder(on)
                        }
                    )
                    if (reminderOn) {
                        Row(Modifier.padding(start = 4.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(8, 12, 18, 21).forEach { hour ->
                                val selected = hour == reminderHour
                                Text(
                                    hourLabel(hour),
                                    color = if (selected) P.Accent else P.Muted, fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, if (selected) P.Accent else P.Border, RoundedCornerShape(10.dp))
                                        .clickable { vm.setReviewReminder(true, hour) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Section("Backup") {
                    Text(
                        "Cloud backup is off, so your chats live only on this tablet. A backup file keeps " +
                            "chats, custom personas, flashcards and settings safe (models aren't included). " +
                            "Keep it somewhere other than this tablet.",
                        color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    LinkRow("Export backup", busy ?: "Save everything to one file, optionally with a password") {
                        if (busy == null) vm.startExport()
                    }
                    HorizontalDivider(color = P.Border)
                    LinkRow("Restore from backup", "Add a backup's chats, personas and flashcards to this tablet") {
                        if (busy == null) openBackup.launch(arrayOf("*/*"))
                    }
                    HorizontalDivider(color = P.Border)
                    ToggleRow("Back up every week", autoBackupLine(auto), auto.enabled) { on ->
                        when {
                            !on -> vm.setAutoBackup(false)
                            auto.folder == null -> {
                                pendingEnable = true
                                pickBackupFolder.launch(null)
                            }
                            !auto.hasPassword -> {
                                pendingEnable = true
                                autoPassword = true
                            }
                            else -> vm.setAutoBackup(true)
                        }
                    }
                    HorizontalDivider(color = P.Border)
                    LinkRow("Backup folder", auto.folder ?: "Where weekly backups go. An SD card or USB stick is safest") {
                        pickBackupFolder.launch(null)
                    }
                    HorizontalDivider(color = P.Border)
                    LinkRow(
                        "Backup password",
                        if (auto.hasPassword) "Set. Tap to change it" else "Needed: weekly backups are always encrypted"
                    ) { autoPassword = true }
                    if (auto.ready) {
                        HorizontalDivider(color = P.Border)
                        LinkRow("Back up now", busy ?: "Save a weekly-style backup into the folder now") {
                            if (busy == null) vm.autoBackupNow()
                        }
                    }
                }

                Section("About") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.sloth_llama),
                            contentDescription = null,
                            modifier = Modifier.size(width = 32.dp, height = 28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("SlothLlama ${BuildConfig.VERSION_NAME}", color = P.Text, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Runs entirely on this device with llama.cpp (GGUF) and Google AI Edge (.task / .litertlm). " +
                            "Conversations never leave the tablet.",
                        color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    dictionaryLicense?.let { text ->
                        LinkRow("Dictionary sources", "CC-CEDICT (CC BY-SA 4.0) and ToJyutping") { dictionarySources = text }
                    }
                    LinkRow(
                        "Diagnostics",
                        "Version, device and " + when (crashCount) {
                            0 -> "no crashes recorded"
                            1 -> "1 recent crash"
                            else -> "$crashCount recent crashes"
                        } + ", to copy into a bug report"
                    ) {
                        diagnosticsOpen = true
                        loadDiagnostics()
                    }
                }
            }
            val sectionColumn = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            if (twoColumns) {
                Row(
                    Modifier
                        .widthIn(max = 1280.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(Modifier.weight(1f).then(sectionColumn), verticalArrangement = Arrangement.spacedBy(28.dp)) { leftSections() }
                    Column(Modifier.weight(1f).then(sectionColumn), verticalArrangement = Arrangement.spacedBy(28.dp)) { rightSections() }
                }
            } else {
                Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .then(sectionColumn),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    leftSections()
                    rightSections()
                }
            }
        }
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    when (val dialog = backupDialog) {
        BackupDialog.ExportPassword -> ExportPasswordDialog(
            onConfirm = { password ->
                vm.confirmExportPassword(password)
                saveBackup.launch("SlothLlama backup ${java.time.LocalDate.now()}.slothllama")
            },
            onDismiss = vm::dismissDialog
        )
        is BackupDialog.RestorePassword -> RestorePasswordDialog(
            wrongBefore = dialog.wrongBefore,
            onConfirm = { password -> vm.restoreWithPassword(dialog.uri, password) },
            onDismiss = vm::dismissDialog
        )
        null -> Unit
    }
    if (autoPassword) {
        AutoBackupPasswordDialog(
            changing = auto.hasPassword,
            onConfirm = { password ->
                vm.setAutoBackupPassword(password)
                autoPassword = false
                continueEnable()
            },
            onDismiss = {
                autoPassword = false
                pendingEnable = false
            }
        )
    }
    toImport?.let { items -> ToImportDialog(items, onDismiss = vm::dismissToImport) }
    if (diagnosticsOpen) {
        DiagnosticsDialog(
            report = diagnostics,
            onCopy = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SlothLlama diagnostics", diagnostics.orEmpty()))
                scope.launch { snackbar.showSnackbar("Diagnostics copied.") }
            },
            onClear = {
                vm.clearCrashes()
                loadDiagnostics()
            },
            onDismiss = { diagnosticsOpen = false }
        )
    }
    dictionarySources?.let { text ->
        AlertDialog(
            onDismissRequest = { dictionarySources = null },
            confirmButton = { TextButton(onClick = { dictionarySources = null }) { Text("Close", color = P.Accent) } },
            title = { Text("Dictionary sources", color = P.Text) },
            text = {
                Text(
                    text, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                )
            },
            containerColor = P.Card
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(), color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
        )
        content()
    }
}

/** A miniature preview of the theme: canvas, a user bubble and two text lines. */
@Composable
private fun ThemeOption(
    mode: ThemeMode,
    preview: SlothColors,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = current == mode
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) P.Accent else P.Border, RoundedCornerShape(16.dp))
            .clickable { onSelect(mode) }
            .padding(10.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(preview.canvas)
                .border(1.dp, preview.border, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.align(Alignment.End).width(56.dp).height(14.dp)
                    .clip(RoundedCornerShape(7.dp)).background(preview.elevated)
            )
            Box(Modifier.fillMaxWidth(0.85f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(preview.muted.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(preview.muted.copy(alpha = 0.6f)))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                    .background(preview.elevated).border(1.dp, preview.border, RoundedCornerShape(7.dp))
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.width(8.dp))
            Text(mode.label, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp)
        }
    }
}

/** Size of Chinese characters in replies, with a live preview. */
@Composable
private fun CharSizeRow(scale: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Character size", color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${(scale * 100).roundToInt()}%", color = P.Muted, fontSize = 13.sp)
        }
        Text(
            buildAnnotatedString {
                append("Reads like this: ")
                withStyle(SpanStyle(fontSize = (15 * scale).sp)) { append("你好嗎？") }
                append(" nǐ hǎo ma?")
            },
            color = P.Muted, fontSize = 15.sp, lineHeight = (22 * scale).sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = scale,
            onValueChange = { onChange((it * 20).roundToInt() / 20f) },
            valueRange = 1f..1.6f,
            steps = 11,
            colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Border)
        )
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Outlined.Shield else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (ok) P.Success else P.Warning,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/** A language and its level chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelRow(language: SpeechLanguage, level: TutorLevel, onChange: (TutorLevel) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(language.label, color = P.Text, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TutorLevel.entries.forEach { option ->
                val selected = option == level
                Text(
                    option.label,
                    color = if (selected) P.Accent else P.Muted, fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (selected) P.Accent else P.Border, RoundedCornerShape(10.dp))
                        .clickable { onChange(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = P.OnAccent,
                checkedTrackColor = P.Accent,
                uncheckedThumbColor = P.Muted,
                uncheckedTrackColor = P.Elevated,
                uncheckedBorderColor = P.BorderStrong
            )
        )
    }
}

@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 15.sp)
            Text(detail, color = P.Muted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = P.Faint)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) P.Accent else Color.Transparent)
            .border(if (selected) 0.dp else 1.5.dp, if (selected) P.Accent else P.BorderStrong, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = P.OnAccent, modifier = Modifier.size(14.dp))
    }
}

/** "8:00 AM" / "20:00", following the tablet's 12/24-hour setting. */
@Composable
private fun hourLabel(hour: Int): String {
    val context = LocalContext.current
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, 0)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(cal.time)
}

private fun autoBackupLine(auto: app.localmind.assistant.data.backup.AutoBackupState): String {
    val day = { ms: Long -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(ms)) }
    return when {
        !auto.enabled -> "Keeps the newest ${app.localmind.assistant.data.backup.AutoBackup.KEEP} encrypted backups in a folder you choose"
        auto.lastAt == 0L -> "On. The first backup runs within a day"
        !auto.lastOk -> "Last try (${day(auto.lastAt)}) failed: ${auto.lastResult.orEmpty()}"
        else -> "On. Last saved ${day(auto.lastAt)}: ${auto.lastResult.orEmpty()}"
    }
}
