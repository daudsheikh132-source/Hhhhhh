package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ImageAspectOption
import com.example.localization.AppStrings
import com.example.model.ImageGenUiState
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText
import com.example.ui.theme.SuccessMint
import com.example.util.ImageUtils
import com.example.viewmodel.ImageGenViewModel
import kotlinx.coroutines.launch

@Composable
fun ImageGenScreen(
    strings: AppStrings,
    viewModel: ImageGenViewModel,
    onSetAsAvatar: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val selectedAspect by viewModel.selectedAspect.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    var saveFeedback by remember { mutableStateOf<String?>(null) }

    val styles = remember {
        listOf(
            "Cinematic Digital Art",
            "Cyberpunk Neon",
            "Watercolor Painting",
            "Photorealistic",
            "Minimalist Vector",
            "Anime Illustration"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("image_gen_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.aiImageGeneration,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Powered by Firebase AI Logic (Gemini Nano Banana 2.1)",
                style = MaterialTheme.typography.labelMedium,
                color = BrandCyan
            )
        }

        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text(strings.promptPlaceholder) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandCyan,
                    unfocusedBorderColor = NavyOutline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_gen_prompt_input")
            )
        }

        item {
            Text(
                text = "Art Style",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(styles) { style ->
                    val active = style == selectedStyle
                    Surface(
                        color = if (active) BrandPurple.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) BrandCyan else NavyOutline,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.setStyle(style) }
                    ) {
                        Text(
                            text = style,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aspect Ratio:", style = MaterialTheme.typography.labelLarge)
                ImageAspectOption.entries.forEach { option ->
                    val active = option == selectedAspect
                    Surface(
                        color = if (active) BrandCyan.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (active) BrandCyan else NavyOutline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.setAspect(option) }
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    saveFeedback = null
                    viewModel.generateImage(prompt)
                },
                enabled = prompt.isNotBlank() && uiState !is ImageGenUiState.Loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_image_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.generateImage, style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            when (val state = uiState) {
                is ImageGenUiState.Idle -> {
                    Surface(
                        color = NavySurfaceElevated,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .border(1.dp, NavyOutline, RoundedCornerShape(20.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Enter a vivid description above and choose your style to create original AI artwork.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is ImageGenUiState.Loading -> {
                    val pulse = rememberInfiniteTransition(label = "img_shimmer")
                    val alpha by pulse.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "shimmer_alpha"
                    )
                    Surface(
                        color = NavySurfaceElevated,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .alpha(alpha)
                            .border(
                                1.5.dp,
                                Brush.linearGradient(listOf(BrandPurple, BrandCyan)),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = BrandCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Generating image (may take 30–60s)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite
                            )
                        }
                    }
                }

                is ImageGenUiState.Error -> {
                    Surface(
                        color = ErrorCoral.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ErrorCoral.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Image Generation Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = ErrorCoral
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite
                            )
                            Text(
                                text = "Note: Gemini image generation (Nano Banana 2.1) requires the Firebase project to be on the Blaze pay-as-you-go plan with App Check enabled.",
                                style = MaterialTheme.typography.labelMedium,
                                color = SlateText
                            )
                        }
                    }
                }

                is ImageGenUiState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated AI image for ${state.prompt}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    when (selectedAspect) {
                                        ImageAspectOption.SQUARE -> 1f
                                        ImageAspectOption.LANDSCAPE -> 16f / 9f
                                        ImageAspectOption.PORTRAIT -> 9f / 16f
                                    }
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.5.dp, BrandCyan, RoundedCornerShape(20.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        ImageUtils.saveBitmapToGallery(context, state.bitmap, state.prompt)
                                            .onSuccess { filename ->
                                                saveFeedback = "Saved to Pictures/SoulMateAI/$filename"
                                            }
                                            .onFailure {
                                                saveFeedback = it.localizedMessage ?: "Failed to save image"
                                            }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.saveToGallery)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        ImageUtils.shareBitmap(context, state.bitmap, state.prompt)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = PureWhite),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.shareImage)
                            }
                        }

                        OutlinedButton(
                            onClick = { onSetAsAvatar(state.bitmap) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("set_generated_as_avatar_button")
                        ) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.setAsAvatar)
                        }

                        saveFeedback?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelMedium,
                                color = SuccessMint
                            )
                        }
                    }
                }
            }
        }
    }
}
