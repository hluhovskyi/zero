package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.OnSurface

@Composable
fun CategoryCard(
    name: String,
    modifier: Modifier = Modifier,
    colorScheme: UiColorScheme? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CategoryIconView(
            colorScheme = colorScheme ?: UiColorScheme.default(),
            size = 40.dp,
            contentPadding = 8.dp,
        ) { iconTint ->
            icon?.invoke(iconTint)
        }
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
