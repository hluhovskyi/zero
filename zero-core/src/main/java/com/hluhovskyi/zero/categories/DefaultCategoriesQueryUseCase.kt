package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.observe
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import java.math.BigDecimal
import kotlin.math.exp
import kotlin.math.ln

internal class DefaultCategoriesQueryUseCase(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val transactionRepository: TransactionRepository,
    private val configurationRepository: ConfigurationRepository,
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
                idToColors = idToColors,
            )
        }
    }

    override fun queryAll(): Flow<List<CategoriesQueryUseCase.Category>> = queryAll

    override fun queryById(id: Id.Known): Flow<CategoriesQueryUseCase.Category> = queryAll
        .mapNotNull { categories -> categories.firstOrNull { it.id == id } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun queryRanked(
        signals: Flow<CategoriesQueryUseCase.RankSignal>,
    ): Flow<List<CategoriesQueryUseCase.Category>> {
        val effectiveSignals = configurationRepository
            .observe(CategoryConfigurationKey.RankingSignalsEnabled)
            .flatMapLatest { enabled -> if (enabled) signals else emptyFlow() }

        val signalState = effectiveSignals.runningFold(SignalState()) { state, signal ->
            when (signal) {
                is CategoriesQueryUseCase.RankSignal.AccountChanged ->
                    state.copy(accountId = signal.accountId)
                is CategoriesQueryUseCase.RankSignal.DateChanged ->
                    state.copy(date = signal.date)
                is CategoriesQueryUseCase.RankSignal.AmountChanged ->
                    state.copy(amount = signal.amount)
            }
        }

        return signalState.flatMapLatest { state ->
            val accountStatsFlow = state.accountId?.let {
                transactionRepository.query(
                    TransactionRepository.Criteria.CategoryUsageStatisticsByAccount(it),
                )
            } ?: flowOf(emptyList())

            val monthStatsFlow = state.date?.let {
                transactionRepository.query(
                    TransactionRepository.Criteria.CategoryUsageStatisticsByMonth(it.monthNumber),
                )
            } ?: flowOf(emptyList())

            val amountStatsFlow = transactionRepository.query(
                TransactionRepository.Criteria.CategoryAmountStatistics(),
            )

            combine(
                queryAll,
                transactionRepository.query(TransactionRepository.Criteria.CategoryUsageStatistics()),
                accountStatsFlow,
                monthStatsFlow,
                amountStatsFlow,
            ) { categories, globalStats, accountStats, monthStats, amountStats ->
                rankCategories(categories, globalStats, accountStats, monthStats, amountStats, state.amount)
            }
        }
    }

    private fun rankCategories(
        categories: List<CategoriesQueryUseCase.Category>,
        globalStats: List<TransactionRepository.CategoryUsageStatistic>,
        accountStats: List<TransactionRepository.CategoryUsageStatistic>,
        monthStats: List<TransactionRepository.CategoryUsageStatistic>,
        amountStats: List<TransactionRepository.CategoryAmountStatistic>,
        enteredAmount: BigDecimal?,
    ): List<CategoriesQueryUseCase.Category> {
        val globalById = globalStats.associateBy { it.categoryId }
        val accountById = accountStats.associateBy { it.categoryId }
        val monthById = monthStats.associateBy { it.categoryId }
        val amountById = amountStats.associateBy { it.categoryId }
        val nowInstant = clock.now()
        val timeZone = zoneProvider.timeZone()

        val (used, unused) = categories.partition { globalById.containsKey(it.id) }

        val scored = used
            .map { category ->
                val globalStat = globalById.getValue(category.id)
                val daysSinceLastUse = (nowInstant - globalStat.lastUsedDateTime.toInstant(timeZone))
                    .inWholeDays.toDouble()
                val recencyDecay = exp(-daysSinceLastUse / DECAY_PERIOD_DAYS)
                val baseScore = globalStat.transactionCount * recencyDecay

                val accountMultiplier = accountById[category.id]?.let { accountStat ->
                    1.0 + accountStat.transactionCount.toDouble() / globalStat.transactionCount
                } ?: 1.0

                val monthMultiplier = monthById[category.id]?.let { monthStat ->
                    1.0 + MONTH_WEIGHT * monthStat.transactionCount.toDouble() / globalStat.transactionCount
                } ?: 1.0

                val amountMultiplier = amountProximityMultiplier(enteredAmount, amountById[category.id])

                val score = baseScore * accountMultiplier * monthMultiplier * amountMultiplier
                category to score
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val alphabetical = unused.sortedBy { it.name }
        return scored + alphabetical
    }

    private fun amountProximityMultiplier(
        enteredAmount: BigDecimal?,
        amountStat: TransactionRepository.CategoryAmountStatistic?,
    ): Double {
        if (enteredAmount == null || amountStat == null) return 1.0
        val entered = enteredAmount.toDouble()
        val average = amountStat.averageAmount.toDouble()
        if (entered <= 0.0 || average <= 0.0) return 1.0
        val logRatio = ln(entered / average)
        val proximity = exp(-(logRatio * logRatio) / (2.0 * AMOUNT_SIGMA * AMOUNT_SIGMA))
        return 1.0 + AMOUNT_WEIGHT * proximity
    }

    private fun resolve(
        category: CategoryRepository.Category,
        idToIcons: Map<Id.Known, Icon>,
        idToColors: Map<Id.Known, Color>,
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
            type = category.type,
        )
    }

    private data class SignalState(
        val accountId: Id.Known? = null,
        val date: LocalDate? = null,
        val amount: BigDecimal? = null,
    )

    private companion object {
        const val DECAY_PERIOD_DAYS = 30.0
        const val MONTH_WEIGHT = 0.5
        const val AMOUNT_WEIGHT = 0.75
        const val AMOUNT_SIGMA = 1.0
    }
}
