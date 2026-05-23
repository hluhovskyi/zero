package com.hluhovskyi.zero.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ZeroColors(
    val primary: Color,
    val primaryContainer: Color,
    val primaryContainerLight: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondary: Color,
    val onSecondaryContainer: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val errorContainer: Color,
    val onError: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val selectedPill: Color,
    val scrim: Color,
    val transactionExpense: Color,
    val transactionIncome: Color,
    val importMergeContainer: Color,
    val importNewContainer: Color,
    val importNewContent: Color,
    val importErrorContainer: Color,
    val importErrorContent: Color,
    val welcomeCardLine: Color,
    val isLight: Boolean,
)

val LightZeroColors = ZeroColors(
    primary = Color(0xFF000E2F),
    primaryContainer = Color(0xFF0A2351),
    primaryContainerLight = Color(0xFFC8D8FE),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF778BBF),
    secondary = Color(0xFF006C4A),
    secondaryContainer = Color(0xFF82F5C1),
    onSecondary = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF00714E),
    surface = Color(0xFFFAF8FD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F7),
    surfaceContainer = Color(0xFFEFEDF2),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F5),
    inversePrimary = Color(0xFFB1C6FD),
    selectedPill = Color(0xFFD9E2FF),
    scrim = Color(0x52000000),
    transactionExpense = Color(0xFFBA1A1A),
    transactionIncome = Color(0xFF006C4A),
    importMergeContainer = Color(0xFFE8EEFF),
    importNewContainer = Color(0xFFE8F5E9),
    importNewContent = Color(0xFF1B5E20),
    importErrorContainer = Color(0xFFFFEBEE),
    importErrorContent = Color(0xFF93000A),
    welcomeCardLine = Color(0xFFFFFFFF),
    isLight = true,
)

// TODO(dark-mode): tune these to real dark values. Mirroring light for now keeps
// the wiring honest without committing to a palette before design lands.
val DarkZeroColors = LightZeroColors.copy(isLight = false)

val LocalZeroColors = staticCompositionLocalOf<ZeroColors> {
    error("ZeroColors not provided — wrap content in ZeroTheme")
}

object ZeroTheme {
    val colors: ZeroColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZeroColors.current
}
