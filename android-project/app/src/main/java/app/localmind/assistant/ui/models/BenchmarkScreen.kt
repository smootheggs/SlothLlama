package app.localmind.assistant.ui.models

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.bench.Bench
import app.localmind.assistant.domain.bench.BenchBackend
import app.localmind.assistant.domain.bench.BenchRun
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the benchmark screen can ask for. */
class BenchActions(
    val select: (String) -> Unit,
    val backend: (BenchBackend) -> Unit,
    val threads: (Int) -> Unit,
    val start: () -> Unit,
    val stop: () -> Unit,
    val copy: () -> Unit,
    val clear: () -> Unit
)

/**
 * Models › Benchmark (1.52): times a model on this device. Two columns on a wide screen (the test
 * at left, the results at right), one on a phone with the Run button kept at the bottom.
 */
@Composable
fun BenchmarkScreen(viewModel: BenchmarkViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val actions = remember(viewModel) {
        BenchActions(
            select = viewModel::select, backend = viewModel::setBackend, threads = viewModel::setThreads,
            start = viewModel::start, stop = viewModel::stop,
            copy = { clipboard.setText(AnnotatedString(viewModel.resultsText())) },
            clear = viewModel::clearResults
        )
    }
    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(
                title = "Benchmark",
                subtitle = state.device?.let { d -> listOf(d.name, gpuLine(state)).filter { it.isNotBlank() }.joinToString(" · ") }
                    ?: "How fast this device runs your models",
                onBack = onBack
            )
            BenchmarkContent(state, actions)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** The screen below its top bar; takes only state, so it can be drawn on its own (device previews). */
@Composable
fun BenchmarkContent(state: BenchUiState, actions: BenchActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight
        if (wide) {
            Row(
                Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    Modifier.width(400.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DeviceCard(state)
                    TestCard(state, actions)
                    RunButton(state, actions)
                }
                Column(
                    Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LatestCard(state, actions)
                    HistoryCard(state, actions)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(
                    Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LatestCard(state, actions)
                    TestCard(state, actions)
                    HistoryCard(state, actions)
                }
                Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)) {
                    RunButton(state, actions)
                }
            }
        }
    }
}

private fun gpuLine(state: BenchUiState): String = when {
    state.gpuName == null -> ""
    state.gpuName.isEmpty() -> "no GPU for llama.cpp"
    else -> shortGpu(state.gpuName)
}

private fun shortGpu(raw: String): String =
    raw.replace("(TM)", "").replace("QUALCOMM", "", ignoreCase = true).replace(Regex("\\s+"), " ").trim()

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card)
            .border(1.dp, P.Border, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = P.Muted, fontSize = 12.sp, letterSpacing = 1.2.sp)
}

@Composable
private fun DeviceCard(state: BenchUiState) {
    val d = state.device ?: return
    Card {
        Label("This device")
        Text(d.name, color = P.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            listOf(d.chip, gpuLine(state).ifBlank { "checking the GPU" }, "${d.ramGb} GB", "${d.cores} cores")
                .filter { it.isNotBlank() }.joinToString(" · "),
            color = P.Muted, fontSize = 14.sp
        )
    }
}

@Composable
private fun TestCard(state: BenchUiState, actions: BenchActions) {
    Card {
        Label("The test")
        if (state.models.isEmpty()) {
            Text("No GGUF model on this device yet: add one on the Models screen.", color = P.Muted, fontSize = 14.sp)
        }
        state.models.forEach { m ->
            val picked = m.path == state.selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                    .selectable(picked, enabled = !state.running, role = Role.RadioButton) { actions.select(m.path) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                        .border(2.dp, if (picked) P.Accent else P.BorderStrong, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) { if (picked) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(P.Accent)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.displayName, color = P.Text, fontSize = 15.sp)
                    Text(listOfNotNull(m.quantization, formatBytes(m.sizeBytes)).joinToString(" · "), color = P.Muted, fontSize = 12.sp)
                }
            }
        }
        if (state.liteRtCount > 0) {
            Text("LiteRT models aren't timed here yet; their speed shows under each reply.", color = P.Muted, fontSize = 12.sp)
        }
        Text("Runs on", color = P.Muted, fontSize = 13.sp)
        Segments(
            options = BenchBackend.entries.map { it.label },
            selected = if (state.hasGpu) state.backend.ordinal else BenchBackend.CPU.ordinal,
            enabled = !state.running && state.hasGpu
        ) { actions.backend(BenchBackend.entries[it]) }
        if (!state.hasGpu && state.gpuName != null) {
            Text("llama.cpp has no GPU support on this chip, so models run on the CPU.", color = P.Muted, fontSize = 12.sp)
        }
        Text("CPU threads", color = P.Muted, fontSize = 13.sp)
        Segments(
            options = state.threadChoices.map { if (it == state.usualThreads) "$it (usual)" else "$it" },
            selected = state.threadChoices.indexOf(state.threads).coerceAtLeast(0),
            enabled = !state.running
        ) { actions.threads(state.threadChoices[it]) }
        Text(
            "Reads a prompt of about 600 tokens, then writes ${Bench.WRITE_TOKENS} tokens, ${Bench.RUNS} times. " +
                "Plug in and close other apps: a warm device runs slower.",
            color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
        )
    }
}

@Composable
private fun Segments(options: List<String>, selected: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (on) P.AccentSoft else P.Card)
                    .border(1.dp, if (on) P.Accent else P.BorderStrong, RoundedCornerShape(22.dp))
                    .selectable(on, enabled = enabled, role = Role.RadioButton) { onPick(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (enabled || on) P.Text else P.Faint, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RunButton(state: BenchUiState, actions: BenchActions) {
    val running = state.running
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (running) P.Elevated else P.Accent)
            .clickable(enabled = running || state.models.isNotEmpty(), role = Role.Button) {
                if (running) actions.stop() else actions.start()
            },
        contentAlignment = Alignment.Center
    ) {
        if (running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = P.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("${state.stage} · Stop", color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("Run · about a minute", color = P.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LatestCard(state: BenchUiState, actions: BenchActions) {
    val r = state.latest
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Label(if (r == null) "Latest run" else "Latest · ${r.modelName} · ${Bench.where(r)} · ${dateOf(r.at)}")
            }
            if (state.runs.isNotEmpty()) TextButton(onClick = actions.copy) { Text("Copy results", color = P.Accent) }
        }
        if (r == null) {
            Text("No runs yet. Pick a model and tap Run.", color = P.Muted, fontSize = 14.sp)
            return@Card
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val two = maxWidth >= 360.dp
            val read: @Composable () -> Unit = {
                Metric(
                    "Reading the prompt", r.readTps,
                    "${r.promptTokens} tokens: how long before a reply to a long message or an article starts."
                )
            }
            val write: @Composable () -> Unit = {
                Metric("Writing the reply", r.writeTps, "How fast the words appear; people read about 5 tokens a second.")
            }
            if (two) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.weight(1f)) { read() }
                    Box(Modifier.weight(1f)) { write() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { read(); write() }
            }
        }
        Text(
            "Load %.1f s · Memory %.1f GB · runs within ±%.0f%%".format(Locale.ROOT, r.loadSeconds, r.memoryMb / 1024.0, r.spreadPct / 2),
            color = P.Muted, fontSize = 13.sp
        )
        if (r.fellBack) {
            Text("The GPU couldn't take this model, so it ran on the CPU.", color = P.Warning, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Metric(title: String, tps: Double, note: String) {
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$title: ${Bench.speed(tps)} tokens a second" },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = P.Muted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Bench.speed(tps), color = P.Text, fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("tokens/s", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 7.dp))
        }
        Text(note, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun HistoryCard(state: BenchUiState, actions: BenchActions) {
    if (state.runs.size < 2) return
    Card {
        Label("Earlier runs on this device")
        state.runs.drop(1).take(20).forEach { r -> HistoryRow(r) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Reading / writing, tokens a second. Kept on this device only.",
                color = P.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = actions.clear, enabled = !state.running) { Text("Clear", color = P.Muted) }
        }
    }
}

@Composable
private fun HistoryRow(r: BenchRun) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.modelName, color = P.Text, fontSize = 14.sp)
            Text("${Bench.where(r)} · ${dateOf(r.at)}", color = P.Muted, fontSize = 12.sp)
        }
        Text("${Bench.speed(r.readTps)} / ${Bench.speed(r.writeTps)}", color = P.Text, fontSize = 14.sp)
    }
}

private fun dateOf(at: Long): String = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(at))

private fun formatBytes(bytes: Long): String = app.localmind.assistant.data.models.ModelLibrary.formatBytes(bytes)
