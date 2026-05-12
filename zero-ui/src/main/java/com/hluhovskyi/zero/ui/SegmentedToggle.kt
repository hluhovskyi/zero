package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

@Composable
fun <T> SegmentedToggle(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    labelMapping: (T) -> String,
) {
    Row(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val label = labelMapping(item)

            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onItemSelected(item) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) SurfaceContainerLowest else Color.Transparent,
                elevation = if (isSelected) 2.dp else 0.dp,
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 10.dp),
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colors.primary
                    } else {
                        OnSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
