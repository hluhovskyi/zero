package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.observeArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrencyUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

internal class DefaultTransactionEditCurrencyUseCase(
    private val navigator: Navigator,
    private val requestIdGenerator: IdGenerator,
) : TransactionEditCurrencyUseCase {

    private val requestId = AtomicReference<Id>(Id.Unknown)
    private val pickAction = MutableSharedFlow<TransactionEditCurrencyUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: TransactionEditCurrencyUseCase.Action) {
        when (action) {
            is TransactionEditCurrencyUseCase.Action.Request -> {
                val id = requestIdGenerator()
                requestId.set(id)
                navigator.navigateTo(
                    destination = Destinations.Currency.Picker,
                    Destinations.Currency.Picker.RequestId.withValue(id),
                )
            }
            is TransactionEditCurrencyUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<TransactionEditCurrencyUseCase.State> =
        navigator.observeArgumentValue(
            destination = Destinations.Currency.Picker,
            argument = Destinations.Currency.Picker.RequestId,
        )
            .flatMapLatest { requestId ->
                pickAction.map { pick -> requestId to pick }
            }
            .filter { (requestId, _) -> requestId.value == this.requestId.get() }
            .mapNotNull { (_, pick) -> TransactionEditCurrencyUseCase.State.Picked(pick.currency) }
            .onEach { navigator.back() }
}
