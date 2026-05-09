package com.hluhovskyi.zero.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
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

@Composable
private fun IconPickerView(
    viewModel: IconPickerViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = IconPickerViewModel.State())

    // Pre-compute the UI list once per state so indexOf comparisons are stable.
    val uiColorSchemes = state.colorSchemes.map { it.toUi() }
    val uiSelectedColorScheme = state.selectedColorScheme?.toUi()

    IconAndColorPicker(
        sections = state.sections,
        colorSchemes = uiColorSchemes,
        selectedIcon = state.selectedIcon,
        selectedColorScheme = uiSelectedColorScheme,
        imageLoader = imageLoader,
        onIconSelected = { icon ->
            viewModel.perform(IconPickerViewModel.Action.SelectIcon(icon))
        },
        onColorSchemeSelected = { uiScheme ->
            // Reverse-map UiColorScheme → domain ColorScheme by index (same source order).
            val index = uiColorSchemes.indexOf(uiScheme)
            if (index != -1) {
                state.colorSchemes.getOrNull(index)?.let { domainScheme ->
                    viewModel.perform(IconPickerViewModel.Action.SelectColorScheme(domainScheme))
                }
            }
        },
    )
}
