package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

private const val TAG = "AiAssistantService"

enum class ImageAspectOption(val label: String, val ratio: AspectRatio) {
    SQUARE("1:1", AspectRatio.SQUARE_1x1),
    LANDSCAPE("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT("9:16", AspectRatio.PORTRAIT_9x16)
}

class AiAssistantService {

    private val ai by lazy { Firebase.ai }

    private fun buildSystemInstruction(
        personality: AiPersonality,
        language: AppLanguage,
        memoryContextPrompt: String,
        isVoiceMode: Boolean = false
    ): Content {
        val personalityDirective = when (personality) {
            AiPersonality.FRIENDLY ->
                "Tone: Friendly AI. Be warm, natural, supportive, and conversational."
            AiPersonality.FUNNY ->
                "Tone: Funny AI. Include light, tasteful humor, clever wit, and upbeat energy while still answering accurately."
            AiPersonality.TEACHER ->
                "Tone: Teacher AI. Explain concepts step-by-step clearly, use helpful examples, assist with coding, math, science, and verify understanding."
            AiPersonality.CREATIVE ->
                "Tone: Creative AI. Be imaginative, expressive, and inspiring—great at stories, poetry, brainstorming, and original ideas."
            AiPersonality.SUPPORT ->
                "Tone: Support AI. Listen patiently, respond with genuine empathy, help the user organize thoughts, and suggest gentle positive activities without ever judging."
        }

        val languageDirective = if (language == AppLanguage.BENGALI) {
            "Language: Prefer responding in natural, clear Bengali (বাংলা) unless the user writes in English or asks for another language/translation."
        } else {
            "Language: Respond in clear English by default, or naturally in Bengali (বাংলা) if the user writes in Bengali or asks for Bengali."
        }

        val voiceDirective = if (isVoiceMode) {
            "Voice Mode: Keep responses concise, natural to hear aloud, and avoid heavy markdown tables or long URLs."
        } else {
            "Chat Mode: Provide well-structured, readable answers."
        }

        val memoryBlock = if (memoryContextPrompt.isNotBlank()) {
            "\n\nContext-Aware Memory (use naturally when helpful, without forcing it):\n$memoryContextPrompt"
        } else {
            ""
        }

        val fullPrompt = """
            You are SoulMate AI ("Your Smart AI Companion", created by Daud).
            Core Safety & Honesty Rules (ALWAYS enforced regardless of personality):
            1. Always be transparent that you are an AI assistant, never claim to be a human, a replacement for family/friends, or a licensed therapist/doctor.
            2. If a user expresses severe emotional distress, self-harm, or crisis, respond with care and responsibly encourage contacting a trusted person, family member, or local emergency/professional support helpline.
            3. Never fabricate facts about uploaded images; describe only what is visible, read visible text accurately, and explain diagrams/screenshots clearly.
            $personalityDirective
            $languageDirective
            $voiceDirective$memoryBlock
        """.trimIndent()

        return content { text(fullPrompt) }
    }

    private fun createTextModel(
        personality: AiPersonality,
        language: AppLanguage,
        memoryContextPrompt: String,
        isVoiceMode: Boolean = false
    ): GenerativeModel {
        return ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = buildSystemInstruction(
                personality = personality,
                language = language,
                memoryContextPrompt = memoryContextPrompt,
                isVoiceMode = isVoiceMode
            )
        )
    }

    fun streamChatReply(
        history: List<ChatMessage>,
        userPrompt: String,
        attachedImages: List<Bitmap>,
        personality: AiPersonality,
        language: AppLanguage,
        memoryContextPrompt: String
    ): Flow<String> {
        val model = createTextModel(
            personality = personality,
            language = language,
            memoryContextPrompt = memoryContextPrompt,
            isVoiceMode = false
        )

        val recentHistory = history.takeLast(16).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                val note = if (msg.hasImage) "[User attached ${msg.imageCount} image(s)]\n" else ""
                text(note + msg.content)
            }
        }

        val currentTurn = content(role = "user") {
            attachedImages.forEach { bitmap ->
                image(bitmap)
            }
            val effectivePrompt = userPrompt.trim().ifEmpty {
                if (language == AppLanguage.BENGALI) {
                    "অনুগ্রহ করে এই ছবিটি বিশ্লেষণ করুন এবং বিস্তারিত বলুন।"
                } else {
                    "Please analyze the attached image(s) and explain what you see."
                }
            }
            text(effectivePrompt)
        }

        val contents = recentHistory + currentTurn
        return model.generateContentStream(contents)
            .map { chunk -> chunk.text.orEmpty() }
    }

    suspend fun generateVoiceReply(
        history: List<Pair<String, String>>,
        spokenText: String,
        personality: AiPersonality,
        language: AppLanguage,
        memoryContextPrompt: String
    ): String {
        val model = createTextModel(
            personality = personality,
            language = language,
            memoryContextPrompt = memoryContextPrompt,
            isVoiceMode = true
        )
        val contents = history.takeLast(8).map { (role, text) ->
            content(role = role) { text(text) }
        } + content(role = "user") { text(spokenText) }

        val response = withTimeout(45_000L) {
            model.generateContent(contents)
        }
        return response.text?.trim().takeIf { !it.isNullOrBlank() }
            ?: throw IllegalStateException("Model returned an empty voice response.")
    }

    suspend fun generateImage(
        prompt: String,
        style: String,
        aspectOption: ImageAspectOption
    ): Bitmap {
        val styledPrompt = if (style.isBlank() || style == "Natural") {
            prompt.trim()
        } else {
            "${prompt.trim()}, in $style style, high detail, clean composition"
        }

        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectOption.ratio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }

        val model = ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )

        val response = withTimeout(60_000L) {
            model.generateContent(styledPrompt)
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("No image was returned by the model.")
    }

    fun generateTitleFromPrompt(prompt: String): String {
        val cleaned = prompt.trim().replace("\n", " ")
        return if (cleaned.isBlank()) "Image Analysis" else cleaned.take(42)
    }

    companion object {
        fun logError(context: String, throwable: Throwable) {
            Log.e(TAG, context, throwable)
        }
    }
}
