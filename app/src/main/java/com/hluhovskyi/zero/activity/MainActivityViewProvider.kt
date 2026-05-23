package com.hluhovskyi.zero.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Surface
import androidx.compose.material.navigation.BottomSheetNavigator
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.hluhovskyi.zero.activity.screens.MainActivityScreenComponent
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class MainActivityViewProvider(
    private val screenComponent: MainActivityScreenComponent.Builder,
    private val biometricLockGateComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        ZeroTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colors.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // TODO: issue #213 — sheets should open at content height, not full-screen
                    val sheetState = rememberModalBottomSheetState(
                        initialValue = ModalBottomSheetValue.Hidden,
                        skipHalfExpanded = true,
                    )
                    val bottomSheetNavigator = remember(sheetState) {
                        BottomSheetNavigator(sheetState)
                    }
                    val navController = rememberNavController(bottomSheetNavigator)

                    // Build and attach the screen component per composition rather than via
                    // the Builder's retaining AttachWithView overload. The component captures
                    // this Activity's NavController, bottom-sheet navigator and sheet state;
                    // retaining it in a ViewModel across a configuration change (e.g. a dark-
                    // mode toggle) would hand the recreated Activity a NavController whose
                    // back-stack entries belong to the destroyed Activity, crashing when
                    // NavHost.setGraph re-runs (ZERO-2). Rebuilding here lets rememberNavController
                    // restore navigation state into a fresh, correctly-scoped controller.
                    val screen = remember(navController, bottomSheetNavigator, sheetState) {
                        screenComponent
                            .navHostController(navController)
                            .bottomSheetNavigator(bottomSheetNavigator)
                            .modalBottomSheetState(sheetState)
                            .build()
                    }
                    screen.AttachWithView()

                    biometricLockGateComponent.AttachWithView()
                }
            }
        }
    }
}
