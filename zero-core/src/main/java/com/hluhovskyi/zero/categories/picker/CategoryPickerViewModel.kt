package com.hluhovskyi.zero.categories.picker

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryPickerViewModel
    : AttachableActionStateModel<CategoryPickerViewModel.Action, CategoryPickerViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryPickerItem) : Action
    }

    data class State(
        val categories: List<CategoryPickerItem> = emptyList()
    )

    data class CategoryPickerItem(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    )
}
