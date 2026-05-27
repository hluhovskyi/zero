package com.hluhovskyi.zero.activity.screens.bottombar

import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultBottomBarViewModel(
    private val androidUriResourceFactory: AndroidUriResourceFactory,
    private val navigator: Navigator,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : BottomBarViewModel {

    private val homeId = Id.Known("home")
    private val categoriesId = Id.Known("categories")
    private val budgetId = Id.Known("budget")
    private val accountsId = Id.Known("accounts")
    private val settingsId = Id.Known("settings")

    private val bottomNavigationItems = listOf(
        BottomBarViewModel.Item(
            id = homeId,
            name = "Home",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_home_24"),
                description = "Home icon",
            ),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = accountsId,
            name = "Accounts",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_accounts_24"),
                description = "Accounts icon",
            ),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = budgetId,
            name = "Budget",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_budget_24"),
                description = "Budget icon",
            ),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = categoriesId,
            name = "Categories",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_categories_24"),
                description = "Category icon",
            ),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = settingsId,
            name = "Settings",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_settings_24"),
                description = "Settings icon",
            ),
            selected = false,
        ),
    )

    private val mutableState = MutableStateFlow<BottomBarViewModel.State>(
        BottomBarViewModel.State(bottomNavigationItems),
    )
    override val state: Flow<BottomBarViewModel.State> = mutableState

    override fun perform(action: BottomBarViewModel.Action) {
        when (action) {
            is BottomBarViewModel.Action.SelectItem -> {
                val destination = when (action.item.id) {
                    homeId -> Destinations.Home
                    categoriesId -> Destinations.Category.All
                    accountsId -> Destinations.Account.All
                    budgetId -> Destinations.Budget.All
                    settingsId -> Destinations.Settings
                    else -> null
                }

                destination?.let {
                    navigator.perform(
                        Navigator.Action.NavigateTo(
                            destination = it,
                            clearBackStack = true,
                            arguments = emptyList(),
                        ),
                    )
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                navigator.state,
                budgetQueryUseCase.observeAnyOver(),
            ) { navigatorState, isOver -> navigatorState to isOver }
                .collectLatest { (navigatorState, isOver) ->
                    val bottomBarId = navigatorState.destination.toBottomBarId()
                    mutableState.update { state ->
                        state.copy(
                            items = if (bottomBarId is Id.Known) {
                                bottomNavigationItems.map { item ->
                                    item.copy(
                                        selected = item.id == bottomBarId,
                                        hasAlert = item.id == budgetId && isOver,
                                    )
                                }
                            } else {
                                emptyList()
                            },
                        )
                    }
                }
        }
    }

    private fun Destination.toBottomBarId(): Id = when (this.route) {
        Destinations.Home.route -> homeId
        Destinations.Category.All.route -> categoriesId
        Destinations.Account.All.route -> accountsId
        Destinations.Budget.All.route -> budgetId
        Destinations.Settings.route -> settingsId
        else -> Id.Unknown
    }
}
