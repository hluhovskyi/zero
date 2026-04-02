package com.hluhovskyi.zero.ui.mapping

import androidx.compose.ui.graphics.Color as ComposeColor
import com.hluhovskyi.zero.colors.Color as DomainColor
import com.hluhovskyi.zero.colors.ColorScheme as DomainColorScheme
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.ui.UiColorScheme

fun ColorValue.toCompose(): ComposeColor =
    if (isUnspecified()) {
        ComposeColor.Unspecified
    } else {
        ComposeColor(hex.toInt())
    }

fun DomainColor.toUi(): ComposeColor = value.toCompose()

fun DomainColorScheme.toUi(): UiColorScheme = UiColorScheme(
    primary = primary.toUi(),
    background = background.toUi()
)
