package com.hluhovskyi.zero.imports.categoriesreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.ImportCategory

interface CategoriesReviewViewModel : ActionStateModel<CategoriesReviewViewModel.Action, CategoriesReviewViewModel.State> {

    data class State(val categories: List<ImportCategory> = emptyList())

    sealed interface Action {
        object Next : Action
        object Back : Action
    }
}
