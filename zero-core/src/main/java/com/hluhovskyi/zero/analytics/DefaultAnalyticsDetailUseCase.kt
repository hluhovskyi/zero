package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Composes the Analytics hub model: monthly cash flow (from the shared [MonthlyCashFlowUseCase])
 * plus the category breakdown (from the shared [SpendingBreakdownUseCase]).
 */
internal class DefaultAnalyticsDetailUseCase(
    private val monthlyCashFlowUseCase: MonthlyCashFlowUseCase,
    private val spendingBreakdownUseCase: SpendingBreakdownUseCase,
) : AnalyticsDetailUseCase {

    override fun query(range: DateRange): Flow<AnalyticsDetailUseCase.Analytics> {
        // The breakdown narrows to expenses itself; it only needs the date scope.
        val filter = TransactionFilterCriteria(from = range.start, to = range.end)
        val midpoint = range.start.plus(range.start.daysUntil(range.end) / 2, DateTimeUnit.DAY)
        return combine(
            monthlyCashFlowUseCase.query(range),
            spendingBreakdownUseCase.query(filter, trendSince = midpoint),
        ) { cashFlow, breakdown ->
            AnalyticsDetailUseCase.Analytics(
                totalIn = cashFlow.fold(Amount.zero()) { sum, bucket -> sum + bucket.income },
                totalOut = cashFlow.fold(Amount.zero()) { sum, bucket -> sum + bucket.expense },
                cashFlow = cashFlow,
                breakdown = breakdown,
            )
        }
    }
}
