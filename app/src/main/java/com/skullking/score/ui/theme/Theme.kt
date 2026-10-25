package com.skullking.score.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF10222E)
private val Teal = Color(0xFF1F6F78)
private val Gold = Color(0xFFC9A24A)
private val Crimson = Color(0xFF9E2A2B)
private val Parchment = Color(0xFFFBF6EC)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Navy,
    tertiary = Crimson,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E2A8),
    onTertiaryContainer = Navy,
    background = Parchment,
    onBackground = Navy,
    surface = Parchment,
    onSurface = Navy,
    surfaceVariant = Color(0xFFEFE6D2),
    onSurfaceVariant = Color(0xFF4A4A3F),
    error = Crimson,
)

// Thème sombre : marine profond, surfaces étagées pour distinguer cartes et fonds, accents turquoise et or.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD0D8),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF00505A),
    onPrimaryContainer = Color(0xFFA2F0F7),
    secondary = Color(0xFFE2BC62),
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF54410A),
    onSecondaryContainer = Color(0xFFFFE19A),
    tertiary = Color(0xFFFF9A8F),
    onTertiary = Color(0xFF4A0A0C),
    tertiaryContainer = Color(0xFF6B2A27),
    onTertiaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0B1620),
    onBackground = Color(0xFFE7E2D4),
    surface = Color(0xFF0B1620),
    onSurface = Color(0xFFE7E2D4),
    surfaceVariant = Color(0xFF1F3342),
    onSurfaceVariant = Color(0xFFBFC8CC),
    surfaceContainerLowest = Color(0xFF08121A),
    surfaceContainerLow = Color(0xFF101E29),
    surfaceContainer = Color(0xFF142532),
    surfaceContainerHigh = Color(0xFF1B2F3E),
    surfaceContainerHighest = Color(0xFF223A4B),
    outline = Color(0xFF7C9099),
    outlineVariant = Color(0xFF33495A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF561E19),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Couleur d'un score négatif ou d'une annonce ratée. */
val ScoreNegative: Color
    @Composable get() = MaterialTheme.colorScheme.error

/** Le thème suit celui d'Android : aucun réglage propre à l'application. */
@Composable
fun SkullKingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
