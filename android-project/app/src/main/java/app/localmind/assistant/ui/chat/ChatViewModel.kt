package app.localmind.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.repository.DEFAULT_MODEL_ID
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.data.persona.PersonaRepository
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    personaRepository: PersonaRepository
) : ViewModel() {

    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _inputText = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _streamingMessageId = MutableStateFlow<String?>(null)
    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _renameTarget = MutableStateFlow<Conversation?>(null)
    private val _deleteTarget = MutableStateFlow<Conversation?>(null)
    private val _isClearAllConfirmOpen = MutableStateFlow(false)
    private val _isDrawerOpen = MutableStateFlow(false)
    /** Persona for new chats; follows the last tutor picked. */
    private val _selectedPersonaId = MutableStateFlow(BuiltInPersonas.default.id)

    private var activeGenerationJob: Job? = null

    init {
        // Load the model in the background so the header shows its real status
        // and the first message doesn't wait for a multi-second load.
        viewModelScope.launch { repository.warmUpModel() }
    }

    /** The conversation on screen: the one picked, else the most recent. */
    private val effectiveConversationId: Flow<String?> = combine(
        _activeConversationId,
        repository.observeConversations()
    ) { activeId, conversations ->
        activeId ?: conversations.firstOrNull()?.id
    }.distinctUntilChanged()

    // kotlinx.coroutines only has typed combine() overloads for up to 5 flows,
    // so the rest are chained on below.
    val uiState: StateFlow<ChatUiState> = combine(
        repository.observeConversations(),
        repository.observeArchivedConversations(),
        effectiveConversationId,
        effectiveConversationId.flatMapLatest { id ->
            if (id != null) repository.observeMessages(id) else flowOf(emptyList())
        },
        _inputText
    ) { conversations, archived, activeId, messages, input ->
        ChatUiState(
            conversations = conversations,
            archivedConversations = archived,
            activeConversationId = activeId,
            messages = messages,
            inputText = input
        )
    }.combine(combine(_selectedPersonaId, personaRepository.personas) { id, list -> id to list }) { state, (selectedId, personas) ->
        val wantedId = state.activeConversation?.personaId ?: selectedId
        state.copy(
            personas = personas,
            activePersona = personas.firstOrNull { it.id == wantedId } ?: BuiltInPersonas.GENERAL_ASSISTANT
        )
    }.combine(_isGenerating) { state, generating ->
        state.copy(isGenerating = generating)
    }.combine(_streamingMessageId) { state, streamingId ->
        state.copy(streamingMessageId = streamingId)
    }.combine(repository.engineState) { state, engine ->
        state.withEngineState(engine)
    }.combine(_editingMessage) { state, editing ->
        state.copy(editingMessage = editing)
    }.combine(_errorMessage) { state, err ->
        state.copy(errorMessage = err)
    }.combine(_renameTarget) { state, rename ->
        state.copy(renameTarget = rename)
    }.combine(_deleteTarget) { state, delete ->
        state.copy(deleteTarget = delete)
    }.combine(_isClearAllConfirmOpen) { state, clearAll ->
        state.copy(isClearAllConfirmOpen = clearAll)
    }.combine(_isDrawerOpen) { state, drawer ->
        state.copy(isDrawerOpen = drawer)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun setDrawerOpen(isOpen: Boolean) {
        _isDrawerOpen.value = isOpen
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        // "+" then continues with the same tutor as the chat you opened.
        (uiState.value.conversations + uiState.value.archivedConversations)
            .firstOrNull { it.id == conversationId }
            ?.let { _selectedPersonaId.value = it.personaId }
        _isDrawerOpen.value = false
        _editingMessage.value = null
    }

    fun createNewConversation(
        title: String = "New Conversation",
        personaId: String = _selectedPersonaId.value,
        modelId: String = DEFAULT_MODEL_ID
    ) {
        viewModelScope.launch {
            val newConv = repository.createConversation(title, personaId, modelId)
            _activeConversationId.value = newConv.id
            _isDrawerOpen.value = false
            _inputText.value = ""
            _editingMessage.value = null
        }
    }

    /** Switching tutor starts a fresh chat, so lessons in different languages never mix. */
    fun startConversationWith(personaId: String) {
        if (_isGenerating.value) return
        _selectedPersonaId.value = personaId
        createNewConversation(personaId = personaId)
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isGenerating.value) return

        viewModelScope.launch {
            var convId = _activeConversationId.value ?: uiState.value.activeConversationId
            if (convId == null) {
                val newConv = repository.createConversation("New Conversation", _selectedPersonaId.value, DEFAULT_MODEL_ID)
                convId = newConv.id
                _activeConversationId.value = convId
            }

            _inputText.value = ""
            _isGenerating.value = true

            activeGenerationJob = launch {
                try {
                    repository.sendMessage(convId, text).collect { message ->
                        _streamingMessageId.value = message.id
                    }
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Failed to generate response"
                } finally {
                    _isGenerating.value = false
                    _streamingMessageId.value = null
                }
            }
        }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            repository.stopGeneration()
            val job = activeGenerationJob ?: return@launch
            // Give the engine a moment to report the partial reply so it is saved as CANCELLED;
            // force-stop if it doesn't. isGenerating is reset in the job's finally block.
            if (withTimeoutOrNull(STOP_GRACE_MS) { job.join() } == null) job.cancel()
        }
    }

    fun regenerateLastResponse() {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            activeGenerationJob = launch {
                try {
                    repository.regenerateLastResponse(convId).collect { message ->
                        _streamingMessageId.value = message.id
                    }
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Failed to regenerate response"
                } finally {
                    _isGenerating.value = false
                    _streamingMessageId.value = null
                }
            }
        }
    }

    fun startEditingMessage(message: ChatMessage) {
        _editingMessage.value = message
        _inputText.value = message.content
    }

    fun cancelEditingMessage() {
        _editingMessage.value = null
        _inputText.value = ""
    }

    fun submitEditedMessage() {
        val message = _editingMessage.value ?: return
        val newText = _inputText.value.trim()
        if (newText.isEmpty() || _isGenerating.value) return

        val convId = _activeConversationId.value ?: message.conversationId

        viewModelScope.launch {
            _editingMessage.value = null
            _inputText.value = ""
            _isGenerating.value = true

            activeGenerationJob = launch {
                try {
                    repository.editAndResend(convId, message.id, newText).collect { msg ->
                        _streamingMessageId.value = msg.id
                    }
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Failed to resend edited message"
                } finally {
                    _isGenerating.value = false
                    _streamingMessageId.value = null
                }
            }
        }
    }

    fun requestRenameConversation(conversation: Conversation) {
        _renameTarget.value = conversation
    }

    fun confirmRenameConversation(newTitle: String) {
        val target = _renameTarget.value ?: return
        if (newTitle.isNotBlank()) {
            viewModelScope.launch {
                repository.renameConversation(target.id, newTitle)
                _renameTarget.value = null
            }
        } else {
            _renameTarget.value = null
        }
    }

    fun dismissRenameDialog() {
        _renameTarget.value = null
    }

    fun archiveConversation(conversation: Conversation) {
        viewModelScope.launch {
            val willBeArchived = !conversation.isArchived
            repository.archiveConversation(conversation.id, willBeArchived)
            // If the active conversation was archived, pick next active
            if (willBeArchived && _activeConversationId.value == conversation.id) {
                _activeConversationId.value = uiState.value.conversations
                    .firstOrNull { it.id != conversation.id }?.id
            }
        }
    }

    fun requestDeleteConversation(conversation: Conversation) {
        _deleteTarget.value = conversation
    }

    fun confirmDeleteConversation() {
        val target = _deleteTarget.value ?: return
        viewModelScope.launch {
            repository.deleteConversation(target.id)
            if (_activeConversationId.value == target.id) {
                _activeConversationId.value = uiState.value.conversations
                    .firstOrNull { it.id != target.id }?.id
            }
            _deleteTarget.value = null
        }
    }

    fun dismissDeleteDialog() {
        _deleteTarget.value = null
    }

    fun requestClearAll() {
        _isClearAllConfirmOpen.value = true
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            _activeConversationId.value = null
            _isClearAllConfirmOpen.value = false
        }
    }

    fun dismissClearAllDialog() {
        _isClearAllConfirmOpen.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun ChatUiState.withEngineState(engine: EngineRuntimeState): ChatUiState = when (engine) {
        is EngineRuntimeState.Idle -> copy(
            modelStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
            activeModelName = "Starting…"
        )
        is EngineRuntimeState.Loading -> copy(
            modelStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
            activeModelName = "Loading ${engine.modelName}…"
        )
        is EngineRuntimeState.Ready -> copy(
            modelStatus = if (isGenerating) ModelRuntimeStatus.GENERATING else ModelRuntimeStatus.READY,
            activeModelName = prettyModelName(engine.modelName)
        )
        is EngineRuntimeState.NoModelFound -> copy(
            modelStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
            activeModelName = "No model found"
        )
        is EngineRuntimeState.Failed -> copy(
            modelStatus = ModelRuntimeStatus.ERROR,
            activeModelName = "Couldn't load ${engine.modelName}"
        )
    }

    private companion object {
        const val STOP_GRACE_MS = 3_000L

        /** "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task" -> "Qwen2.5-1.5B-Instruct" */
        fun prettyModelName(fileName: String): String =
            fileName.substringBeforeLast('.').substringBefore("_multi-prefill").substringBefore("_q8").ifBlank { fileName }
    }
}
