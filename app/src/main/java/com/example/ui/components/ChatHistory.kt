package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SoulMateRepository
import com.example.localization.AppStrings
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.Conversation
import com.example.model.UiState
import com.example.ui.screens.personalityIcon
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatHistoryDefaults {
    fun filterAndSortConversations(
        conversations: List<Conversation>,
        query: String
    ): List<Conversation> {
        val trimmedQuery = query.trim()
        return conversations
            .asSequence()
            .filter { conv ->
                trimmedQuery.isEmpty() ||
                    conv.title.contains(trimmedQuery, ignoreCase = true) ||
                    conv.lastMessagePreview.contains(trimmedQuery, ignoreCase = true)
            }
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            .toList()
    }

    fun formatSessionTimestamp(
        timestamp: Timestamp?,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (timestamp == null) return ""
        val thenMs = timestamp.seconds * 1000L + timestamp.nanoseconds / 1_000_000L
        val diffMs = (nowMs - thenMs).coerceAtLeast(0L)
        val minutes = diffMs / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1L -> "Just now"
            minutes < 60L -> "${minutes}m ago"
            hours < 24L -> "${hours}h ago"
            days < 7L -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(thenMs))
        }
    }
}

/**
 * Reusable ChatHistory component that retrieves previous sessions from Cloud Firestore
 * (via [SoulMateRepository.observeConversations] when [conversationsState] is null) and displays
 * them in a searchable, scrollable list so users can select and resume a past conversation.
 */
@Composable
fun ChatHistory(
    strings: AppStrings,
    language: AppLanguage,
    onSelectConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    activeConversationId: String? = null,
    conversationsState: UiState<List<Conversation>>? = null,
    onRenameConversation: ((String, String) -> Unit)? = null,
    onDeleteConversation: ((String) -> Unit)? = null,
    onStartNewChat: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = Firebase.auth.currentUser?.uid
    var refreshNonce by remember { mutableIntStateOf(0) }

    val repository = remember(context) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        SoulMateRepository(FirebaseFirestore.getInstance(dbId))
    }

    val observedFirestoreState by produceState<UiState<List<Conversation>>>(
        initialValue = UiState.Loading,
        key1 = conversationsState == null,
        key2 = currentUserId,
        key3 = refreshNonce
    ) {
        if (conversationsState != null) return@produceState
        if (currentUserId.isNullOrBlank()) {
            value = UiState.Error("Please sign in with Google to view your chat history.")
            return@produceState
        }
        value = UiState.Loading
        repository.observeConversations(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                value = UiState.Error(root.localizedMessage ?: "Failed to load chat history from Firestore.")
            }
            .collect { conversations ->
                value = UiState.Success(conversations)
            }
    }

    val effectiveState = conversationsState ?: observedFirestoreState
    var searchQuery by remember { mutableStateOf("") }
    var renamingConv by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    val handleRename: (String, String) -> Unit = { id, newTitle ->
        if (onRenameConversation != null) {
            onRenameConversation(id, newTitle)
        } else {
            scope.launch { repository.renameConversation(id, newTitle) }
        }
    }

    val handleDelete: (String) -> Unit = { id ->
        if (onDeleteConversation != null) {
            onDeleteConversation(id)
        } else {
            scope.launch { repository.deleteConversation(id) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_history_component"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = BrandPurple.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = strings.chatHistory,
                            tint = BrandCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = strings.chatHistory,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (effectiveState is UiState.Success) {
                        Text(
                            text = "${effectiveState.data.size} ${if (effectiveState.data.size == 1) "session" else "sessions"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateText
                        )
                    }
                }
            }

            if (onStartNewChat != null) {
                OutlinedButton(
                    onClick = onStartNewChat,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("chat_history_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.newChat, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(strings.searchConversations) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = BrandCyan)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = SlateText
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandCyan,
                unfocusedBorderColor = NavyOutline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_history_search_input")
        )

        // Scrollable Firestore Session List
        when (effectiveState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandCyan)
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = ErrorCoral.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ErrorCoral.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = effectiveState.message,
                                color = ErrorCoral,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (conversationsState == null) {
                                OutlinedButton(
                                    onClick = { refreshNonce++ },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(strings.retry)
                                }
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                val filtered = remember(effectiveState.data, searchQuery) {
                    ChatHistoryDefaults.filterAndSortConversations(effectiveState.data, searchQuery)
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = BrandCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (effectiveState.data.isEmpty()) {
                                        strings.noConversationsYet
                                    } else {
                                        strings.noMatchingConversations
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateText
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("chat_history_list"),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { conv ->
                            ConversationHistoryCard(
                                conversation = conv,
                                language = language,
                                strings = strings,
                                isActive = conv.id == activeConversationId,
                                onClick = { onSelectConversation(conv.id) },
                                onRenameClick = {
                                    renamingConv = conv
                                    renameText = conv.title
                                },
                                onDeleteClick = { handleDelete(conv.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    renamingConv?.let { conv ->
        AlertDialog(
            onDismissRequest = { renamingConv = null },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            handleRename(conv.id, renameText)
                        }
                        renamingConv = null
                    }
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingConv = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun ConversationHistoryCard(
    conversation: Conversation,
    language: AppLanguage,
    strings: AppStrings,
    isActive: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val personality = AiPersonality.fromKey(conversation.personality)
    val personalityLabel = if (language == AppLanguage.BENGALI) personality.titleBn else personality.titleEn
    val relativeTime = remember(conversation.updatedAt, conversation.createdAt) {
        ChatHistoryDefaults.formatSessionTimestamp(conversation.updatedAt ?: conversation.createdAt)
    }

    Surface(
        color = if (isActive) {
            BrandPurple.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) BrandCyan else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .testTag("conversation_card_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isActive) BrandCyan.copy(alpha = 0.2f) else BrandPurple.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = personalityIcon(personality),
                        contentDescription = personalityLabel,
                        tint = BrandCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BrandCyan.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = strings.activeSession,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (conversation.lastMessagePreview.isNotBlank()) {
                    Text(
                        text = conversation.lastMessagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val timeSuffix = if (relativeTime.isNotBlank()) " • $relativeTime" else ""
                    Text(
                        text = "$personalityLabel • ${conversation.messageCount} msgs$timeSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandCyan.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = strings.resumeChat,
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite.copy(alpha = 0.78f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = strings.resumeChat,
                                tint = PureWhite.copy(alpha = 0.78f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onRenameClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = strings.rename,
                    tint = SlateText,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = strings.delete,
                    tint = ErrorCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
