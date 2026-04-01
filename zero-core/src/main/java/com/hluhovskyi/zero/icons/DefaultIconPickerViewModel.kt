package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultIconPickerViewModel(
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onIconSelectedHandler: OnIconSelectedHandler,
    private val colorId: Id = Id.Unknown,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : IconPickerViewModel {

    private val mutableState = MutableStateFlow(IconPickerViewModel.State())
    override val state: Flow<IconPickerViewModel.State> = mutableState

    override fun perform(action: IconPickerViewModel.Action) {
        when (action) {
            is IconPickerViewModel.Action.SelectIcon -> {
                onIconSelectedHandler.onIconSelected(action.icon)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val colorScheme = (colorId as? Id.Known)
                ?.let { colorRepository.schemeFor(it) }
                ?: ColorScheme.Grey
            mutableState.update { it.copy(colorScheme = colorScheme) }
        }
        coroutineScope.launch {
            iconRepository.query(IconRepository.Criteria.All())
                .map { icons ->
                    icons.map { icon ->
                        Icon(
                            id = icon.id,
                            image = icon.image
                        )
                    }
                }
                .collectLatest { icons ->
                    mutableState.update { state ->
                        state.copy(icons = icons)
                    }
                }
        }
    }
}
