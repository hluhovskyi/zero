package com.hluhovskyi.zero.activity.screens

import androidx.navigation.NavController
import com.hluhovskyi.zero.categories.edit.CategoryEditIconUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.random.Random

internal class NavControllerCategoryEditIconUseCase(
    private val navController: NavController,
    private val requestIdGenerator: () -> Int = { Random.nextInt() }
) : CategoryEditIconUseCase {

    private var requestId: String = ""
    private val mutableState = MutableSharedFlow<CategoryEditIconUseCase.State>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun perform(action: CategoryEditIconUseCase.Action) {
        when (action) {
            is CategoryEditIconUseCase.Action.Request -> {
                requestId = requestIdGenerator().toString()
                navController.navigate(
                    Destination.Icon.Picker.routeWith(
                        Destination.Icon.Picker.RequestId.withValue(requestId)
                    )
                )
            }
            is CategoryEditIconUseCase.Action.Pick -> {
                val requestId = navController.currentBackStackEntry
                    ?.arguments
                    ?.getString("requestId")

                if (requestId == this.requestId) {
                    mutableState.tryEmit(
                        CategoryEditIconUseCase.State.Picked(
                            icon = action.icon
                        )
                    )
                    navController.popBackStack()
                }
            }
        }
    }

    override val state: Flow<CategoryEditIconUseCase.State> = mutableState
}