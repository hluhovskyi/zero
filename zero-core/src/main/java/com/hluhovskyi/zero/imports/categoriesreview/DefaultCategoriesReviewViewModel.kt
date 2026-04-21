package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultCategoriesReviewViewModel(
    private val importUseCase: ImportUseCase,
) : CategoriesReviewViewModel {

    override val state: Flow<CategoriesReviewViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.CategoriesReview>()
        .map { CategoriesReviewViewModel.State(categories = it.categories, excludedIds = it.excludedIds) }

    override fun perform(action: CategoriesReviewViewModel.Action) {
        when (action) {
            is CategoriesReviewViewModel.Action.Next ->
                importUseCase.perform(ImportUseCase.Action.ConfirmCategories)
            is CategoriesReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
            is CategoriesReviewViewModel.Action.ToggleCategory ->
                importUseCase.perform(ImportUseCase.Action.ToggleCategory(action.id))
        }
    }
}
