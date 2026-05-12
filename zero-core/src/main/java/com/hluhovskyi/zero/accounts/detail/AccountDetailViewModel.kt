package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import java.io.Closeable

interface AccountDetailViewModel : AttachableActionStateModel<AccountDetailViewModel.Action, AccountDetailViewModel.State> {

    sealed interface Action {
        object Back : Action
        object Edit : Action
        object Archive : Action
        object Unarchive : Action
        object CreateTransaction : Action
    }

    data class State(
        val accountName: String = "",
        val accountIcon: Image = Image.empty(),
        val accountDetails: String? = null,
        val balance: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val isNegativeBalance: Boolean = false,
        val isArchived: Boolean = false,
        val periodDate: LocalDate? = null,
        val totalIn: Amount = Amount.zero(),
        val totalOut: Amount = Amount.zero(),
        val transactionCount: Int = 0,
    )

    object Noop : AccountDetailViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
