package com.hluhovskyi.zero.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun IconPickerView(
    viewModel: IconPickerViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = IconPickerViewModel.State())

    IconAndColorPicker(
        sections = state.sections,
        colorSchemes = state.colorSchemes,
        selectedIcon = state.selectedIcon,
        selectedColorScheme = state.selectedColorScheme,
        imageLoader = imageLoader,
        onIconSelected = { icon ->
            viewModel.perform(IconPickerViewModel.Action.SelectIcon(icon))
        },
        onColorSchemeSelected = { scheme ->
            viewModel.perform(IconPickerViewModel.Action.SelectColorScheme(scheme))
        },
    )
}
