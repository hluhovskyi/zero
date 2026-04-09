package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CategoryEditColorUseCase : ActionStateModel<CategoryEditColorUseCase.Action, CategoryEditColorUseCase.State> {

    sealed interface Action {
        object Request : Action
        data class Pick(val color: Color) : Action
    }

    sealed interface State {
        data class Picked(val color: Color) : State
    }

    data class Color(
        val id: Id.Known,
        val color: ColorValue,
    ) {

        companion object {

            private val EMPTY = Color(
                id = Id("empty_color"),
                color = ColorValue.unspecified(),
            )

            fun empty(): Color = EMPTY
        }
    }

    object Noop : CategoryEditColorUseCase {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
    }
}
