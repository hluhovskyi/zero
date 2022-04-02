package com.hluhovskyi.zero.imports.categories

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id

interface ImportCategoryPickerViewModel
    : AttachableActionStateModel<ImportCategoryPickerViewModel.Action, ImportCategoryPickerViewModel.State> {

    sealed interface Action {
        data class ChangeSelection(val item: CategoryItem) : Action
        object Submit : Action
    }

    data class State(
        val items: List<CategoryItem> = emptyList()
    )

    data class CategoryItem(
        val id: Id.Known,
        val name: String,
        val selected: Boolean,
    )
}