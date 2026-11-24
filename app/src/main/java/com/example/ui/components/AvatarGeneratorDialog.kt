package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.localization.AppStrings
import com.example.model.ImageGenUiState
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText

@Composable
fun AvatarGeneratorDialog(
    strings: AppStrings,
    currentCustomAvatar: Bitmap?,
    avatarPreviewState: ImageGenUiState,
    onGenerateAvatar: (description: String, style: String) -> Unit,
    onConfirmAvatar: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("3D Pixar Portrait") }

    val avatarStyles = remember {
        listOf(
            "3D Pixar Portrait",
            "Cyberpunk Neon",
            "Anime Avatar",
            "Minimalist Vector",
            "Digital Painting",
            "Photorealistic Studio"
        )
    }

    val quickIdeas = remember {
        listOf(
            "Smiling young tech creator with glowing purple and cyan aura",
            "Futuristic cosmic explorer with sleek visor and starlight",
            "Warm studio portrait with soft neon rim lighting",
            "Friendly anime scholar surrounded by floating digital sparks"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = NavySurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(BrandPurple.copy(alpha = 0.7f), BrandCyan.copy(alpha = 0.7f))),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("avatar_generator_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.generateAiAvatar,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel, tint = SlateText)
                    }
                }

                // Circular Preview Frame
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                            .background(NavySurfaceElevated)
                            .border(
                                width = 2.5.dp,
                                brush = Brush.linearGradient(listOf(BrandPurple, BrandCyan)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (avatarPreviewState) {
                            is ImageGenUiState.Loading -> {
                                CircularProgressIndicator(color = BrandCyan)
                            }
                            is ImageGenUiState.Success -> {
                                Image(
                                    bitmap = avatarPreviewState.bitmap.asImageBitmap(),
                                    contentDescription = "Generated AI avatar preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .testTag("generated_avatar_preview")
                                )
                            }
                            else -> {
                                if (currentCustomAvatar != null) {
                                    Image(
                                        bitmap = currentCustomAvatar.asImageBitmap(),
                                        contentDescription = strings.customAiAvatar,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.creator_photo),
                                        contentDescription = strings.uploadedPhoto,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                if (avatarPreviewState is ImageGenUiState.Error) {
                    Surface(
                        color = ErrorCoral.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = avatarPreviewState.message,
                            color = ErrorCoral,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text(strings.avatarPromptHint) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandCyan,
                        unfocusedBorderColor = NavyOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("avatar_prompt_input")
                )

                // Quick Ideas
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickIdeas) { idea ->
                        Surface(
                            color = NavySurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, NavyOutline, RoundedCornerShape(12.dp))
                                .clickable { description = idea }
                        ) {
                            Text(
                                text = idea.take(32) + "...",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Style Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(avatarStyles) { style ->
                        val active = style == selectedStyle
                        Surface(
                            color = if (active) BrandPurple.copy(alpha = 0.25f) else NavySurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) BrandCyan else NavyOutline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedStyle = style }
                        ) {
                            Text(
                                text = style,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = { onGenerateAvatar(description, selectedStyle) },
                    enabled = description.isNotBlank() && avatarPreviewState !is ImageGenUiState.Loading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = PureWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("avatar_generate_submit_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.generateAiAvatar)
                }

                if (avatarPreviewState is ImageGenUiState.Success) {
                    Button(
                        onClick = { onConfirmAvatar(avatarPreviewState.bitmap) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = PureWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("avatar_confirm_button")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.useAsProfileAvatar)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(strings.cancel)
                    }
                }
            }
        }
    }
}
