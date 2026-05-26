package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // Trailing 3 months ending the day before the current period (used to order unset rows).
    private val trailingStart = LocalDate(2026, 2, 1)
    private val trailingEnd = LocalDate(2026, 4, 30)
    private val type = BudgetType.EXPENSE

    private val catA = Id.Known("cat-a")
    private val catB = Id.Known("cat-b")
    private val catC = Id.Known("cat-c")

    private val periodResolver = object : PeriodResolver {
        override fun today(): LocalDate = today
        override fun currentMonth(): Pair<LocalDate, LocalDate> = currentStart to currentEnd
        override fun monthOffsetFrom(reference: LocalDate, offsetMonths: Int): Pair<LocalDate, LocalDate> = when (offsetMonths) {
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

    // ── remove ───────────────────────────────────────────────────────────────

    @Test
    fun `remove soft-deletes the budget for the category and period`() = runTest {
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForCategoryAndPeriod(catA, currentStart, currentEnd, type),
            ),
        ).thenReturn(flowOf(budget("b-a", catA, BigDecimal("100"))))

        useCase().remove(monthOffset = 0, type = type, categoryId = catA)

        verify(budgetRepository).delete(Id.Known("b-a"))
    }

    @Test
    fun `remove is a no-op when the category has no budget in the period`() = runTest {
        whenever(
            budgetRepository.query(
                BudgetRepository.Criteria.ForCategoryAndPeriod(catA, currentStart, currentEnd, type),
            ),
        ).thenReturn(flowOf(null))

        useCase().remove(monthOffset = 0, type = type, categoryId = catA)

        verify(budgetRepository, never()).delete(any())
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

    // ── observe ──────────────────────────────────────────────────────────────
    // The use case is the single source of truth for derived display state. The
    // view layer reads `state.current` (already sorted), `state.summary`, and
    // `state.hasAnyBudget` directly — no sort, mapping, or checks downstream.

    @Test
    fun `observe sorts rows over → in-progress by pct desc → unset`() = runTest {
        whenever(budgetQueryUseCase.query(previousStart, previousEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(trailingStart, trailingEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(
                listOf(
                    row("in-30", budgetId = "b1", budgeted = "100", spent = "30"),
                    row("over-1", budgetId = "b2", budgeted = "100", spent = "150"),
                    row("unset-a", budgetId = null, budgeted = "0", spent = "5"),
                    row("in-80", budgetId = "b3", budgeted = "100", spent = "80"),
                    row("over-2", budgetId = "b4", budgeted = "50", spent = "60"),
                    row("unset-b", budgetId = null, budgeted = "0", spent = "0"),
                    row("in-50", budgetId = "b5", budgeted = "100", spent = "50"),
                    row("over-3", budgetId = "b6", budgeted = "10", spent = "15"),
                ),
            ),
        )

        val state = useCase().observe(MutableStateFlow(0), type).first()

        assertEquals(
            listOf("over-1", "over-2", "over-3", "in-80", "in-50", "in-30", "unset-a", "unset-b"),
            state.current.map { it.categoryId.value },
        )
    }

    @Test
    fun `observe orders unset categories by trailing spend desc then alphabetically`() = runTest {
        whenever(budgetQueryUseCase.query(previousStart, previousEnd))
            .thenReturn(flowOf(emptyList()))
        // Only groceries and transport have trailing-3-month spend; dining and health have none.
        whenever(budgetQueryUseCase.query(trailingStart, trailingEnd)).thenReturn(
            flowOf(
                listOf(
                    row("groceries", budgetId = null, budgeted = "0", spent = "300"),
                    row("transport", budgetId = null, budgeted = "0", spent = "100"),
                ),
            ),
        )
        // Current period: all unset, deliberately not in the expected output order.
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(
                listOf(
                    row("health", budgetId = null, budgeted = "0", spent = "0"),
                    row("groceries", budgetId = null, budgeted = "0", spent = "0"),
                    row("dining", budgetId = null, budgeted = "0", spent = "0"),
                    row("transport", budgetId = null, budgeted = "0", spent = "0"),
                ),
            ),
        )

        val state = useCase().observe(MutableStateFlow(0), type).first()

        // groceries (300) > transport (100); dining/health have no history → alphabetical by name.
        assertEquals(
            listOf("groceries", "transport", "dining", "health"),
            state.current.map { it.categoryId.value },
        )
    }

    @Test
    fun `observe derives empty summary when no budgets are set`() = runTest {
        whenever(budgetQueryUseCase.query(previousStart, previousEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(trailingStart, trailingEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(
                listOf(
                    row("u1", budgetId = null, budgeted = "0", spent = "10"),
                    row("u2", budgetId = null, budgeted = "0", spent = "5"),
                ),
            ),
        )

        val state = useCase().observe(MutableStateFlow(0), type).first()

        assertFalse(state.hasAnyBudget)
        assertEquals(BudgetUseCase.Summary.empty, state.summary)
    }

    @Test
    fun `observe derives summary totals over only set rows`() = runTest {
        whenever(budgetQueryUseCase.query(previousStart, previousEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(trailingStart, trailingEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(
                listOf(
                    row("c1", budgetId = "b1", budgeted = "200", spent = "50"),
                    row("c2", budgetId = "b2", budgeted = "300", spent = "100"),
                    row("u1", budgetId = null, budgeted = "0", spent = "999"),
                ),
            ),
        )

        val state = useCase().observe(MutableStateFlow(0), type).first()

        assertTrue(state.hasAnyBudget)
        assertEquals(BigDecimal("500"), state.summary.totalBudgeted.value)
        assertEquals(BigDecimal("150"), state.summary.totalSpent.value)
        assertEquals(0, state.summary.overCount)
        assertEquals(0.3f, state.summary.overallPct, 0.0001f)
        assertFalse(state.summary.isOver)
    }

    @Test
    fun `observe flags over-budget and clips pct at 1`() = runTest {
        whenever(budgetQueryUseCase.query(previousStart, previousEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(trailingStart, trailingEnd))
            .thenReturn(flowOf(emptyList()))
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(
                listOf(
                    row("c1", budgetId = "b1", budgeted = "100", spent = "150"),
                    row("c2", budgetId = "b2", budgeted = "200", spent = "50"),
                ),
            ),
        )

        val state = useCase().observe(MutableStateFlow(0), type).first()

        assertEquals(1, state.summary.overCount)
        assertEquals(BigDecimal("300"), state.summary.totalBudgeted.value)
        assertEquals(BigDecimal("200"), state.summary.totalSpent.value)
        assertFalse(state.summary.isOver)
        assertEquals(200f / 300f, state.summary.overallPct, 0.0001f)
    }

    // ── observeAnyOver ─────────────────────────────────────────────────────────
    // Lightweight current-month-only signal that drives the bottom-bar dot.

    @Test
    fun `observeAnyOver emits true when a set budget is over spent`() = runTest {
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(listOf(row("c1", budgetId = "b1", budgeted = "100", spent = "150"))),
        )

        assertTrue(useCase().observeAnyOver(type).first())
    }

    @Test
    fun `observeAnyOver emits false when set budgets are within limit`() = runTest {
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(listOf(row("c1", budgetId = "b1", budgeted = "100", spent = "80"))),
        )

        assertFalse(useCase().observeAnyOver(type).first())
    }

    @Test
    fun `observeAnyOver ignores unset categories even when spent is positive`() = runTest {
        whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
            flowOf(listOf(row("u1", budgetId = null, budgeted = "0", spent = "999"))),
        )

        assertFalse(useCase().observeAnyOver(type).first())
    }

    private fun row(
        categoryId: String,
        budgetId: String?,
        budgeted: String,
        spent: String,
    ) = BudgetQueryUseCase.Budgeted(
        categoryId = Id.Known(categoryId),
        categoryName = "Cat $categoryId",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        spent = Amount(BigDecimal(spent)),
        budgetId = budgetId?.let { Id.Known(it) },
        budgeted = Amount(BigDecimal(budgeted)),
    )
}
