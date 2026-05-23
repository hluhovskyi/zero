package com.hluhovskyi.zero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
fun ZeroFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        backgroundColor = ZeroTheme.colors.primaryContainer,
        contentColor = ZeroTheme.colors.onPrimary,
        elevation = elevation,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = if (expanded) 20.dp else 0.dp),
        ) {
            Icon(icon, contentDescription = contentDescription)
            AnimatedVisibility(visible = expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text)
                }
            }
        }
    }
}
