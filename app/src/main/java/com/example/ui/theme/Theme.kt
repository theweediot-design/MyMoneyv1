package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = Color(0xFF042F24),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = AccentPurple,
    background = NavyDarkBg,
    onBackground = TextPrimaryDark,
    surface = NavyCardBg,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = NavyBorder,
    error = BrightRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    tertiary = AccentPurple,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightCardBg,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCardElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    error = BrightRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
