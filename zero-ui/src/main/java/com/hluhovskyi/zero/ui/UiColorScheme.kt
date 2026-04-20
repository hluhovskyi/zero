package com.hluhovskyi.zero.ui

import androidx.compose.ui.graphics.Color

data class UiColorScheme(
    val primary: Color,
    val background: Color,
) {
    companion object {
        fun default(): UiColorScheme = UiColorScheme(
            primary = Color(0xFF8E8E93),
            background = Color(0xFFE5E5EA),
        )
    }
}
