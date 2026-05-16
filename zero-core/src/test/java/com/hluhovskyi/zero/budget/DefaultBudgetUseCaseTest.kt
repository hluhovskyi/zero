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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultBudgetUseCaseTest {

    @Mock private lateinit var budgetRepository: BudgetRepository
    @Mock private lateinit var budgetQueryUseCase: BudgetQueryUseCase

    private val today = LocalDate(2026, 5, 15)
    private val currentStart = LocalDate(2026, 5, 1)
    private val currentEnd = LocalDate(2026, 5, 31)
    private val previousStart = LocalDate(2026, 4, 1)
    private val previousEnd = LocalDate(2026, 4, 30)
    private val type = BudgetType.EXPENSE

    private val catA = Id.Known("cat-a")
    private val catB = Id.Known("cat-b")
    private val catC = Id.Known("cat-c")

    private val periodResolver = object : PeriodResolver {
        override fun today(): LocalDate = today
        override fun currentMonth(): Pair<LocalDate, LocalDate> = currentStart to currentEnd
        override fun monthOffsetFrom(reference: LocalDate, offsetMonths: Int): Pair<LocalDate, LocalDate> =
            when (offsetMonths) {
                0 -> currentStart to currentEnd
                -1 -> previousStart to previousEnd
                else -> error("unexpected offset $offsetMonths")
            }
    }

    private fun budget(
        id: String,
        categoryId: Id.Known,
        amount: BigDecimal,
        start: LocalDate = currentStart,
        end: LocalDate = currentEnd,
    ) = BudgetRepository.Budget(
        id = Id.Known(id),
        categoryId = categoryId,
        type = type,
        amount = Amount(amount),
        periodStart = start,
        periodEnd = end,
    )

    private fun useCase() = DefaultBudgetUseCase(
        budgetRepository = budgetRepository,
        budgetQueryUseCase = budgetQueryUseCase,
        periodResolver = periodResolver,
    )

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save inserts a new budget when none exists for category and period`() = runTest {
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForCategoryAndPeriod(catA, currentStart, currentEnd, type),
            ),
        ).thenReturn(flowOf(null))

        useCase().save(monthOffset = 0, type = type, categoryId = catA, amount = Amount(BigDecimal("100")))

        val captor = argumentCaptor<BudgetRepository.BudgetInsert>()
        verify(budgetRepository).insert(captor.capture())
        assertEquals(Id.Unknown, captor.firstValue.id)
        assertEquals(catA, captor.firstValue.categoryId)
        assertEquals(0, captor.firstValue.amount.value.compareTo(BigDecimal("100")))
    }

    @Test
    fun `save preserves existing budget id when one is already present`() = runTest {
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForCategoryAndPeriod(catA, currentStart, currentEnd, type),
            ),
        ).thenReturn(flowOf(budget("b-a", catA, BigDecimal("100"))))

        useCase().save(monthOffset = 0, type = type, categoryId = catA, amount = Amount(BigDecimal("120")))

        val captor = argumentCaptor<BudgetRepository.BudgetInsert>()
        verify(budgetRepository).insert(captor.capture())
        assertEquals(Id.Known("b-a"), captor.firstValue.id)
        assertEquals(0, captor.firstValue.amount.value.compareTo(BigDecimal("120")))
    }

    // ── replaceFromPrevious ──────────────────────────────────────────────────

    @Test
    fun `replaceFromPrevious is a no-op when previous period has no budgets`() = runTest {
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForPeriod(previousStart, previousEnd, type),
            ),
        ).thenReturn(flowOf(emptyList()))

        useCase().replaceFromPrevious(monthOffset = 0, type = type)

        verify(budgetRepository, never()).replace(any(), any(), any())
    }

    @Test
    fun `replaceFromPrevious calls repository replace with source amounts preserving current ids`() = runTest {
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForPeriod(previousStart, previousEnd, type),
            ),
        ).thenReturn(
            flowOf(
                listOf(
                    budget("prev-a", catA, BigDecimal("100"), previousStart, previousEnd),
                    budget("prev-c", catC, BigDecimal("75"), previousStart, previousEnd),
                ),
            ),
        )
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForPeriod(currentStart, currentEnd, type),
            ),
        ).thenReturn(flowOf(listOf(budget("cur-a", catA, BigDecimal("80")))))

        useCase().replaceFromPrevious(monthOffset = 0, type = type)

        val periodCaptor = argumentCaptor<DateRange>()
        val insertsCaptor = argumentCaptor<List<BudgetRepository.BudgetInsert>>()
        verify(budgetRepository).replace(periodCaptor.capture(), eq(type), insertsCaptor.capture())
        assertEquals(currentStart, periodCaptor.firstValue.start)
        assertEquals(currentEnd, periodCaptor.firstValue.end)
        val inserts = insertsCaptor.firstValue.associateBy { it.categoryId }
        assertEquals(Id.Known("cur-a"), inserts.getValue(catA).id)
        assertEquals(0, inserts.getValue(catA).amount.value.compareTo(BigDecimal("100")))
        assertEquals(Id.Unknown, inserts.getValue(catC).id)
        assertEquals(0, inserts.getValue(catC).amount.value.compareTo(BigDecimal("75")))
        // cur-b (not in source) gets handled by the repository's atomic replace — no individual delete.
    }
}
