package com.hluhovskyi.zero.activity.screens.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

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
            backgroundColor = ZeroTheme.colors.surfaceContainerLowest,
            elevation = 0.dp,
        ) {
            state.items.forEach { item ->
                BottomNavigationItem(
                    selectedContentColor = ZeroTheme.colors.primary,
                    unselectedContentColor = ZeroTheme.colors.outline,
                    alwaysShowLabel = true,
                    selected = item.selected,
                    onClick = { viewModel.perform(BottomBarViewModel.Action.SelectItem(item)) },
                    icon = {
                        val iconTint = if (item.selected) ZeroTheme.colors.primary else ZeroTheme.colors.outline
                        val pillColor = if (item.selected) ZeroTheme.colors.selectedPill else Color.Transparent
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(width = 56.dp, height = 32.dp)
                                    .background(pillColor, RoundedCornerShape(50)),
                            )
                            Box {
                                imageLoader.View(
                                    image = item.icon,
                                    modifier = Modifier.sizeIn(maxHeight = 24.dp),
                                    tint = iconTint,
                                )
                                if (item.hasAlert) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 5.dp, y = (-5).dp)
                                            .size(13.dp)
                                            .background(ZeroTheme.colors.surfaceContainerLowest, CircleShape)
                                            .padding(2.dp)
                                            .background(ZeroTheme.colors.error, CircleShape)
                                            .semantics { contentDescription = "Over budget" },
                                    )
                                }
                            }
                        }
                    },
                    label = { Text(text = item.name) },
                )
            }
        }
    }
}
