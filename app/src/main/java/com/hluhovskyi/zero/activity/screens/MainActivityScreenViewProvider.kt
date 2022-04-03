package com.hluhovskyi.zero.activity.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hluhovskyi.zero.activity.navigation.BundleArguments
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.common.ViewProvider

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
    private val navigator: Navigator,
    private val startDestination: Destination,
    private val navigationEntries: Collection<NavigatorEntry>,
    private val bottomBar: @Composable () -> Unit
) : ViewProvider {

    @Composable
    override fun View() {
        Scaffold(bottomBar = bottomBar) { innerPadding ->
            NavHost(
                modifier = Modifier.padding(innerPadding),
                navController = navController,
                startDestination = startDestination.route
            ) {
                navigationEntries.forEach { entry ->
                    composable(
                        route = navigator.routeWithPlaceholders(entry.destination),
                        arguments = entry.destination.arguments.map { argument ->
                            navArgument(name = argument.key) {
                                nullable = argument.optional
                            }
                        }
                    ) {
                        entry.view.invoke(
                            BundleArguments(
                                bundle = it.arguments,
                                destination = entry.destination
                            )
                        )
                    }
                }
            }
        }
    }
}
