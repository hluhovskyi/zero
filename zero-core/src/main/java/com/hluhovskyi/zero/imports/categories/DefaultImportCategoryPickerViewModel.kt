package com.hluhovskyi.zero.imports.categories

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultImportCategoryPickerViewModel(
    private val importUseCase: ImportUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : ImportCategoryPickerViewModel {

    private val mutableState = MutableStateFlow(ImportCategoryPickerViewModel.State())
    override val state: Flow<ImportCategoryPickerViewModel.State> = mutableState

    override fun perform(action: ImportCategoryPickerViewModel.Action) {
        when (action) {
            is ImportCategoryPickerViewModel.Action.ChangeSelection -> {
                mutableState.update { state ->
                    state.copy(
                        items = state.items.map { category ->
                            if (category.id == action.item.id) {
                                category.copy(
                                    selected = !category.selected
                                )
                            } else {
                                category
                            }
                        }
                    )
                }
            }

            is ImportCategoryPickerViewModel.Action.Submit -> {
                importUseCase.perform(ImportUseCase.Action.SelectCategories(
                    categoryIds = mutableState.value.items
                        .filter { it.selected }
                        .map { it.id }
                ))
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            importUseCase.state
                .map { state ->
                    if (state is ImportUseCase.State.CategoriesPicker) {
                        state.categories.map { category ->
                            ImportCategoryPickerViewModel.CategoryItem(
                                id = category.id,
                                name = category.name,
                                selected = true
                            )
                        }
                    } else {
                        emptyList()
                    }
                }
                .collectLatest { categories ->
                    mutableState.update { state ->
                        state.copy(items = categories)
                    }
                }
        }
    }
}
