package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.transactions.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface TransactionFilterSheetViewModel : AttachableActionStateModel<TransactionFilterSheetViewModel.Action, TransactionFilterSheetViewModel.State> {

    sealed interface Action {
        data object Close : Action
        data class Apply(val filter: TransactionFilter) : Action
    }

    data class State(
        val activeFilter: TransactionFilter = TransactionFilter(),
        val availableCategories: List<FilterCategory> = emptyList(),
        val availableAccounts: List<FilterAccount> = emptyList(),
    )

    data class FilterCategory(
        val id: Id.Known,
        val name: String,
        val colorScheme: ColorScheme,
        val icon: Image,
    )

    data class FilterAccount(
        val id: Id.Known,
        val name: String,
        val icon: Image,
    )

    object Noop : TransactionFilterSheetViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
