package com.hluhovskyi.zero.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand colors that have no Material 3 role. Standard roles (primary, surface, outline, …) live in
 * the [ColorScheme]; only these app-specific extras live here. Provided via [LocalZeroExtraColors].
 */
@Immutable
data class ZeroExtraColors(
    val primaryContainerLight: Color,
    val selectedPill: Color,
    val transactionExpense: Color,
    val transactionIncome: Color,
    val importMergeContainer: Color,
    val importNewContainer: Color,
    val importNewContent: Color,
    val importErrorContainer: Color,
    val importErrorContent: Color,
    val welcomeCardLine: Color,
    val chartCashIn: Color,
    val chartCashOut: Color,
    val chartHeroSurface: Color,
    val chartHeroContent: Color,
    val chartHeroContentDim: Color,
    val isLight: Boolean,
)

val LightZeroColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF000E2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A2351),
    onPrimaryContainer = Color(0xFF778BBF),
    inversePrimary = Color(0xFFB1C6FD),
    secondary = Color(0xFF006C4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF82F5C1),
    onSecondaryContainer = Color(0xFF00714E),
    background = Color(0xFFFAF8FD),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAF8FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFEFEDF2),
    onSurfaceVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F7),
    surfaceContainer = Color(0xFFEFEDF2),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F5),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    scrim = Color(0x52000000),
)

val DarkZeroColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB1C6FD),
    onPrimary = Color(0xFF00132C),
    primaryContainer = Color(0xFF2D4B7E),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF000E2F),
    secondary = Color(0xFF65D9A6),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF005237),
    onSecondaryContainer = Color(0xFF82F5C1),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF22252D),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFF1B1D24),
    surfaceContainerLow = Color(0xFF181A20),
    surfaceContainer = Color(0xFF22252D),
    surfaceContainerHigh = Color(0xFF2A2D35),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF303034),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF44464F),
    error = Color(0xFFE5564C),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF5A1F1B),
    scrim = Color(0x52000000),
)

val LightZeroExtraColors = ZeroExtraColors(
    primaryContainerLight = Color(0xFFC8D8FE),
    selectedPill = Color(0xFFD9E2FF),
    transactionExpense = Color(0xFFBA1A1A),
    transactionIncome = Color(0xFF006C4A),
    importMergeContainer = Color(0xFFE8EEFF),
    importNewContainer = Color(0xFFE8F5E9),
    importNewContent = Color(0xFF1B5E20),
    importErrorContainer = Color(0xFFFFEBEE),
    importErrorContent = Color(0xFF93000A),
    welcomeCardLine = Color(0xFFFFFFFF),
    chartCashIn = Color(0xFF5DDBA8),
    chartCashOut = Color(0xFFEBA07C),
    chartHeroSurface = Color(0xFF1A2E52),
    chartHeroContent = Color(0xFFFFFFFF),
    chartHeroContentDim = Color(0x80FFFFFF),
    isLight = true,
)

val DarkZeroExtraColors = ZeroExtraColors(
    primaryContainerLight = Color(0xFF4F6FA8),
    selectedPill = Color(0xFF2D4B7E),
    transactionExpense = Color(0xFFE5564C),
    transactionIncome = Color(0xFF65D9A6),
    importMergeContainer = Color(0xFF15193A),
    importNewContainer = Color(0xFF0E2A12),
    importNewContent = Color(0xFF7FD18C),
    importErrorContainer = Color(0xFF3A0F12),
    importErrorContent = Color(0xFFFFB4AB),
    welcomeCardLine = Color(0xFFFFFFFF),
    chartCashIn = Color(0xFF5DDBA8),
    chartCashOut = Color(0xFFEBA07C),
    chartHeroSurface = Color(0xFF1A2E52),
    chartHeroContent = Color(0xFFFFFFFF),
    chartHeroContentDim = Color(0x80FFFFFF),
    isLight = false,
)

val LocalZeroExtraColors = staticCompositionLocalOf<ZeroExtraColors> {
    error("ZeroExtraColors not provided — wrap content in ZeroTheme")
}

/**
 * Read-through facade over the Material [ColorScheme] (standard roles) and [ZeroExtraColors] (brand
 * extras). Single source per color — nothing is copied. Access via `ZeroTheme.colors`.
 */
@Immutable
class ZeroColors(
    private val scheme: ColorScheme,
    private val extras: ZeroExtraColors,
) {
    val primary get() = scheme.primary
    val onPrimary get() = scheme.onPrimary
    val primaryContainer get() = scheme.primaryContainer
    val onPrimaryContainer get() = scheme.onPrimaryContainer
    val inversePrimary get() = scheme.inversePrimary
    val secondary get() = scheme.secondary
    val onSecondary get() = scheme.onSecondary
    val secondaryContainer get() = scheme.secondaryContainer
    val onSecondaryContainer get() = scheme.onSecondaryContainer
    val surface get() = scheme.surface
    val onSurface get() = scheme.onSurface
    val onSurfaceVariant get() = scheme.onSurfaceVariant
    val surfaceContainerLowest get() = scheme.surfaceContainerLowest
    val surfaceContainerLow get() = scheme.surfaceContainerLow
    val surfaceContainer get() = scheme.surfaceContainer
    val surfaceContainerHigh get() = scheme.surfaceContainerHigh
    val inverseSurface get() = scheme.inverseSurface
    val inverseOnSurface get() = scheme.inverseOnSurface
    val outline get() = scheme.outline
    val outlineVariant get() = scheme.outlineVariant
    val error get() = scheme.error
    val onError get() = scheme.onError
    val errorContainer get() = scheme.errorContainer
    val scrim get() = scheme.scrim

    val primaryContainerLight get() = extras.primaryContainerLight
    val selectedPill get() = extras.selectedPill
    val transactionExpense get() = extras.transactionExpense
    val transactionIncome get() = extras.transactionIncome
    val importMergeContainer get() = extras.importMergeContainer
    val importNewContainer get() = extras.importNewContainer
    val importNewContent get() = extras.importNewContent
    val importErrorContainer get() = extras.importErrorContainer
    val importErrorContent get() = extras.importErrorContent
    val welcomeCardLine get() = extras.welcomeCardLine
    val chartCashIn get() = extras.chartCashIn
    val chartCashOut get() = extras.chartCashOut
    val chartHeroSurface get() = extras.chartHeroSurface
    val chartHeroContent get() = extras.chartHeroContent
    val chartHeroContentDim get() = extras.chartHeroContentDim
    val isLight get() = extras.isLight
}

object ZeroTheme {
    val colors: ZeroColors
        @Composable
        @ReadOnlyComposable
        get() = ZeroColors(MaterialTheme.colorScheme, LocalZeroExtraColors.current)
}
