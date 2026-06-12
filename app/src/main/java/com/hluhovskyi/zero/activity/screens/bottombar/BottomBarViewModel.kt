package com.hluhovskyi.zero.activity.screens.bottombar

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri

internal interface BottomBarViewModel : AttachableActionStateModel<BottomBarViewModel.Action, BottomBarViewModel.State> {

    sealed interface Action {
        data class SelectItem(val item: Item) : Action
    }

    data class State(val items: List<Item> = emptyList())

    data class Item(
        val id: Id.Known,
        val iconUri: Uri,
        val selected: Boolean,
        val hasAlert: Boolean = false,
    )

    companion object {
        val HomeId = Id.Known("home")
        val AccountsId = Id.Known("accounts")
        val BudgetId = Id.Known("budget")
        val AnalyticsId = Id.Known("analytics")
        val SettingsId = Id.Known("settings")
    }
}
