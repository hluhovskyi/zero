package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class DefaultMainActivityViewModel(
    private val androidUriResourceFactory: AndroidUriResourceFactory
) : MainActivityViewModel {

    private val bottomNavigationItems = listOf(
        BottomNavigation.Item(
            name = "Transactions",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_florist_24"),
                description = "Transaction icon"
            ),
            destination = Destination.Transaction.All
        ),
        BottomNavigation.Item(
            name = "Categories",
            icon = Image(
                uri = androidUriResourceFactory.drawable("ic_fastfood_24"),
                description = "Category icon"
            ),
            destination = Destination.Category.All
        ),
    )

    private val mutableState = MutableStateFlow(
        MainActivityViewModel.State(
            currentDestination = Destination.Transaction.All,
            bottomNavigation = BottomNavigation.WithItems(bottomNavigationItems)
        )
    )
    override val state: Flow<MainActivityViewModel.State> = mutableState

    override fun perform(action: MainActivityViewModel.Action) {
        when (action) {
            is MainActivityViewModel.Action.BottomNavigationItemSelect -> {
                mutableState.update { state ->
                    state.copy(
                        currentDestination = action.item.destination
                    )
                }
            }
        }
    }
}