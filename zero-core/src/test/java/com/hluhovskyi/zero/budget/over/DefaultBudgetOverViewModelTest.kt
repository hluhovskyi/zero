package com.hluhovskyi.zero.budget.over

import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultBudgetOverViewModelTest {

    @Mock private lateinit var budgetQueryUseCase: BudgetQueryUseCase

    @Mock private lateinit var budgetRepository: BudgetRepository

    private val targetId = Id.Known("cat-target")
    private val sourceCoversId = Id.Known("cat-source-covers")
    private val sourcePartialId = Id.Known("cat-source-partial")
    private val sourceNoRemainingId = Id.Known("cat-source-no-remaining")
    private val sourceUnbudgetedId = Id.Known("cat-source-unbudgeted")

    private val periodStart = LocalDate(2026, 5, 1)
    private val periodEnd = LocalDate(2026, 5, 31)

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun cpu() = dispatcher
    }

    private fun budgeted(
        categoryId: Id.Known,
        name: String,
        budgeted: BigDecimal,
        spent: BigDecimal,
        hasBudget: Boolean = true,
    ) = BudgetQueryUseCase.Budgeted(
        categoryId = categoryId,
        categoryName = name,
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        spent = Amount(spent),
        budgetId = if (hasBudget) Id.Known("budget-${categoryId.value}") else null,
        budgeted = Amount(budgeted),
    )

    private fun rows() = listOf(
        budgeted(targetId, "Groceries", BigDecimal("100"), BigDecimal("150")),
        budgeted(sourceCoversId, "Entertainment", BigDecimal("200"), BigDecimal("100")),
        budgeted(sourcePartialId, "Dining", BigDecimal("50"), BigDecimal("30")),
        budgeted(sourceNoRemainingId, "Gas", BigDecimal("80"), BigDecimal("80")),
        budgeted(sourceUnbudgetedId, "Misc", BigDecimal("0"), BigDecimal("10"), hasBudget = false),
    )

    private fun storedBudget(
        id: String,
        categoryId: Id.Known,
        amount: BigDecimal,
    ) = BudgetRepository.Budget(
        id = Id.Known(id),
        categoryId = categoryId,
        type = BudgetType.EXPENSE,
        amount = Amount(amount),
        periodStart = periodStart,
        periodEnd = periodEnd,
    )

    private fun viewModel(
        initialMode: BudgetOverViewModel.Mode? = null,
        onReallocateCompletedHandler: OnReallocateCompletedHandler = OnReallocateCompletedHandler.Noop,
        onIncreaseCompletedHandler: OnIncreaseCompletedHandler = OnIncreaseCompletedHandler.Noop,
        onBackHandler: OnBackHandler = OnBackHandler.Noop,
    ) = DefaultBudgetOverViewModel(
        categoryId = targetId,
        period = DateRange(periodStart, periodEnd),
        initialMode = initialMode,
        budgetQueryUseCase = budgetQueryUseCase,
        budgetRepository = budgetRepository,
        onReallocateCompletedHandler = onReallocateCompletedHandler,
        onIncreaseCompletedHandler = onIncreaseCompletedHandler,
        onBackHandler = onBackHandler,
        dispatchers = dispatchers,
    )

    @Test
    fun `initial mode REALLOCATE seeds state mode`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val vm = viewModel(initialMode = BudgetOverViewModel.Mode.REALLOCATE)
        vm.attach()
        val state = vm.state.first()

        assertEquals(BudgetOverViewModel.Mode.REALLOCATE, state.mode)
    }

    @Test
    fun `reallocationSources excludes target, unbudgeted, and no-remaining categories`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val vm = viewModel()
        vm.attach()
        val state = vm.state.first()

        val sourceIds = state.reallocationSources.map { it.categoryId }
        assertEquals(listOf(sourceCoversId, sourcePartialId), sourceIds)
    }

    @Test
    fun `source coversIt is true only when remaining is at least the overage`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val vm = viewModel()
        vm.attach()
        val state = vm.state.first()

        val covers = state.reallocationSources.first { it.categoryId == sourceCoversId }
        val partial = state.reallocationSources.first { it.categoryId == sourcePartialId }
        assertTrue(covers.coversIt)
        assertFalse(partial.coversIt)
    }

    @Test
    fun `non-over target produces no target and no sources`() = runTest {
        val notOver = listOf(
            budgeted(targetId, "Groceries", BigDecimal("100"), BigDecimal("40")),
            budgeted(sourceCoversId, "Entertainment", BigDecimal("200"), BigDecimal("100")),
        )
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(notOver))

        val vm = viewModel()
        vm.attach()
        val state = vm.state.first()

        assertNull(state.target)
        assertTrue(state.reallocationSources.isEmpty())
    }

    @Test
    fun `SelectSource then ConfirmReallocate inserts adjusted source and target budgets`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(sourceCoversId, periodStart, periodEnd)))
            .thenReturn(flowOf(storedBudget("budget-source", sourceCoversId, BigDecimal("200"))))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(targetId, periodStart, periodEnd)))
            .thenReturn(flowOf(storedBudget("budget-target", targetId, BigDecimal("100"))))

        val reallocateHandler = mock<OnReallocateCompletedHandler>()
        val backHandler = mock<OnBackHandler>()
        val vm = viewModel(
            onReallocateCompletedHandler = reallocateHandler,
            onBackHandler = backHandler,
        )
        vm.attach()
        vm.perform(BudgetOverViewModel.Action.SelectSource(sourceCoversId))
        vm.perform(BudgetOverViewModel.Action.ConfirmReallocate)

        val captor = argumentCaptor<List<BudgetRepository.BudgetInsert>>()
        verify(budgetRepository).insert(captor.capture())
        val inserts = captor.firstValue
        assertEquals(2, inserts.size)
        val sourceInsert = inserts.first { it.categoryId == sourceCoversId }
        val targetInsert = inserts.first { it.categoryId == targetId }
        assertEquals(0, sourceInsert.amount.value.compareTo(BigDecimal("150")))
        assertEquals(0, targetInsert.amount.value.compareTo(BigDecimal("150")))
        verify(reallocateHandler).onComplete(eq("Entertainment"), eq("Groceries"), any())
        verify(backHandler).onBack()
    }

    @Test
    fun `SelectSource caps amountToMove at target overage when source has more remaining`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val vm = viewModel()
        vm.attach()
        vm.perform(BudgetOverViewModel.Action.SelectSource(sourceCoversId))
        val state = vm.state.first()

        assertEquals(0, state.amountToMove.value.compareTo(BigDecimal("50")))
    }

    @Test
    fun `ConfirmIncrease inserts target with new amount and invokes handlers`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(targetId, periodStart, periodEnd)))
            .thenReturn(flowOf(storedBudget("budget-target", targetId, BigDecimal("100"))))

        val increaseHandler = mock<OnIncreaseCompletedHandler>()
        val backHandler = mock<OnBackHandler>()
        val vm = viewModel(
            onIncreaseCompletedHandler = increaseHandler,
            onBackHandler = backHandler,
        )
        vm.attach()
        vm.perform(BudgetOverViewModel.Action.ChangeIncreaseAmount("75"))
        vm.perform(BudgetOverViewModel.Action.ConfirmIncrease)

        val captor = argumentCaptor<BudgetRepository.BudgetInsert>()
        verify(budgetRepository).insert(captor.capture())
        assertEquals(targetId, captor.firstValue.categoryId)
        assertEquals(0, captor.firstValue.amount.value.compareTo(BigDecimal("175")))
        val amountCaptor = argumentCaptor<Amount>()
        verify(increaseHandler).onComplete(eq("Groceries"), amountCaptor.capture())
        assertEquals(0, amountCaptor.firstValue.value.compareTo(BigDecimal("175")))
        verify(backHandler).onBack()
    }

    @Test
    fun `increaseSuggestions populated when target is over budget`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val vm = viewModel()
        vm.attach()
        val state = vm.state.first()

        assertNotNull(state.target)
        assertTrue(state.increaseSuggestions.isNotEmpty())
        assertTrue(state.increaseSuggestions.all { it.amount > Amount.zero() })
    }

    @Test
    fun `TapBack from REALLOCATE returns to CHOICE when initialMode is null`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val backHandler = mock<OnBackHandler>()
        val vm = viewModel(onBackHandler = backHandler)
        vm.attach()
        vm.perform(BudgetOverViewModel.Action.TapReallocateOption)
        vm.perform(BudgetOverViewModel.Action.TapBack)
        val state = vm.state.first()

        assertEquals(BudgetOverViewModel.Mode.CHOICE, state.mode)
        verify(backHandler, org.mockito.kotlin.never()).onBack()
    }

    @Test
    fun `TapBack closes sheet when initialMode was explicit`() = runTest {
        whenever(budgetQueryUseCase.query(periodStart, periodEnd)).thenReturn(flowOf(rows()))

        val backHandler = mock<OnBackHandler>()
        val vm = viewModel(
            initialMode = BudgetOverViewModel.Mode.REALLOCATE,
            onBackHandler = backHandler,
        )
        vm.attach()
        vm.perform(BudgetOverViewModel.Action.TapBack)

        verify(backHandler).onBack()
    }
}
