package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultColorPickerViewModel(
    private val colorRepository: ColorRepository,
    private val onColorSelectedHandler: OnColorSelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : ColorPickerViewModel {

    private val mutableState = MutableStateFlow(ColorPickerViewModel.State())
    override val state: Flow<ColorPickerViewModel.State> = mutableState

    override fun perform(action: ColorPickerViewModel.Action) {
        when (action) {
            is ColorPickerViewModel.Action.SelectColor -> {
                onColorSelectedHandler.onColorSelected(
                    action.color,
                    colorRepository.schemeFor(action.color.id),
                )
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            colorRepository.query(ColorRepository.Criteria.All())
                .collectLatest { colors ->
                    mutableState.update { state ->
                        state.copy(
                            colors = colors,
                        )
                    }
                }
        }
    }
}
