package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.accounts.edit.AccountEditIconUseCase
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.observeArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DefaultAccountEditIconUseCase"

internal class DefaultAccountEditIconUseCase(
    private val navigator: Navigator,
    private val requestIdGenerator: IdGenerator,
    inputLogger: Logger,
) : AccountEditIconUseCase {

    private val logger = inputLogger.withTag(TAG)

    private var requestId = AtomicReference<Id>(Id.Unknown)
    private val pickAction = MutableSharedFlow<AccountEditIconUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val colorPickAction = MutableSharedFlow<AccountEditIconUseCase.Action.PickColor>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: AccountEditIconUseCase.Action) {
        when (action) {
            is AccountEditIconUseCase.Action.Request -> {
                val id = requestIdGenerator()
                requestId.set(id)
                logger.d("perform, requestId=$requestId")
                val args = buildList {
                    add(Destinations.Icon.Picker.RequestId.withValue(id))
                    (action.iconId as? Id.Known)?.let { selectedId ->
                        add(Destinations.Icon.Picker.SelectedIconId.withValue(selectedId))
                    }
                    (action.colorId as? Id.Known)?.let { colorId ->
                        add(Destinations.Icon.Picker.ColorId.withValue(colorId))
                    }
                }
                navigator.navigateTo(
                    destination = Destinations.Icon.Picker,
                    *args.toTypedArray(),
                )
            }
            is AccountEditIconUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
            is AccountEditIconUseCase.Action.PickColor -> {
                colorPickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<AccountEditIconUseCase.State> = merge(
        navigator.observeArgumentValue(
            destination = Destinations.Icon.Picker,
            argument = Destinations.Icon.Picker.RequestId,
        )
            .flatMapLatest { requestId ->
                pickAction.map { pick ->
                    logger.d("state, requestId=$requestId, pick=$pick")
                    requestId to pick
                }
            }
            .filter { (requestId, _) -> requestId.value == this.requestId.get() }
            .mapNotNull { (_, pick) -> AccountEditIconUseCase.State.Picked(pick.icon, pick.colorScheme) }
            .onEach { navigator.back() },
        colorPickAction.map { AccountEditIconUseCase.State.ColorChanged(it.colorId, it.colorScheme) },
    )
}
