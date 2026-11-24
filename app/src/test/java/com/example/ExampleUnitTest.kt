package com.example

import com.example.data.MemoryManager
import com.example.localization.StringsProvider
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.Conversation
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.UserProfile
import com.example.ui.components.ChatHistoryDefaults
import com.example.ui.screens.ChatInputUtils
import com.example.util.ProfileAvatarStore
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `bilingual strings provider supports English and Bengali`() {
        val en = StringsProvider.get(AppLanguage.ENGLISH)
        val bn = StringsProvider.get(AppLanguage.BENGALI)
        assertEquals("SoulMate AI", en.appName)
        assertEquals("Created by Daud", en.developerCredit)
        assertTrue(en.welcomeGreeting.contains("SoulMate AI"))
        assertTrue(bn.welcomeGreeting.contains("SoulMate AI"))
        assertTrue(en.aiIsTyping.contains("typing"))
        assertTrue(bn.aiIsTyping.contains("SoulMate AI"))
    }

    @Test
    fun `memory manager detects preferences and sensitive details`() {
        val pref = MemoryManager.detectMemoryCandidate("I prefer step-by-step Kotlin tutorials.")
        assertNotNull(pref)
        assertEquals(MemoryCategory.PREFERENCE, pref?.category)

        val sensitive = MemoryManager.detectMemoryCandidate("My birthday is January 12 and I live in Dhaka.")
        assertNotNull(sensitive)
        assertTrue(sensitive?.sensitive == true)
        assertEquals(MemoryCategory.PERSONAL, sensitive?.category)

        val normal = MemoryManager.detectMemoryCandidate("What is the weather?")
        assertNull(normal)
    }

    @Test
    fun `memory manager builds context prompt with preferences, memories, and past conversation snippets`() {
        val profile = UserProfile(
            userId = "u1",
            displayName = "Daud",
            preferredLanguage = "en",
            selectedPersonality = "TEACHER",
            memoryEnabled = true
        )
        val memories = listOf(
            MemoryItem(
                id = "m1",
                userId = "u1",
                title = "Language Style",
                content = "Likes concise explanations with code examples",
                category = "PREFERENCE",
                approvedByUser = true
            )
        )
        val pastConversations = listOf(
            Conversation(
                id = "c_old",
                userId = "u1",
                title = "Jetpack Compose Navigation",
                lastMessagePreview = "Discussed BackHandler and NavigationBar setup"
            ),
            Conversation(
                id = "c_current",
                userId = "u1",
                title = "Current Thread",
                lastMessagePreview = "Should be excluded from cross-session snippets"
            )
        )

        val prompt = MemoryManager.buildChatContextPrompt(
            userProfile = profile,
            memories = memories,
            pastConversations = pastConversations,
            currentConversationId = "c_current"
        )

        assertTrue(prompt.contains("Daud"))
        assertTrue(prompt.contains("Likes concise explanations with code examples"))
        assertTrue(prompt.contains("Jetpack Compose Navigation"))
        assertFalse(prompt.contains("Should be excluded from cross-session snippets"))

        // When memoryEnabled = false, context prompt must be empty
        val disabledPrompt = MemoryManager.buildChatContextPrompt(
            userProfile = profile.copy(memoryEnabled = false),
            memories = memories,
            pastConversations = pastConversations,
            currentConversationId = "c_current"
        )
        assertEquals("", disabledPrompt)
    }

    @Test
    fun `formatTurnSnippet trims and combines prompt and reply within 500 chars`() {
        val snippet = MemoryManager.formatTurnSnippet("  How do I use Flow?\n ", "Use StateFlow\nin ViewModel.")
        assertEquals("Q: How do I use Flow? | A: Use StateFlow in ViewModel.", snippet)
    }

    @Test
    fun `chat history filters by query and sorts newest first`() {
        val older = Conversation(
            id = "c1",
            title = "Kotlin Coroutines",
            lastMessagePreview = "Explained StateFlow basics",
            updatedAt = Timestamp(1_000L, 0)
        )
        val newer = Conversation(
            id = "c2",
            title = "Compose UI",
            lastMessagePreview = "Built ChatHistory with Kotlin",
            updatedAt = Timestamp(2_000L, 0)
        )
        val unrelated = Conversation(
            id = "c3",
            title = "Poem Ideas",
            lastMessagePreview = "Evening rain in Dhaka",
            updatedAt = Timestamp(3_000L, 0)
        )

        val filtered = ChatHistoryDefaults.filterAndSortConversations(
            conversations = listOf(older, newer, unrelated),
            query = "kotlin"
        )
        assertEquals(listOf("c2", "c1"), filtered.map { it.id })
    }

    @Test
    fun `chat history formats relative session timestamps cleanly`() {
        val nowMs = 1_700_000_000_000L
        assertEquals("", ChatHistoryDefaults.formatSessionTimestamp(null, nowMs))
        assertEquals("Just now", ChatHistoryDefaults.formatSessionTimestamp(Timestamp((nowMs - 10_000L) / 1000L, 0), nowMs))
        assertEquals("5m ago", ChatHistoryDefaults.formatSessionTimestamp(Timestamp((nowMs - 5 * 60_000L) / 1000L, 0), nowMs))
        assertEquals("3h ago", ChatHistoryDefaults.formatSessionTimestamp(Timestamp((nowMs - 3 * 3_600_000L) / 1000L, 0), nowMs))
        assertEquals("2d ago", ChatHistoryDefaults.formatSessionTimestamp(Timestamp((nowMs - 2 * 86_400_000L) / 1000L, 0), nowMs))
    }

    @Test
    fun `appendSpokenText appends recognized speech cleanly to chat input`() {
        assertEquals("Hello SoulMate", ChatInputUtils.appendSpokenText("", "  Hello SoulMate  "))
        assertEquals("Explain Kotlin coroutines please", ChatInputUtils.appendSpokenText("Explain Kotlin", "coroutines please"))
        assertEquals("Explain Kotlin coroutines", ChatInputUtils.appendSpokenText("Explain Kotlin ", "coroutines"))
        assertEquals("Existing text", ChatInputUtils.appendSpokenText("Existing text", "   "))
    }

    @Test
    fun `profile avatar prompt builder includes description, style, and circular framing`() {
        val prompt = ProfileAvatarStore.buildAvatarPrompt("young coder with neon glasses", "Cyberpunk Neon")
        assertTrue(prompt.contains("young coder with neon glasses"))
        assertTrue(prompt.contains("Cyberpunk Neon"))
        assertTrue(prompt.contains("circular profile picture"))

        val fallbackPrompt = ProfileAvatarStore.buildAvatarPrompt("   ", "")
        assertTrue(fallbackPrompt.contains("3D Pixar Portrait"))
    }

    @Test
    fun `all five personalities resolve cleanly`() {
        assertEquals(5, AiPersonality.entries.size)
        assertEquals(AiPersonality.FRIENDLY, AiPersonality.fromKey("UNKNOWN"))
        assertEquals(AiPersonality.SUPPORT, AiPersonality.fromKey("SUPPORT"))
    }
}
