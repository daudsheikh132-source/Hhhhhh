package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AiAssistantService
import com.example.data.ImageAspectOption
import com.example.model.ImageGenUiState
import com.example.util.ProfileAvatarStore
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ImageGenVM"

class ImageGenViewModel(
    private val aiService: AiAssistantService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    private val _avatarPreviewState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val avatarPreviewState: StateFlow<ImageGenUiState> = _avatarPreviewState.asStateFlow()

    private val _selectedStyle = MutableStateFlow("Cinematic Digital Art")
    val selectedStyle: StateFlow<String> = _selectedStyle.asStateFlow()

    private val _selectedAspect = MutableStateFlow(ImageAspectOption.SQUARE)
    val selectedAspect: StateFlow<ImageAspectOption> = _selectedAspect.asStateFlow()

    fun setStyle(style: String) {
        _selectedStyle.value = style
    }

    fun setAspect(aspect: ImageAspectOption) {
        _selectedAspect.value = aspect
    }

    fun generateImage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiService.generateImage(
                    prompt = trimmed,
                    style = _selectedStyle.value,
                    aspectOption = _selectedAspect.value
                )
                _uiState.value = ImageGenUiState.Success(bitmap, trimmed)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = ImageGenUiState.Error("Image generation timed out after 60 seconds. Please try again.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _uiState.value = ImageGenUiState.Error(
                    "Image generation quota or Blaze billing limit reached. Please check your Firebase Blaze plan or try later."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed. Ensure Firebase AI Logic & Blaze billing are enabled for Nano Banana 2.1."
                )
            }
        }
    }

    fun generateAvatar(description: String, style: String) {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) return

        val avatarPrompt = ProfileAvatarStore.buildAvatarPrompt(trimmed, style)
        viewModelScope.launch {
            _avatarPreviewState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiService.generateImage(
                    prompt = avatarPrompt,
                    style = style,
                    aspectOption = ImageAspectOption.SQUARE
                )
                _avatarPreviewState.value = ImageGenUiState.Success(bitmap, trimmed)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Avatar generation timed out", e)
                _avatarPreviewState.value = ImageGenUiState.Error("Avatar generation timed out after 60 seconds. Please try again.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Avatar generation quota exceeded", e)
                _avatarPreviewState.value = ImageGenUiState.Error(
                    "Image generation quota or Blaze billing limit reached. Please check your Firebase Blaze plan or try later."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Avatar generation failed", e)
                _avatarPreviewState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Avatar generation failed. Ensure Firebase AI Logic & Blaze billing are enabled."
                )
            }
        }
    }

    fun resetAvatarPreview() {
        _avatarPreviewState.value = ImageGenUiState.Idle
    }

    companion object {
        fun provideFactory(aiService: AiAssistantService): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImageGenViewModel(aiService) }
        }
    }
}
