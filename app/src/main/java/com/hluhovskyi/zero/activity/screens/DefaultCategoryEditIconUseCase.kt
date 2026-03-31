package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.observeArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.categories.edit.CategoryEditIconUseCase
import com.hluhovskyi.zero.colors.ColorScheme
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
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "DefaultCategoryEditIconUseCase"

internal class DefaultCategoryEditIconUseCase(
    private val navigator: Navigator,
    private val requestIdGenerator: IdGenerator,
    inputLogger: Logger,
) : CategoryEditIconUseCase {

    private val logger = inputLogger.withTag(TAG)

    override var colorScheme: ColorScheme? = null
        private set

    private var requestId = AtomicReference<Id>(Id.Unknown)
    private val pickAction = MutableSharedFlow<CategoryEditIconUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun perform(action: CategoryEditIconUseCase.Action) {
        when (action) {
            is CategoryEditIconUseCase.Action.Request -> {
                colorScheme = action.colorScheme
                val id = requestIdGenerator()
                requestId.set(id)
                logger.d("perform, requestId=$requestId")
                navigator.navigateTo(
                    destination = Destinations.Icon.Picker,
                    Destinations.Icon.Picker.RequestId.withValue(id)
                )
            }
            is CategoryEditIconUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<CategoryEditIconUseCase.State> =
        navigator.observeArgumentValue(
            destination = Destinations.Icon.Picker,
            argument = Destinations.Icon.Picker.RequestId
        )
            .flatMapLatest { requestId ->
                pickAction.map { pick ->
                    logger.d("state, requestId=$requestId, pick=$pick")
                    requestId to pick
                }
            }
            .filter { (requestId, _) -> requestId.value == this.requestId.get() }
            .mapNotNull { (_, pick) -> CategoryEditIconUseCase.State.Picked(pick.icon) }
            .onEach { navigator.back() }
}