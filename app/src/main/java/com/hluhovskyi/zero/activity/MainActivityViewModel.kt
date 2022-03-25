package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.activity.screens.Destination
import com.hluhovskyi.zero.common.ActionStateModel

internal interface MainActivityViewModel
    : ActionStateModel<MainActivityViewModel.Action, MainActivityViewModel.State> {

    sealed interface Action {
        data class BottomNavigationItemSelect(val item: BottomNavigation.Item) : Action
    }

    data class State(
        val currentDestination: Destination = Destination.Transaction.All,
        val bottomNavigation: BottomNavigation = BottomNavigation.None
    )
}