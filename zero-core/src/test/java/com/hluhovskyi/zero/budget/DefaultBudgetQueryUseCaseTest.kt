package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class DefaultBudgetQueryUseCaseTest {

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    @Mock private lateinit var budgetRepository: BudgetRepository

    @Mock private lateinit var categorySpendingUseCase: CategorySpendingUseCase

    private val from = LocalDate(2026, 5, 1)
    private val to = LocalDate(2026, 5, 31)

    private fun useCase() = DefaultBudgetQueryUseCase(
        categoriesQueryUseCase = categoriesQueryUseCase,
        budgetRepository = budgetRepository,
        categorySpendingUseCase = categorySpendingUseCase,
    )

    private fun category(id: String, type: CategoryType = CategoryType.EXPENSE) = CategoriesQueryUseCase.Category(
        id = Id.Known(id),
        name = "Cat $id",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        type = type,
    )

    private fun budget(id: String, categoryId: String, amount: BigDecimal) = BudgetRepository.Budget(
        id = Id.Known(id),
        categoryId = Id.Known(categoryId),
        type = BudgetType.EXPENSE,
        amount = Amount(amount),
        periodStart = from,
        periodEnd = to,
    )

    private fun spending(categoryId: String, total: BigDecimal) = CategorySpendingUseCase.CategorySpending(
        categoryId = Id.Known(categoryId),
        totalAmount = Amount(total),
        transactionCount = 1,
    )

    @Test
    fun `empty inputs return empty list`() = runTest {
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(flowOf(emptyList()))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria<List<BudgetRepository.Budget>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))

        val result = useCase().query(from, to).last()

        assertEquals(emptyList<BudgetQueryUseCase.Budgeted>(), result)
    }

    @Test
    fun `categories with no budgets return rows with zero budgeted`() = runTest {
        whenever(categoriesQueryUseCase.queryAll())
            .thenReturn(flowOf(listOf(category("c1"))))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria<List<BudgetRepository.Budget>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))

        val result = useCase().query(from, to).last()

        assertEquals(1, result.size)
        assertNull(result[0].budgetId)
        assertEquals(Amount.zero(), result[0].budgeted)
    }

    @Test
    fun `budget present attaches amount`() = runTest {
        whenever(categoriesQueryUseCase.queryAll())
            .thenReturn(flowOf(listOf(category("c1"))))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria<List<BudgetRepository.Budget>>>()))
            .thenReturn(flowOf(listOf(budget("b1", "c1", BigDecimal("250.00")))))
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))

        val result = useCase().query(from, to).last()

        assertEquals("b1", result[0].budgetId?.value)
        assertEquals(BigDecimal("250.00"), result[0].budgeted.value)
    }

    @Test
    fun `spending matched by category id`() = runTest {
        whenever(categoriesQueryUseCase.queryAll())
            .thenReturn(flowOf(listOf(category("c1"), category("c2"))))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria<List<BudgetRepository.Budget>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(categorySpendingUseCase.query(any()))
            .thenReturn(flowOf(listOf(spending("c1", BigDecimal("80.00")))))

        val result = useCase().query(from, to).last().associateBy { it.categoryId }

        assertEquals(BigDecimal("80.00"), result[Id.Known("c1")]!!.spent.value)
        assertEquals(Amount.zero(), result[Id.Known("c2")]!!.spent)
    }

    @Test
    fun `income categories are filtered out`() = runTest {
        whenever(categoriesQueryUseCase.queryAll())
            .thenReturn(flowOf(listOf(category("c1"), category("c2", CategoryType.INCOME))))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria<List<BudgetRepository.Budget>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))

        val result = useCase().query(from, to).last()

        assertEquals(listOf("c1"), result.map { it.categoryId.value })
    }
}
