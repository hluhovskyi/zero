package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class RoomTransactionRepositoryPaginationTest {

    @Mock private lateinit var transactionRoom: TransactionRoom

    @Mock private lateinit var zonedClock: ZonedClock

    private lateinit var repo: RoomTransactionRepository

    private val userId = Id.Known("user1")
    private val jan15h10 = LocalDateTime(2024, 1, 15, 10, 0)
    private val jan15h08 = LocalDateTime(2024, 1, 15, 8, 0)
    private val jan14h18 = LocalDateTime(2024, 1, 14, 18, 0)

    @Before
    fun setUp() {
        repo = RoomTransactionRepository(
            transactionRoom = { transactionRoom },
            currentUserId = flowOf(userId),
            incorrectStateDetector = IncorrectStateDetector.ignoreIncorrect(),
            zonedClock = zonedClock,
        )
    }

    private fun expenseEntity(id: String, dateTime: LocalDateTime) = TransactionEntity(
        id = Id.Known(id),
        userId = userId,
        type = TransactionEntity.Type.EXPENSE,
        currencyId = Id.Known("usd"),
        accountId = Id.Known("acc"),
        categoryId = "cat",
        amount = AmountEntity(BigDecimal.ONE),
        rate = RateEntity(BigDecimal.ONE),
        targetAccount = null,
        targetAmount = AmountEntity.empty(),
        enteredDateTime = dateTime,
        creationDateTime = dateTime,
        updatedDateTime = dateTime,
    )

    // --- Criteria.All without trigger (one-shot fetch-all) ---

    @Test
    fun `Criteria_All without trigger returns all alive transactions in one batch`() = runTest {
        whenever(transactionRoom.selectAllAlive("user1"))
            .thenReturn(flowOf(listOf(expenseEntity("t1", jan15h10), expenseEntity("t2", jan15h08))))

        val result = repo.query(TransactionRepository.Criteria.All()).first()

        assertEquals(listOf("t1", "t2"), result.map { it.id.value })
    }

    // --- Criteria.All with trigger (reactive window) ---

    @Test
    fun `Criteria_All with trigger emits the first window`() = runTest {
        whenever(transactionRoom.selectWindow("user1", 100))
            .thenReturn(flowOf(listOf(expenseEntity("t1", jan15h10), expenseEntity("t2", jan15h08))))

        val result = repo.query(TransactionRepository.Criteria.All(), MutableSharedFlow<Unit>()).first()

        assertEquals(listOf("t1", "t2"), result.map { it.id.value })
    }

    @Test
    fun `Criteria_All trigger grows the window by one page`() = runTest {
        val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        whenever(transactionRoom.selectWindow("user1", 100))
            .thenReturn(flowOf(listOf(expenseEntity("t1", jan15h10))))
        whenever(transactionRoom.selectWindow("user1", 200))
            .thenReturn(flowOf(listOf(expenseEntity("t1", jan15h10), expenseEntity("t2", jan14h18))))

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.All(), trigger).collect { results.add(it) }
        }

        advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        trigger.emit(Unit) // load more -> window 100 -> 200
        advanceUntilIdle()
        assertEquals(listOf("t1", "t2"), results.last().map { it.id.value })

        job.cancel()
    }

    @Test
    fun `Criteria_All window re-emits live when the table changes`() = runTest {
        // selectWindow is a reactive Room query: Room re-emits on every write to the table,
        // including an import landing historical-dated rows. No trigger / load-more involved.
        val window = MutableSharedFlow<List<TransactionEntity>>(replay = 1)
        whenever(transactionRoom.selectWindow("user1", 100)).thenReturn(window)

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.All(), MutableSharedFlow<Unit>()).collect { results.add(it) }
        }

        window.emit(listOf(expenseEntity("t1", jan15h10)))
        advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        // An import writes rows -> Room re-runs selectWindow and re-emits, with no scroll.
        window.emit(listOf(expenseEntity("t1", jan15h10), expenseEntity("imported", jan14h18)))
        advanceUntilIdle()
        assertEquals(listOf("t1", "imported"), results.last().map { it.id.value })

        job.cancel()
    }

    // --- Criteria.Search ---

    @Test
    fun `Criteria_Search delegates to search DAO with percent-wrapped query`() = runTest {
        val searchFlow = MutableSharedFlow<List<TransactionEntity>>(replay = 1)
        whenever(transactionRoom.search("user1", "%food%")).thenReturn(searchFlow)

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.Search("food")).collect { results.add(it) }
        }

        searchFlow.emit(listOf(expenseEntity("t1", jan15h10)))
        advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        // Simulates Room re-emitting on new matching insert
        searchFlow.emit(listOf(expenseEntity("t2", jan15h10), expenseEntity("t1", jan15h10)))
        advanceUntilIdle()
        assertEquals(listOf("t2", "t1"), results.last().map { it.id.value })

        job.cancel()
    }

    @Test
    fun `delete soft-deletes the transaction`() = runTest {
        val now = LocalDateTime(2024, 1, 16, 9, 0)
        whenever(zonedClock.localDateTime()).thenReturn(now)

        repo.delete(Id.Known("t1"))
        advanceUntilIdle()

        verify(transactionRoom).softDelete(
            id = "t1",
            userId = "user1",
            deletedAt = now.toString(),
            updatedDateTime = now.toString(),
        )
    }

    // --- Criteria.ForCategories ---

    @Test
    fun `ForCategories criterion calls selectByCategories with the right userId and categoryIds`() = runTest {
        val categoryId = Id.Known("cat1")
        whenever(transactionRoom.selectByCategories(userId.value, listOf(categoryId.value)))
            .thenReturn(flowOf(emptyList()))

        repo.query(TransactionRepository.Criteria.ForCategories(setOf(categoryId))).first()

        verify(transactionRoom).selectByCategories(userId.value, listOf(categoryId.value))
    }

    @Test
    fun `ForCategories criterion maps expense entity to Expense transaction`() = runTest {
        val categoryId = Id.Known("cat1")
        val entity = TransactionEntity(
            id = Id.Known("t1"),
            userId = userId,
            type = TransactionEntity.Type.EXPENSE,
            currencyId = Id.Known("usd"),
            accountId = Id.Known("acc"),
            categoryId = categoryId.value,
            amount = AmountEntity(java.math.BigDecimal("42.00")),
            rate = RateEntity(java.math.BigDecimal.ONE),
            targetAccount = null,
            targetAmount = AmountEntity(java.math.BigDecimal.ZERO),
            enteredDateTime = jan15h10,
            creationDateTime = jan15h10,
            updatedDateTime = jan15h10,
        )
        whenever(transactionRoom.selectByCategories(userId.value, listOf(categoryId.value)))
            .thenReturn(flowOf(listOf(entity)))

        val result = repo.query(TransactionRepository.Criteria.ForCategories(setOf(categoryId))).first()

        assertEquals(1, result.size)
        val tx = result.first()
        assertTrue(tx is TransactionRepository.Transaction.Expense)
        assertEquals(Id.Known("t1"), tx.id)
        assertEquals(categoryId, (tx as TransactionRepository.Transaction.Expense).categoryId)
    }

    // --- Criteria.Filtered (universal) ---

    @Test
    fun `Filtered maps each dimension to selectFiltered args`() = runTest {
        whenever(
            transactionRoom.selectFiltered("user1", "2024-01-01", "2024-01-31", "INCOME", 1, listOf("c1"), 0, listOf("")),
        ).thenReturn(flowOf(emptyList()))

        repo.query(
            TransactionRepository.Criteria.Filtered(
                from = LocalDate(2024, 1, 1),
                to = LocalDate(2024, 1, 31),
                type = TransactionRepository.Type.Income,
                categoryIds = setOf(Id.Known("c1")),
                accountIds = null,
            ),
        ).first()

        verify(transactionRoom).selectFiltered(
            userId = "user1",
            from = "2024-01-01",
            to = "2024-01-31",
            type = "INCOME",
            filterCategories = 1,
            categoryIds = listOf("c1"),
            filterAccounts = 0,
            accountIds = listOf(""),
        )
    }

    @Test
    fun `Filtered with no dimensions passes nulls and bypass flags with sentinel lists`() = runTest {
        whenever(
            transactionRoom.selectFiltered("user1", null, null, null, 0, listOf(""), 0, listOf("")),
        ).thenReturn(flowOf(emptyList()))

        repo.query(
            TransactionRepository.Criteria.Filtered(from = null, to = null, type = null, categoryIds = null, accountIds = null),
        ).first()

        verify(transactionRoom).selectFiltered(
            userId = "user1",
            from = null,
            to = null,
            type = null,
            filterCategories = 0,
            categoryIds = listOf(""),
            filterAccounts = 0,
            accountIds = listOf(""),
        )
    }
}
