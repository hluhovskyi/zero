package com.hluhovskyi.zero.transactions.breakdown

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

/** Ranks expenses matched by [filter] by category (primary currency); [trendSince] splits each row recent/prior. */
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
