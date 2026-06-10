package com.hluhovskyi.zero.transactions.breakdown

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

internal class DefaultSpendingBreakdownUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
) : SpendingBreakdownUseCase {

    override fun query(
        filter: TransactionFilterCriteria,
        trendSince: LocalDate?,
    ): Flow<SpendingBreakdownUseCase.Breakdown> = combine(
        transactionRepository.query(filter.toExpenseCriteria()),
        categoriesQueryUseCase.queryAll().onStartWithEmptyList(),
    ) { transactions, categories ->
        aggregate(transactions, categories, trendSince)
    }

    private fun TransactionFilterCriteria.toExpenseCriteria() = TransactionRepository.Criteria.Filtered(filter = this, type = TransactionRepository.Type.Expense)

    private suspend fun aggregate(
        transactions: List<TransactionRepository.Transaction>,
        categories: List<CategoriesQueryUseCase.Category>,
        trendSince: LocalDate?,
    ): SpendingBreakdownUseCase.Breakdown {
        val categoriesById = categories.associateBy { it.id }
        val accumulators = LinkedHashMap<Id.Known, SpendAccumulator>()
        var total = Amount.zero()
        var transactionCount = 0
        transactions.forEach { transaction ->
            if (transaction !is TransactionRepository.Transaction.Expense) return@forEach
            if (transaction.categoryId !in categoriesById) return@forEach
            val converted = currencyConvertUseCase.convertToPrimary(transaction.amount, transaction.currencyId)
            val accumulator = accumulators.getOrPut(transaction.categoryId) { SpendAccumulator() }
            accumulator.amount += converted
            accumulator.count += 1
            total += converted
            transactionCount += 1
            if (trendSince != null) {
                if (transaction.dateTime.date >= trendSince) {
                    accumulator.recent += converted
                } else {
                    accumulator.prior += converted
                }
            }
        }
        val ranked = accumulators
            .mapNotNull { (categoryId, accumulator) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                SpendingBreakdownUseCase.CategorySpend(
                    categoryId = categoryId,
                    name = category.name,
                    icon = category.icon,
                    colorScheme = category.colorScheme,
                    amount = accumulator.amount,
                    transactionCount = accumulator.count,
                    recentAmount = accumulator.recent,
                    priorAmount = accumulator.prior,
                )
            }
            .sortedByDescending { it.amount.value }
        return SpendingBreakdownUseCase.Breakdown(
            total = total,
            transactionCount = transactionCount,
            categoryCount = categories.count { it.type == CategoryType.EXPENSE },
            categories = ranked,
        )
    }

    private class SpendAccumulator(
        var amount: Amount = Amount.zero(),
        var count: Int = 0,
        var recent: Amount = Amount.zero(),
        var prior: Amount = Amount.zero(),
    )
}
