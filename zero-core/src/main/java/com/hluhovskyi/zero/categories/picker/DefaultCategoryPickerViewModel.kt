package com.hluhovskyi.zero.categories.picker

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.OnCategorySelectedHandler
import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryPickerViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CategoryPickerViewModel {

    private val mutableState = MutableStateFlow(CategoryPickerViewModel.State())
    override val state: Flow<CategoryPickerViewModel.State> = mutableState

    override fun perform(action: CategoryPickerViewModel.Action) {
        when (action) {
            is CategoryPickerViewModel.Action.SelectCategory -> coroutineScope.launch(context = Dispatchers.Main) {
                onCategorySelectedHandler.onSelected(action.category.id)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            categoriesQueryUseCase.queryAll()
                .map { categories ->
                    categories
                        .sortedBy { it.name }
                        .map { category ->
                            CategoryPickerViewModel.CategoryPickerItem(
                                id = category.id,
                                name = category.name,
                                icon = category.icon,
                                colorScheme = category.colorScheme,
                            )
                        }
                }
                .collectLatest { categories ->
                    mutableState.update { state ->
                        state.copy(categories = categories)
                    }
                }
        }
    }
}
