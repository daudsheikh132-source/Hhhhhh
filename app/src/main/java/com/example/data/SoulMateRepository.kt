package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
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

class SoulMateRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
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

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
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

    suspend fun ensureUserProfile(
        displayName: String?,
        email: String?
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName?.trim()?.take(100)?.ifBlank { "Explorer" } ?: "Explorer"
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredLanguage" to AppLanguage.ENGLISH.code,
                    "selectedPersonality" to AiPersonality.FRIENDLY.key,
                    "memoryEnabled" to true,
                    "voiceSpeechRate" to 1.0,
                    "voiceAutoSpeak" to true,
                    "themeMode" to AppThemeMode.DARK_NAVY.key,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    payload["email"] = email.trim().take(160)
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
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

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User ID mismatch" }
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users").document(uid).collection("conversations")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapFirestoreError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createConversation(
        title: String,
        personality: AiPersonality,
        language: AppLanguage,
        initialPreview: String = ""
    ): Result<String> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("conversations").document()
            path = docRef.path
            val safeTitle = title.trim().take(120).ifBlank { "New Chat" }
            val safePreview = initialPreview.trim().take(300)
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "personality" to personality.key,
                "language" to language.code,
                "lastMessagePreview" to safePreview,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            path = docRef.path
            val safeTitle = newTitle.trim().take(120).ifBlank { "Untitled Chat" }
            docRef.update(
                mapOf(
                    "title" to safeTitle,
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

    suspend fun updateConversationMeta(
        conversationId: String,
        title: String? = null,
        personality: AiPersonality? = null,
        language: AppLanguage? = null,
        lastMessagePreview: String? = null,
        messageCount: Long? = null
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            path = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            title?.trim()?.take(120)?.takeIf { it.isNotBlank() }?.let { updates["title"] = it }
            personality?.let { updates["personality"] = it.key }
            language?.let { updates["language"] = it.code }
            lastMessagePreview?.let { updates["lastMessagePreview"] = it.trim().take(300) }
            messageCount?.let { updates["messageCount"] = it.coerceAtLeast(0L) }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            path = convRef.path
            val messages = convRef.collection("messages").get().await()
            for (doc in messages.documents) {
                doc.reference.delete().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User ID mismatch" }
        require(conversationId.isNotBlank()) { "Conversation ID required" }
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 2, 0) }
                }
                .catch { error ->
                    unwrapFirestoreError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        personality: AiPersonality,
        hasImage: Boolean = false,
        imageCount: Int = 0
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val msgRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").document()
            path = msgRef.path
            val safeRole = if (role == "model") "model" else "user"
            val payload = mapOf(
                "userId" to uid,
                "conversationId" to conversationId,
                "role" to safeRole,
                "content" to content.take(20000),
                "personality" to personality.key,
                "hasImage" to hasImage,
                "imageCount" to imageCount.coerceIn(0, 5).toLong(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
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

    suspend fun addMemory(
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

    suspend fun clearAllConversations(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val convCol = db.collection("users").document(uid).collection("conversations")
            path = convCol.path
            val convs = convCol.get().await()
            for (conv in convs.documents) {
                val msgs = conv.reference.collection("messages").get().await()
                for (msg in msgs.documents) {
                    msg.reference.delete().await()
                }
                conv.reference.delete().await()
            }
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
            val mems = memCol.get().await()
            for (doc in mems.documents) {
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

    suspend fun deleteAllUserData(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            clearAllConversations().getOrThrow()
            clearAllMemories().getOrThrow()
            db.collection("users").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
