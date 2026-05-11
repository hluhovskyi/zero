package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconPickerSection

interface CategoryEditViewModel : AttachableActionStateModel<CategoryEditViewModel.Action, CategoryEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        object TogglePicker : Action
        data class PickIcon(val icon: Icon) : Action
        data class PickColorScheme(val colorScheme: ColorScheme) : Action
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
        val pickerVisible: Boolean = false,
        val iconSections: List<IconPickerSection> = emptyList(),
        val colorSchemes: List<ColorScheme> = emptyList(),
        val selectedIcon: Icon? = null,
    )
}
