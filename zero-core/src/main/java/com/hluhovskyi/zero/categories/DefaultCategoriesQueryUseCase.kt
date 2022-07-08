package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull

internal class DefaultCategoriesQueryUseCase(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
) : CategoriesQueryUseCase {

    private val queryAll = combine(
        categoryRepository.query(CategoryRepository.Criteria.All()),
        iconRepository.query(IconRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
        colorRepository.query(ColorRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
    ) { categories, idToIcons, idToColors ->
        categories.map { category ->
            resolve(
                category = category,
                idToIcons = idToIcons,
                idToColors = idToColors
            )
        }
    }

    override fun queryAll(): Flow<List<CategoriesQueryUseCase.Category>> = queryAll

    // TODO: Either share queryAll or use more specific queries
    override fun queryById(id: Id.Known): Flow<CategoriesQueryUseCase.Category> = queryAll
        .mapNotNull { categories -> categories.firstOrNull { it.id == id } }

    private fun resolve(
        category: CategoryRepository.Category,
        idToIcons: Map<Id.Known, Icon>,
        idToColors: Map<Id.Known, Color>
    ): CategoriesQueryUseCase.Category {
        val icon = idToIcons[category.iconId]
            ?: idToIcons[IconRepository.unknownCategoryIconId()]
            ?: Icon.empty()

        val color = idToColors[category.colorId]
            ?: idToColors[ColorRepository.unknownCategoryColorId()]

        return CategoriesQueryUseCase.Category(
            id = category.id,
            name = category.name,
            icon = icon.image,
            color = color?.value ?: ColorValue.unspecified()
        )
    }
}