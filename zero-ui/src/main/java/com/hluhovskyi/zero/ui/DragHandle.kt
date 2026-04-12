package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant

@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    color: Color = OnSurfaceVariant.copy(alpha = 0.4f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(color = color, shape = CircleShape),
        )
    }
}
