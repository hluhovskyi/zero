package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.common.ActionStateModel

internal interface MainActivityViewModel
    : ActionStateModel<MainActivityViewModel.Action, MainActivityViewModel.State> {

    sealed interface Action {
        data class BottomNavigationItemSelect(val item: BottomNavigation.Item) : Action
    }

    data class State(
        val currentDestination: Destination = Destinations.Transaction.All,
        val bottomNavigation: BottomNavigation = BottomNavigation.None
    )
}