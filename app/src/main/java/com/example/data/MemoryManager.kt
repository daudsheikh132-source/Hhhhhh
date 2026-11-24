package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.Conversation
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.PendingMemorySuggestion
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Manages user preferences, approved long-term memories, and past conversation snippets
 * in Cloud Firestore, and assembles context-aware prompt blocks for AI chat sessions.
 */
class MemoryManager(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing MemoryManager.")
    }

    private fun unwrapFirestoreError(error: Throwable, op: OperationType, path: String, uid: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        return firestoreError ?: error
    }

    fun observeUserPreferences(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User ID mismatch" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    unwrapFirestoreError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun updatePreferences(
        preferredLanguage: AppLanguage? = null,
        selectedPersonality: AiPersonality? = null,
        memoryEnabled: Boolean? = null,
        voiceSpeechRate: Double? = null,
        voiceAutoSpeak: Boolean? = null,
        themeMode: AppThemeMode? = null,
        displayName: String? = null
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            preferredLanguage?.let { updates["preferredLanguage"] = it.code }
            selectedPersonality?.let { updates["selectedPersonality"] = it.key }
            memoryEnabled?.let { updates["memoryEnabled"] = it }
            voiceSpeechRate?.let { updates["voiceSpeechRate"] = it.coerceIn(0.5, 2.0) }
            voiceAutoSpeak?.let { updates["voiceAutoSpeak"] = it }
            themeMode?.let { updates["themeMode"] = it.key }
            displayName?.trim()?.take(100)?.takeIf { it.isNotBlank() }?.let {
                updates["displayName"] = it
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User ID mismatch" }
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MemoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapFirestoreError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun savePreferenceMemory(
        title: String,
        content: String,
        sensitive: Boolean = false
    ): Result<Unit> = saveMemory(
        title = title,
        content = content,
        category = MemoryCategory.PREFERENCE,
        sensitive = sensitive
    )

    suspend fun saveConversationSnippet(
        title: String,
        snippet: String,
        sensitive: Boolean = false
    ): Result<Unit> = saveMemory(
        title = title,
        content = snippet,
        category = MemoryCategory.CONTEXT,
        sensitive = sensitive
    )

    suspend fun saveMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        sensitive: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document()
            path = docRef.path
            val safeTitle = title.trim().take(100).ifBlank { "Saved Note" }
            val safeContent = content.trim().take(500)
            require(safeContent.isNotEmpty()) { "Memory content cannot be empty" }
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "content" to safeContent,
                "category" to category.key,
                "sensitive" to sensitive,
                "approvedByUser" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(
        memoryId: String,
        title: String,
        content: String,
        category: MemoryCategory,
        sensitive: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            path = docRef.path
            val safeTitle = title.trim().take(100).ifBlank { "Saved Note" }
            val safeContent = content.trim().take(500)
            require(safeContent.isNotEmpty()) { "Memory content cannot be empty" }
            docRef.update(
                mapOf(
                    "title" to safeTitle,
                    "content" to safeContent,
                    "category" to category.key,
                    "sensitive" to sensitive,
                    "approvedByUser" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllMemories(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val memCol = db.collection("users").document(uid).collection("memories")
            path = memCol.path
            val docs = memCol.get().await()
            for (doc in docs.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchRecentConversationSnippets(
        excludeConversationId: String? = null,
        limit: Int = 5
    ): Result<List<Conversation>> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val convCol = db.collection("users").document(uid).collection("conversations")
            path = convCol.path
            val snapshot = convCol.get().await()
            val snippets = snapshot.toObjects(
                Conversation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
                .filter { it.id != excludeConversationId && it.lastMessagePreview.isNotBlank() }
                .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                .take(limit)
            Result.success(snippets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun buildChatContextPrompt(
        userProfile: UserProfile?,
        memories: List<MemoryItem>,
        pastConversations: List<Conversation>,
        currentConversationId: String? = null
    ): String = Companion.buildChatContextPrompt(
        userProfile = userProfile,
        memories = memories,
        pastConversations = pastConversations,
        currentConversationId = currentConversationId
    )

    fun detectMemoryCandidate(userText: String): PendingMemorySuggestion? =
        Companion.detectMemoryCandidate(userText)

    fun formatTurnSnippet(userPrompt: String, modelReply: String): String =
        Companion.formatTurnSnippet(userPrompt, modelReply)

    companion object {
        /**
         * Builds a structured context prompt block combining user preferences, approved memories,
         * and past conversation snippets when Memory is enabled.
         */
        fun buildChatContextPrompt(
            userProfile: UserProfile?,
            memories: List<MemoryItem>,
            pastConversations: List<Conversation>,
            currentConversationId: String? = null
        ): String {
            val memoryEnabled = userProfile?.memoryEnabled ?: true
            if (!memoryEnabled) return ""

            val sections = mutableListOf<String>()

            userProfile?.let { profile ->
                val prefLines = buildList {
                    if (profile.displayName.isNotBlank()) add("User name: ${profile.displayName}")
                    add("Preferred language: ${AppLanguage.fromCode(profile.preferredLanguage).label}")
                    add("Preferred AI mode: ${AiPersonality.fromKey(profile.selectedPersonality).titleEn}")
                }
                sections.add("User Preferences:\n" + prefLines.joinToString("\n") { "- $it" })
            }

            val approvedMemories = memories.filter { it.approvedByUser }.take(20)
            if (approvedMemories.isNotEmpty()) {
                val memLines = approvedMemories.joinToString("\n") { item ->
                    "- [${item.category}] ${item.title}: ${item.content}"
                }
                sections.add("User-Approved Memories & Saved Snippets:\n$memLines")
            }

            val relevantPastChats = pastConversations
                .filter { it.id != currentConversationId && it.lastMessagePreview.isNotBlank() }
                .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                .take(5)

            if (relevantPastChats.isNotEmpty()) {
                val snippetLines = relevantPastChats.joinToString("\n") { conv ->
                    "- Topic \"${conv.title}\": ${conv.lastMessagePreview}"
                }
                sections.add("Recent Past Conversation Snippets (for cross-session continuity):\n$snippetLines")
            }

            return if (sections.isEmpty()) "" else sections.joinToString("\n\n")
        }

        fun detectMemoryCandidate(userText: String): PendingMemorySuggestion? {
            val trimmed = userText.trim()
            if (trimmed.length < 10 || trimmed.length > 400) return null
            val lower = trimmed.lowercase()

            val explicitTrigger = listOf(
                "remember that", "please remember", "remember my", "মনে রেখো", "মনে রাখবে"
            ).any { lower.contains(it) }

            val preferenceTrigger = listOf(
                "i prefer", "my favorite", "i always like", "call me ", "i am learning",
                "আমার পছন্দ", "আমি ভালোবাসি"
            ).any { lower.contains(it) }

            val sensitiveTrigger = listOf(
                "my phone", "my address", "my birthday", "my age is", "i live in",
                "my email", "my health", "i feel depressed",
                "আমার ঠিকানা", "আমার জন্মদিন", "আমার বয়স"
            ).any { lower.contains(it) }

            return when {
                sensitiveTrigger -> PendingMemorySuggestion(
                    title = "Personal Detail",
                    content = trimmed.take(280),
                    category = MemoryCategory.PERSONAL,
                    sensitive = true
                )
                explicitTrigger -> PendingMemorySuggestion(
                    title = "Conversation Snippet",
                    content = trimmed.take(280),
                    category = MemoryCategory.CONTEXT,
                    sensitive = false
                )
                preferenceTrigger -> PendingMemorySuggestion(
                    title = "User Preference",
                    content = trimmed.take(280),
                    category = MemoryCategory.PREFERENCE,
                    sensitive = false
                )
                else -> null
            }
        }

        fun formatTurnSnippet(userPrompt: String, modelReply: String): String {
            val u = userPrompt.trim().replace("\n", " ").take(160)
            val a = modelReply.trim().replace("\n", " ").take(280)
            return "Q: $u | A: $a".take(500)
        }
    }
}
