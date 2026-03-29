package com.hluhovskyi.zero.activity.screens.bottombar

import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultBottomBarViewModel(
    private val androidUriResourceFactory: AndroidUriResourceFactory,
    private val navigator: Navigator,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : BottomBarViewModel {

    private val transactionsId = Id.Known("transactions")
    private val categoriesId = Id.Known("categories")
    private val budgetId = Id.Known("budget")
    private val accountsId = Id.Known("accounts")
    private val settingsId = Id.Known("settings")

    private val bottomNavigationItems = listOf(
        BottomBarViewModel.Item(
            id = transactionsId,
            name = "Transactions",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_transactions_24"),
                description = "Transaction icon"
            ),
            selected = false
        ),
        BottomBarViewModel.Item(
            id = accountsId,
            name = "Accounts",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_accounts_24"),
                description = "Accounts icon"
            ),
            selected = false
        ),
        BottomBarViewModel.Item(
            id = budgetId,
            name = "Budget",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_budget_24"),
                description = "Budget icon"
            ),
            selected = false
        ),
        BottomBarViewModel.Item(
            id = categoriesId,
            name = "Categories",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_categories_24"),
                description = "Category icon"
            ),
            selected = false,
        ),
        BottomBarViewModel.Item(
            id = settingsId,
            name = "Settings",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_settings_24"),
                description = "Settings icon"
            ),
            selected = false
        )
    )

    private val mutableState = MutableStateFlow<BottomBarViewModel.State>(
        BottomBarViewModel.State(bottomNavigationItems)
    )
    override val state: Flow<BottomBarViewModel.State> = mutableState

    override fun perform(action: BottomBarViewModel.Action) {
        when (action) {
            is BottomBarViewModel.Action.SelectItem -> {
                val destination = when (action.item.id) {
                    transactionsId -> Destinations.Transaction.All
                    categoriesId -> Destinations.Category.All
                    accountsId -> Destinations.Account.All
                    settingsId -> Destinations.Settings
                    else -> null
                }

                destination?.let {
                    navigator.perform(
                        Navigator.Action.NavigateTo(
                            destination = it,
                            clearBackStack = true,
                            arguments = emptyList(),
                        )
                    )
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            navigator.state.collectLatest { navigatorState ->
                val bottomBarId = navigatorState.destination.toBottomBarId()
                mutableState.update { state ->
                    state.copy(
                        items = if (bottomBarId is Id.Known) {
                            bottomNavigationItems.map { item ->
                                item.copy(
                                    selected = item.id == bottomBarId
                                )
                            }
                        } else {
                            emptyList()
                        }
                    )
                }
            }
        }
    }

    private fun Destination.toBottomBarId(): Id = when (this.route) {
        Destinations.Transaction.All.route -> transactionsId
        Destinations.Category.All.route -> categoriesId
        Destinations.Account.All.route -> accountsId
        Destinations.Settings.route -> settingsId
        //Destinations.Budget.route -> budgetId
        else -> Id.Unknown
    }
}