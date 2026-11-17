package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CrickEnglishColorScheme = lightColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.White,
    primaryContainer = PaleBlueSurface,
    onPrimaryContainer = NavyHeader,
    secondary = NavyHeader,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = NavyHeader,
    tertiary = LiveRed,
    onTertiary = Color.White,
    tertiaryContainer = LiveRedBg,
    onTertiaryContainer = LiveRed,
    background = LightBackground,
    onBackground = TextPrimarySlate,
    surface = ScorecardWhite,
    onSurface = TextPrimarySlate,
    surfaceVariant = ScorecardSubtle,
    onSurfaceVariant = TextSecondarySlate,
    outline = DividerSlate,
    outlineVariant = PaleBlueBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CrickEnglishColorScheme,
        typography = Typography,
        content = content
    )
}
