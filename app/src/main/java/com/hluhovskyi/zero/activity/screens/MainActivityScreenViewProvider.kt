package com.hluhovskyi.zero.activity.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.hluhovskyi.zero.activity.navigation.BundleArguments
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.DragHandle
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class MainActivityScreenViewProvider(
    private val navController: NavHostController,
    private val startDestination: Destination,
    private val navigationEntries: Collection<NavigatorEntry>,
    private val bottomBar: @Composable () -> Unit,
    private val bottomSheetNavigator: BottomSheetNavigator,
    private val modalBottomSheetState: ModalBottomSheetState,
) : ViewProvider {

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

        val focusManager = LocalFocusManager.current
        LaunchedEffect(modalBottomSheetState.targetValue) {
            if (modalBottomSheetState.targetValue != ModalBottomSheetValue.Hidden) {
                focusManager.clearFocus()
            }
        }

        ModalBottomSheetLayout(
            bottomSheetNavigator = bottomSheetNavigator,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetBackgroundColor = ZeroTheme.colors.surface,
            scrimColor = ZeroTheme.colors.scrim,
        ) {
            Scaffold(
                backgroundColor = ZeroTheme.colors.surface,
                bottomBar = { if (!isKeyboardVisible) bottomBar() },
            ) { innerPadding ->
                NavHost(
                    modifier = Modifier.padding(innerPadding),
                    navController = navController,
                    startDestination = startDestination.route,
                    enterTransition = {
                        if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                            fadeIn(tween(NAV_TRANSITION_MILLIS))
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(NAV_TRANSITION_MILLIS, easing = FastOutSlowInEasing),
                                initialOffsetX = { it / 8 },
                            ) + fadeIn(tween(NAV_TRANSITION_MILLIS))
                        }
                    },
                    exitTransition = {
                        if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                            fadeOut(tween(NAV_TRANSITION_MILLIS))
                        } else {
                            slideOutHorizontally(
                                animationSpec = tween(NAV_TRANSITION_MILLIS, easing = FastOutSlowInEasing),
                                targetOffsetX = { -it / 8 },
                            ) + fadeOut(tween(NAV_TRANSITION_MILLIS))
                        }
                    },
                    popEnterTransition = {
                        if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                            fadeIn(tween(NAV_TRANSITION_MILLIS))
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(NAV_TRANSITION_MILLIS, easing = FastOutSlowInEasing),
                                initialOffsetX = { -it / 8 },
                            ) + fadeIn(tween(NAV_TRANSITION_MILLIS))
                        }
                    },
                    popExitTransition = {
                        if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                            fadeOut(tween(NAV_TRANSITION_MILLIS))
                        } else {
                            slideOutHorizontally(
                                animationSpec = tween(NAV_TRANSITION_MILLIS, easing = FastOutSlowInEasing),
                                targetOffsetX = { it / 8 },
                            ) + fadeOut(tween(NAV_TRANSITION_MILLIS))
                        }
                    },
                ) {
                    navigationEntries.forEach { entry ->
                        val navArguments = entry.destination.arguments.map { argument ->
                            navArgument(name = argument.key) {
                                nullable = argument.optional
                            }
                        }
                        val navDeepLinks = entry.deepLinks.map { pattern ->
                            navDeepLink { uriPattern = pattern }
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
                                        DragHandle(
                                            modifier = Modifier.height(dragHandleHeight),
                                        )
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
                                    deepLinks = navDeepLinks,
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

private const val NAV_TRANSITION_MILLIS = 180

private val BOTTOM_BAR_TAB_ROUTES = setOf("home", "accounts", "categories", "budget", "settings")

private fun isTabSwitch(from: String?, to: String?): Boolean = from in BOTTOM_BAR_TAB_ROUTES && to in BOTTOM_BAR_TAB_ROUTES
