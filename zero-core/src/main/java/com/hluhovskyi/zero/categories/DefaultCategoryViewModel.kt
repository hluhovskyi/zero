package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CategoryViewModel {

    private val mutableState = MutableStateFlow(CategoryViewModel.State())
    override val state: Flow<CategoryViewModel.State> = mutableState

    override fun perform(action: CategoryViewModel.Action) {
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                categoryRepository.query(CategoryRepository.Criteria.All()),
                iconRepository.query(IconRepository.Criteria.All())
                    .onStartWithEmptyList()
                    .onEmptyReturnEmptyList()
                    .map { icons -> icons.associateBy { it.id } }
            ) { categories, idToIcons ->
                categories.map { category ->
                    resolve(
                        category = category,
                        idToIcons = idToIcons
                    )
                }
            }.collectLatest { categories ->
                mutableState.update { state ->
                    state.copy(categories = categories)
                }
            }
        }
    }

    private fun resolve(
        category: CategoryRepository.Category,
        idToIcons: Map<Id.Known, IconRepository.Icon>
    ): CategoryViewModel.CategoryItem {
        val icon = idToIcons[category.iconId] ?: IconRepository.Icon.empty()
        return CategoryViewModel.CategoryItem(
            id = category.id,
            name = category.name,
            icon = icon.image
        )
    }
}