package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.observeArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategoryUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

internal class DefaultTransactionEditCategoryUseCase(
    private val navigator: Navigator,
    private val requestIdGenerator: IdGenerator,
) : TransactionEditCategoryUseCase {

    private val requestId = AtomicReference<Id>(Id.Unknown)
    private val pickAction = MutableSharedFlow<TransactionEditCategoryUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun perform(action: TransactionEditCategoryUseCase.Action) {
        when (action) {
            is TransactionEditCategoryUseCase.Action.Request -> {
                val id = requestIdGenerator()
                requestId.set(id)
                navigator.navigateTo(
                    destination = Destinations.Category.Picker,
                    Destinations.Category.Picker.RequestId.withValue(id)
                )
            }
            is TransactionEditCategoryUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<TransactionEditCategoryUseCase.State> =
        navigator.observeArgumentValue(
            destination = Destinations.Category.Picker,
            argument = Destinations.Category.Picker.RequestId
        )
            .flatMapLatest { requestId ->
                pickAction.map { pick -> requestId to pick }
            }
            .filter { (requestId, _) -> requestId.value == this.requestId.get() }
            .mapNotNull { (_, pick) -> TransactionEditCategoryUseCase.State.Picked(pick.categoryId) }
            .onEach { navigator.back() }
}
