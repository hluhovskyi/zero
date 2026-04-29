package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

internal class DefaultCategorySpendingUseCase(
    private val transactionRepository: TransactionRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : CategorySpendingUseCase {

    override fun query(period: CategorySpendingUseCase.Period): Flow<List<CategorySpendingUseCase.CategorySpending>> {
        val (from, to) = period.resolve()
        return transactionRepository
            .query(TransactionRepository.Criteria.CategorySpendingBetween(from = from, to = to))
            .flatMapLatest { rows -> flow { emit(aggregate(rows)) } }
    }

    private suspend fun aggregate(
        rows: List<TransactionRepository.CategorySpendingStatistic>,
    ): List<CategorySpendingUseCase.CategorySpending> {
        val totals = mutableMapOf<Id.Known, Pair<Amount, Int>>()
        for (row in rows) {
            val converted = currencyConvertUseCase.convertToPrimary(row.totalAmount, row.currencyId)
            val (prev, prevCount) = totals[row.categoryId] ?: Pair(Amount.zero(), 0)
            totals[row.categoryId] = Pair(prev + converted, prevCount + row.transactionCount)
        }
        return totals.map { (categoryId, pair) ->
            CategorySpendingUseCase.CategorySpending(
                categoryId = categoryId,
                totalAmount = pair.first,
                transactionCount = pair.second,
            )
        }
    }

    private fun CategorySpendingUseCase.Period.resolve(): Pair<LocalDate, LocalDate> {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        return when (this) {
            is CategorySpendingUseCase.Period.CurrentMonth ->
                LocalDate(today.year, today.month, 1) to today
            is CategorySpendingUseCase.Period.CurrentWeek ->
                today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) to today
            is CategorySpendingUseCase.Period.CurrentYear ->
                LocalDate(today.year, 1, 1) to today
            is CategorySpendingUseCase.Period.Between ->
                from to to
        }
    }
}
