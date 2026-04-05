package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Common icon container with a rounded-square background.
 * Supports a "selected" state with a double border.
 * We always allocate space for the selection border to avoid layout jumps.
 */
@Composable
fun CategoryIconView(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    isSelected: Boolean = false,
    primaryColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(percent = 30)

    Box(
        modifier = modifier
            .size(size + 8.dp)
            .then(
                if (isSelected) {
                    Modifier
                        .border(2.dp, primaryColor, shape)
                        .padding(2.dp)
                        .background(Color.White, shape)
                        .padding(2.dp)
                } else {
                    Modifier.padding(4.dp)
                }
            )
            .background(
                color = color,
                shape = shape
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Overload of [CategoryIconView] that uses [UiColorScheme].
 * Provides [iconTint] to the content lambda for convenient icon tinting.
 */
@Composable
fun CategoryIconView(
    colorScheme: UiColorScheme,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    isSelected: Boolean = false,
    content: @Composable (iconTint: Color) -> Unit,
) {
    CategoryIconView(
        color = colorScheme.background,
        modifier = modifier,
        size = size,
        contentPadding = contentPadding,
        isSelected = isSelected,
        primaryColor = colorScheme.primary,
    ) {
        content(colorScheme.primary)
    }
}
