package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.OnColorSelectedHandler
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

// Maps icon id values to their section title. Icons not in any list end up in "Other".
private val SECTION_DEFINITIONS: List<Pair<String, List<String>>> = listOf(
    "Money & Banking" to listOf("cash", "bank", "credit_card"),
    "Food & Drink" to listOf("flowers", "grocery", "fastfood"),
    "Travel" to listOf("car", "car_repair", "beach"),
    "Shopping" to listOf("diamond"),
    "Entertainment" to listOf("game_controller", "movie"),
    "Education" to listOf("book"),
)

internal class DefaultIconPickerViewModel(
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val onIconSelectedHandler: OnIconSelectedHandler,
    private val onColorSelectedHandler: OnColorSelectedHandler,
    private val colorId: Id = Id.Unknown,
    private val selectedIconId: Id = Id.Unknown,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : IconPickerViewModel {

    // Keeps the domain Color object for each ColorScheme so we can call onColorSelectedHandler
    // with the correct domain Color when the user taps a swatch.
    private var colorSchemeToColor: Map<ColorScheme, com.hluhovskyi.zero.colors.Color> = emptyMap()

    private val mutableState = MutableStateFlow(IconPickerViewModel.State())
    override val state: Flow<IconPickerViewModel.State> = mutableState

    override fun perform(action: IconPickerViewModel.Action) {
        when (action) {
            is IconPickerViewModel.Action.SelectIcon -> {
                onIconSelectedHandler.onIconSelected(action.icon)
            }
            is IconPickerViewModel.Action.SelectColorScheme -> {
                mutableState.update { it.copy(selectedColorScheme = action.colorScheme) }
                colorSchemeToColor[action.colorScheme]?.let { color ->
                    onColorSelectedHandler.onColorSelected(color)
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch { loadColors() }
        coroutineScope.launch { loadIcons() }
    }

    private suspend fun loadColors() {
        colorRepository.query(ColorRepository.Criteria.All())
            .collectLatest { colors ->
                val mapping = colors.associate { color ->
                    colorRepository.schemeFor(color.id) to color
                }
                colorSchemeToColor = mapping

                val selectedScheme = (colorId as? Id.Known)
                    ?.let { colorRepository.schemeFor(it) }
                    ?: ColorScheme.Grey

                mutableState.update { state ->
                    state.copy(
                        colorSchemes = mapping.keys.toList(),
                        selectedColorScheme = selectedScheme,
                    )
                }
            }
    }

    private suspend fun loadIcons() {
        iconRepository.query(IconRepository.Criteria.All())
            .collectLatest { icons ->
                val sections = buildSections(icons)
                val selectedIcon = (selectedIconId as? Id.Known)
                    ?.let { id -> icons.find { it.id == id } }

                mutableState.update { state ->
                    state.copy(
                        sections = sections,
                        selectedIcon = selectedIcon,
                    )
                }
            }
    }

    private fun buildSections(icons: List<Icon>): List<IconPickerSection> {
        val iconById = icons.associateBy { it.id.value }
        val sections = SECTION_DEFINITIONS.mapNotNull { (title, ids) ->
            val sectionIcons = ids.mapNotNull { iconById[it] }
            if (sectionIcons.isEmpty()) null else IconPickerSection(title, sectionIcons)
        }
        val assignedIds = SECTION_DEFINITIONS.flatMap { (_, ids) -> ids }.toSet()
        val other = icons.filter { it.id.value !in assignedIds }
        return if (other.isEmpty()) sections else sections + IconPickerSection("Other", other)
    }
}
