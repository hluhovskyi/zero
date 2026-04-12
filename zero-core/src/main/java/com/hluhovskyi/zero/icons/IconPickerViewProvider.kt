package com.hluhovskyi.zero.icons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi

internal class IconPickerViewProvider(
    private val viewModel: IconPickerViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        IconPickerView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconPickerView(
    viewModel: IconPickerViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = IconPickerViewModel.State())
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.icons) { item ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { viewModel.perform(IconPickerViewModel.Action.SelectIcon(item)) },
                contentAlignment = Alignment.Center,
            ) {
                IconCell(
                    item = item,
                    isSelected = item == state.selectedIcon,
                    colorScheme = state.colorScheme,
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun IconCell(
    item: Icon,
    isSelected: Boolean,
    colorScheme: ColorScheme?,
    imageLoader: ImageLoader,
) {
    if (colorScheme != null) {
        CategoryIconView(
            colorScheme = colorScheme.toUi(),
            size = 48.dp,
            contentPadding = 10.dp,
            isSelected = isSelected,
        ) { tint ->
            imageLoader.View(
                modifier = Modifier.size(28.dp),
                image = item.image,
                tint = tint,
            )
        }
    } else {
        val primary = MaterialTheme.colors.primary
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, primary, shape).padding(2.dp)
                    } else {
                        Modifier.padding(4.dp)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.size(48.dp),
                image = item.image,
            )
        }
    }
}
