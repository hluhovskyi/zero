package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.io.Closeable
import java.time.format.TextStyle
import java.util.Locale

private const val MONTHS_OF_TREND = 6

internal class DefaultCategoryDetailViewModel(
    private val categoryId: Id.Known,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val amountFormatter: AmountFormatter,
    private val onEditHandler: OnCategoryDetailEditHandler,
    private val onBackHandler: OnBackHandler,
    private val onCreateTransactionHandler: OnCategoryDetailCreateTransactionHandler,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : CategoryDetailViewModel {

    private val mutableState = MutableStateFlow(CategoryDetailViewModel.State())
    override val state: Flow<CategoryDetailViewModel.State> = mutableState

    override fun perform(action: CategoryDetailViewModel.Action) {
        when (action) {
            CategoryDetailViewModel.Action.Edit -> coroutineScope.launch(Dispatchers.Main) {
                onEditHandler.onEdit()
            }
            CategoryDetailViewModel.Action.Back -> coroutineScope.launch(Dispatchers.Main) {
                onBackHandler.onBack()
            }
            CategoryDetailViewModel.Action.CreateTransaction -> coroutineScope.launch(Dispatchers.Main) {
                onCreateTransactionHandler.onCreate()
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        val periodDate = LocalDate(today.year, today.month, 1)

        coroutineScope.launch {
            launch {
                val primaryCurrency = currencyPrimaryUseCase.getPrimaryCurrency()
                mutableState.update {
                    it.copy(
                        currencySymbol = primaryCurrency.symbol,
                        periodDate = periodDate,
                    )
                }
            }

            launch {
                categoriesQueryUseCase.queryById(categoryId).collectLatest { category ->
                    mutableState.update {
                        it.copy(
                            categoryName = category.name,
                            categoryIcon = category.icon,
                            categoryColorScheme = category.colorScheme,
                        )
                    }
                }
            }

            launch {
                categorySpendingUseCase.queryForCategory(categoryId, CategorySpendingUseCase.Period.CurrentMonth)
                    .collectLatest { spending ->
                        val total = spending?.totalAmount ?: Amount.zero()
                        val count = spending?.transactionCount ?: 0
                        mutableState.update {
                            it.copy(
                                totalAmount = total,
                                transactionCount = count,
                                averageAmount = if (count > 0) total / count else Amount.zero(),
                                largestAmount = spending?.largestTransactionAmount ?: Amount.zero(),
                            )
                        }
                    }
            }

            launch {
                val currencySymbol = currencyPrimaryUseCase.getPrimaryCurrency().symbol
                categorySpendingUseCase.queryMonthlyTrend(categoryId, MONTHS_OF_TREND).collectLatest { months ->
                    val lastIndex = months.lastIndex
                    mutableState.update { state ->
                        state.copy(
                            trend = months.mapIndexed { index, bucket ->
                                CategoryDetailViewModel.TrendPoint(
                                    value = bucket.totalAmount.value.toFloat(),
                                    label = bucket.month
                                        .toJavaLocalDate()
                                        .month
                                        .getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    amountLabel = amountFormatter.format(bucket.totalAmount, currencySymbol),
                                    isCurrent = index == lastIndex,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
