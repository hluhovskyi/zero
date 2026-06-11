package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Thin projection over [AnalyticsDetailUseCase]. Hardcodes the range to the last 6 months (works for
 * any range) and does all screen-shape math here — top-N/"Other" splitting, share %, trend → enum,
 * Amount → Float — so the ViewProvider performs zero arithmetic.
 */
internal class DefaultAnalyticsViewModel(
    private val analyticsDetailUseCase: AnalyticsDetailUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onSeeAllCategoriesHandler: OnSeeAllCategoriesHandler,
    private val onAnalyticsCategorySelectedHandler: OnAnalyticsCategorySelectedHandler,
    private val zonedClock: ZonedClock,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AnalyticsViewModel {

    private val mutableState = MutableStateFlow(AnalyticsViewModel.State())
    override val state: Flow<AnalyticsViewModel.State> = mutableState

    override fun perform(action: AnalyticsViewModel.Action) {
        when (action) {
            is AnalyticsViewModel.Action.SeeAllCategories ->
                coroutineScope.launch(Dispatchers.Main) { onSeeAllCategoriesHandler.onSeeAllCategories() }

            is AnalyticsViewModel.Action.SelectCategory ->
                coroutineScope.launch(Dispatchers.Main) { onAnalyticsCategorySelectedHandler.onSelected(action.categoryId) }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val currencySymbol = currencyPrimaryUseCase.getPrimaryCurrency().symbol
            mutableState.update { it.copy(currencySymbol = currencySymbol) }
            analyticsDetailUseCase.query(lastSixMonths()).collectLatest { analytics ->
                mutableState.update {
                    it.copy(
                        cashFlow = analytics.toCashFlow(),
                        breakdown = analytics.toBreakdown(),
                    )
                }
            }
        }
    }

    private fun lastSixMonths(): DateRange {
        val today = zonedClock.localDateTime().date
        val start = LocalDate(today.year, today.monthNumber, 1).minus(MONTHS_BACK, DateTimeUnit.MONTH)
        return DateRange(start = start, end = today)
    }

    private fun AnalyticsDetailUseCase.Analytics.toCashFlow() = AnalyticsViewModel.CashFlow(
        net = totalIn - totalOut,
        totalIn = totalIn,
        totalOut = totalOut,
        bars = cashFlow.map {
            AnalyticsViewModel.Bar(
                label = it.label,
                income = it.income.value.toFloat(),
                expense = it.expense.value.toFloat(),
            )
        },
    )

    private fun AnalyticsDetailUseCase.Analytics.toBreakdown(): AnalyticsViewModel.Breakdown? {
        val categories = breakdown.categories
        if (categories.isEmpty()) return null
        val total = breakdown.total
        val otherValue = categories.drop(DONUT_SLICES).fold(Amount.zero()) { sum, row -> sum + row.amount }
        val hasOther = otherValue > 0L

        val donut = categories.take(DONUT_SLICES)
            .map { AnalyticsViewModel.Slice(it.colorScheme, it.amount.value.toFloat()) }
            .plusOther(hasOther) { AnalyticsViewModel.Slice(colorScheme = null, value = otherValue.value.toFloat()) }

        val legend = categories.take(LEGEND_ITEMS)
            .map { AnalyticsViewModel.LegendItem(it.colorScheme, it.name, share(it.amount, total)) }
            .plusOther(hasOther) { AnalyticsViewModel.LegendItem(colorScheme = null, name = null, sharePercent = share(otherValue, total)) }

        val rows = categories.take(ROWS).map {
            AnalyticsViewModel.Row(
                categoryId = it.categoryId,
                name = it.name,
                icon = it.icon,
                colorScheme = it.colorScheme,
                amount = it.amount,
                sharePercent = share(it.amount, total),
                barFraction = fraction(it.amount, total),
                trend = trend(it.recentAmount, it.priorAmount),
            )
        }
        return AnalyticsViewModel.Breakdown(
            totalSpent = total,
            totalStyle = if (total > LARGE_TOTAL) AmountFormatter.Style.Short else AmountFormatter.Style.Whole,
            donut = donut,
            legend = legend,
            rows = rows,
            categoryCount = breakdown.categoryCount,
        )
    }

    private fun trend(recent: Amount, prior: Amount): AnalyticsViewModel.Trend {
        if (prior <= 0L) return AnalyticsViewModel.Trend.New
        val delta = (recent - prior).div(prior)
        return when {
            abs(delta) < FLAT_THRESHOLD -> AnalyticsViewModel.Trend.Flat
            delta > 0 -> AnalyticsViewModel.Trend.Up((delta * PERCENT).roundToInt())
            else -> AnalyticsViewModel.Trend.Down(abs(delta * PERCENT).roundToInt())
        }
    }

    private fun share(amount: Amount, total: Amount): Int = if (total > 0L) (amount.div(total) * PERCENT).roundToInt() else 0

    private fun fraction(amount: Amount, total: Amount): Float = if (total > 0L) amount.div(total).toFloat() else 0f

    private inline fun <T> List<T>.plusOther(hasOther: Boolean, other: () -> T): List<T> = if (hasOther) this + other() else this

    private companion object {
        const val MONTHS_BACK = 5 // current month + 5 prior = 6 buckets
        const val DONUT_SLICES = 6
        const val LEGEND_ITEMS = 3
        const val ROWS = 5
        const val PERCENT = 100.0
        const val FLAT_THRESHOLD = 0.02
        const val LARGE_TOTAL = 100_000L // above this the donut total switches to the compact style
    }
}
