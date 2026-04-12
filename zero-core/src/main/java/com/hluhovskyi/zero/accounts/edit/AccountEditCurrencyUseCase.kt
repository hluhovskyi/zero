package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AccountEditCurrencyUseCase : ActionStateModel<AccountEditCurrencyUseCase.Action, AccountEditCurrencyUseCase.State> {

    sealed interface Action {
        data class Request(val selectedCurrencyId: Id = Id.Unknown) : Action
        data class Pick(val currency: Currency) : Action
    }

    sealed interface State {
        data class Picked(val currency: Currency) : State
    }

    object Noop : AccountEditCurrencyUseCase {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
    }
}
