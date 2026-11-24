package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText
import com.example.util.ProfileAvatarStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun IntroScreen(
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    remember(context) { ProfileAvatarStore.loadSyncIfNeeded(context) }
    val customAvatar by ProfileAvatarStore.avatarBitmap.collectAsStateWithLifecycle()

    val scaleAnim = remember { Animatable(0.84f) }
    val alphaAnim = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "intro_glow_transition")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "intro_ring_rotation"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "intro_glow_pulse"
    )
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "intro_particle_phase"
    )

    LaunchedEffect(Unit) {
        launch {
            alphaAnim.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            scaleAnim.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
        }
        delay(3000L)
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .safeDrawingPadding()
            .testTag("intro_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Lightweight ambient background glow & floating particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.43f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandPurple.copy(alpha = 0.34f * glowPulse),
                        BrandCyan.copy(alpha = 0.16f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.56f
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * 0.56f
            )

            val particleCount = 16
            for (i in 0 until particleCount) {
                val angle = (i.toFloat() / particleCount) * (2f * Math.PI.toFloat()) + particlePhase * 0.35f
                val orbit = size.minDimension * (0.28f + (i % 3) * 0.085f)
                val px = cx + cos(angle) * orbit
                val py = cy + sin(angle) * orbit + sin(particlePhase + i) * 12f
                val dotColor = if (i % 2 == 0) BrandCyan else BrandPurple
                drawCircle(
                    color = dotColor.copy(alpha = 0.5f),
                    radius = (2f + (i % 3) * 1.4f).dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Circular personal photo frame with glowing purple & cyan border
            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Outer soft aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BrandCyan.copy(alpha = 0.42f * glowPulse),
                                BrandPurple.copy(alpha = 0.32f * glowPulse),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2f
                    )
                    // Rotating glowing border ring
                    rotate(ringRotation) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    BrandPurple,
                                    BrandCyan,
                                    BrandPurple.copy(alpha = 0.65f),
                                    BrandCyan,
                                    BrandPurple
                                )
                            ),
                            radius = (size.minDimension / 2f) - 10.dp.toPx()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(184.dp)
                        .clip(CircleShape)
                        .background(DarkNavy)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .border(2.dp, BrandCyan.copy(alpha = 0.6f), CircleShape)
                ) {
                    val activeAvatar = customAvatar
                    if (activeAvatar != null) {
                        Image(
                            bitmap = activeAvatar.asImageBitmap(),
                            contentDescription = "Custom AI profile avatar in SoulMate AI intro",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .testTag("intro_user_photo")
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.creator_photo),
                            contentDescription = "User uploaded portrait in SoulMate AI intro",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .testTag("intro_user_photo")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.soulmate_app_icon_1795508847294),
                    contentDescription = "SoulMate AI Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.dp, BrandCyan.copy(alpha = 0.75f), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SoulMate AI",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = PureWhite,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Smart AI Companion",
                style = MaterialTheme.typography.titleMedium,
                color = BrandCyan,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                color = NavySurfaceElevated.copy(alpha = 0.88f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(BrandPurple.copy(alpha = 0.55f), BrandCyan.copy(alpha = 0.55f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
            ) {
                Text(
                    text = "Created by Daud",
                    style = MaterialTheme.typography.labelLarge,
                    color = SlateText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
