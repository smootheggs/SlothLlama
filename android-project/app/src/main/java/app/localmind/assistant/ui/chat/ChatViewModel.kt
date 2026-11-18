package app.localmind.assistant.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.repository.DEFAULT_MODEL_ID
import app.localmind.assistant.domain.attachments.Attachments
import app.localmind.assistant.domain.attachments.DocumentText
import app.localmind.assistant.domain.attachments.LibraryPassage
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.domain.inference.EngineRuntimeState
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.domain.model.MessageStatus
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.data.persona.PersonaRepository
import app.localmind.assistant.data.persona.ChineseStyleStore
import app.localmind.assistant.data.voice.Endpointing
import app.localmind.assistant.data.voice.VoiceRecorder
import app.localmind.assistant.domain.persona.ChineseStyle
import app.localmind.assistant.data.voice.VoiceTranscriber
import app.localmind.assistant.domain.persona.BuiltInPersonas
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    personaRepository: PersonaRepository,
    private val transcriber: VoiceTranscriber,
    private val recorder: VoiceRecorder,
    chineseStyleStore: ChineseStyleStore,
    private val attachmentStore: AttachmentStore,
    private val library: LibraryStore
) : ViewModel() {

    /** Something in the library is switched on to be searched. */
    val hasLibrary: StateFlow<Boolean> = library.items
        .map { items -> items.orEmpty().any { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _libraryOn = MutableStateFlow(false)
    /** Each message is sent with the library passages that best match it. */
    val libraryOn: StateFlow<Boolean> = _libraryOn.asStateFlow()

    private val _lookingUp = MutableStateFlow(false)
    val lookingUp: StateFlow<Boolean> = _lookingUp.asStateFlow()

    init {
        viewModelScope.launch { runCatching { library.refresh() } }
    }

    fun toggleLibrary() {
        _libraryOn.update { !it }
        _errorMessage.value = if (_libraryOn.value) "Your library will be searched with each message." else null
    }

    /** The library passages for [question] (none if the switch is off or nothing matched). */
    private suspend fun lookUp(question: String): List<LibraryPassage> {
        if (!_libraryOn.value || question.isBlank() || !hasLibrary.value) return emptyList()
        _lookingUp.value = true
        return try {
            library.search(question).also {
                if (it.isEmpty()) _errorMessage.value = "Nothing in your library matched, so the question went on its own."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _errorMessage.value = "The library search failed, so the question went on its own."
            emptyList()
        } finally {
            _lookingUp.value = false
        }
    }

    private val _attachments = MutableStateFlow(PendingAttachments())
    /** The photo / document waiting to go with the next message. */
    val attachments: StateFlow<PendingAttachments> = _attachments.asStateFlow()

    fun attachPhoto(uri: Uri) = attach("photo") {
        val name = attachmentStore.savePhoto(uri)
        _attachments.update { it.copy(photo = name, busy = false) }
        if (uiState.value.canSeePhotos == false) {
            _errorMessage.value = "This model can't see photos until you add its vision file: Models → its card → Photos → Add."
        }
    }

    fun attachDocument(uri: Uri) = attach("document") {
        val doc = attachmentStore.readDocument(uri)
        _attachments.update { it.copy(document = doc, busy = false) }
        if (doc.truncated) {
            _errorMessage.value = "${doc.name} is long, so only the first part fits in the model's memory."
        }
    }

    private fun attach(what: String, block: suspend () -> Unit) {
        if (_attachments.value.busy) return
        viewModelScope.launch {
            _attachments.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _errorMessage.value = e.message?.takeIf { it.isNotBlank() } ?: "Couldn't add that $what."
            } finally {
                _attachments.update { it.copy(busy = false) }
            }
        }
    }

    fun removePhoto() = _attachments.update { it.copy(photo = null) }

    fun removeDocument() = _attachments.update { it.copy(document = null) }

    /** A stored photo, for thumbnails. */
    fun photoFile(name: String): java.io.File? = attachmentStore.photoFile(name)

    private val _searchQuery = MutableStateFlow("")
    /** Drawer search text. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    /** Chats matching the search (title or any message), or null when not searching. */
    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<Set<String>?> = _searchQuery
        .debounce(200)
        .mapLatest { q -> if (q.isBlank()) null else repository.searchConversationIds(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Characters and romanization chosen in Settings → Chinese (for the start page's date). */
    val chineseStyle: StateFlow<ChineseStyle> = chineseStyleStore.style

    private val _voiceState = MutableStateFlow(VoiceInputState.IDLE)
    /** Voice input: idle, listening to the microphone, or turning speech into text. */
    val voiceState: StateFlow<VoiceInputState> = _voiceState.asStateFlow()
    /** Microphone loudness 0..1 while listening. */
    val voiceLevel: StateFlow<Float> = recorder.level
    private var voiceJob: Job? = null

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
        // Each persona may run on its own model: load it when the persona changes (not mid-reply;
        // sending a message loads the right model anyway).
        viewModelScope.launch {
            _selectedPersonaId.collect { personaId ->
                if (!_isGenerating.value) repository.warmUpForPersona(personaId)
            }
        }
    }

    /** The conversation on screen: the one picked, else the most recent. */
    private val effectiveConversationId: Flow<String?> = combine(
        _activeConversationId,
        repository.observeConversations()
    ) { activeId, conversations ->
        when (activeId) {
            DRAFT_ID -> null                       // a new, not-yet-sent chat
            null -> conversations.firstOrNull()?.id
            else -> activeId
        }
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

    /**
     * Opens a blank chat. Nothing is written to the database until the first message is sent,
     * so switching personas or tapping "New chat" never leaves empty conversations behind.
     */
    fun createNewConversation(personaId: String = _selectedPersonaId.value) {
        _selectedPersonaId.value = personaId
        _activeConversationId.value = DRAFT_ID
        _isDrawerOpen.value = false
        _inputText.value = ""
        _editingMessage.value = null
    }

    /**
     * Text sent from another app with "Ask SlothLlama": a new chat with the current persona, with
     * the question waiting in the message box so it can be edited (or the persona changed) first.
     */
    fun askAbout(text: String) {
        if (_isGenerating.value) return
        createNewConversation()
        val chinese = text.any { it.code in 0x3400..0x9FFF }
        _inputText.value = if (chinese) "Explain this word by word, with meanings:\n\n$text" else "Explain this:\n\n$text"
    }

    /** Switching tutor starts a fresh chat, so lessons in different languages never mix. */
    fun startConversationWith(personaId: String) {
        if (_isGenerating.value) return
        _selectedPersonaId.value = personaId
        createNewConversation(personaId = personaId)
    }

    /** Sends what's in the box, with any attached photo or document. */
    fun sendMessage() {
        if (_isGenerating.value || _lookingUp.value) return
        val pending = _attachments.value
        if (pending.busy) return
        val typed = _inputText.value.trim()
        if (typed.isEmpty() && pending.isEmpty) return
        val text = typed.ifEmpty {
            if (pending.photo != null) DEFAULT_PHOTO_PROMPT else DEFAULT_DOCUMENT_PROMPT
        }
        _attachments.value = PendingAttachments()
        if (!_libraryOn.value || typed.isEmpty()) {
            send(Attachments.compose(text, pending.photo, pending.document), fromInputBox = true)
            return
        }
        // The box empties straight away; the question goes once the library has been searched.
        _inputText.value = ""
        viewModelScope.launch {
            val passages = lookUp(typed)
            send(Attachments.compose(text, pending.photo, pending.document, passages), fromInputBox = false)
        }
    }

    /** "Continue" after a reply stopped at the length limit. Leaves the input box alone. */
    fun continueReply() = send("Continue.", fromInputBox = false)

    /** A quick action ("Quiz me", "Correct me" …): sent as-is, leaving any draft in the box. */
    fun sendQuickAction(prompt: String) = send(prompt, fromInputBox = false)

    private fun send(rawText: String, fromInputBox: Boolean) {
        val text = rawText.trim()
        if (text.isEmpty() || _isGenerating.value) return
        viewModelScope.launch { sendAndAwait(text, fromInputBox) }
    }

    /** Sends [text] and returns the finished reply (null if nothing came back). */
    private suspend fun sendAndAwait(text: String, fromInputBox: Boolean): ChatMessage? {
        if (_isGenerating.value) return null
        var reply: ChatMessage? = null
        coroutineScope {
            var convId = when (val current = _activeConversationId.value) {
                DRAFT_ID -> null
                null -> uiState.value.activeConversationId
                else -> current
            }
            if (convId == null) {
                val newConv = repository.createConversation("New Conversation", _selectedPersonaId.value, DEFAULT_MODEL_ID)
                convId = newConv.id
                _activeConversationId.value = convId
            }

            if (fromInputBox) _inputText.value = ""
            _isGenerating.value = true

            val job = launch {
                try {
                    repository.sendMessage(convId, text).collect { message ->
                        _streamingMessageId.value = message.id
                        reply = message
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Failed to generate response"
                } finally {
                    _isGenerating.value = false
                    _streamingMessageId.value = null
                }
            }
            activeGenerationJob = job
            job.join()
        }
        // The stream ends with the saved reply; read it back in case the last emission was partial.
        val id = reply?.id ?: return null
        return uiState.value.messages.firstOrNull { it.id == id }?.takeIf { it.status != MessageStatus.STREAMING } ?: reply
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
        // The text goes in the box; a photo or document stays attached (and can be removed).
        val parsed = Attachments.parse(message.content)
        _inputText.value = parsed.text
        _attachments.value = PendingAttachments(photo = parsed.photos.firstOrNull(), document = parsed.documents.firstOrNull())
    }

    fun cancelEditingMessage() {
        _editingMessage.value = null
        _inputText.value = ""
        _attachments.value = PendingAttachments()
    }

    fun submitEditedMessage() {
        val message = _editingMessage.value ?: return
        val pending = _attachments.value
        val typed = _inputText.value.trim()
        if ((typed.isEmpty() && pending.isEmpty) || _isGenerating.value || _lookingUp.value || pending.busy) return
        _attachments.value = PendingAttachments()

        val convId = _activeConversationId.value ?: message.conversationId

        viewModelScope.launch {
            _editingMessage.value = null
            _inputText.value = ""
            // An edited question is looked up again (the old passages may not fit it any more).
            val newText = Attachments.compose(
                typed.ifEmpty { if (pending.photo != null) DEFAULT_PHOTO_PROMPT else DEFAULT_DOCUMENT_PROMPT },
                pending.photo, pending.document, lookUp(typed)
            )
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

    /**
     * Starts listening (the caller has the microphone permission). The transcript is added to the
     * input box so it can be checked or fixed before sending.
     */
    fun startVoiceInput() {
        if (_voiceState.value != VoiceInputState.IDLE) return
        val language = speechLanguageCode()
        if (!transcriber.isReadyFor(language)) {
            _errorMessage.value = "Voice input needs ${transcriber.modelNeededFor(language)}: Models → Voice input → Import."
            return
        }
        voiceJob = viewModelScope.launch {
            try {
                _voiceState.value = VoiceInputState.LISTENING
                val samples = recorder.record()
                if (samples.size < VoiceTranscriber.SAMPLE_RATE / 3) {
                    _errorMessage.value = "That was too short to hear anything."
                    return@launch
                }
                _voiceState.value = VoiceInputState.TRANSCRIBING
                val text = transcriber.transcribe(samples, language)
                if (text.isBlank()) {
                    _errorMessage.value = "Didn't catch any speech. Try again a little closer to the mic."
                } else {
                    val current = _inputText.value.trimEnd()
                    _inputText.value = if (current.isEmpty()) text else "$current $text"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _errorMessage.value = "Voice input failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _voiceState.value = VoiceInputState.IDLE
            }
        }
    }

    private val _handsFree = MutableStateFlow(HandsFreeState.OFF)
    /** Hands-free conversation mode and what it's doing right now. */
    val handsFree: StateFlow<HandsFreeState> = _handsFree.asStateFlow()
    private var handsFreeJob: Job? = null
    /** Identifies the current hands-free session; cleared by [stopHandsFree]. */
    @Volatile private var handsFreeSession: Any? = null

    /**
     * Hands-free conversation: listen until the learner pauses, send what they said, read the
     * reply aloud with [speak], then listen again, until [stopHandsFree] or two silent turns in a
     * row. The caller has the microphone permission. [speak] returns once the reply has been read.
     */
    fun startHandsFree(speak: suspend (text: String) -> Boolean) {
        if (handsFreeJob?.isActive == true || _isGenerating.value) return
        if (!transcriber.isReadyFor(speechLanguageCode())) {
            _errorMessage.value = "Hands-free needs ${transcriber.modelNeededFor(speechLanguageCode())}: Models → Voice input → Import."
            return
        }
        cancelVoiceInput()
        val session = Any()
        handsFreeSession = session
        handsFreeJob = viewModelScope.launch {
            var silentTurns = 0
            try {
                while (isActive && handsFreeSession === session) {
                    _handsFree.value = HandsFreeState.LISTENING
                    val samples = recorder.record(maxSeconds = HANDS_FREE_MAX_SECONDS, endpointing = Endpointing())
                    if (!isActive || handsFreeSession !== session) break
                    val heard = if (samples.size < VoiceTranscriber.SAMPLE_RATE / 3) "" else {
                        _handsFree.value = HandsFreeState.TRANSCRIBING
                        transcriber.transcribe(samples, speechLanguageCode())
                    }
                    if (heard.isBlank()) {
                        if (++silentTurns >= 2) {
                            _errorMessage.value = "Hands-free paused: I didn't hear anything."
                            break
                        }
                        continue
                    }
                    silentTurns = 0
                    _handsFree.value = HandsFreeState.THINKING
                    val reply = sendAndAwait(heard, fromInputBox = false) ?: break
                    if (reply.role != MessageRole.ASSISTANT || reply.status != MessageStatus.COMPLETE || reply.content.isBlank()) break
                    if (handsFreeSession !== session) break
                    _handsFree.value = HandsFreeState.SPEAKING
                    if (!speak(reply.content)) {
                        _errorMessage.value = "Hands-free stopped: there's no voice to read the reply aloud."
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _errorMessage.value = "Hands-free stopped: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                recorder.stop()
                if (handsFreeSession === session) handsFreeSession = null
                if (handsFreeSession == null) _handsFree.value = HandsFreeState.OFF
            }
        }
    }

    fun stopHandsFree() {
        val job = handsFreeJob ?: return
        handsFreeJob = null
        handsFreeSession = null
        recorder.stop()
        if (job.isActive && _isGenerating.value) {
            // Mid-reply: stop it the normal way (the partial reply is kept), then let the loop end.
            stopGeneration()
            viewModelScope.launch { if (withTimeoutOrNull(STOP_GRACE_MS + 1_000) { job.join() } == null) job.cancel() }
        } else {
            job.cancel()
        }
        _handsFree.value = HandsFreeState.OFF
    }

    /** A language hint helps a learner's accent be heard as the language being practised. */
    private fun speechLanguageCode(): String = uiState.value.activePersona.speech?.speechCode ?: "auto"

    /** Stops listening and transcribes what was said. */
    fun finishVoiceInput() = recorder.stop()

    /** Stops listening and throws the recording away. */
    fun cancelVoiceInput() {
        recorder.stop()
        voiceJob?.cancel()
        voiceJob = null
    }

    override fun onCleared() {
        handsFreeJob?.cancel()
        recorder.stop()
        super.onCleared()
    }

    private fun ChatUiState.withEngineState(engine: EngineRuntimeState): ChatUiState = when (engine) {
        is EngineRuntimeState.Idle -> copy(
            modelStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
            activeModelName = "Starting…"
        )
        is EngineRuntimeState.Loading -> copy(
            modelStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
            activeModelName = engine.note?.let { "$it…" } ?: "Loading ${prettyModelName(engine.modelName)}…"
        )
        is EngineRuntimeState.Ready -> copy(
            modelStatus = if (isGenerating) ModelRuntimeStatus.GENERATING else ModelRuntimeStatus.READY,
            activeModelName = prettyModelName(engine.modelName),
            contextSize = engine.maxTokens,
            canSeePhotos = engine.canSee
        )
        is EngineRuntimeState.NoModelFound -> copy(
            modelStatus = ModelRuntimeStatus.NO_MODEL_LOADED,
            activeModelName = "No model found"
        )
        is EngineRuntimeState.Failed -> copy(
            modelStatus = ModelRuntimeStatus.ERROR,
            activeModelName = "Couldn't load ${prettyModelName(engine.modelName)}"
        )
    }

    private companion object {
        const val DEFAULT_PHOTO_PROMPT = "What's in this photo?"
        const val DEFAULT_DOCUMENT_PROMPT = "Summarise this document."
        const val STOP_GRACE_MS = 3_000L
        /** One hands-free turn can't run longer than this. */
        const val HANDS_FREE_MAX_SECONDS = 45
        const val DRAFT_ID = "__draft__"

        /** "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task" -> "Qwen2.5-1.5B-Instruct" */
        private val QUANT_SUFFIX = Regex("[-._](?i:i?q\\d\\w*|f16|bf16|f32)$")
        private val ORG_PREFIX = Regex("^([A-Za-z0-9]+)_(\\1.*)$")

        /** "Qwen_Qwen3.5-4B-Q4_K_M.gguf" → "Qwen3.5-4B": no extension, quantization tag or uploader prefix. */
        fun prettyModelName(fileName: String): String {
            var name = fileName.substringBeforeLast('.').substringBefore("_multi-prefill").substringBefore("_q8")
            name = name.replace(QUANT_SUFFIX, "")
            name = ORG_PREFIX.find(name)?.groupValues?.get(2) ?: name
            return name.ifBlank { fileName }
        }
    }
}
