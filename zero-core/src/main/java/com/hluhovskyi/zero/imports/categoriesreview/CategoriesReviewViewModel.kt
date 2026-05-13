package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.imports.ImportCategory
import com.hluhovskyi.zero.imports.ResolveStrategy

interface CategoriesReviewViewModel : ActionStateModel<CategoriesReviewViewModel.Action, CategoriesReviewViewModel.State> {

    data class State(
        val categories: List<ImportCategory> = emptyList(),
        val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    )

    sealed interface Action {
        object Next : Action
        object Back : Action
        data class SetStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
    }
}
