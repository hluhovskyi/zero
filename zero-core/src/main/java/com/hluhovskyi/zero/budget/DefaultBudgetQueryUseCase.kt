package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class DefaultBudgetQueryUseCase(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val budgetRepository: BudgetRepository,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val periodResolver: PeriodResolver,
) : BudgetQueryUseCase {

    override fun observeAnyOver(): Flow<Boolean> {
        val (start, end) = periodResolver.currentMonth()
        return query(start, end).map { rows ->
            rows.any { it.budgetId != null && it.spent > it.budgeted }
        }
    }

    override fun query(from: LocalDate, to: LocalDate): Flow<List<BudgetQueryUseCase.Budgeted>> = combine(
        categoriesQueryUseCase.queryAll().onStartWithEmptyList(),
        budgetRepository.query(BudgetRepository.Criteria.ForPeriod(from, to)).onStartWithEmptyList(),
        categorySpendingUseCase.query(CategorySpendingUseCase.Period.Between(from, to)).onStartWithEmptyList(),
    ) { categories, budgets, spending ->
        val budgetsByCategory = budgets.associateBy { it.categoryId }
        val spendingByCategory = spending.associateBy { it.categoryId }
        categories
            .filter { it.type == CategoryType.EXPENSE }
            .map { category ->
                val budget = budgetsByCategory[category.id]
                val spent = spendingByCategory[category.id]?.totalAmount ?: Amount.zero()
                BudgetQueryUseCase.Budgeted(
                    categoryId = category.id,
                    categoryName = category.name,
                    icon = category.icon,
                    colorScheme = category.colorScheme,
                    spent = spent,
                    budgetId = budget?.id,
                    budgeted = budget?.amount ?: Amount.zero(),
                )
            }
    }
}
