package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.filter.TransactionFilterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

internal class DefaultTransactionFilterUseCase(
    private val navigator: Navigator,
) : TransactionFilterUseCase {

    private val _pendingFilter = AtomicReference(TransactionFilter())
    override val pendingFilter: TransactionFilter get() = _pendingFilter.get()

    private val applyAction = MutableSharedFlow<TransactionFilterUseCase.Action.Apply>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: TransactionFilterUseCase.Action) {
        when (action) {
            is TransactionFilterUseCase.Action.Open -> {
                _pendingFilter.set(action.filter)
                navigator.navigateTo(Destinations.Transaction.Filter)
            }
            is TransactionFilterUseCase.Action.Apply -> {
                applyAction.tryEmit(action)
            }
            TransactionFilterUseCase.Action.Close -> {
                navigator.back()
            }
        }
    }

    override val state: Flow<TransactionFilterUseCase.State> = applyAction
        .mapNotNull { TransactionFilterUseCase.State.Applied(it.filter) }
        .onEach { withContext(Dispatchers.Main) { navigator.back() } }
}
