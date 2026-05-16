package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultBulkBudgetSaveUseCaseTest {

    @Mock private lateinit var budgetRepository: BudgetRepository

    private val period = DateRange(LocalDate(2026, 5, 1), LocalDate(2026, 5, 31))
    private val type = BudgetType.EXPENSE

    private val catA = Id.Known("cat-a")
    private val catB = Id.Known("cat-b")
    private val catC = Id.Known("cat-c")

    private fun existing(id: String, categoryId: Id.Known, amount: BigDecimal) = BudgetRepository.Budget(
        id = Id.Known(id),
        categoryId = categoryId,
        type = type,
        amount = Amount(amount),
        periodStart = period.start,
        periodEnd = period.end,
    )

    private fun useCase() = DefaultBulkBudgetSaveUseCase(budgetRepository = budgetRepository)

    @Test
    fun `deletes existing budgets whose category is not in the new entries`() = runTest {
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForPeriod(period.start, period.end, type)))
            .thenReturn(flowOf(listOf(existing("b-a", catA, BigDecimal("100")), existing("b-b", catB, BigDecimal("50")))))

        useCase().save(
            period = period,
            type = type,
            entries = listOf(BulkBudgetSaveUseCase.Entry(catA, Amount(BigDecimal("120")))),
        )

        verify(budgetRepository).delete(Id.Known("b-b"))
        verify(budgetRepository, never()).delete(Id.Known("b-a"))
    }

    @Test
    fun `upserts entries preserving existing ids for surviving categories`() = runTest {
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForPeriod(period.start, period.end, type)))
            .thenReturn(flowOf(listOf(existing("b-a", catA, BigDecimal("100")))))

        useCase().save(
            period = period,
            type = type,
            entries = listOf(
                BulkBudgetSaveUseCase.Entry(catA, Amount(BigDecimal("120"))),
                BulkBudgetSaveUseCase.Entry(catC, Amount(BigDecimal("75"))),
            ),
        )

        val captor = argumentCaptor<List<BudgetRepository.BudgetInsert>>()
        verify(budgetRepository).insert(captor.capture())
        val inserts = captor.firstValue.associateBy { it.categoryId }
        assertEquals(Id.Known("b-a"), inserts.getValue(catA).id)
        assertEquals(Id.Unknown, inserts.getValue(catC).id)
        assertEquals(0, inserts.getValue(catA).amount.value.compareTo(BigDecimal("120")))
        assertEquals(0, inserts.getValue(catC).amount.value.compareTo(BigDecimal("75")))
    }

    @Test
    fun `empty entries deletes all existing rows for period`() = runTest {
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForPeriod(period.start, period.end, type)))
            .thenReturn(flowOf(listOf(existing("b-a", catA, BigDecimal("100")), existing("b-b", catB, BigDecimal("50")))))

        useCase().save(period, type, entries = emptyList())

        verify(budgetRepository).delete(Id.Known("b-a"))
        verify(budgetRepository).delete(Id.Known("b-b"))
        val captor = argumentCaptor<List<BudgetRepository.BudgetInsert>>()
        verify(budgetRepository).insert(captor.capture())
        assertEquals(emptyList<BudgetRepository.BudgetInsert>(), captor.firstValue)
    }

    @Test
    fun `empty existing inserts all entries with new ids`() = runTest {
        whenever(budgetRepository.query(any<BudgetRepository.Criteria.ForPeriod>()))
            .thenReturn(flowOf(emptyList()))

        useCase().save(
            period = period,
            type = type,
            entries = listOf(BulkBudgetSaveUseCase.Entry(catA, Amount(BigDecimal("10")))),
        )

        verify(budgetRepository, never()).delete(any())
        val captor = argumentCaptor<List<BudgetRepository.BudgetInsert>>()
        verify(budgetRepository).insert(captor.capture())
        assertEquals(Id.Unknown, captor.firstValue.single().id)
    }
}
