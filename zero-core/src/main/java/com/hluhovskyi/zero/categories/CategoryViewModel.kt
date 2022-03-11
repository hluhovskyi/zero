package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.AttachableStateViewModel
import com.hluhovskyi.zero.common.Category

interface CategoryViewModel
    : AttachableStateViewModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {

    }

    data class State(
        val categories: List<Category> = emptyList()
    )
}