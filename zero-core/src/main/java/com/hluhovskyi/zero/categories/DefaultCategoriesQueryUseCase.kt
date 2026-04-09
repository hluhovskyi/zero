package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.math.exp

internal class DefaultCategoriesQueryUseCase(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
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

    override fun queryById(id: Id.Known): Flow<CategoriesQueryUseCase.Category> = queryAll
        .mapNotNull { categories -> categories.firstOrNull { it.id == id } }

    override fun queryRanked(
        signals: Flow<CategoriesQueryUseCase.RankSignal>,
    ): Flow<List<CategoriesQueryUseCase.Category>> = combine(
        queryAll,
        transactionRepository.query(TransactionRepository.Criteria.CategoryUsageStatistics()),
    ) { categories, usageStatistics ->
        rankCategories(categories, usageStatistics)
    }

    private fun rankCategories(
        categories: List<CategoriesQueryUseCase.Category>,
        usageStatistics: List<TransactionRepository.CategoryUsageStatistic>,
    ): List<CategoriesQueryUseCase.Category> {
        val statsById = usageStatistics.associateBy { it.categoryId }
        val nowInstant = clock.now()
        val timeZone = zoneProvider.timeZone()

        val (used, unused) = categories.partition { statsById.containsKey(it.id) }

        val scored = used
            .map { category ->
                val stat = statsById.getValue(category.id)
                val daysSinceLastUse = (nowInstant - stat.lastUsedDateTime.toInstant(timeZone))
                    .inWholeDays.toDouble()
                val recencyDecay = exp(-daysSinceLastUse / DECAY_PERIOD_DAYS)
                val score = stat.transactionCount * recencyDecay
                category to score
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val alphabetical = unused.sortedBy { it.name }

        return scored + alphabetical
    }

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

        val colorScheme = color?.let { colorRepository.schemeFor(it.id) }
            ?: colorRepository.schemeFor(ColorRepository.unknownCategoryColorId())

        return CategoriesQueryUseCase.Category(
            id = category.id,
            name = category.name,
            icon = icon.image,
            colorScheme = colorScheme,
        )
    }

    private companion object {
        const val DECAY_PERIOD_DAYS = 30.0
    }
}
