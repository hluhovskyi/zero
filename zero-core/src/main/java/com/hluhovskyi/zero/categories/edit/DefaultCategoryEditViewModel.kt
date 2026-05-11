package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryEditViewModel(
    private val categoryId: Id,
    private val initialType: CategoryType = CategoryType.EXPENSE,
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val categoryEditIconUseCase: CategoryEditIconUseCase,
    private val categoryEditColorUseCase: CategoryEditColorUseCase,
    private val onCategorySavedHandler: OnCategorySavedHandler,
    private val ioCoroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : CategoryEditViewModel {

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<CategoryEditViewModel.State> = mutableState.map { state ->
        CategoryEditViewModel.State(
            name = state.name,
            icon = state.icon,
            colorScheme = state.colorScheme,
            type = state.type,
        )
    }

    override fun perform(action: CategoryEditViewModel.Action) {
        when (action) {
            is CategoryEditViewModel.Action.ChangeName ->
                mutableState.update { it.copy(name = action.name) }
            is CategoryEditViewModel.Action.SelectIcon ->
                categoryEditIconUseCase.perform(
                    CategoryEditIconUseCase.Action.Request(
                        colorId = mutableState.value.colorId,
                        iconId = mutableState.value.iconId,
                    ),
                )
            is CategoryEditViewModel.Action.SelectColor ->
                categoryEditColorUseCase.perform(CategoryEditColorUseCase.Action.Request)
            is CategoryEditViewModel.Action.SelectType ->
                mutableState.update { it.copy(type = action.type) }
            is CategoryEditViewModel.Action.Save -> ioCoroutineScope.launch {
                val state = mutableState.value
                categoryRepository.insert(
                    CategoryRepository.CategoryInsert(
                        id = categoryId,
                        parentCategoryId = Id.Unknown,
                        name = state.name,
                        iconId = state.iconId,
                        colorId = state.colorId,
                        type = state.type,
                    ),
                )
                launch(context = Dispatchers.Main) { onCategorySavedHandler.onSaved() }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        ioCoroutineScope.launch {
            if (categoryId is Id.Known) {
                launch {
                    val category = categoryRepository.query(CategoryRepository.Criteria.ById(categoryId)).firstOrNull()
                    if (category != null) {
                        val colorId = (category.colorId as? Id.Known) ?: ColorRepository.unknownCategoryColorId()
                        val iconId = (category.iconId as? Id.Known) ?: IconRepository.unknownCategoryIconId()
                        combine(
                            colorRepository.query(ColorRepository.Criteria.ById(colorId)),
                            iconRepository.query(IconRepository.Criteria.ById(iconId)),
                        ) { color, icon -> color to icon }
                            .firstOrNull()?.let { (color, icon) ->
                                mutableState.update { state ->
                                    state.copy(
                                        name = category.name,
                                        iconId = icon.id,
                                        icon = icon.image,
                                        colorId = color.id,
                                        colorScheme = colorRepository.schemeFor(color.id),
                                        type = category.type,
                                    )
                                }
                            }
                    }
                }
            } else {
                mutableState.update { it.copy(type = initialType) }
                launch {
                    iconRepository.query(IconRepository.Criteria.ById(IconRepository.unknownCategoryIconId()))
                        .firstOrNull()?.let { icon ->
                            mutableState.update { it.copy(iconId = icon.id, icon = icon.image) }
                        }
                }
                launch {
                    colorRepository.query(ColorRepository.Criteria.ById(ColorRepository.unknownCategoryColorId()))
                        .firstOrNull()?.let { color ->
                            mutableState.update { it.copy(colorId = color.id, colorScheme = colorRepository.schemeFor(color.id)) }
                        }
                }
            }

            launch(context = Dispatchers.Main) {
                categoryEditIconUseCase.state
                    .filterIsInstance<CategoryEditIconUseCase.State.Picked>()
                    .collectLatest { iconState ->
                        mutableState.update { it.copy(iconId = iconState.icon.id, icon = iconState.icon.image) }
                    }
            }

            launch(context = Dispatchers.Main) {
                categoryEditColorUseCase.state
                    .filterIsInstance<CategoryEditColorUseCase.State.Picked>()
                    .collectLatest { colorState ->
                        mutableState.update { it.copy(colorId = colorState.color.id, colorScheme = colorRepository.schemeFor(colorState.color.id)) }
                    }
            }
        }
    }

    private data class CompositeState(
        val name: String = "",
        val iconId: Id = Id.Unknown,
        val icon: Image = Image.empty(),
        val colorId: Id = Id.Unknown,
        val colorScheme: ColorScheme = ColorScheme(
            swatch = Color.empty(),
            primary = Color.empty(),
            background = Color.empty(),
        ),
        val type: CategoryType = CategoryType.EXPENSE,
    )
}
