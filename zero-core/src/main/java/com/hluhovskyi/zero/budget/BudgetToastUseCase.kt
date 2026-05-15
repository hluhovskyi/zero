package com.hluhovskyi.zero.budget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface BudgetToastUseCase {

    val messages: Flow<String>

    fun show(message: String)

    object Noop : BudgetToastUseCase {
        override val messages: Flow<String> = emptyFlow()
        override fun show(message: String) = Unit
    }
}
