package com.hluhovskyi.zero.categories.edit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class DefaultCategoryEditIconUseCase : CategoryEditIconUseCase {

    private val mutableState = MutableStateFlow<CategoryEditIconUseCase.State>(CategoryEditIconUseCase.State.None)
    override val state: Flow<CategoryEditIconUseCase.State> = mutableState

    override fun perform(action: CategoryEditIconUseCase.Action) {
        when (action) {
            is CategoryEditIconUseCase.Action.Request -> {
                mutableState.value = CategoryEditIconUseCase.State.Request
            }
            is CategoryEditIconUseCase.Action.Pick -> {
                mutableState.value = CategoryEditIconUseCase.State.Picked(action.icon)
            }
        }
    }
}