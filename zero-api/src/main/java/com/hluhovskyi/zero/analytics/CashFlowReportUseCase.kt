package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Data for the cash-flow report: the [current] window's totals, monthly in/out buckets, and income
 * grouped by category, plus the [prior] window's totals for period-over-period. The view-shaping math
 * (net, savings rate + trend, income shares, deltas) lives in the ViewModel.
 */
interface CashFlowReportUseCase {

    fun query(current: DateRange, prior: DateRange): Flow<Report>

    data class Report(
        val totalIn: Amount,
        val totalOut: Amount,
        val months: List<MonthlyCashFlow>,
        val incomeSources: List<IncomeSource>,
        val priorTotalIn: Amount,
        val priorTotalOut: Amount,
    )

    /** One month bucket. [label] is the short month name (e.g. "Apr"). */
    data class MonthlyCashFlow(val label: String, val income: Amount, val expense: Amount)

    /** Income for one category over the current window, ranked by [amount] descending. */
    data class IncomeSource(
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val amount: Amount,
    )

    object Noop : CashFlowReportUseCase {
        override fun query(current: DateRange, prior: DateRange): Flow<Report> = emptyFlow()
    }
}
