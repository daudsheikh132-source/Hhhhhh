package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.model.AppThemeMode

private val SoulMateDarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    onPrimary = PureWhite,
    primaryContainer = BrandPurpleDark,
    onPrimaryContainer = PureWhite,
    secondary = BrandCyan,
    onSecondary = DarkNavy,
    secondaryContainer = NavySurfaceHighest,
    onSecondaryContainer = BrandCyanLight,
    tertiary = BrandPurpleLight,
    background = DarkNavy,
    onBackground = PureWhite,
    surface = NavySurface,
    onSurface = PureWhite,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = SlateText,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurfaceElevated,
    surfaceContainerHigh = NavySurfaceHighest,
    outline = NavyOutline,
    error = ErrorCoral,
    onError = DarkNavy
)

private val SoulMateLightColorScheme = lightColorScheme(
    primary = BrandPurpleDark,
    onPrimary = PureWhite,
    primaryContainer = BrandPurpleLight.copy(alpha = 0.25f),
    onPrimaryContainer = BrandPurpleDark,
    secondary = BrandCyanDark,
    onSecondary = PureWhite,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = BrandCyanDark,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateText,
    outline = LightOutline,
    error = ErrorCoral
)

@Composable
fun SoulMateTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_NAVY,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK_NAVY -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) SoulMateDarkColorScheme else SoulMateLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SoulMateTypography,
        content = content
    )
}
