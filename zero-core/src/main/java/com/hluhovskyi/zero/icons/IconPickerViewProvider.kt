package com.hluhovskyi.zero.icons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider

internal class IconPickerViewProvider(
    private val viewModel: IconPickerViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        IconPickerView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconPickerView(
    viewModel: IconPickerViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = IconPickerViewModel.State())
    LazyVerticalGrid(cells = GridCells.Fixed(4)) {
        items(state.icons) { item ->
            imageLoader.View(
                modifier = Modifier.clickable {
                    viewModel.perform(IconPickerViewModel.Action.SelectIcon(item))
                },
                image = item.image
            )
        }
    }
}