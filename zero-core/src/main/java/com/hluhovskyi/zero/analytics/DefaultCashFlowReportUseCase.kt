package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Reuses [AnalyticsDetailUseCase] for both windows' totals + monthly buckets (no re-bucketing), and
 * adds income-by-category for the current window — the one thing the shared, expense-only spending
 * breakdown doesn't provide.
 */
internal class DefaultCashFlowReportUseCase(
    private val analyticsDetailUseCase: AnalyticsDetailUseCase,
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
) : CashFlowReportUseCase {

    override fun query(current: DateRange, prior: DateRange): Flow<CashFlowReportUseCase.Report> = combine(
        analyticsDetailUseCase.query(current),
        analyticsDetailUseCase.query(prior),
        incomeByCategory(current),
    ) { currentAnalytics, priorAnalytics, incomeSources ->
        CashFlowReportUseCase.Report(
            totalIn = currentAnalytics.totalIn,
            totalOut = currentAnalytics.totalOut,
            months = currentAnalytics.cashFlow.map {
                CashFlowReportUseCase.MonthlyCashFlow(label = it.label, income = it.income, expense = it.expense)
            },
            incomeSources = incomeSources,
            priorTotalIn = priorAnalytics.totalIn,
            priorTotalOut = priorAnalytics.totalOut,
        )
    }

    private fun incomeByCategory(range: DateRange): Flow<List<CashFlowReportUseCase.IncomeSource>> {
        val filter = TransactionFilterCriteria(from = range.start, to = range.end)
        val criteria = TransactionRepository.Criteria.Filtered(filter = filter, type = TransactionRepository.Type.Income)
        return combine(
            transactionRepository.query(criteria),
            categoriesQueryUseCase.queryAll().onStartWithEmptyList(),
        ) { transactions, categories ->
            aggregate(transactions, categories)
        }
    }

    private suspend fun aggregate(
        transactions: List<TransactionRepository.Transaction>,
        categories: List<CategoriesQueryUseCase.Category>,
    ): List<CashFlowReportUseCase.IncomeSource> {
        val categoriesById = categories.associateBy { it.id }
        val byCategory = LinkedHashMap<Id.Known, Amount>()
        transactions.forEach { transaction ->
            if (transaction !is TransactionRepository.Transaction.Income) return@forEach
            if (transaction.categoryId !in categoriesById) return@forEach
            val converted = currencyConvertUseCase.convertToPrimary(transaction.amount, transaction.currencyId)
            byCategory[transaction.categoryId] = (byCategory[transaction.categoryId] ?: Amount.zero()) + converted
        }
        return byCategory
            .mapNotNull { (categoryId, amount) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                CashFlowReportUseCase.IncomeSource(
                    name = category.name,
                    icon = category.icon,
                    colorScheme = category.colorScheme,
                    amount = amount,
                )
            }
            .sortedByDescending { it.amount.value }
    }
}
