package com.hluhovskyi.zero.activity.screens

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.hluhovskyi.zero.activity.navigation.BundleArguments
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.common.ViewProvider
import kotlinx.coroutines.flow.map

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
    private val startDestination: Destination,
    private val navigationEntries: Collection<NavigatorEntry>,
    private val bottomBar: @Composable () -> Unit
) : ViewProvider {

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun View() {
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        
        val bottomSheetEntry = remember(currentBackStackEntry) {
            navigationEntries.find { it.route == currentBackStackEntry?.destination?.route && it.displayOption is NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet }
        }

        val sheetState = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            skipHalfExpanded = false,
        )

        LaunchedEffect(bottomSheetEntry) {
            if (bottomSheetEntry != null) {
                sheetState.show()
            } else {
                sheetState.hide()
            }
        }

        LaunchedEffect(sheetState.currentValue) {
            if (sheetState.currentValue == ModalBottomSheetValue.Hidden && bottomSheetEntry != null) {
                navController.popBackStack()
            }
        }

        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetBackgroundColor = MaterialTheme.colors.background,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            sheetContent = {
                if (bottomSheetEntry != null && currentBackStackEntry != null) {
                    bottomSheetEntry.view.invoke(
                        BundleArguments(
                            bundle = currentBackStackEntry!!.arguments,
                            destination = bottomSheetEntry.destination,
                        )
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
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
                            is NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet -> {
                                dialog(
                                    route = entry.route,
                                    arguments = navArguments,
                                    dialogProperties = DialogProperties(
                                        usePlatformDefaultWidth = false,
                                        dismissOnBackPress = false,
                                        dismissOnClickOutside = false,
                                    ),
                                ) {
                                    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                                    SideEffect {
                                        window?.let {
                                            it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                                            it.setDimAmount(0f)
                                            it.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                                            it.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                                        }
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
}

