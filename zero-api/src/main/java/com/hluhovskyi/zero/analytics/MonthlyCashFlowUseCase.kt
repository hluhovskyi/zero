package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Reusable monthly cash-flow aggregation: buckets transactions by calendar month over a date
 * range into income/expense totals (primary currency). One bucket per month in the range, empty
 * months included. Shared by the Analytics cash-flow hero and the Accounts net-worth trend.
 */
interface MonthlyCashFlowUseCase {

    fun query(range: DateRange): Flow<List<MonthBucket>>

    /** One month of cash flow. [label] is the short month name (e.g. "Apr"); amounts are primary. */
    data class MonthBucket(
        val label: String,
        val income: Amount,
        val expense: Amount,
    ) {
        /** Net effect on net worth for the month: income minus expense. */
        val net: Amount get() = income - expense
    }

    object Noop : MonthlyCashFlowUseCase {
        override fun query(range: DateRange): Flow<List<MonthBucket>> = emptyFlow()
    }
}
