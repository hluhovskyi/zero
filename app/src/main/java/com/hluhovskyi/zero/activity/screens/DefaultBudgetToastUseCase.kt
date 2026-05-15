package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.budget.BudgetToastUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class DefaultBudgetToastUseCase : BudgetToastUseCase {

    private val sink = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val messages: Flow<String> = sink

    override fun show(message: String) {
        sink.tryEmit(message)
    }
}
