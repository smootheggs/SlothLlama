package app.localmind.assistant.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.inference.EngineStatus
import app.localmind.assistant.data.models.LocalModel
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.data.voice.VoiceModelState
import app.localmind.assistant.data.voice.VoiceTranscriber
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.inference.LocalInferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(
    val models: List<LocalModel> = emptyList(),
    val activePath: String? = null,
    val engine: EngineRuntimeState = EngineRuntimeState.Idle,
    val importProgress: Float? = null,
    val importFolder: String = "",
    val message: String? = null,
    val pendingDelete: LocalModel? = null,
    /** Model whose SHA-256 is being computed, with progress 0..1. */
    val hashing: Pair<LocalModel, Float>? = null,
    /** Finished fingerprint to show: model and its SHA-256 hex. */
    val fingerprint: Pair<LocalModel, String>? = null
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val library: ModelLibrary,
    private val engine: LocalInferenceEngine,
    status: EngineStatus,
    private val voice: VoiceTranscriber
) : ViewModel() {

    /** The offline speech-recognition model used for voice input. */
    val voiceModel: StateFlow<VoiceModelState> = voice.state

    fun importVoiceModel(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            voice.import(uris)
                .onSuccess { local.update { it.copy(message = "Voice input is ready. Tap the mic in any chat.") } }
                .onFailure { e -> local.update { it.copy(message = e.message ?: "Couldn't import the voice model.") } }
        }
    }

    fun removeVoiceModel() {
        viewModelScope.launch { voice.remove() }
    }

    private val local = MutableStateFlow(ModelsUiState(importFolder = library.importFolder.absolutePath))
    private var importJob: Job? = null

    val uiState: StateFlow<ModelsUiState> = combine(
        local, library.models, library.activePath, status.state
    ) { l, models, active, engineState ->
        l.copy(models = models, activePath = active, engine = engineState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), local.value)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { library.refresh() }
    }

    fun activate(model: LocalModel) {
        if (model.path == uiState.value.activePath && uiState.value.engine is EngineRuntimeState.Ready) return
        library.setActive(model)
        viewModelScope.launch {
            engine.loadModel(model.file.name).onFailure { e ->
                local.update { it.copy(message = e.message ?: "Couldn't load ${model.displayName}") }
            }
        }
    }

    fun import(uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            local.update { it.copy(importProgress = 0f) }
            try {
                val model = library.import(uri) { p -> local.update { s -> s.copy(importProgress = p) } }
                local.update { it.copy(message = "Imported ${model.displayName}") }
                activate(model)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.copy(message = "Import failed: ${e.message}") }
            } finally {
                local.update { it.copy(importProgress = null) }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun requestDelete(model: LocalModel) = local.update { it.copy(pendingDelete = model) }

    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val model = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            if (model.path == uiState.value.activePath) engine.unloadModel()
            library.delete(model)
            local.update { it.copy(message = "Deleted ${model.displayName}") }
        }
    }

    fun clearMessage() = local.update { it.copy(message = null) }

    /** Shows the model's SHA-256 so it can be compared with the hash on Hugging Face. */
    fun verify(model: LocalModel) {
        if (local.value.hashing != null) return
        viewModelScope.launch {
            local.update { it.copy(hashing = model to 0f) }
            try {
                val hex = library.sha256(model) { p -> local.update { s -> s.copy(hashing = model to p) } }
                local.update { it.copy(fingerprint = model to hex) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.copy(message = "Couldn't read ${model.displayName}: ${e.message}") }
            } finally {
                local.update { it.copy(hashing = null) }
            }
        }
    }

    fun dismissFingerprint() = local.update { it.copy(fingerprint = null) }
}
