package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

interface CategorySpendingUseCase {

    fun query(period: Period): Flow<List<CategorySpending>>

    fun queryForCategory(id: Id.Known, period: Period): Flow<CategorySpending?>

    /** [months] monthly buckets for a category, oldest → newest, zero-filled for empty months. */
    fun queryMonthlyTrend(id: Id.Known, months: Int): Flow<List<MonthlySpending>>

    data class CategorySpending(
        val categoryId: Id.Known,
        val totalAmount: Amount,
        val transactionCount: Int,
        val largestTransactionAmount: Amount = Amount.zero(),
    )

    data class MonthlySpending(
        val month: LocalDate, // first day of the month
        val totalAmount: Amount,
    )

    sealed class Period {
        object CurrentMonth : Period()
        object CurrentWeek : Period()
        object CurrentYear : Period()
        data class Between(val from: LocalDate, val to: LocalDate) : Period()
    }

    object Noop : CategorySpendingUseCase {
        override fun query(period: Period): Flow<List<CategorySpending>> = emptyFlow()
        override fun queryForCategory(id: Id.Known, period: Period): Flow<CategorySpending?> = emptyFlow()
        override fun queryMonthlyTrend(id: Id.Known, months: Int): Flow<List<MonthlySpending>> = emptyFlow()
    }
}
