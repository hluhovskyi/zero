package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Color
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryEditViewModel(
    private val categoryRepository: CategoryRepository,
    private val categoryEditIconUseCase: CategoryEditIconUseCase,
    private val ioCoroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    private val mainCoroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.Main),
) : CategoryEditViewModel {

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<CategoryEditViewModel.State> = mutableState
        .map { state ->
            CategoryEditViewModel.State(
                name = state.name,
                icon = state.icon,
                color = state.color,
            )
        }

    override fun perform(action: CategoryEditViewModel.Action) {
        when (action) {
            is CategoryEditViewModel.Action.ChangeName ->
                mutableState.update { state ->
                    state.copy(
                        name = action.name
                    )
                }
            is CategoryEditViewModel.Action.SelectIcon ->
                categoryEditIconUseCase.perform(CategoryEditIconUseCase.Action.Request)
            is CategoryEditViewModel.Action.SelectColor -> TODO()
            is CategoryEditViewModel.Action.Save -> ioCoroutineScope.launch {
                val state = mutableState.value
                categoryRepository.insert(
                    CategoryRepository.CategoryInsert(
                        parentCategoryId = Id.Unknown,
                        name = state.name,
                        iconId = state.iconId,
                        colorId = state.colorId,
                    )
                )
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        mainCoroutineScope.launch {
            categoryEditIconUseCase.state
                .filterIsInstance<CategoryEditIconUseCase.State.Picked>()
                .collectLatest { iconState ->
                    mutableState.update { state ->
                        state.copy(
                            iconId = iconState.icon.id,
                            icon = iconState.icon.image,
                        )
                    }
                }
        }
    }

    private data class CompositeState(
        val name: String = "",
        val iconId: Id = Id.Unknown,
        val icon: Image = Image.empty(),
        val colorId: Id = Id.Unknown,
        val color: Color = Color.unspecified(),
    )
}