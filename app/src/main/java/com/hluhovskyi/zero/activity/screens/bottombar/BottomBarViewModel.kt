package com.hluhovskyi.zero.activity.screens.bottombar

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

internal interface BottomBarViewModel : AttachableActionStateModel<BottomBarViewModel.Action, BottomBarViewModel.State> {

    sealed interface Action {
        data class SelectItem(val item: Item) : Action
    }

    data class State(val items: List<Item> = emptyList())

    data class Item(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val selected: Boolean,
        val hasAlert: Boolean = false,
    )
}
