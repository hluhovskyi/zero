package com.hluhovskyi.zero.categories

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

internal class DefaultCategoryViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CategoryViewModel {

    private val mutableState = MutableStateFlow(CategoryViewModel.State())
    override val state: Flow<CategoryViewModel.State> = mutableState

    override fun perform(action: CategoryViewModel.Action) {
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            categoriesQueryUseCase.queryAll()
                .map { categories ->
                    categories.map { category ->
                        CategoryViewModel.CategoryItem(
                            id = category.id,
                            name = category.name,
                            icon = category.icon
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