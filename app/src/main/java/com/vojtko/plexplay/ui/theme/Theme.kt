package com.vojtko.plexplay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PlexGold,
    onPrimary = Color.Black,
    secondary = PlexGoldDim,
    background = PlexSlate,
    onBackground = PlexText,
    surface = PlexSlateSoft,
    onSurface = PlexText,
    onSurfaceVariant = PlexMuted,
    surfaceContainer = PlexPanel,
    surfaceContainerHigh = Color(0xFF272B35),
    outline = Color(0xFF4F5663),
    error = Color(0xFFFF7A6D)
)

private val LightColorScheme = lightColorScheme(
    primary = PlexGoldDim,
    onPrimary = Color.White,
    secondary = PlexGold,
    background = Color(0xFFF4F0E8),
    onBackground = Color(0xFF18191D),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF18191D),
    onSurfaceVariant = Color(0xFF5F6269),
    surfaceContainer = Color(0xFFE7E0D5),
    surfaceContainerHigh = Color(0xFFDDD6CA),
    outline = Color(0xFF8E877B),
    error = Color(0xFFB3261E)
)

@Composable
fun PlexPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
