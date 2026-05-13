package com.hluhovskyi.zero.ui

import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.PrimaryContainer

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
    if (expanded) {
        ExtendedFloatingActionButton(
            modifier = modifier,
            icon = { Icon(icon, contentDescription = contentDescription) },
            text = { Text(text) },
            onClick = onClick,
            backgroundColor = PrimaryContainer,
            contentColor = Color.White,
            elevation = elevation,
        )
    } else {
        FloatingActionButton(
            modifier = modifier,
            onClick = onClick,
            backgroundColor = PrimaryContainer,
            contentColor = Color.White,
            elevation = elevation,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
