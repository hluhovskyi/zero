package com.hluhovskyi.zero.transactions.breakdown

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

/**
 * Shared spending breakdown: ranks the expenses matched by [filter] by category, in the primary
 * currency. Income and transfers are excluded. Consumed by the Analytics hub (date-range scoped) and
 * the scoped Spending report (filter scoped) so both rank spend the same way — and both speak in the
 * domain [TransactionFilterCriteria], independent of the data layer. When [trendSince] is non-null
 * each row also carries its recent (on/after [trendSince]) vs prior (before) sub-totals, for a
 * recent-vs-prior trend.
 */
interface SpendingBreakdownUseCase {

    fun query(
        filter: TransactionFilterCriteria,
        trendSince: LocalDate? = null,
    ): Flow<Breakdown>

    data class Breakdown(
        val total: Amount,
        val transactionCount: Int,
        val categoryCount: Int,
        val categories: List<CategorySpend>,
    )

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

    object Noop : SpendingBreakdownUseCase {
        override fun query(filter: TransactionFilterCriteria, trendSince: LocalDate?): Flow<Breakdown> = emptyFlow()
    }
}
