package com.example.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage(val code: String, val label: String, val nativeLabel: String, val localeTag: String) {
    ENGLISH("en", "English", "English", "en-US"),
    BENGALI("bn", "Bengali", "বাংলা", "bn-BD");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class AiPersonality(
    val key: String,
    val titleEn: String,
    val titleBn: String,
    val subtitleEn: String,
    val subtitleBn: String
) {
    FRIENDLY(
        key = "FRIENDLY",
        titleEn = "Friendly AI",
        titleBn = "বন্ধুত্বপূর্ণ AI",
        subtitleEn = "Warm, natural, everyday companion",
        subtitleBn = "আন্তরিক ও স্বাভাবিক কথোপকথন"
    ),
    FUNNY(
        key = "FUNNY",
        titleEn = "Funny AI",
        titleBn = "মজার AI",
        subtitleEn = "Light jokes, wit & playful energy",
        subtitleBn = "হালকা রসিকতা ও মজার আলাপ"
    ),
    TEACHER(
        key = "TEACHER",
        titleEn = "Teacher AI",
        titleBn = "শিক্ষক AI",
        subtitleEn = "Clear lessons, coding & problem solving",
        subtitleBn = "সহজ ব্যাখ্যা, কোডিং ও শিক্ষণ সহায়তা"
    ),
    CREATIVE(
        key = "CREATIVE",
        titleEn = "Creative AI",
        titleBn = "সৃজনশীল AI",
        subtitleEn = "Stories, ideas & imaginative writing",
        subtitleBn = "গল্প, কবিতা ও নতুন আইডিয়া"
    ),
    SUPPORT(
        key = "SUPPORT",
        titleEn = "Support AI",
        titleBn = "সহানুভূতিশীল AI",
        subtitleEn = "Patient, empathetic & encouraging listener",
        subtitleBn = "ধৈর্যশীল ও সহানুভূতিশীল সহায়তা"
    );

    companion object {
        fun fromKey(key: String?): AiPersonality =
            entries.firstOrNull { it.key == key } ?: FRIENDLY
    }
}

enum class AppThemeMode(val key: String) {
    DARK_NAVY("DARK_NAVY"),
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: DARK_NAVY
    }
}

enum class MemoryCategory(val key: String) {
    PREFERENCE("PREFERENCE"),
    CONTEXT("CONTEXT"),
    PERSONAL("PERSONAL"),
    GOAL("GOAL");

    companion object {
        fun fromKey(key: String?): MemoryCategory =
            entries.firstOrNull { it.key == key } ?: PREFERENCE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Explorer",
    val email: String = "",
    val preferredLanguage: String = AppLanguage.ENGLISH.code,
    val selectedPersonality: String = AiPersonality.FRIENDLY.key,
    val memoryEnabled: Boolean = true,
    val voiceSpeechRate: Double = 1.0,
    val voiceAutoSpeak: Boolean = true,
    val themeMode: String = AppThemeMode.DARK_NAVY.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val personality: String = AiPersonality.FRIENDLY.key,
    val language: String = AppLanguage.ENGLISH.code,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val content: String = "",
    val personality: String = AiPersonality.FRIENDLY.key,
    val hasImage: Boolean = false,
    val imageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = MemoryCategory.PREFERENCE.key,
    val sensitive: Boolean = false,
    val approvedByUser: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

data class PendingMemorySuggestion(
    val title: String,
    val content: String,
    val category: MemoryCategory,
    val sensitive: Boolean
)
