package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.localization.AppStrings
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.Conversation
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.ChatHistoryDefaults
import com.example.ui.components.ConversationHistoryCard
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText

@Composable
fun HomeScreen(
    strings: AppStrings,
    language: AppLanguage,
    personality: AiPersonality,
    userProfile: UserProfile?,
    customAvatar: Bitmap?,
    activeConversationId: String?,
    conversationsState: UiState<List<Conversation>>,
    onToggleLanguage: () -> Unit,
    onSelectPersonality: (AiPersonality) -> Unit,
    onStartNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenVisionChat: () -> Unit,
    onOpenVoiceChat: () -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenMemoryVault: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var renamingConv by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Brand & Profile Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.soulmate_app_icon_1795508847294),
                        contentDescription = "SoulMate AI Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, BrandCyan.copy(alpha = 0.7f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.developerCredit,
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandCyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .clickable(onClick = onToggleLanguage)
                            .testTag("language_toggle_chip")
                    ) {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) "EN | বাংলা" else "বাংলা | EN",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_settings_button")
                    ) {
                        if (customAvatar != null) {
                            Image(
                                bitmap = customAvatar.asImageBitmap(),
                                contentDescription = strings.settings,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BrandCyan, CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.creator_photo),
                                contentDescription = strings.settings,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BrandPurple.copy(alpha = 0.8f), CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // Welcome Hero Banner + New Chat CTA
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                BrandPurple.copy(alpha = 0.28f),
                                NavySurfaceElevated,
                                BrandCyan.copy(alpha = 0.22f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(BrandPurple.copy(alpha = 0.65f), BrandCyan.copy(alpha = 0.65f))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = BrandPurple.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = userProfile?.displayName?.let { "Hi, $it" } ?: "Welcome",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.welcomeGreeting,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PureWhite,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(
                        onClick = onStartNewChat,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandPurple,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.newChat,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // AI Personality Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.aiPersonality,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(AiPersonality.entries) { mode ->
                        val selected = mode == personality
                        val label = if (language == AppLanguage.BENGALI) mode.titleBn else mode.titleEn
                        val desc = if (language == AppLanguage.BENGALI) mode.subtitleBn else mode.subtitleEn
                        Surface(
                            color = if (selected) BrandPurple.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .width(172.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) BrandCyan else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { onSelectPersonality(mode) }
                                .testTag("personality_chip_${mode.key}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = personalityIcon(mode),
                                    contentDescription = label,
                                    tint = if (selected) BrandCyan else BrandPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Feature Quick Grid (Vision, Voice, Image Studio, Memory Vault)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureActionCard(
                        title = strings.imageUnderstanding,
                        subtitle = strings.imageUnderstandingDesc,
                        icon = Icons.Rounded.PhotoCamera,
                        accent = BrandCyan,
                        onClick = onOpenVisionChat,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("feature_vision_card")
                    )
                    FeatureActionCard(
                        title = strings.voiceConversation,
                        subtitle = strings.voiceConversationDesc,
                        icon = Icons.Rounded.Mic,
                        accent = BrandPurple,
                        onClick = onOpenVoiceChat,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("feature_voice_card")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureActionCard(
                        title = strings.aiImageGeneration,
                        subtitle = strings.aiImageGenerationDesc,
                        icon = Icons.Rounded.Brush,
                        accent = BrandPurple,
                        onClick = onOpenImageStudio,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("feature_studio_card")
                    )
                    FeatureActionCard(
                        title = strings.memoryVault,
                        subtitle = strings.memoryVaultDesc,
                        icon = Icons.Rounded.Psychology,
                        accent = BrandCyan,
                        onClick = onOpenMemoryVault,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("feature_memory_card")
                    )
                }
            }
        }

        // Previous Conversations Header & Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.previousConversations,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchConversations) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandCyan,
                        unfocusedBorderColor = NavyOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )
            }
        }

        // Conversations List
        when (conversationsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandCyan)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        color = ErrorCoral.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = conversationsState.message,
                            color = ErrorCoral,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val filtered = ChatHistoryDefaults.filterAndSortConversations(
                    conversations = conversationsState.data,
                    query = searchQuery
                )
                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
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
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (conversationsState.data.isEmpty()) strings.noConversationsYet else strings.noMatchingConversations,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateText
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { conv ->
                        ConversationHistoryCard(
                            conversation = conv,
                            language = language,
                            strings = strings,
                            isActive = conv.id == activeConversationId,
                            onClick = { onOpenConversation(conv.id) },
                            onRenameClick = {
                                renamingConv = conv
                                renameText = conv.title
                            },
                            onDeleteClick = { onDeleteConversation(conv.id) }
                        )
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
                            onRenameConversation(conv.id, renameText)
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
private fun FeatureActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = SlateText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun personalityIcon(personality: AiPersonality): ImageVector = when (personality) {
    AiPersonality.FRIENDLY -> Icons.Rounded.AutoAwesome
    AiPersonality.FUNNY -> Icons.Rounded.SentimentVerySatisfied
    AiPersonality.TEACHER -> Icons.Rounded.School
    AiPersonality.CREATIVE -> Icons.Rounded.Brush
    AiPersonality.SUPPORT -> Icons.Rounded.FavoriteBorder
}
