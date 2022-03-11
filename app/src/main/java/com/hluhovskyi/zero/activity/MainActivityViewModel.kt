package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.common.StateViewModel

internal interface MainActivityViewModel
    : StateViewModel<MainActivityViewModel.Action, MainActivityViewModel.State> {

    sealed interface Action {
        data class BottomNavigationItemSelect(val item: BottomNavigation.Item) : Action
    }

    data class State(
        val currentDestination: Destination = Destination.Transaction.All,
        val bottomNavigation: BottomNavigation = BottomNavigation.None
    )
}