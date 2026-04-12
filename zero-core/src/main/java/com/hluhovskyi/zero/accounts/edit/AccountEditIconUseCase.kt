package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AccountEditIconUseCase : ActionStateModel<AccountEditIconUseCase.Action, AccountEditIconUseCase.State> {

    sealed interface Action {
        data class Request(val iconId: Id = Id.Unknown) : Action
        data class Pick(val icon: Icon) : Action
    }

    sealed interface State {
        data class Picked(val icon: Icon) : State
    }

    data class Icon(
        val id: Id.Known,
        val image: Image,
    ) {

        companion object {

            private val EMPTY = Icon(
                id = Id("empty_icon"),
                image = Image.empty(),
            )

            fun empty(): Icon = EMPTY
        }
    }

    object Noop : AccountEditIconUseCase {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
    }
}
