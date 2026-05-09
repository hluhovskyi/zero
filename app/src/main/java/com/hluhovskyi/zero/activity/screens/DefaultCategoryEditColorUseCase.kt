package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.observeArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.categories.edit.CategoryEditColorUseCase
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

private const val TAG = "DefaultCategoryEditColorUseCase"

internal class DefaultCategoryEditColorUseCase(
    private val navigator: Navigator,
    private val requestIdGenerator: IdGenerator,
    inputLogger: Logger,
) : CategoryEditColorUseCase {

    private val logger = inputLogger.withTag(TAG)

    private var requestId = AtomicReference<Id>(Id.Unknown)
    private val pickAction = MutableSharedFlow<CategoryEditColorUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val inlinePickAction = MutableSharedFlow<CategoryEditColorUseCase.Action.PickWithoutNavigation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: CategoryEditColorUseCase.Action) {
        when (action) {
            is CategoryEditColorUseCase.Action.Request -> {
                val id = requestIdGenerator()
                requestId.set(id)
                logger.d("perform, requestId=$requestId")
                navigator.navigateTo(
                    destination = Destinations.Color.Picker,
                    Destinations.Color.Picker.RequestId.withValue(id),
                )
            }
            is CategoryEditColorUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
            is CategoryEditColorUseCase.Action.PickWithoutNavigation -> {
                inlinePickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<CategoryEditColorUseCase.State> = merge(
        // Picks from the dedicated Color.Picker bottom sheet — navigate back after pick
        navigator.observeArgumentValue(
            destination = Destinations.Color.Picker,
            argument = Destinations.Color.Picker.RequestId,
        )
            .flatMapLatest { requestId ->
                pickAction.map { pick ->
                    logger.d("state (picker), requestId=$requestId, pick=$pick")
                    requestId to pick.color
                }
            }
            .filter { (requestId, _) -> requestId.value == this.requestId.get() }
            .mapNotNull { (_, color) -> CategoryEditColorUseCase.State.Picked(color) }
            .onEach { navigator.back() },

        // Inline picks from Icon.Picker — emit state without closing the picker
        inlinePickAction
            .filter { this.requestId.get() is Id.Known }
            .mapNotNull { action -> CategoryEditColorUseCase.State.Picked(action.color) },
    )
}
