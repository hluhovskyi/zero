package com.hluhovskyi.zero.activity.screens.bottombar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider

internal class BottomBarViewProvider(
    private val viewModel: BottomBarViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        BottomBarView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
internal fun BottomBarView(
    viewModel: BottomBarViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = BottomBarViewModel.State())
    AnimatedVisibility(
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it }
        ),
        exit = fadeOut() + slideOutVertically(
            targetOffsetY = { it }
        ),
        visible = state.items.isNotEmpty()
    ) {
        BottomNavigation {
            state.items.forEach { item ->
                BottomNavigationItem(
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(0.4f),
                    alwaysShowLabel = true,
                    selected = item.selected,
                    onClick = { viewModel.perform(BottomBarViewModel.Action.SelectItem(item)) },
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