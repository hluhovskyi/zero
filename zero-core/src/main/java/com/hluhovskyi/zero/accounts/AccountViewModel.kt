package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.StateFlow

interface AccountViewModel : AttachableActionStateModel<AccountViewModel.Action, AccountViewModel.State> {

    override val state: StateFlow<State>

    sealed interface Action {
        data class Select(val accountId: Id.Known) : Action
        data class Edit(val accountId: Id.Known) : Action
        data class Archive(val accountId: Id.Known) : Action
        data class Unarchive(val accountId: Id.Known) : Action
    }

    data class State(
        val netWorth: NetWorth = NetWorth(),
        val activeAccounts: List<Account> = emptyList(),
        val archivedAccounts: List<Account> = emptyList(),
        val hasAddedAccount: Boolean = true,
    ) {
        /**
         * Active accounts grouped by [AccountCategory] and ordered by the category's declared
         * order — fed straight into the LazyColumn so the view does no grouping or sorting.
         */
        val activeAccountsByCategory: List<Pair<AccountCategory, List<Account>>> = activeAccounts
            .groupBy { it.category }
            .entries
            .sortedBy { it.key.ordinal }
            .map { it.key to it.value }

        /**
         * Net-worth hero data, pre-shaped for the header: the headline number and its breakdown,
         * the trend chart points, and the change chip — so the view does no arithmetic.
         */
        data class NetWorth(
            val balance: Amount = Amount.zero(),
            val assets: Amount = Amount.zero(),
            val liabilities: Amount = Amount.zero(),
            val currency: Currency? = null,
            val trendPoints: List<Float> = emptyList(),
            val change: NetWorthChange? = null,
            val isNegative: Boolean = false,
        )
    }
}
