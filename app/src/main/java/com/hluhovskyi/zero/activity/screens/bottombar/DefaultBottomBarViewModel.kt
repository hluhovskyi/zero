package com.hluhovskyi.zero.activity.screens.bottombar

import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultBottomBarViewModel(
    private val androidUriResourceFactory: AndroidUriResourceFactory,
    private val navigator: Navigator,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : BottomBarViewModel {

    private val bottomNavigationItems = listOf(
        BottomBarViewModel.Item(
            id = BottomBarViewModel.HomeId,
            iconUri = androidUriResourceFactory.drawable("ic_home_24"),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = BottomBarViewModel.AccountsId,
            iconUri = androidUriResourceFactory.drawable("ic_accounts_24"),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = BottomBarViewModel.BudgetId,
            iconUri = androidUriResourceFactory.drawable("ic_budget_24"),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = BottomBarViewModel.AnalyticsId,
            iconUri = androidUriResourceFactory.drawable("ic_analytics_24"),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = BottomBarViewModel.SettingsId,
            iconUri = androidUriResourceFactory.drawable("ic_settings_24"),
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
                    BottomBarViewModel.HomeId -> Destinations.Home
                    BottomBarViewModel.AnalyticsId -> Destinations.Analytics
                    BottomBarViewModel.AccountsId -> Destinations.Account.All
                    BottomBarViewModel.BudgetId -> Destinations.Budget.All
                    BottomBarViewModel.SettingsId -> Destinations.Settings
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
                budgetQueryUseCase.observeAnyOver().onStart { emit(false) },
            ) { navigatorState, isOver -> navigatorState to isOver }
                .collectLatest { (navigatorState, isOver) ->
                    val bottomBarId = navigatorState.destination.toBottomBarId()
                    mutableState.update { state ->
                        state.copy(
                            items = if (bottomBarId is Id.Known) {
                                bottomNavigationItems.map { item ->
                                    item.copy(
                                        selected = item.id == bottomBarId,
                                        hasAlert = item.id == BottomBarViewModel.BudgetId && isOver,
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
        Destinations.Home.route -> BottomBarViewModel.HomeId
        Destinations.Analytics.route -> BottomBarViewModel.AnalyticsId
        Destinations.Account.All.route -> BottomBarViewModel.AccountsId
        Destinations.Budget.All.route -> BottomBarViewModel.BudgetId
        Destinations.Settings.route -> BottomBarViewModel.SettingsId
        else -> Id.Unknown
    }
}
