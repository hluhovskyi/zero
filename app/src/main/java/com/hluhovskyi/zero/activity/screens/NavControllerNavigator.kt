package com.hluhovskyi.zero.activity.screens

import androidx.navigation.NavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class NavControllerNavigator(
    private val navController: NavController
) : Navigator {

    override fun perform(action: Navigator.Action) {
        when (action) {
            is Navigator.Action.Back -> {
                navController.popBackStack()
            }
            is Navigator.Action.NavigateTo -> {
                navController.navigate(action.destination.route) {

                }
            }
        }
    }

    override val state: Flow<Navigator.State> = emptyFlow()
}