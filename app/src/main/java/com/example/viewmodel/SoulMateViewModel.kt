package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AiAssistantService
import com.example.data.MemoryManager
import com.example.data.SoulMateRepository
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.PendingMemorySuggestion
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "SoulMateVM"
private const val STOP_TIMEOUT_MS = 5000L

@OptIn(ExperimentalCoroutinesApi::class)
class SoulMateViewModel(
    private val repository: SoulMateRepository,
    private val memoryManager: MemoryManager,
    private val aiService: AiAssistantService,
    private val currentUserId: String
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = memoryManager.observeUserPreferences(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load conversations"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val memoriesState: StateFlow<UiState<List<MemoryItem>>> =
        memoryManager.observeMemories(currentUserId)
            .map<List<MemoryItem>, UiState<List<MemoryItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing memories", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load memories"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(currentUserId, convId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing messages", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isAiResponding = MutableStateFlow(false)
    val isAiResponding: StateFlow<Boolean> = _isAiResponding.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _pendingMemorySuggestion = MutableStateFlow<PendingMemorySuggestion?>(null)
    val pendingMemorySuggestion: StateFlow<PendingMemorySuggestion?> = _pendingMemorySuggestion.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var activeStreamJob: Job? = null
    private var lastSendTimestampMs: Long = 0L

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            repository.ensureUserProfile(user?.displayName, user?.email)
        }
    }

    fun currentLanguage(): AppLanguage =
        AppLanguage.fromCode(userProfile.value?.preferredLanguage)

    fun currentPersonality(): AiPersonality =
        AiPersonality.fromKey(userProfile.value?.selectedPersonality)

    fun isMemoryEnabled(): Boolean =
        userProfile.value?.memoryEnabled ?: true

    fun currentMemories(): List<MemoryItem> =
        (memoriesState.value as? UiState.Success)?.data.orEmpty()

    fun currentConversations(): List<Conversation> =
        (conversationsState.value as? UiState.Success)?.data.orEmpty()

    fun buildActiveMemoryContext(): String =
        memoryManager.buildChatContextPrompt(
            userProfile = userProfile.value,
            memories = currentMemories(),
            pastConversations = currentConversations(),
            currentConversationId = _activeConversationId.value
        )

    fun startNewChat() {
        activeStreamJob?.cancel()
        _isAiResponding.value = false
        _streamingReply.value = null
        _chatError.value = null
        _activeConversationId.value = null
    }

    fun openConversation(conversationId: String) {
        activeStreamJob?.cancel()
        _isAiResponding.value = false
        _streamingReply.value = null
        _chatError.value = null
        _activeConversationId.value = conversationId
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun sendMessage(prompt: String, attachedImages: List<Bitmap>) {
        val now = System.currentTimeMillis()
        if (_isAiResponding.value || (now - lastSendTimestampMs) < 500L) return
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && attachedImages.isEmpty()) return

        lastSendTimestampMs = now
        _chatError.value = null
        _isAiResponding.value = true
        _streamingReply.value = ""

        val personality = currentPersonality()
        val language = currentLanguage()
        val memoryOn = isMemoryEnabled()
        val historySnapshot = activeMessages.value
        val currentConvId = _activeConversationId.value

        activeStreamJob = viewModelScope.launch {
            try {
                // Build rich MemoryManager context including user preferences, saved memories & past conversation snippets
                val recentPastSnippets = if (memoryOn) {
                    val cached = currentConversations()
                    if (cached.isNotEmpty()) cached
                    else memoryManager.fetchRecentConversationSnippets(excludeConversationId = currentConvId).getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val memoryContextPrompt = memoryManager.buildChatContextPrompt(
                    userProfile = userProfile.value,
                    memories = currentMemories(),
                    pastConversations = recentPastSnippets,
                    currentConversationId = currentConvId
                )

                val convId = currentConvId ?: run {
                    val title = aiService.generateTitleFromPrompt(trimmed)
                    repository.createConversation(
                        title = title,
                        personality = personality,
                        language = language,
                        initialPreview = trimmed.ifEmpty { "Image analysis" }
                    ).getOrThrow().also { newId ->
                        _activeConversationId.value = newId
                    }
                }

                val displayUserText = trimmed.ifEmpty {
                    if (language == AppLanguage.BENGALI) "ছবি বিশ্লেষণ করুন" else "Analyze attached image(s)"
                }

                repository.addMessage(
                    conversationId = convId,
                    role = "user",
                    content = displayUserText,
                    personality = personality,
                    hasImage = attachedImages.isNotEmpty(),
                    imageCount = attachedImages.size
                ).getOrThrow()

                if (memoryOn && trimmed.isNotBlank()) {
                    memoryManager.detectMemoryCandidate(trimmed)?.let { suggestion ->
                        _pendingMemorySuggestion.value = suggestion
                    }
                }

                val buffer = StringBuilder()
                withTimeout(60_000L) {
                    aiService.streamChatReply(
                        history = historySnapshot,
                        userPrompt = trimmed,
                        attachedImages = attachedImages,
                        personality = personality,
                        language = language,
                        memoryContextPrompt = memoryContextPrompt
                    ).collect { chunk ->
                        buffer.append(chunk)
                        _streamingReply.value = buffer.toString()
                    }
                }

                val finalReply = buffer.toString().trim()
                if (finalReply.isEmpty()) {
                    _chatError.value = "The AI model returned an empty response. Please try again."
                } else {
                    repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        content = finalReply,
                        personality = personality,
                        hasImage = false,
                        imageCount = 0
                    )
                    val newCount = (historySnapshot.size + 2).toLong()
                    repository.updateConversationMeta(
                        conversationId = convId,
                        personality = personality,
                        language = language,
                        lastMessagePreview = finalReply.take(280),
                        messageCount = newCount
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI response timed out", e)
                _chatError.value = "Response timed out after 60 seconds. Please check your connection and try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _chatError.value = e.localizedMessage ?: "Unable to reach SoulMate AI right now. Please try again."
            } finally {
                _isAiResponding.value = false
                _streamingReply.value = null
            }
        }
    }

    fun stopGenerating() {
        activeStreamJob?.cancel()
        _isAiResponding.value = false
        _streamingReply.value = null
    }

    fun approvePendingMemory(title: String, content: String, category: MemoryCategory, sensitive: Boolean) {
        _pendingMemorySuggestion.value = null
        viewModelScope.launch {
            memoryManager.saveMemory(title, content, category, sensitive)
                .onSuccess { _statusMessage.value = "Saved to Memory Vault" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to save memory" }
        }
    }

    fun dismissPendingMemory() {
        _pendingMemorySuggestion.value = null
    }

    fun requestSaveMessageToMemory(content: String) {
        val cleaned = content.trim().take(480)
        if (cleaned.isNotEmpty()) {
            _pendingMemorySuggestion.value = PendingMemorySuggestion(
                title = "Conversation Snippet",
                content = cleaned,
                category = MemoryCategory.CONTEXT,
                sensitive = false
            )
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(conversationId, newTitle)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Rename failed" }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            repository.deleteConversation(conversationId)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun addMemory(title: String, content: String, category: MemoryCategory, sensitive: Boolean) {
        viewModelScope.launch {
            memoryManager.saveMemory(title, content, category, sensitive)
                .onSuccess { _statusMessage.value = "Memory added" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to add memory" }
        }
    }

    fun updateMemory(memoryId: String, title: String, content: String, category: MemoryCategory, sensitive: Boolean) {
        viewModelScope.launch {
            memoryManager.updateMemory(memoryId, title, content, category, sensitive)
                .onSuccess { _statusMessage.value = "Memory updated" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update memory" }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            memoryManager.deleteMemory(memoryId)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete memory" }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            memoryManager.updatePreferences(preferredLanguage = language)
        }
    }

    fun setPersonality(personality: AiPersonality) {
        viewModelScope.launch {
            memoryManager.updatePreferences(selectedPersonality = personality)
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            memoryManager.updatePreferences(memoryEnabled = enabled)
        }
    }

    fun setVoiceSpeechRate(rate: Double) {
        viewModelScope.launch {
            memoryManager.updatePreferences(voiceSpeechRate = rate)
        }
    }

    fun setVoiceAutoSpeak(autoSpeak: Boolean) {
        viewModelScope.launch {
            memoryManager.updatePreferences(voiceAutoSpeak = autoSpeak)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            memoryManager.updatePreferences(themeMode = mode)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            _activeConversationId.value = null
            repository.clearAllConversations()
                .onSuccess { _statusMessage.value = "All chat history cleared" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to clear history" }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryManager.clearAllMemories()
                .onSuccess { _statusMessage.value = "All memories deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete memories" }
        }
    }

    fun deleteAllDataAndSignOut(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _activeConversationId.value = null
            repository.deleteAllUserData()
                .onSuccess { onDeleted() }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete user data" }
        }
    }

    companion object {
        fun provideFactory(
            repository: SoulMateRepository,
            memoryManager: MemoryManager,
            aiService: AiAssistantService,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SoulMateViewModel(repository, memoryManager, aiService, currentUserId)
            }
        }
    }
}
