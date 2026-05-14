package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class RoomBudgetRepositoryTest {

    @Mock private lateinit var budgetRoom: BudgetRoom

    @Mock private lateinit var zonedClock: ZonedClock

    private lateinit var repo: RoomBudgetRepository

    private val userId = Id.Known("user1")
    private val categoryId = Id.Known("cat1")
    private val from = LocalDate(2026, 5, 1)
    private val to = LocalDate(2026, 5, 31)
    private val now = LocalDateTime(2026, 5, 13, 12, 0)

    private val idGenerator = object : IdGenerator {
        override fun invoke(): Id.Known = Id.Known("generated")
    }

    @Before
    fun setUp() {
        whenever(zonedClock.localDateTime()).thenReturn(now)
        repo = RoomBudgetRepository(
            budgetRoom = { budgetRoom },
            currentUserId = flowOf(userId),
            idGenerator = idGenerator,
            zonedClock = zonedClock,
            incorrectStateDetector = IncorrectStateDetector.ignoreIncorrect(),
        )
    }

    private fun entity(id: String, amount: BigDecimal = BigDecimal("100.00")): BudgetEntity = BudgetEntity(
        id = Id.Known(id),
        userId = userId,
        categoryId = categoryId,
        type = BudgetType.EXPENSE.name,
        amount = amount,
        periodStart = from,
        periodEnd = to,
        creationDateTime = now,
        updatedDateTime = now,
    )

    @Test
    fun `Criteria_ForPeriod returns matched rows`() = runTest {
        whenever(budgetRoom.selectForPeriod(userId, from, to, BudgetType.EXPENSE.name))
            .thenReturn(flowOf(listOf(entity("b1"), entity("b2"))))

        val result = repo.query(BudgetRepository.Criteria.ForPeriod(from, to)).first()

        assertEquals(listOf("b1", "b2"), result.map { it.id.value })
    }

    @Test
    fun `Criteria_ForCategoryAndPeriod returns single row or null`() = runTest {
        whenever(budgetRoom.selectForCategoryAndPeriod(userId, categoryId, from, to, BudgetType.EXPENSE.name))
            .thenReturn(flowOf(entity("b1")))

        val result = repo.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, from, to)).first()

        assertNotNull(result)
        assertEquals("b1", result!!.id.value)
    }

    @Test
    fun `Criteria_HasAnyForPeriod returns true when row exists`() = runTest {
        whenever(budgetRoom.selectHasAnyForPeriod(userId, from, to, BudgetType.EXPENSE.name))
            .thenReturn(flowOf(true))

        val result = repo.query(BudgetRepository.Criteria.HasAnyForPeriod(from, to)).first()

        assertTrue(result)
    }

    @Test
    fun `insert with Id_Unknown generates id`() = runTest {
        repo.insert(
            BudgetRepository.BudgetInsert(
                id = Id.Unknown,
                categoryId = categoryId,
                type = BudgetType.EXPENSE,
                amount = Amount(BigDecimal("250.00")),
                periodStart = from,
                periodEnd = to,
            ),
        )

        val captor = argumentCaptor<BudgetEntity>()
        verify(budgetRoom).insert(captor.capture())
        assertEquals("generated", captor.firstValue.id.value)
    }

    @Test
    fun `insert with Id_Known reuses existing id`() = runTest {
        repo.insert(
            BudgetRepository.BudgetInsert(
                id = Id.Known("existing"),
                categoryId = categoryId,
                type = BudgetType.EXPENSE,
                amount = Amount(BigDecimal("250.00")),
                periodStart = from,
                periodEnd = to,
            ),
        )

        val captor = argumentCaptor<BudgetEntity>()
        verify(budgetRoom).insert(captor.capture())
        assertEquals("existing", captor.firstValue.id.value)
    }

    @Test
    fun `delete soft-deletes with current updatedDateTime`() = runTest {
        repo.delete(Id.Known("b1"))

        verify(budgetRoom).softDelete(Id.Known("b1"), userId, now)
    }
}
