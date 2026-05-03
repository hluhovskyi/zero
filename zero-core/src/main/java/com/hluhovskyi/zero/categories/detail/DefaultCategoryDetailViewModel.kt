package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import java.io.Closeable
import java.math.BigDecimal
import java.math.RoundingMode

internal class DefaultCategoryDetailViewModel(
    private val categoryId: Id.Known,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val transactionRepository: TransactionRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onEditHandler: OnCategoryDetailEditHandler,
    private val onBackHandler: OnCategoryDetailBackHandler,
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
        }
    }

    override fun attach(): Closeable = Closeables.of {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        val monthStart = LocalDate(today.year, today.month, 1)
        val periodLabel = today.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() } + " " + today.year

        coroutineScope.launch {
            launch {
                flow { emit(currencyPrimaryUseCase.getPrimaryCurrency()) }
                    .collectLatest { primaryCurrency ->
                        mutableState.update {
                            it.copy(
                                currencySymbol = primaryCurrency.symbol,
                                periodLabel = periodLabel,
                            )
                        }
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
                categorySpendingUseCase.query(CategorySpendingUseCase.Period.CurrentMonth)
                    .collectLatest { spendingList ->
                        val spending = spendingList.firstOrNull { it.categoryId == categoryId }
                        val total = spending?.totalAmount ?: Amount.zero()
                        val count = spending?.transactionCount ?: 0
                        val average = if (count > 0) {
                            Amount(total.value.divide(BigDecimal(count), 2, RoundingMode.HALF_UP))
                        } else {
                            Amount.zero()
                        }
                        mutableState.update {
                            it.copy(
                                totalAmount = total,
                                transactionCount = count,
                                averageAmount = average,
                            )
                        }
                    }
            }

            launch {
                transactionRepository.query(TransactionRepository.Criteria.ForCategory(categoryId))
                    .collectLatest { transactions ->
                        val largest = transactions
                            .filter { tx -> tx.dateTime.date >= monthStart && tx.dateTime.date <= today }
                            .filterNot { it is TransactionRepository.Transaction.Transfer }
                            .fold(Amount.zero()) { max, tx ->
                                val converted = currencyConvertUseCase.convertToPrimary(tx.amount, tx.currencyId)
                                if (converted > max) converted else max
                            }
                        mutableState.update { it.copy(largestAmount = largest) }
                    }
            }
        }
    }
}
