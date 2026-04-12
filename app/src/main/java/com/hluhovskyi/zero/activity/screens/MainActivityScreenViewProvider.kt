package com.hluhovskyi.zero.activity.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.navigation.BottomSheetNavigator
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.bottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hluhovskyi.zero.activity.navigation.BundleArguments
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.DragHandle

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
    private val startDestination: Destination,
    private val navigationEntries: Collection<NavigatorEntry>,
    private val bottomBar: @Composable () -> Unit,
    private val bottomSheetNavigator: BottomSheetNavigator,
    private val modalBottomSheetState: ModalBottomSheetState,
) : ViewProvider {

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun View() {
        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        LaunchedEffect(isKeyboardVisible) {
            if (isKeyboardVisible && modalBottomSheetState.isVisible) {
                try {
                    modalBottomSheetState.show()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

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
                    startDestination = startDestination.route,
                ) {
                    navigationEntries.forEach { entry ->
                        val navArguments = entry.destination.arguments.map { argument ->
                            navArgument(name = argument.key) {
                                nullable = argument.optional
                            }
                        }
                        when (entry.displayOption) {
                            is NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet -> {
                                bottomSheet(
                                    route = entry.route,
                                    arguments = navArguments,
                                ) { backStackEntry ->
                                    val targetValue = modalBottomSheetState.targetValue
                                    val isExpanded = targetValue == ModalBottomSheetValue.Expanded
                                    val dragHandleHeight by animateDpAsState(
                                        targetValue = if (isExpanded) 0.dp else 24.dp,
                                        label = "DragHandleHeight",
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .imePadding(),
                                    ) {
                                        if (dragHandleHeight > 0.dp) {
                                            DragHandle(
                                                modifier = Modifier
                                                    .height(dragHandleHeight)
                                                    .alpha(dragHandleHeight / 24.dp),
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            entry.view.invoke(
                                                BundleArguments(
                                                    bundle = backStackEntry.arguments,
                                                    destination = entry.destination,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                composable(
                                    route = entry.route,
                                    arguments = navArguments,
                                ) { backStackEntry ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .imePadding(),
                                    ) {
                                        entry.view.invoke(
                                            BundleArguments(
                                                bundle = backStackEntry.arguments,
                                                destination = entry.destination,
                                            ),
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
}
