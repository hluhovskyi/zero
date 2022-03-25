package com.hluhovskyi.zero.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hluhovskyi.zero.activity.screens.AccountsEditScreen
import com.hluhovskyi.zero.activity.screens.AccountsScreen
import com.hluhovskyi.zero.activity.screens.CategoriesScreen
import com.hluhovskyi.zero.activity.screens.TransactionScreen
import com.hluhovskyi.zero.categories.edit.CategoryEditIconUseCase
import com.hluhovskyi.zero.common.AttachWithView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ScreenSurface(
    activityComponent: ActivityComponent,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var categoryEditIconUseCase: CategoryEditIconUseCase by remember { mutableStateOf(CategoryEditIconUseCase.Noop) }
    LaunchedEffect(categoryEditIconUseCase) {
        categoryEditIconUseCase.state
            .collectLatest { state ->
                when (state) {
                    is CategoryEditIconUseCase.State.None -> {
                    }
                    is CategoryEditIconUseCase.State.Picked -> {
                        navController.popBackStack()
                    }
                    is CategoryEditIconUseCase.State.Request -> {
                        navController.navigate(Destination.Icon.Picker.route)
                    }
                }
            }
    }

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.Transaction.All.route
    ) {
        composable(Destination.Account.All) {
            AccountsScreen(
                component = activityComponent.accountComponentBuilder,
                onAccountEdit = { navController.navigate("accounts/edit") }
            )
        }
        composable(Destination.Account.Edit) {
            AccountsEditScreen(
                component = activityComponent.accountEditComponentBuilder
                    .onAccountSavedHandler { navController.navigate("accounts") },
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
                .AttachWithView(
                    onAttach = {
                        coroutineScope.launch { categoryEditIconUseCase = it.categoryEditIconUseCase }
                    },
                    onDispose = {
                        coroutineScope.launch { categoryEditIconUseCase = CategoryEditIconUseCase.Noop }
                    },
                )
        }
        composable(Destination.Icon.Picker.route) {
            activityComponent.iconPickerComponentBuilder
                .onIconSelectedHandler { icon ->
                    coroutineScope.launch {
                        categoryEditIconUseCase.perform(
                            CategoryEditIconUseCase.Action.Pick(
                                CategoryEditIconUseCase.Icon(
                                    id = icon.id,
                                    image = icon.image
                                )
                            )
                        )
                    }
                }
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