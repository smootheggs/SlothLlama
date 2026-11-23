package app.localmind.assistant.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.bench.BenchDevice
import app.localmind.assistant.data.bench.BenchStore
import app.localmind.assistant.data.inference.GpuSettings
import app.localmind.assistant.data.inference.RoutingInferenceEngine
import app.localmind.assistant.data.models.LocalModel
import app.localmind.assistant.data.models.ModelFormat
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.domain.bench.Bench
import app.localmind.assistant.domain.bench.BenchBackend
import app.localmind.assistant.domain.bench.BenchRun
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class BenchUiState(
    val device: BenchDevice? = null,
    /** The GPU llama.cpp found ("" none, null still checking). */
    val gpuName: String? = null,
    /** GGUF models only: the benchmark times llama.cpp. */
    val models: List<LocalModel> = emptyList(),
    /** LiteRT models on the device, which the benchmark doesn't time yet. */
    val liteRtCount: Int = 0,
    val selected: String? = null,
    val backend: BenchBackend = BenchBackend.GPU,
    val threadChoices: List<Int> = emptyList(),
    val threads: Int = 4,
    val usualThreads: Int = 4,
    /** What's happening now ("Run 2 of 3"), or null when idle. */
    val stage: String? = null,
    val runs: List<BenchRun> = emptyList(),
    val message: String? = null
) {
    val running: Boolean get() = stage != null
    val latest: BenchRun? get() = runs.firstOrNull()
    val hasGpu: Boolean get() = !gpuName.isNullOrEmpty()
}

/**
 * The benchmark (1.52): times a GGUF model on this device, on the GPU, the CPU or both, and keeps
 * the results here. Nothing leaves the device; "Copy results" gives plain text to paste elsewhere.
 */
@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val engine: RoutingInferenceEngine,
    private val library: ModelLibrary,
    private val gpu: GpuSettings,
    private val store: BenchStore
) : ViewModel() {

    private val _state = MutableStateFlow(BenchUiState())
    val state: StateFlow<BenchUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        val device = store.device()
        val usual = Bench.usualThreads(device.cores)
        _state.update {
            it.copy(
                device = device, threadChoices = Bench.threadChoices(device.cores),
                threads = usual, usualThreads = usual, runs = store.runs.value
            )
        }
        viewModelScope.launch {
            val all = runCatching { library.refresh() }.getOrDefault(emptyList())
            val gguf = all.filter { it.format == ModelFormat.GGUF }
            val active = library.activePath.value
            _state.update { s ->
                s.copy(
                    models = gguf,
                    liteRtCount = all.count { it.format == ModelFormat.LITERT },
                    selected = s.selected ?: gguf.firstOrNull { it.path == active }?.path ?: gguf.firstOrNull()?.path
                )
            }
        }
        viewModelScope.launch { gpu.gpuName.collect { name -> _state.update { it.copy(gpuName = name) } } }
        viewModelScope.launch { store.runs.collect { runs -> _state.update { it.copy(runs = runs) } } }
    }

    fun select(path: String) = _state.update { it.copy(selected = path) }
    fun setBackend(b: BenchBackend) = _state.update { it.copy(backend = b) }
    fun setThreads(n: Int) = _state.update { it.copy(threads = n) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun start() {
        val s = _state.value
        if (s.running) return
        val model = s.models.firstOrNull { it.path == s.selected }
        if (model == null) {
            _state.update { it.copy(message = "Add a GGUF model on the Models screen first.") }
            return
        }
        val device = s.device ?: return
        // Without a GPU llama.cpp can use, "GPU" and "Both" would only time the CPU again.
        val plan = when {
            !s.hasGpu -> listOf(BenchBackend.CPU)
            s.backend == BenchBackend.BOTH -> listOf(BenchBackend.GPU, BenchBackend.CPU)
            else -> listOf(s.backend)
        }
        _state.update { it.copy(stage = "Starting") }
        job = viewModelScope.launch {
            try {
                for (backend in plan) {
                    val label = if (plan.size > 1) "${backend.label}: " else ""
                    val timing = engine.benchmark(model.file, backend == BenchBackend.GPU, s.threads) { stage ->
                        _state.update { it.copy(stage = label + stage) }
                    }
                    store.add(
                        Bench.run(
                            id = UUID.randomUUID().toString(), at = System.currentTimeMillis(),
                            model = model.file.name, modelName = model.displayName, asked = backend,
                            timing = timing, device = device.name
                        )
                    )
                }
                if (!s.hasGpu && s.backend != BenchBackend.CPU) {
                    _state.update { it.copy(message = "No GPU llama.cpp can use here, so it ran on the CPU.") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(message = e.message?.takeIf { m -> m.isNotBlank() } ?: "The benchmark didn't finish.") }
            } finally {
                _state.update { it.copy(stage = null) }
            }
        }
    }

    fun stop() {
        engine.stopBenchmark()
    }

    fun clearResults() {
        viewModelScope.launch { store.clear() }
    }

    /** The results as plain text, for the clipboard. */
    fun resultsText(): String {
        val s = _state.value
        val name = listOfNotNull(s.device?.name, s.device?.chip?.takeIf { it.isNotBlank() }).joinToString(" · ")
        val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
        return Bench.asText(s.runs, name) { fmt.format(Date(it)) }
    }

    override fun onCleared() {
        // Leaving the screen ends a run; the model stays loaded for the next reply.
        if (job?.isActive == true) engine.stopBenchmark()
        super.onCleared()
    }
}
