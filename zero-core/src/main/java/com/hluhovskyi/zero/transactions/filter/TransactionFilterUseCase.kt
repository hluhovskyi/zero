package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.transactions.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface TransactionFilterUseCase : ActionStateModel<TransactionFilterUseCase.Action, TransactionFilterUseCase.State> {

    val pendingFilter: TransactionFilter

    sealed interface Action {
        data class Open(val filter: TransactionFilter) : Action
        data class Apply(val filter: TransactionFilter) : Action
        data object Close : Action
    }

    sealed interface State {
        data class Applied(val filter: TransactionFilter) : State
    }

    object Noop : TransactionFilterUseCase {
        override val pendingFilter: TransactionFilter = TransactionFilter()
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
    }
}
