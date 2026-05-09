package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image

interface CategoryEditViewModel : AttachableActionStateModel<CategoryEditViewModel.Action, CategoryEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        object SelectIcon : Action
        object SelectColor : Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val icon: Image = Image.empty(),
        val colorScheme: ColorScheme = ColorScheme(
            swatch = Color.empty(),
            primary = Color.empty(),
            background = Color.empty(),
        ),
    )
}
