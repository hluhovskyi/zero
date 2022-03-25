package com.hluhovskyi.zero.activity.screens

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.activity.BottomNavigation

@Composable
internal fun MainBottomNavigation(
    currentDestination: Destination,
    bottomNavigation: BottomNavigation,
    imageLoader: ImageLoader,
    onItemClick: (BottomNavigation.Item) -> Unit
) {
    if (bottomNavigation is BottomNavigation.WithItems) {
        BottomNavigation {
            bottomNavigation.items.forEach { item ->
                BottomNavigationItem(
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(0.4f),
                    alwaysShowLabel = true,
                    selected = item.destination == currentDestination,
                    onClick = { onItemClick(item) },
                    icon = {
                        imageLoader.View(
                            image = item.icon,
                            modifier = Modifier.sizeIn(maxHeight = 24.dp)
                        )
                    },
                    label = { Text(text = item.name) }
                )
            }
        }
    }
}