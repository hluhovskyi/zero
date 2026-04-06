@file:OptIn(ExperimentalMaterialNavigationApi::class)

package com.hluhovskyi.zero.activity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import com.hluhovskyi.zero.activity.screens.MainActivityScreenComponent
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class MainActivityViewProvider(
    private val screenComponent: MainActivityScreenComponent.Builder
) : ViewProvider {

    @OptIn(ExperimentalMaterialNavigationApi::class)
    @Composable
    override fun View() {
        ZeroTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colors.background
            ) {
                val bottomSheetNavigator = rememberBottomSheetNavigator()
                val navController = rememberNavController(bottomSheetNavigator)
                screenComponent
                    .navHostController(navController)
                    .bottomSheetNavigator(bottomSheetNavigator)
                    .AttachWithView()
            }
        }
    }
}
