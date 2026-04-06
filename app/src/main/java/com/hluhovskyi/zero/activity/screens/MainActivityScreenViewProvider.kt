@file:OptIn(ExperimentalMaterialNavigationApi::class)

package com.hluhovskyi.zero.activity.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.accompanist.navigation.material.BottomSheetNavigator
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.bottomSheet
import com.hluhovskyi.zero.activity.navigation.BundleArguments
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.common.ViewProvider

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
    private val startDestination: Destination,
    private val navigationEntries: Collection<NavigatorEntry>,
    private val bottomBar: @Composable () -> Unit,
    private val bottomSheetNavigator: BottomSheetNavigator,
) : ViewProvider {

    @OptIn(ExperimentalMaterialNavigationApi::class)
    @Composable
    override fun View() {
        ModalBottomSheetLayout(
            bottomSheetNavigator = bottomSheetNavigator,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetBackgroundColor = MaterialTheme.colors.background,
            scrimColor = Color.Black.copy(alpha = 0.32f),
        ) {
            Scaffold(bottomBar = bottomBar) { innerPadding ->
                NavHost(
                    modifier = Modifier.padding(innerPadding),
                    navController = navController,
                    startDestination = startDestination.route
                ) {
                    navigationEntries.forEach { entry ->
                        val navArguments = entry.destination.arguments.map { argument ->
                            navArgument(name = argument.key) {
                                nullable = argument.optional
                            }
                        }
                        when (entry.displayOption) {
                            is NavigatorEntry.DisplayOption.PartiallyVisible -> {
                                bottomSheet(
                                    route = entry.route,
                                    arguments = navArguments,
                                ) { backStackEntry ->
                                    entry.view.invoke(
                                        BundleArguments(
                                            bundle = backStackEntry.arguments,
                                            destination = entry.destination,
                                        )
                                    )
                                }
                            }
                            else -> {
                                composable(
                                    route = entry.route,
                                    arguments = navArguments,
                                ) { backStackEntry ->
                                    entry.view.invoke(
                                        BundleArguments(
                                            bundle = backStackEntry.arguments,
                                            destination = entry.destination,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
