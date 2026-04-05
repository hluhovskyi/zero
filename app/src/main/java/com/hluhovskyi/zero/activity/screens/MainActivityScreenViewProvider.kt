package com.hluhovskyi.zero.activity.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.hluhovskyi.zero.activity.navigation.BundleArguments
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.common.ViewProvider

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
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
                    val navArguments = entry.destination.arguments.map { argument ->
                        navArgument(name = argument.key) {
                            nullable = argument.optional
                        }
                    }
                    when (entry.displayOption) {
                        is NavigatorEntry.DisplayOption.PartiallyVisible -> {
                            dialog(
                                route = entry.route,
                                arguments = navArguments,
                                dialogProperties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = false,
                                ),
                            ) { backStackEntry ->
                                BottomSheetNavDestination(
                                    onDismiss = { navController.popBackStack() },
                                ) {
                                    entry.view.invoke(
                                        BundleArguments(
                                            bundle = backStackEntry.arguments,
                                            destination = entry.destination,
                                        )
                                    )
                                }
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BottomSheetNavDestination(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = false,
    )

    LaunchedEffect(Unit) {
        sheetState.show()
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
            onDismiss()
        }
    }

    ModalBottomSheetLayout(
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetBackgroundColor = MaterialTheme.colors.background,
        sheetContent = {
            content()
        },
    ) {
        Box(Modifier.fillMaxSize())
    }
}
