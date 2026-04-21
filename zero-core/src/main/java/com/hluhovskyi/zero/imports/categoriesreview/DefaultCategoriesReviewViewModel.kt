package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update

internal class DefaultCategoriesReviewViewModel(
    private val importUseCase: ImportUseCase,
) : CategoriesReviewViewModel {

    private val excludedIds = MutableStateFlow<Set<Id.Known>>(emptySet())

    override val state: Flow<CategoriesReviewViewModel.State> = combine(
        importUseCase.state.filterIsInstance<ImportUseCase.State.CategoriesReview>(),
        excludedIds,
    ) { useCaseState, excluded ->
        CategoriesReviewViewModel.State(
            categories = useCaseState.categories,
            excludedIds = excluded,
        )
    }

    override fun perform(action: CategoriesReviewViewModel.Action) {
        when (action) {
            is CategoriesReviewViewModel.Action.Next ->
                importUseCase.perform(ImportUseCase.Action.ConfirmCategories(excludedIds = excludedIds.value))
            is CategoriesReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
            is CategoriesReviewViewModel.Action.ToggleCategory ->
                excludedIds.update { current ->
                    if (action.id in current) current - action.id else current + action.id
                }
        }
    }
}
