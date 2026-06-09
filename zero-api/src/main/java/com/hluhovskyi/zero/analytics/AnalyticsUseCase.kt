package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AnalyticsUseCase {

    fun query(range: DateRange): Flow<Analytics>

    data class Analytics(
        val totalIn: Amount,
        val totalOut: Amount,
        val cashFlow: List<CashFlowBucket>,
        val breakdown: List<CategorySpend>,
        val categoryCount: Int,
    )

    /** One month bucket of cash flow. [label] is the bucket's short month name (e.g. "Apr"). */
    data class CashFlowBucket(
        val label: String,
        val income: Amount,
        val expense: Amount,
    )

    /**
     * A single expense category's spend over the range, with the two halves that drive the
     * recent-vs-prior trend ([recentAmount] = second half, [priorAmount] = first half).
     */
    data class CategorySpend(
        val categoryId: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val amount: Amount,
        val transactionCount: Int,
        val recentAmount: Amount,
        val priorAmount: Amount,
    )

    object Noop : AnalyticsUseCase {
        override fun query(range: DateRange): Flow<Analytics> = emptyFlow()
    }
}
