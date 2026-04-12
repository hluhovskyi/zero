package com.hluhovskyi.zero.activity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
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
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class MainActivityViewProvider(
    private val screenComponent: MainActivityScreenComponent.Builder,
) : ViewProvider {

    @OptIn(ExperimentalMaterialApi::class)
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
                val sheetState = rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    skipHalfExpanded = false,
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
            }
        }
    }
}
