package com.audioly.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AudiolyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AudiolyTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

private val LightColorScheme = lightColorScheme(
    primary = AudiolyLightPrimary,
    onPrimary = AudiolyLightOnPrimary,
    primaryContainer = AudiolyLightPrimaryContainer,
    onPrimaryContainer = AudiolyLightOnPrimaryContainer,
    secondary = AudiolyLightSecondary,
    onSecondary = AudiolyLightOnSecondary,
    secondaryContainer = AudiolyLightSecondaryContainer,
    onSecondaryContainer = AudiolyLightOnSecondaryContainer,
    background = AudiolyLightBackground,
    onBackground = AudiolyLightOnBackground,
    surface = AudiolyLightSurface,
    onSurface = AudiolyLightOnSurface,
    surfaceVariant = AudiolyLightSurfaceVariant,
    onSurfaceVariant = AudiolyLightOnSurfaceVariant,
    error = AudiolyLightError,
    onError = AudiolyLightOnError,
)

private val DarkColorScheme = darkColorScheme(
    primary = AudiolyDarkPrimary,
    onPrimary = AudiolyDarkOnPrimary,
    primaryContainer = AudiolyDarkPrimaryContainer,
    onPrimaryContainer = AudiolyDarkOnPrimaryContainer,
    secondary = AudiolyDarkSecondary,
    onSecondary = AudiolyDarkOnSecondary,
    secondaryContainer = AudiolyDarkSecondaryContainer,
    onSecondaryContainer = AudiolyDarkOnSecondaryContainer,
    background = AudiolyDarkBackground,
    onBackground = AudiolyDarkOnBackground,
    surface = AudiolyDarkSurface,
    onSurface = AudiolyDarkOnSurface,
    surfaceVariant = AudiolyDarkSurfaceVariant,
    onSurfaceVariant = AudiolyDarkOnSurfaceVariant,
    error = AudiolyDarkError,
    onError = AudiolyDarkOnError,
)

/**
 * Shared Audioly theme for Compose Multiplatform (Android + iOS).
 *
 * @param darkTheme Force dark/light; null = follow system.
 * @param content Composable content.
 */
@Composable
fun AudiolyTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AudiolyTypography,
        shapes = AudiolyShapes,
        content = content,
    )
}
