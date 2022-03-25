package com.hluhovskyi.zero.activity.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hluhovskyi.zero.common.ViewProvider

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
    private val startDestination: Destination,
    private val navigationEntries: Collection<NavigatorEntry>,
) : ViewProvider {

    @Composable
    override fun View() {
        NavHost(
            navController = navController,
            startDestination = startDestination.route
        ) {
            navigationEntries.forEach { entry ->
                composable(
                    route = entry.destination.routeWithPlaceholders(entry.destination.arguments),
                    arguments = entry.destination.arguments.map { argument ->
                        navArgument(name = argument.key) {
                            nullable = argument.optional
                        }
                    }
                ) {
                    entry.view.invoke()
                }
            }
        }
    }
}
