package com.hluhovskyi.zero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ZeroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val zeroColors = if (darkTheme) DarkZeroColors else LightZeroColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = zeroColors.primary,
            onPrimary = zeroColors.onPrimary,
            primaryContainer = zeroColors.primaryContainer,
            onPrimaryContainer = zeroColors.onPrimaryContainer,
            inversePrimary = zeroColors.inversePrimary,
            secondary = zeroColors.secondary,
            onSecondary = zeroColors.onSecondary,
            secondaryContainer = zeroColors.secondaryContainer,
            onSecondaryContainer = zeroColors.onSecondaryContainer,
            background = zeroColors.surface,
            onBackground = zeroColors.onSurface,
            surface = zeroColors.surface,
            onSurface = zeroColors.onSurface,
            surfaceVariant = zeroColors.surfaceContainer,
            onSurfaceVariant = zeroColors.onSurfaceVariant,
            surfaceContainerLowest = zeroColors.surfaceContainerLowest,
            surfaceContainerLow = zeroColors.surfaceContainerLow,
            surfaceContainer = zeroColors.surfaceContainer,
            surfaceContainerHigh = zeroColors.surfaceContainerHigh,
            inverseSurface = zeroColors.inverseSurface,
            inverseOnSurface = zeroColors.inverseOnSurface,
            outline = zeroColors.outline,
            outlineVariant = zeroColors.outlineVariant,
            error = zeroColors.error,
            onError = zeroColors.onError,
            errorContainer = zeroColors.errorContainer,
            scrim = zeroColors.scrim,
        )
    } else {
        lightColorScheme(
            primary = zeroColors.primary,
            onPrimary = zeroColors.onPrimary,
            primaryContainer = zeroColors.primaryContainer,
            onPrimaryContainer = zeroColors.onPrimaryContainer,
            inversePrimary = zeroColors.inversePrimary,
            secondary = zeroColors.secondary,
            onSecondary = zeroColors.onSecondary,
            secondaryContainer = zeroColors.secondaryContainer,
            onSecondaryContainer = zeroColors.onSecondaryContainer,
            background = zeroColors.surface,
            onBackground = zeroColors.onSurface,
            surface = zeroColors.surface,
            onSurface = zeroColors.onSurface,
            surfaceVariant = zeroColors.surfaceContainer,
            onSurfaceVariant = zeroColors.onSurfaceVariant,
            surfaceContainerLowest = zeroColors.surfaceContainerLowest,
            surfaceContainerLow = zeroColors.surfaceContainerLow,
            surfaceContainer = zeroColors.surfaceContainer,
            surfaceContainerHigh = zeroColors.surfaceContainerHigh,
            inverseSurface = zeroColors.inverseSurface,
            inverseOnSurface = zeroColors.inverseOnSurface,
            outline = zeroColors.outline,
            outlineVariant = zeroColors.outlineVariant,
            error = zeroColors.error,
            onError = zeroColors.onError,
            errorContainer = zeroColors.errorContainer,
            scrim = zeroColors.scrim,
        )
    }
    CompositionLocalProvider(LocalZeroColors provides zeroColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
