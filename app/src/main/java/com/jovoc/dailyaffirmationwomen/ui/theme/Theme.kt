package com.jovoc.dailyaffirmationwomen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ============================================================================
// Material 3 Color Schemes
// Strictly adheres to AGENT_INSTRUCTIONS.md Section 7:
// - Soft neutral backgrounds (warm linen/cream for light, deep slate for dark)
// - Calm accent color (gentle sage green, strictly not pink)
// - High contrast meeting WCAG AA (>= 4.5:1 for body text)
// ============================================================================

val LightColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = SageOnPrimary,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = SageOnPrimaryContainer,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageSecondaryContainer,
    onSecondaryContainer = SageOnSecondaryContainer,
    background = CreamBackground,
    onBackground = CharcoalPrimaryText,
    surface = CreamSurface,
    onSurface = CharcoalPrimaryText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = CharcoalSecondaryText,
    outline = MutedBorder,
    outlineVariant = MutedBorderSubtle
)

val DarkColorScheme = darkColorScheme(
    primary = SageDarkPrimary,
    onPrimary = SageDarkOnPrimary,
    primaryContainer = SageDarkPrimaryContainer,
    onPrimaryContainer = SageDarkOnPrimaryContainer,
    secondary = SageDarkSecondary,
    onSecondary = SageDarkOnSecondary,
    secondaryContainer = SageDarkSecondaryContainer,
    onSecondaryContainer = SageDarkOnSecondaryContainer,
    background = SlateBackground,
    onBackground = CreamDarkPrimaryText,
    surface = SlateSurface,
    onSurface = CreamDarkPrimaryText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = CreamDarkSecondaryText,
    outline = SlateBorder,
    outlineVariant = SlateBorderSubtle
)

// Sleep Mode Color Scheme: Ultra-dim dark screen for bedtime paper timer
val SleepColorScheme = darkColorScheme(
    primary = SleepAccent,
    onPrimary = SleepOnAccent,
    primaryContainer = SleepSurfaceVariant,
    onPrimaryContainer = SleepTextPrimary,
    secondary = SleepAccent,
    onSecondary = SleepOnAccent,
    secondaryContainer = SleepSurfaceVariant,
    onSecondaryContainer = SleepTextSecondary,
    background = SleepBackground,
    onBackground = SleepTextPrimary,
    surface = SleepSurface,
    onSurface = SleepTextPrimary,
    surfaceVariant = SleepSurfaceVariant,
    onSurfaceVariant = SleepTextSecondary,
    outline = SleepOutline,
    outlineVariant = SleepOutlineVariant
)

/**
 * Standard app theme supporting system light and dark modes.
 */
@Composable
fun DailyAffirmationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Dedicated sleep mode theme for the Bedtime Unload / Paper Timer screen.
 * AGENT_INSTRUCTIONS.md Section 3.3 & Section 7:
 * "The sleep timer screen is always dark and dim."
 */
@Composable
fun SleepTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SleepColorScheme,
        typography = Typography,
        content = content
    )
}
