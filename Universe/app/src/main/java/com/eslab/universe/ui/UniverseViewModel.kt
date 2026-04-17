package com.eslab.universe.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eslab.universe.data.ChatHistoryMessage
import com.eslab.universe.data.ChatRole
import com.eslab.universe.data.ConversationSettings
import com.eslab.universe.data.DownloadableModel
import com.eslab.universe.data.LiteRtChatRepository
import com.eslab.universe.data.ModelDownloadStatusType
import com.eslab.universe.data.ModelCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<UiChatMessage>,
)

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val elapsedMs: Long? = null,
)

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float? = null,
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val remainingMs: Long = -1L,
    val errorMessage: String? = null,
)

data class UniverseUiState(
    val models: List<DownloadableModel> = ModelCatalog.all,
    val downloadedModelIds: Set<String> = emptySet(),
    val selectedModel: DownloadableModel? = null,
    val isModelPickerVisible: Boolean = true,
    val isEngineLoading: Boolean = false,
    val activeBackend: String? = null,
    val sessions: List<ChatSession> = listOf(
        ChatSession(
            id = "session-1",
            title = "New chat",
            messages = emptyList(),
        ),
    ),
    val activeSessionId: String = "session-1",
    val draftMessage: String = "",
    val isGenerating: Boolean = false,
    val statusMessage: String = "Select a model to start chatting.",
    val downloadState: DownloadState = DownloadState(),
    val conversationSettings: ConversationSettings = ConversationSettings(),
    val isGenerationSettingsVisible: Boolean = false,
)

class UniverseViewModel(
    application: Application,
) : ViewModel() {
    private val repository = LiteRtChatRepository(application)
    private val _uiState = MutableStateFlow(
        UniverseUiState(
            downloadedModelIds = repository.downloadedModelIds(ModelCatalog.all),
        ),
    )
    val uiState: StateFlow<UniverseUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var conversationCleanupJob: Job? = null

    fun updateDraftMessage(text: String) {
        _uiState.update { it.copy(draftMessage = text) }
    }

    fun appendDraftTranscript(text: String) {
        val transcript = text.trim()
        if (transcript.isBlank()) return

        _uiState.update { state ->
            val separator = if (state.draftMessage.isBlank()) "" else " "
            state.copy(draftMessage = state.draftMessage + separator + transcript)
        }
    }

    fun createNewChat() {
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSession(
            id = sessionId,
            title = "New chat",
            messages = emptyList(),
        )
        _uiState.update {
            it.copy(
                sessions = listOf(session) + it.sessions,
                activeSessionId = sessionId,
                statusMessage = "Started a new chat.",
            )
        }
    }

    fun selectChat(sessionId: String) {
        _uiState.update { it.copy(activeSessionId = sessionId) }
    }

    fun deleteChat(sessionId: String) {
        val currentState = _uiState.value
        if (currentState.sessions.size <= 1) {
            _uiState.update { it.copy(statusMessage = "At least one chat must remain.") }
            return
        }

        val updatedSessions = currentState.sessions.filterNot { it.id == sessionId }
        val nextActiveSessionId =
            if (currentState.activeSessionId == sessionId) {
                updatedSessions.first().id
            } else {
                currentState.activeSessionId
            }

        _uiState.update {
            it.copy(
                sessions = updatedSessions,
                activeSessionId = nextActiveSessionId,
                statusMessage = "Chat deleted.",
            )
        }
    }

    fun selectModel(model: DownloadableModel) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    isEngineLoading = true,
                    statusMessage = "Initializing the model engine.",
                )
            }

            repository.initializeModel(model)
                .onSuccess { backend ->
                    repository.selectConversation(
                        currentActiveSession().toHistory(),
                        _uiState.value.conversationSettings,
                    )
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    isModelPickerVisible = false,
                                    isEngineLoading = false,
                                    activeBackend = backend,
                                    statusMessage = "${model.displayName} ($backend) is ready.",
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isEngineLoading = false,
                                    statusMessage = error.message ?: "Failed to prepare the chat.",
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isEngineLoading = false,
                            statusMessage = error.message ?: "Failed to initialize the model.",
                        )
                    }
                }
        }
    }

    fun showModelPicker() {
        _uiState.update { it.copy(isModelPickerVisible = true) }
    }

    fun showGenerationSettings() {
        _uiState.update { it.copy(isGenerationSettingsVisible = true) }
    }

    fun hideGenerationSettings() {
        _uiState.update { it.copy(isGenerationSettingsVisible = false) }
    }

    fun updateConversationSettings(
        topK: Int,
        topP: Double,
        temperature: Double,
        systemInstruction: String,
    ) {
        _uiState.update {
            it.copy(
                conversationSettings = ConversationSettings(
                    topK = topK,
                    topP = topP,
                    temperature = temperature,
                    systemInstruction = systemInstruction,
                ),
                isGenerationSettingsVisible = false,
                statusMessage = "Generation settings updated.",
            )
        }
    }

    fun downloadModel(model: DownloadableModel) {
        _uiState.update {
            it.copy(
                downloadState = DownloadState(isDownloading = true),
                statusMessage = "Started downloading the model.",
            )
        }

        repository.downloadModel(model) { update ->
            when (update.status) {
                ModelDownloadStatusType.IN_PROGRESS -> {
                    val progress =
                        if (update.totalBytes > 0L) {
                            update.receivedBytes.toFloat() / update.totalBytes.toFloat()
                        } else {
                            null
                        }
                    _uiState.update {
                        it.copy(
                            downloadState = DownloadState(
                                isDownloading = true,
                                progress = progress,
                                receivedBytes = update.receivedBytes,
                                totalBytes = update.totalBytes,
                                bytesPerSecond = update.bytesPerSecond,
                                remainingMs = update.remainingMs,
                            ),
                            statusMessage = "Downloading ${model.displayName}.",
                        )
                    }
                }

                ModelDownloadStatusType.SUCCEEDED -> {
                    _uiState.update {
                        it.copy(
                            downloadedModelIds = it.downloadedModelIds + model.id,
                            downloadState = DownloadState(),
                            statusMessage = "Model download completed.",
                        )
                    }
                }

                ModelDownloadStatusType.FAILED,
                ModelDownloadStatusType.NOT_DOWNLOADED -> {
                    _uiState.update {
                        it.copy(
                            downloadState = DownloadState(
                                errorMessage = update.errorMessage ?: "Failed to download the model.",
                            ),
                            statusMessage = update.errorMessage ?: "Failed to download the model.",
                        )
                    }
                }
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val session = currentActiveSession()
        val text = state.draftMessage.trim()
        if (text.isBlank() || state.selectedModel == null || state.isGenerating) {
            return
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            var generationFailed = false
            var generationStopped = false
            val generationStartedAtMs = System.currentTimeMillis()
            conversationCleanupJob?.join()
            conversationCleanupJob = null
            prepareConversation(
                model = state.selectedModel,
                history = session.toHistory(),
                settings = state.conversationSettings,
            )
                .onFailure { error ->
                    _uiState.update {
                        it.copy(statusMessage = error.message ?: "Failed to prepare the chat.")
                    }
                    return@launch
                }

            val userMessage = UiChatMessage(role = ChatRole.USER, text = text)
            val placeholder = UiChatMessage(role = ChatRole.ASSISTANT, text = "")

            _uiState.update {
                it.copy(
                    draftMessage = "",
                    isGenerating = true,
                    statusMessage = "Generating response.",
                    sessions = it.sessions.updateSession(session.id) { current ->
                        val updatedMessages = current.messages + userMessage + placeholder
                        current.copy(
                            title = buildSessionTitle(updatedMessages),
                            messages = updatedMessages,
                        )
                    },
                )
            }

            try {
                repository.streamReply(text).collect { message ->
                    val streamedText = message.toString()
                    _uiState.update {
                        it.copy(
                            sessions = it.sessions.updateSession(session.id) { current ->
                                val updatedMessages = current.messages.map { chatMessage ->
                                    if (chatMessage.id == placeholder.id) {
                                        chatMessage.copy(
                                            text = mergeStreamChunk(chatMessage.text, streamedText),
                                        )
                                    } else {
                                        chatMessage
                                    }
                                }
                                current.copy(messages = updatedMessages)
                            },
                        )
                    }
                }
            } catch (_: CancellationException) {
                generationStopped = true
                repository.closeConversation()
            } catch (error: Throwable) {
                generationFailed = true
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        statusMessage = error.message ?: "Failed to generate a response.",
                        sessions = it.sessions.updateSession(session.id) { current ->
                            val updatedMessages = current.messages.map { chatMessage ->
                                if (chatMessage.id == placeholder.id) {
                                    chatMessage.copy(
                                        text = error.message ?: "Failed to generate a response.",
                                    )
                                } else {
                                    chatMessage
                                }
                            }
                            current.copy(messages = updatedMessages)
                        },
                    )
                }
            }

            if (!generationFailed) {
                val elapsedMs = (System.currentTimeMillis() - generationStartedAtMs).coerceAtLeast(1L)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        statusMessage = if (generationStopped) "Response stopped." else "Response completed.",
                        sessions = it.sessions.updateSession(session.id) { current ->
                            val updatedMessages = current.messages.map { chatMessage ->
                                if (chatMessage.id == placeholder.id) {
                                    chatMessage.copy(
                                        elapsedMs = elapsedMs,
                                    )
                                } else {
                                    chatMessage
                                }
                            }
                            current.copy(messages = updatedMessages)
                        },
                    )
                }
            }
            generationJob = null
        }
    }

    fun stopGeneration() {
        _uiState.update {
            it.copy(
                isGenerating = false,
                statusMessage = "Response stopped.",
            )
        }
        val activeGenerationJob = generationJob
        activeGenerationJob?.cancel()
        generationJob = null
        conversationCleanupJob?.cancel()
        conversationCleanupJob = viewModelScope.launch {
            activeGenerationJob?.cancelAndJoin()
            repository.closeConversation()
        }
    }

    private fun currentActiveSession(): ChatSession {
        val state = _uiState.value
        return state.sessions.first { it.id == state.activeSessionId }
    }

    private suspend fun prepareConversation(
        model: DownloadableModel,
        history: List<ChatHistoryMessage>,
        settings: ConversationSettings,
    ): Result<Unit> {
        val initialAttempt = repository.selectConversation(history, settings)
        val message = initialAttempt.exceptionOrNull()?.message.orEmpty()
        if (!message.contains("A session already exists", ignoreCase = true)) {
            return initialAttempt
        }

        repository.closeEngine()
        return repository.initializeModel(model).fold(
            onSuccess = { backend ->
                _uiState.update { it.copy(activeBackend = backend) }
                repository.selectConversation(history, settings)
            },
            onFailure = { Result.failure(it) },
        )
    }

    override fun onCleared() {
        generationJob?.cancel()
        conversationCleanupJob?.cancel()
        viewModelScope.launch {
            repository.shutdown()
        }
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UniverseViewModel(application) as T
                }
            }

        private fun buildSessionTitle(messages: List<UiChatMessage>): String {
            val firstUserText = messages.firstOrNull { it.role == ChatRole.USER }?.text.orEmpty()
            return firstUserText.take(18).ifBlank { "New chat" }
        }

        private fun mergeStreamChunk(current: String, incoming: String): String {
            if (incoming.isBlank()) return current
            return if (incoming.startsWith(current)) incoming else current + incoming
        }

        private fun List<ChatSession>.updateSession(
            id: String,
            transform: (ChatSession) -> ChatSession,
        ): List<ChatSession> = map { session ->
            if (session.id == id) transform(session) else session
        }

        private fun ChatSession.toHistory(): List<ChatHistoryMessage> = messages
            .filter { it.text.isNotBlank() }
            .map { ChatHistoryMessage(role = it.role, text = it.text) }
    }
}
