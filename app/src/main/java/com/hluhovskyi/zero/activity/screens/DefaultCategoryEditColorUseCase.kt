package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.categories.edit.CategoryEditColorUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

internal class DefaultCategoryEditColorUseCase(
    private val navigator: Navigator,
) : CategoryEditColorUseCase {

    private val pickAction = MutableSharedFlow<CategoryEditColorUseCase.Action.Pick>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: CategoryEditColorUseCase.Action) {
        when (action) {
            is CategoryEditColorUseCase.Action.Request -> {
                navigator.navigateTo(destination = Destinations.Color.Picker)
            }
            is CategoryEditColorUseCase.Action.Pick -> {
                pickAction.tryEmit(action)
            }
        }
    }

    override val state: Flow<CategoryEditColorUseCase.State> = pickAction
        .map { action -> CategoryEditColorUseCase.State.Picked(action.color) }
}
