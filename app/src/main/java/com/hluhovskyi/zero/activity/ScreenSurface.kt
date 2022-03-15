package com.hluhovskyi.zero.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hluhovskyi.zero.activity.screens.AccountsScreen
import com.hluhovskyi.zero.activity.screens.CategoriesScreen
import com.hluhovskyi.zero.activity.screens.TransactionScreen
import com.hluhovskyi.zero.common.AttachWithView

@Composable
fun ScreenSurface(
    activityComponent: ActivityComponent,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.Transaction.All.route
    ) {
        composable(Destination.Account.All) {
            AccountsScreen(
                component = activityComponent.accountComponentBuilder
            )
        }
        composable(Destination.Transaction.All) {
            TransactionScreen(
                component = activityComponent.transactionComponentBuilder,
                onTransactionEdit = { navController.navigate("transactions/edit") }
            )
        }
        composable(Destination.Transaction.Edit) {
            activityComponent.transactionEditComponentBuilder
                .onTransactionSavedHandler { navController.navigate("transactions") }
                .onEditCategoriesHandler { navController.navigate("categories") }
                .AttachWithView()
        }
        composable(Destination.Category.All) {
            CategoriesScreen(
                component = activityComponent.categoryComponentBuilder,
                onCategoriesEdit = { navController.navigate("categories/edit") }
            )
        }
        composable(Destination.Category.Edit) {
            activityComponent.categoryEditComponentBuilder
                .AttachWithView()
        }
    }
}

private fun NavGraphBuilder.composable(
    destination: Destination,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = destination.route,
        content = content
    )
}