package com.hluhovskyi.zero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val LightColorPalette = lightColors(
    primary = PrimaryContainer,
    primaryVariant = Primary,
    secondary = Secondary,
    secondaryVariant = SecondaryContainer,
    background = Surface,
    surface = Surface,
    error = Error,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onError = OnError,
)

private val DarkColorPalette = darkColors(
    primary = InversePrimary,
    primaryVariant = PrimaryContainer,
    secondary = SecondaryContainer,
    background = InverseSurface,
    surface = InverseSurface,
    error = Error,
    onPrimary = Primary,
    onSecondary = OnSecondaryContainer,
    onBackground = InverseOnSurface,
    onSurface = InverseOnSurface,
    onError = OnError,
)

@Composable
fun ZeroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
