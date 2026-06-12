package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AnalyticsDetailUseCase {

    fun query(range: DateRange): Flow<Analytics>

    data class Analytics(
        val totalIn: Amount,
        val totalOut: Amount,
        val cashFlow: List<MonthlyCashFlowUseCase.MonthBucket>,
        val breakdown: SpendingBreakdownUseCase.Breakdown,
    )

    object Noop : AnalyticsDetailUseCase {
        override fun query(range: DateRange): Flow<Analytics> = emptyFlow()
    }
}
