package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AnalyticsUseCase {

    fun query(range: DateRange): Flow<Analytics>

    data class Analytics(
        val totalIn: Amount,
        val totalOut: Amount,
        val cashFlow: List<CashFlowBucket>,
        val breakdown: SpendingBreakdownUseCase.Breakdown,
    )

    /** One month bucket of cash flow. [label] is the bucket's short month name (e.g. "Apr"). */
    data class CashFlowBucket(
        val label: String,
        val income: Amount,
        val expense: Amount,
    )

    object Noop : AnalyticsUseCase {
        override fun query(range: DateRange): Flow<Analytics> = emptyFlow()
    }
}
