package app.localmind.assistant.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.models.LocalModel
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.data.persona.PersonaModelStore
import app.localmind.assistant.data.persona.PersonaRepository
import app.localmind.assistant.data.persona.TutorLevelStore
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.domain.persona.TutorLevel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editable copy of a persona while the editor is open. */
data class PersonaDraft(
    val id: String = "",
    val name: String = "",
    val badge: String = "✦",
    val tagline: String = "",
    val systemInstruction: String = "",
    val temperature: Float = 0.6f,
    val maxOutputTokens: Int = 768,
    val speech: SpeechLanguage? = null,
    val starterPromptsText: String = "",
    /** True when editing a copy of a built-in persona. */
    val isCopy: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank() && systemInstruction.isNotBlank()

    fun toPersona() = Persona(
        id = id,
        name = name.trim(),
        badge = badge.trim().ifBlank { "✦" }.take(2),
        tagline = tagline.trim(),
        systemInstruction = systemInstruction.trim(),
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        speech = speech,
        starterPrompts = starterPromptsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6),
        isBuiltIn = false
    )

    companion object {
        fun from(persona: Persona, asCopy: Boolean) = PersonaDraft(
            id = if (asCopy) "" else persona.id,
            name = if (asCopy) "${persona.name} (my version)" else persona.name,
            badge = persona.badge,
            tagline = persona.tagline,
            systemInstruction = persona.systemInstruction,
            temperature = persona.temperature,
            maxOutputTokens = persona.maxOutputTokens,
            speech = persona.speech,
            starterPromptsText = persona.starterPrompts.joinToString("\n"),
            isCopy = asCopy
        )
    }
}

@HiltViewModel
class PersonasViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val library: ModelLibrary,
    private val personaModels: PersonaModelStore,
    tutorLevels: TutorLevelStore,
    dao: LocalMindDao
) : ViewModel() {

    /** Your level in each language, for the tutors' tiles. */
    val levels: StateFlow<Map<SpeechLanguage, TutorLevel>> = tutorLevels.levels

    /** When you last wrote to each persona. */
    val lastChat: StateFlow<Map<String, Long>> = dao.observeLastWrittenByPersona()
        .map { rows -> rows.associate { it.personaId to it.at } }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val personas: StateFlow<List<Persona>> =
        repository.personas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Models on the device, for the per-persona model picker. */
    val models: StateFlow<List<LocalModel>> = library.models

    /** persona id → model file name; missing = default model. */
    val modelAssignments: StateFlow<Map<String, String>> = personaModels.assignments

    init {
        viewModelScope.launch { library.refresh() }
    }

    /** [model] null = use the default model. */
    fun setModel(persona: Persona, model: LocalModel?) = personaModels.set(persona.id, model?.file?.name)

    private val _draft = MutableStateFlow<PersonaDraft?>(null)
    val draft: StateFlow<PersonaDraft?> = _draft.asStateFlow()

    private val _pendingDelete = MutableStateFlow<Persona?>(null)
    val pendingDelete: StateFlow<Persona?> = _pendingDelete.asStateFlow()

    fun createNew() {
        _draft.value = PersonaDraft()
    }

    fun edit(persona: Persona) {
        _draft.value = PersonaDraft.from(persona, asCopy = persona.isBuiltIn)
    }

    fun updateDraft(transform: (PersonaDraft) -> PersonaDraft) {
        _draft.update { it?.let(transform) }
    }

    fun closeEditor() {
        _draft.value = null
    }

    /** Saves and returns the saved persona's id through [onSaved]. */
    fun save(onSaved: (String) -> Unit = {}) {
        val current = _draft.value ?: return
        if (!current.canSave) return
        viewModelScope.launch {
            val saved = repository.save(current.toPersona())
            _draft.value = null
            onSaved(saved.id)
        }
    }

    fun requestDelete(persona: Persona) {
        if (!persona.isBuiltIn) _pendingDelete.value = persona
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { repository.delete(target.id) }
        personaModels.set(target.id, null)
    }
}
