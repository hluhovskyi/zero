package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface TransactionEditCategoryUseCase : ActionStateModel<TransactionEditCategoryUseCase.Action, TransactionEditCategoryUseCase.State> {

    sealed interface Action {
        object Request : Action
        data class Pick(val categoryId: Id.Known) : Action
    }

    sealed interface State {
        data class Picked(val categoryId: Id.Known) : State
    }

    object Noop : TransactionEditCategoryUseCase {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
    }
}
