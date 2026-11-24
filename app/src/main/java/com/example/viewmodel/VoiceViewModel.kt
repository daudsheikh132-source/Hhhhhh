package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AiAssistantService
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "VoiceVM"

data class VoiceTurn(
    val userText: String,
    val aiText: String
)

class VoiceViewModel(
    private val aiService: AiAssistantService
) : ViewModel() {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _turns = MutableStateFlow<List<VoiceTurn>>(emptyList())
    val turns: StateFlow<List<VoiceTurn>> = _turns.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (!listening) _rmsLevel.value = 0f
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun updateRms(rmsDb: Float) {
        val normalized = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        _rmsLevel.value = normalized
    }

    fun updatePartialTranscript(text: String) {
        _partialTranscript.value = text
    }

    fun setError(message: String?) {
        _errorMessage.value = message
    }

    fun clearHistory() {
        _turns.value = emptyList()
        _partialTranscript.value = ""
        _errorMessage.value = null
    }

    fun processSpokenQuery(
        spokenText: String,
        personality: AiPersonality,
        language: AppLanguage,
        memoryContextPrompt: String,
        onReplyReady: (String) -> Unit
    ) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return

        _partialTranscript.value = trimmed
        _errorMessage.value = null
        _isThinking.value = true

        val historyPairs = _turns.value.flatMap { listOf("user" to it.userText, "model" to it.aiText) }

        viewModelScope.launch {
            try {
                val reply = aiService.generateVoiceReply(
                    history = historyPairs,
                    spokenText = trimmed,
                    personality = personality,
                    language = language,
                    memoryContextPrompt = memoryContextPrompt
                )
                _turns.value = _turns.value + VoiceTurn(trimmed, reply)
                _partialTranscript.value = ""
                onReplyReady(reply)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Voice AI reply timed out", e)
                _errorMessage.value = "Voice response timed out. Please try speaking again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice AI reply failed", e)
                _errorMessage.value = e.localizedMessage ?: "Voice AI request failed."
            } finally {
                _isThinking.value = false
            }
        }
    }

    companion object {
        fun provideFactory(aiService: AiAssistantService): ViewModelProvider.Factory = viewModelFactory {
            initializer { VoiceViewModel(aiService) }
        }
    }
}
