package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconCategory
import com.hluhovskyi.zero.icons.IconPickerSection
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryEditViewModel(
    private val categoryId: Id,
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onCategorySavedHandler: OnCategorySavedHandler,
    private val ioCoroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : CategoryEditViewModel {

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<CategoryEditViewModel.State> = mutableState.map { state ->
        CategoryEditViewModel.State(
            name = state.name,
            icon = state.icon,
            colorScheme = state.colorScheme,
            pickerVisible = state.pickerVisible,
            iconSections = state.iconSections,
            colorSchemes = state.colorSchemes,
            selectedIcon = state.iconSections.flatMap { it.icons }.find { it.id == state.iconId },
        )
    }

    override fun perform(action: CategoryEditViewModel.Action) {
        when (action) {
            is CategoryEditViewModel.Action.ChangeName ->
                mutableState.update { it.copy(name = action.name) }
            is CategoryEditViewModel.Action.TogglePicker ->
                mutableState.update { it.copy(pickerVisible = !it.pickerVisible) }
            is CategoryEditViewModel.Action.PickIcon ->
                mutableState.update { it.copy(iconId = action.icon.id, icon = action.icon.image) }
            is CategoryEditViewModel.Action.PickColorScheme ->
                mutableState.update { it.copy(colorId = action.colorScheme.swatch.id, colorScheme = action.colorScheme) }
            is CategoryEditViewModel.Action.Save -> ioCoroutineScope.launch {
                val state = mutableState.value
                categoryRepository.insert(
                    CategoryRepository.CategoryInsert(
                        id = categoryId,
                        parentCategoryId = Id.Unknown,
                        name = state.name,
                        iconId = state.iconId,
                        colorId = state.colorId,
                    ),
                )
                launch(context = Dispatchers.Main) { onCategorySavedHandler.onSaved() }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        ioCoroutineScope.launch {
            launch { loadIconSections() }
            launch { loadColorSchemes() }
            if (categoryId is Id.Known) {
                launch { loadExistingCategory() }
            } else {
                launch { loadDefaults() }
            }
        }
    }

    private suspend fun loadIconSections() {
        iconRepository.query(IconRepository.Criteria.All())
            .collectLatest { icons ->
                val sections = icons
                    .groupBy { it.category }
                    .filter { (category, _) -> category != IconCategory.system() }
                    .map { (category, categoryIcons) -> IconPickerSection(category, categoryIcons) }
                mutableState.update { it.copy(iconSections = sections) }
            }
    }

    private suspend fun loadColorSchemes() {
        colorRepository.query(ColorRepository.Criteria.AllSchemes())
            .collectLatest { schemes ->
                mutableState.update { it.copy(colorSchemes = schemes) }
            }
    }

    private suspend fun loadExistingCategory() {
        val category = categoryRepository.query(
            CategoryRepository.Criteria.ById(categoryId as Id.Known)
        ).firstOrNull() ?: return
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
                    )
                }
            }
    }

    private suspend fun loadDefaults() {
        coroutineScope {
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
        val pickerVisible: Boolean = false,
        val iconSections: List<IconPickerSection> = emptyList(),
        val colorSchemes: List<ColorScheme> = emptyList(),
    )
}
