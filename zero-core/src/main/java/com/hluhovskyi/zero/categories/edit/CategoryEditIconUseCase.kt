package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CategoryEditIconUseCase
    : ActionStateModel<CategoryEditIconUseCase.Action, CategoryEditIconUseCase.State> {

    val colorScheme: ColorScheme?

    sealed interface Action {
        data class Request(val colorScheme: ColorScheme? = null) : Action
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
                image = Image.empty()
            )

            fun empty(): Icon = EMPTY
        }
    }

    object Noop : CategoryEditIconUseCase {
        override val colorScheme: ColorScheme? = null
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
    }
}