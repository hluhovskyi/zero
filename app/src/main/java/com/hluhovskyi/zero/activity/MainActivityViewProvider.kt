package com.hluhovskyi.zero.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.activity.screens.Destination
import com.hluhovskyi.zero.activity.screens.MainActivityScreenComponent
import com.hluhovskyi.zero.activity.screens.MainBottomNavigation
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class MainActivityViewProvider(
    private val screenComponent: MainActivityScreenComponent.Builder,
    private val viewModel: MainActivityViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        ZeroTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                val state by viewModel.state.collectAsState(
                    initial = MainActivityViewModel.State(
                        currentDestination = Destination.Transaction.All,
                        bottomNavigation = BottomNavigation.None
                    )
                )
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = {
                        MainBottomNavigation(
                            currentDestination = state.currentDestination,
                            bottomNavigation = state.bottomNavigation,
                            imageLoader = imageLoader,
                            onItemClick = { item ->
                                navController.navigate(item.destination.route)
                                viewModel.perform(MainActivityViewModel.Action.BottomNavigationItemSelect(item))
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        screenComponent
                            .navHostController(navController)
                            .AttachWithView()
                    }
                }
            }
        }
    }
}