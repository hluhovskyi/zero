package com.hluhovskyi.zero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ZeroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val zeroColors = if (darkTheme) DarkZeroColors else LightZeroColors
    val materialColors = if (darkTheme) {
        darkColors(
            primary = zeroColors.inversePrimary,
            primaryVariant = zeroColors.primaryContainer,
            secondary = zeroColors.secondaryContainer,
            background = zeroColors.inverseSurface,
            surface = zeroColors.inverseSurface,
            error = zeroColors.error,
            onPrimary = zeroColors.primary,
            onSecondary = zeroColors.onSecondaryContainer,
            onBackground = zeroColors.inverseOnSurface,
            onSurface = zeroColors.inverseOnSurface,
            onError = zeroColors.onError,
        )
    } else {
        lightColors(
            primary = zeroColors.primaryContainer,
            primaryVariant = zeroColors.primary,
            secondary = zeroColors.secondary,
            secondaryVariant = zeroColors.secondaryContainer,
            background = zeroColors.surface,
            surface = zeroColors.surface,
            error = zeroColors.error,
            onPrimary = zeroColors.onPrimary,
            onSecondary = zeroColors.onSecondary,
            onBackground = zeroColors.onSurface,
            onSurface = zeroColors.onSurface,
            onError = zeroColors.onError,
        )
    }
    CompositionLocalProvider(LocalZeroColors provides zeroColors) {
        MaterialTheme(
            colors = materialColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
