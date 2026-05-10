package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.OnColorSelectedHandler
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultIconPickerViewModel(
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onIconSelectedHandler: OnIconSelectedHandler,
    private val onColorSelectedHandler: OnColorSelectedHandler,
    private val colorId: Id = Id.Unknown,
    private val selectedIconId: Id = Id.Unknown,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : IconPickerViewModel {

    private val mutableState = MutableStateFlow(IconPickerViewModel.State())
    override val state: Flow<IconPickerViewModel.State> = mutableState

    override fun perform(action: IconPickerViewModel.Action) {
        when (action) {
            is IconPickerViewModel.Action.SelectIcon -> {
                onIconSelectedHandler.onIconSelected(action.icon, mutableState.value.selectedColorScheme)
            }
            is IconPickerViewModel.Action.SelectColorScheme -> {
                mutableState.update { it.copy(selectedColorScheme = action.colorScheme) }
                onColorSelectedHandler.onColorSelected(action.colorScheme.swatch, action.colorScheme)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            launch { loadColors() }
            launch { loadIcons() }
        }
    }

    private suspend fun loadColors() {
        colorRepository.query(ColorRepository.Criteria.AllSchemes())
            .collectLatest { schemes ->
                val selectedScheme = (colorId as? Id.Known)
                    ?.let { colorRepository.schemeFor(it) }
                    ?: ColorScheme.Grey

                mutableState.update { state ->
                    state.copy(
                        colorSchemes = schemes,
                        selectedColorScheme = selectedScheme,
                    )
                }
            }
    }

    private suspend fun loadIcons() {
        iconRepository.query(IconRepository.Criteria.All())
            .collectLatest { icons ->
                val sections = icons
                    .groupBy { it.category }
                    .filter { (category, _) -> category != IconCategory.system() }
                    .map { (category, categoryIcons) -> IconPickerSection(category, categoryIcons) }
                val selectedIcon = (selectedIconId as? Id.Known)
                    ?.let { id -> icons.find { it.id == id } }

                mutableState.update { state ->
                    state.copy(
                        sections = sections,
                        selectedIcon = selectedIcon,
                    )
                }
            }
    }
}
