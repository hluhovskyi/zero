package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.AttachableStateViewModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryViewModel
    : AttachableStateViewModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {

    }

    data class State(
        val categories: List<CategoryItem> = emptyList()
    )

    data class CategoryItem(
        val id: Id.Known,
        val name: String,
        val icon: Image
    )
}