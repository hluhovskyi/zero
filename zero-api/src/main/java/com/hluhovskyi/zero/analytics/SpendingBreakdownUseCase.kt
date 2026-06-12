package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

/** Ranks [filter]-scoped expenses (primary currency) by category and by account; [trendSince] splits categories recent/prior. */
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
        // Defaulted: category-only consumers (the hub) need not set it.
        val accounts: List<AccountSpend> = emptyList(),
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

    data class AccountSpend(
        val accountId: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val amount: Amount,
        val transactionCount: Int,
    )

    object Noop : SpendingBreakdownUseCase {
        override fun query(filter: TransactionFilterCriteria, trendSince: LocalDate?): Flow<Breakdown> = emptyFlow()
    }
}
