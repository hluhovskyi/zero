package com.hluhovskyi.zero.activity.screens.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
            imageLoader = imageLoader,
        )
    }
}

@Composable
internal fun BottomBarView(
    viewModel: BottomBarViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = BottomBarViewModel.State())
    if (state.items.isNotEmpty()) {
        BottomNavigation(
            modifier = Modifier.height(72.dp),
            backgroundColor = Color(0xFFFFFFFF),
            elevation = 0.dp,
        ) {
            state.items.forEach { item ->
                BottomNavigationItem(
                    selectedContentColor = Color(0xFF000E2F),
                    unselectedContentColor = Color(0xFF757780),
                    alwaysShowLabel = true,
                    selected = item.selected,
                    onClick = { viewModel.perform(BottomBarViewModel.Action.SelectItem(item)) },
                    icon = {
                        val iconTint = if (item.selected) Color(0xFF000E2F) else Color(0xFF757780)
                        val pillColor = if (item.selected) Color(0xFFD9E2FF) else Color.Transparent
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(width = 56.dp, height = 32.dp)
                                    .background(pillColor, RoundedCornerShape(50)),
                            )
                            imageLoader.View(
                                image = item.icon,
                                modifier = Modifier.sizeIn(maxHeight = 24.dp),
                                tint = iconTint,
                            )
                        }
                    },
                    label = { Text(text = item.name) },
                )
            }
        }
    }
}
