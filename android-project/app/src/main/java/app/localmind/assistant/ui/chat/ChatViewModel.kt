package app.localmind.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
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

    private var activeGenerationJob: Job? = null

    val uiState: StateFlow<ChatUiState> = combine(
        repository.observeConversations(),
        repository.observeArchivedConversations(),
        _activeConversationId,
        _activeConversationId.flatMapLatest { id ->
            if (id != null) repository.observeMessages(id) else flowOf(emptyList())
        },
        _inputText,
        _isGenerating,
        _streamingMessageId
    ) { conversations, archived, activeId, messages, input, generating, streamingId ->
        // If no active conversation exists yet but conversations exist, select the most recent
        val effectiveActiveId = activeId ?: conversations.firstOrNull()?.id

        ChatUiState(
            conversations = conversations,
            archivedConversations = archived,
            activeConversationId = effectiveActiveId,
            messages = messages,
            inputText = input,
            isGenerating = generating,
            streamingMessageId = streamingId,
            modelStatus = if (generating) ModelRuntimeStatus.GENERATING else ModelRuntimeStatus.READY
        )
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
        _isDrawerOpen.value = false
        _editingMessage.value = null
    }

    fun createNewConversation(
        title: String = "New Conversation",
        personaId: String = "persona-general-assistant",
        modelId: String = "gemma-2-2b-it-litert-int4"
    ) {
        viewModelScope.launch {
            val newConv = repository.createConversation(title, personaId, modelId)
            _activeConversationId.value = newConv.id
            _isDrawerOpen.value = false
            _inputText.value = ""
            _editingMessage.value = null
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isGenerating.value) return

        viewModelScope.launch {
            var convId = _activeConversationId.value
            if (convId == null) {
                val newConv = repository.createConversation("New Conversation", "persona-general-assistant", "gemma-2-2b-it-litert-int4")
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
            activeGenerationJob?.cancel()
            _isGenerating.value = false
            _streamingMessageId.value = null
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
}
