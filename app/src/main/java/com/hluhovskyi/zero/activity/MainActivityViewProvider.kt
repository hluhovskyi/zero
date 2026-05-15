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

                    screenComponent
                        .navHostController(navController)
                        .bottomSheetNavigator(bottomSheetNavigator)
                        .modalBottomSheetState(sheetState)
                        .AttachWithView()

                    biometricLockGateComponent.AttachWithView()
                }
            }
        }
    }
}
