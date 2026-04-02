package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
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

@Composable
fun CategoryIconView(
    color: Color,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(color, shape = RoundedCornerShape(percent = 30))
            .size(size)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun CategoryIconView(
    colorScheme: UiColorScheme,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable (iconTint: UiColor) -> Unit,
) {
    CategoryIconView(
        color = colorScheme.background.value,
        size = size,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        content(colorScheme.primary)
    }
}
