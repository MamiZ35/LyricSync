package com.lyricsync.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lyricsync.app.domain.ThemeMode

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF090D16),
    surface = Color(0xFF131B2E),
    surfaceVariant = Color(0xFF1E293B),
    onPrimary = Color(0xFF090D16),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val AmoledPalette = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0E17),
    surfaceVariant = Color(0xFF141C2B),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF0369A1),
    secondary = Color(0xFF4F46E5),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF1E293B)
)

@Composable
fun LyricSyncTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val palette = when (themeMode) {
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
        ThemeMode.DARK -> DarkPalette
        ThemeMode.AMOLED -> AmoledPalette
        ThemeMode.LIGHT -> LightPalette
    }
    MaterialTheme(
        colorScheme = palette,
        content = content
    )
}
