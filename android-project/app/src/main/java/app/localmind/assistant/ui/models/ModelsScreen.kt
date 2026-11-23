package app.localmind.assistant.ui.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.inference.GpuSettings
import app.localmind.assistant.data.models.LocalModel
import app.localmind.assistant.data.models.ModelFormat
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.data.voice.VoiceModelState
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P

@Composable
fun ModelsScreen(viewModel: ModelsViewModel, onBack: () -> Unit, onBenchmark: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val voiceModel by viewModel.voiceModel.collectAsStateWithLifecycle()
    val gpu by viewModel.gpuState.collectAsStateWithLifecycle()
    // Which model the vision-file picker is choosing for.
    var visionFor by remember { mutableStateOf<LocalModel?>(null) }
    val visionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val model = visionFor
        visionFor = null
        if (uri != null && model != null) viewModel.addVision(model, uri)
    }
    val whisperModel by viewModel.whisperModel.collectAsStateWithLifecycle()
    val whisperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importWhisper(uris)
    }
    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importVoiceModel(uris)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize().background(P.Canvas)) {
        SlothTopBar(
            title = "Models",
            subtitle = "On-device runtime · 100% offline",
            onBack = onBack,
            actions = {
                IconButton(onClick = onBenchmark) {
                    Icon(Icons.Outlined.Speed, contentDescription = "Benchmark", tint = P.Muted)
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan model folders", tint = P.Muted)
                }
            }
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // One column in portrait; two side by side in landscape.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 440.dp),
                modifier = Modifier.widthIn(max = 1280.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { RuntimeCard(state.engine) }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    GpuCard(
                        gpu = gpu,
                        busy = state.engine is EngineRuntimeState.Loading,
                        onToggle = viewModel::setGpuEnabled,
                        onRetry = viewModel::retryGpu
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) { BenchmarkCard(onBenchmark) }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ImportCard(
                        progress = state.importProgress,
                        folder = state.importFolder,
                        onImport = { picker.launch(arrayOf("*/*")) },
                        onCancel = viewModel::cancelImport
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    VoiceModelCard(
                        state = voiceModel,
                        onImport = { voicePicker.launch(arrayOf("*/*")) },
                        onRemove = viewModel::removeVoiceModel
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    VoiceModelCard(
                        state = whisperModel,
                        onImport = { whisperPicker.launch(arrayOf("*/*")) },
                        onRemove = viewModel::removeWhisper,
                        title = "Voice input: European languages",
                        missingText = "For speaking French, German, Spanish and Italian to the tutors: Whisper Small " +
                            "(about 360 MB). From huggingface.co/csukuangfj/sherpa-onnx-whisper-small download " +
                            "small-encoder.int8.onnx, small-decoder.int8.onnx and small-tokens.txt, then select " +
                            "all three here.",
                        importLabel = "Import Whisper",
                        readyText = "French, German, Spanish and Italian tutors can hear you now. SenseVoice still " +
                            "handles Chinese, Japanese and English (it's faster).",
                        removeLabel = "Remove Whisper"
                    )
                }

                if (state.models.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { GettingStartedCard() }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "ON THIS DEVICE · ${state.models.size}",
                            color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                    items(state.models, key = { it.path }) { model ->
                        ModelCard(
                            model = model,
                            isActive = model.path == state.activePath ||
                                (state.activePath == null && model == state.models.first()),
                            engine = state.engine,
                            hashProgress = state.hashing?.takeIf { it.first.path == model.path }?.second,
                            onClick = { viewModel.activate(model) },
                            onVerify = { viewModel.verify(model) },
                            onDelete = { viewModel.requestDelete(model) },
                            hasVision = model.path in state.withVision,
                            visionProgress = state.visionImport?.takeIf { it.first == model.path }?.second,
                            onAddVision = {
                                visionFor = model
                                visionPicker.launch(arrayOf("*/*"))
                            },
                            onRemoveVision = { viewModel.removeVision(model) }
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    state.fingerprint?.let { (model, hex) ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = viewModel::dismissFingerprint,
            containerColor = P.Elevated,
            title = { Text("SHA-256 fingerprint", color = P.Text) },
            text = {
                Column {
                    Text(model.file.name, color = P.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer {
                        Text(
                            hex.chunked(8).joinToString(" "),
                            color = P.Text, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "To check the file wasn't corrupted or tampered with, open the same file on Hugging Face " +
                            "(Files and versions → the file name) and compare it with the SHA256 shown there. " +
                            "The first and last 8 characters matching is a good quick check.",
                        color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFingerprint) { Text("Done", color = P.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(hex)) }) { Text("Copy", color = P.Muted) }
            }
        )
    }

    state.pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            containerColor = P.Elevated,
            title = { Text("Delete ${model.displayName}?", color = P.Text) },
            text = {
                Text(
                    "This removes the ${ModelLibrary.formatBytes(model.sizeBytes)} file from the tablet. Your chats stay.",
                    color = P.Muted
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete", color = P.Danger) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel", color = P.Muted) }
            }
        )
    }
}

@Composable
private fun RuntimeCard(engine: EngineRuntimeState) {
    val (dot, title, detail) = when (engine) {
        is EngineRuntimeState.Ready -> Triple(
            P.Success,
            "Loaded in Memory",
            "${engine.modelName} · ${engine.maxTokens} token context" +
                (if (engine.modelName.endsWith(".gguf", ignoreCase = true)) " · ${engine.gpu?.let { "GPU ($it)" } ?: "CPU"}" else "") +
                if (engine.canThink) "\nReasoning model · thinking is set in Settings → Assistant" else ""
        )
        is EngineRuntimeState.Loading -> Triple(P.Warning, engine.note?.let { "$it…" } ?: "Loading…", engine.modelName)
        is EngineRuntimeState.Failed -> Triple(P.Danger, "Couldn't load ${engine.modelName}", engine.message)
        is EngineRuntimeState.NoModelFound -> Triple(P.Warning, "No model yet", "Import one below to start chatting.")
        is EngineRuntimeState.Idle -> Triple(P.Faint, "No model loaded", "Tap a model below to load it.")
    }
    SlothCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = P.Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "GGUF → llama.cpp   ·   .task / .litertlm → MediaPipe",
            color = P.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace
        )
    }
}

/** This device's chip as Android names it ("Mediatek MT6991"), or a plain word when it doesn't say. */
private fun chipName(): String {
    val maker = android.os.Build.SOC_MANUFACTURER.orEmpty().takeUnless { it.isBlank() || it == android.os.Build.UNKNOWN }
    val model = android.os.Build.SOC_MODEL.orEmpty().takeUnless { it.isBlank() || it == android.os.Build.UNKNOWN }
    return listOfNotNull(maker, model).joinToString(" ").ifBlank { "chip" }
}

/**
 * The GPU switch. Explains what it does, which GPU was found, and lists any model that crashed
 * the GPU (those run on the CPU until "Try the GPU again").
 */
@Composable
private fun GpuCard(
    gpu: GpuUiState,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRetry: (String) -> Unit
) {
    val name = gpu.gpuName
    val available = !name.isNullOrEmpty()
    SlothCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use the GPU", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        name == null -> "Checking this device's graphics chip…"
                        // llama.cpp's GPU backend is made for Snapdragon's Adreno; on other chips
                        // (the Tab S11 Ultra's MediaTek, say) GGUF models run on the CPU.
                        !available -> "llama.cpp has no GPU support for this ${chipName()}, so GGUF models run on the " +
                            "CPU. LiteRT models (.litertlm) try the GPU on their own."
                        else -> name.replace("(TM)", "").replace("QUALCOMM", "", ignoreCase = true).trim()
                    },
                    color = P.Muted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = available && gpu.enabled,
                onCheckedChange = onToggle,
                enabled = available && !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = P.OnAccent,
                    checkedTrackColor = P.Accent,
                    uncheckedThumbColor = P.Muted,
                    uncheckedTrackColor = P.Elevated,
                    uncheckedBorderColor = P.BorderStrong
                )
            )
        }
        if (available) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Runs the model on the graphics chip. Biggest win: long chats are read much faster, " +
                    "so replies start sooner. Q4_0 files are the fastest on this GPU. The very first " +
                    "GPU load takes about a minute while it sets up; after that it's quick. Compare the " +
                    "tokens/s under replies with this on and off.",
                color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        gpu.cpuOnlyModels.sorted().forEach { file ->
            Spacer(Modifier.height(10.dp))
            val photos = file.endsWith(GpuSettings.VISION_SUFFIX)
            val model = file.removeSuffix(GpuSettings.VISION_SUFFIX)
            Text(
                if (photos) "SlothLlama closed while $model was reading a photo on the GPU, so its photos use the CPU now."
                else "SlothLlama closed while $model was starting on the GPU, so it uses the CPU now.",
                color = P.Warning, fontSize = 12.sp, lineHeight = 17.sp
            )
            TextButton(
                onClick = { onRetry(file) },
                enabled = available && !busy,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) { Text("Try the GPU again", color = P.Accent, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ImportCard(progress: Float?, folder: String, onImport: () -> Unit, onCancel: () -> Unit) {
    SlothCard {
        if (progress == null) {
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import Model File", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a .gguf, .task or .litertlm file from Downloads, an SSD or any folder. " +
                    "Files copied over USB to the folder below also appear here.",
                color = P.Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(folder, color = P.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        } else {
            Text("Importing… ${(progress * 100).toInt()}%", color = P.Text, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = P.Accent,
                trackColor = P.Border,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            )
            TextButton(onClick = onCancel) { Text("Cancel", color = P.Muted) }
        }
    }
}

@Composable
private fun VoiceModelCard(
    state: VoiceModelState,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    title: String = "Voice input",
    missingText: String = "Speak instead of typing, fully offline: SenseVoice understands Mandarin, Cantonese, " +
        "English, Japanese and Korean. Import model.int8.onnx and tokens.txt together " +
        "(select both in the picker).",
    importLabel: String = "Import voice model",
    readyText: String = "Tap the mic in any chat. Tutors listen for their own language; other personas detect it.",
    removeLabel: String = "Remove voice model"
) {
    SlothCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = P.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state is VoiceModelState.Ready) {
                Text("Ready · ${ModelLibrary.formatBytes(state.sizeBytes)}", color = P.Success, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        when (state) {
            is VoiceModelState.Missing -> {
                Text(missingText, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = P.Elevated, contentColor = P.Text),
                    border = BorderStroke(1.dp, P.BorderStrong),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(importLabel)
                }
            }
            is VoiceModelState.Importing -> {
                Text("Importing… ${(state.progress * 100).toInt()}%", color = P.Text, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    color = P.Accent,
                    trackColor = P.Border,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                )
            }
            is VoiceModelState.Ready -> {
                Text(readyText, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                TextButton(onClick = onRemove) { Text(removeLabel, color = P.Muted) }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: LocalModel,
    isActive: Boolean,
    engine: EngineRuntimeState,
    hashProgress: Float?,
    onClick: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    hasVision: Boolean = false,
    visionProgress: Float? = null,
    onAddVision: () -> Unit = {},
    onRemoveVision: () -> Unit = {}
) {
    // A persona may have its own model, so "loaded" doesn't have to be the default.
    val loadedHere = engine is EngineRuntimeState.Ready && engine.modelName == model.file.name
    val loadingHere = engine is EngineRuntimeState.Loading && engine.modelName == model.file.name
    SlothCard(
        borderColor = if (isActive) P.Accent else P.Border,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("${model.format.label} · ${model.format.runtime}", if (model.format == ModelFormat.GGUF) P.Accent else P.Success)
                    model.quantization?.let { Chip(it, P.Muted) }
                    Chip(ModelLibrary.formatBytes(model.sizeBytes), P.Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        loadedHere -> "● Loaded in Memory"
                        loadingHere -> "Loading…"
                        isActive -> "Default model · tap to load"
                        else -> "Tap to make this the default"
                    },
                    color = when {
                        loadedHere -> P.Success
                        loadingHere -> P.Warning
                        isActive -> P.Accent
                        else -> P.Faint
                    },
                    fontSize = 11.sp
                )
                if (hashProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Checking fingerprint… ${(hashProgress * 100).toInt()}%", color = P.Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { hashProgress },
                        color = P.Accent, trackColor = P.Border,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(onClick = onVerify, enabled = hashProgress == null) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = "Show SHA-256 fingerprint", tint = P.Faint)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete model", tint = P.Faint)
            }
        }
        if (model.format == ModelFormat.GGUF) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = if (hasVision) P.Success else P.Faint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        visionProgress != null -> "Adding vision file… ${(visionProgress * 100).toInt()}%"
                        hasVision -> "Photos: on"
                        else -> "Photos: add the model's vision file (mmproj)"
                    },
                    color = if (hasVision) P.Text else P.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                if (visionProgress == null) {
                    TextButton(onClick = if (hasVision) onRemoveVision else onAddVision) {
                        Text(if (hasVision) "Remove" else "Add", color = if (hasVision) P.Muted else P.Accent, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GettingStartedCard() {
    SlothCard {
        Text("Getting a model", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "On Hugging Face, search for a model name plus \"GGUF\" and download a Q4_K_M file " +
                "(a good balance of quality and size). On a 12 GB tablet, models up to about 7–8B parameters " +
                "(files under ~5 GB) run comfortably.",
            color = P.Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Text("Good starting points", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.sp)
        listOf(
            "Qwen2.5-3B-Instruct / Qwen3-4B — fast, strongest for Chinese tutoring",
            "Gemma 3 4B it — strong all-rounder",
            "Llama 3.2 3B Instruct — quick everyday chat",
            "Qwen2.5-7B-Instruct — deeper answers, slower"
        ).forEach {
            Text("• $it", color = P.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(P.Elevated)
            .border(1.dp, P.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** The way to the benchmark: how fast this device runs a model, on the GPU or the CPU. */
@Composable
private fun BenchmarkCard(onOpen: () -> Unit) {
    SlothCard(Modifier.clickable(onClickLabel = "Open the benchmark", role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Benchmark", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "How fast this device reads and writes with a model, on the GPU or the CPU, so you know which to use.",
                    color = P.Muted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.Speed, contentDescription = null, tint = P.Accent)
        }
    }
}

@Composable
fun SlothCard(
    modifier: Modifier = Modifier,
    borderColor: Color = P.Border,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(P.Card)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(modifier)
            .padding(14.dp)
    ) {
        content()
    }
}
