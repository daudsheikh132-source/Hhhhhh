package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.localization.AppStrings
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.ImageGenUiState
import com.example.model.UserProfile
import com.example.ui.components.AvatarGeneratorDialog
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText

@Composable
fun SettingsScreen(
    strings: AppStrings,
    userProfile: UserProfile?,
    customAvatar: Bitmap?,
    avatarPreviewState: ImageGenUiState,
    language: AppLanguage,
    themeMode: AppThemeMode,
    memoryEnabled: Boolean,
    voiceSpeechRate: Float,
    voiceAutoSpeak: Boolean,
    onGenerateAvatar: (String, String) -> Unit,
    onResetAvatarPreview: () -> Unit,
    onSaveCustomAvatar: (Bitmap) -> Unit,
    onResetToUploadedPhoto: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetMemoryEnabled: (Boolean) -> Unit,
    onSetVoiceSpeechRate: (Double) -> Unit,
    onSetVoiceAutoSpeak: (Boolean) -> Unit,
    onClearChatHistory: () -> Unit,
    onClearAllMemories: () -> Unit,
    onDeleteAllDataAndSignOut: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var showAvatarDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Profile Card
        item {
            Surface(
                color = NavySurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyOutline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (customAvatar != null) {
                            Image(
                                bitmap = customAvatar.asImageBitmap(),
                                contentDescription = strings.customAiAvatar,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BrandCyan, CircleShape)
                                    .testTag("settings_user_avatar")
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.creator_photo),
                                contentDescription = strings.uploadedPhoto,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BrandCyan, CircleShape)
                                    .testTag("settings_user_avatar")
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = userProfile?.displayName ?: "SoulMate User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            userProfile?.email?.takeIf { it.isNotBlank() }?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateText
                                )
                            }
                            Text(
                                text = if (customAvatar != null) strings.customAiAvatar else strings.uploadedPhoto,
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandCyan
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onResetAvatarPreview()
                                showAvatarDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPurple,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("generate_ai_avatar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.generateAiAvatar,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (customAvatar != null) {
                            OutlinedButton(
                                onClick = onResetToUploadedPhoto,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("reset_avatar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = strings.resetToUploadedPhoto,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.resetToUploadedPhoto,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Language Settings
        item {
            SettingsSectionCard(title = strings.languageSetting) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val active = lang == language
                        Surface(
                            color = if (active) BrandPurple.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    1.dp,
                                    if (active) BrandCyan else NavyOutline,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSetLanguage(lang) }
                        ) {
                            Text(
                                text = lang.nativeLabel,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Theme Settings
        item {
            SettingsSectionCard(title = strings.themeSetting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        val active = mode == themeMode
                        val label = when (mode) {
                            AppThemeMode.DARK_NAVY -> "Dark Navy"
                            AppThemeMode.SYSTEM -> "System"
                            AppThemeMode.LIGHT -> "Light"
                        }
                        Surface(
                            color = if (active) BrandPurple.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (active) BrandCyan else NavyOutline, RoundedCornerShape(12.dp))
                                .clickable { onSetThemeMode(mode) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Voice Settings
        item {
            SettingsSectionCard(title = strings.voiceSettings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.autoSpeakResponses,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = voiceAutoSpeak,
                        onCheckedChange = onSetVoiceAutoSpeak,
                        colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = BrandCyan)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${strings.speechRate}: ${String.format("%.1fx", voiceSpeechRate)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = voiceSpeechRate,
                    onValueChange = { onSetVoiceSpeechRate(it.toDouble()) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = BrandCyan,
                        activeTrackColor = BrandPurple
                    )
                )
            }
        }

        // Memory Toggle
        item {
            SettingsSectionCard(title = strings.memoryVault) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.memoryEnabledLabel, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = onSetMemoryEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = BrandCyan)
                    )
                }
            }
        }

        // Privacy & Data Control
        item {
            SettingsSectionCard(title = strings.privacyAndData) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.aiIdentityDisclaimer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmAction = "chats" },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(strings.clearChatHistory)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmAction = "memories" },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(strings.clearAllMemories)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { confirmAction = "account" },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCoral.copy(alpha = 0.2f), contentColor = ErrorCoral),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.deleteAccountData)
                }
            }
        }

        // About SoulMate AI
        item {
            SettingsSectionCard(title = strings.aboutSoulMate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SoulMate AI — v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Created by Daud • Powered by Firebase AI Logic & Cloud Firestore",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateText
                        )
                    }
                }
            }
        }

        // Sign Out
        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = PureWhite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.signOut, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Confirm Deletion") },
            text = {
                Text(
                    when (action) {
                        "chats" -> "Permanently delete all saved conversations and messages?"
                        "memories" -> "Permanently delete all saved memories?"
                        else -> "Delete all your conversations, memories, and profile data and sign out?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "chats" -> onClearChatHistory()
                            "memories" -> onClearAllMemories()
                            else -> onDeleteAllDataAndSignOut()
                        }
                        confirmAction = null
                    }
                ) {
                    Text(strings.delete, color = ErrorCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showAvatarDialog) {
        AvatarGeneratorDialog(
            strings = strings,
            currentCustomAvatar = customAvatar,
            avatarPreviewState = avatarPreviewState,
            onGenerateAvatar = onGenerateAvatar,
            onConfirmAvatar = { bitmap ->
                onSaveCustomAvatar(bitmap)
                showAvatarDialog = false
            },
            onDismiss = {
                showAvatarDialog = false
                onResetAvatarPreview()
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = NavySurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyOutline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BrandCyan,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}
