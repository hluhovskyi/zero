package com.hluhovskyi.zero.colors

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.mapping.toCompose

internal class ColorPickerViewProvider(
    private val viewModel: ColorPickerViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        ColorPickerView(viewModel = viewModel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorPickerView(
    viewModel: ColorPickerViewModel
) {
    val state by viewModel.state.collectAsState(initial = ColorPickerViewModel.State())
    LazyVerticalGrid(columns = GridCells.Fixed(5)) {
        items(state.colors) { item ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(item.value.toCompose())
                    .clickable {
                        viewModel.perform(ColorPickerViewModel.Action.SelectColor(item))
                    }
            )
        }
    }
}