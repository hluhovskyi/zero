package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class RoomTransactionRepositoryPaginationTest {

    @Mock private lateinit var transactionRoom: TransactionRoom

    @Mock private lateinit var clock: Clock

    @Mock private lateinit var zoneProvider: ZoneProvider

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
            clock = clock,
            zoneProvider = zoneProvider,
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

    // --- Criteria.After ---

    @Test
    fun `Criteria_After emits transactions newer than given datetime`() = runTest {
        val roomFlow = MutableSharedFlow<List<TransactionEntity>>(replay = 1)
        whenever(transactionRoom.selectAfter("user1", jan15h08.toString()))
            .thenReturn(roomFlow)

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.After(jan15h08)).collect { results.add(it) }
        }

        roomFlow.emit(listOf(expenseEntity("t1", jan15h10)))
        advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        // Simulate new insert — Room re-emits
        roomFlow.emit(listOf(expenseEntity("t2", LocalDateTime(2024, 1, 15, 11, 0)), expenseEntity("t1", jan15h10)))
        advanceUntilIdle()
        assertEquals(listOf("t2", "t1"), results.last().map { it.id.value })

        job.cancel()
    }

    // --- Criteria.All with trigger ---

    @Test
    fun `Criteria_All initial page is padded to complete the last day`() = runTest {
        whenever(transactionRoom.selectFirstPage("user1", 100))
            .thenReturn(listOf(expenseEntity("t1", jan15h10)))
        whenever(transactionRoom.selectRemainingOnDay("user1", "2024-01-15", jan15h10.toString()))
            .thenReturn(listOf(expenseEntity("t2", jan15h08)))

        val result = repo.query(TransactionRepository.Criteria.All()).first()

        assertEquals(listOf("t1", "t2"), result.map { it.id.value })
    }

    @Test
    fun `Criteria_All trigger loads next cursor page appended to first`() = runTest {
        val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        whenever(transactionRoom.selectFirstPage("user1", 100))
            .thenReturn(listOf(expenseEntity("t1", jan15h10)))
        whenever(transactionRoom.selectRemainingOnDay(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(transactionRoom.selectNextPage("user1", "2024-01-15", 100))
            .thenReturn(listOf(expenseEntity("t2", jan14h18)))

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.All(), trigger).collect { results.add(it) }
        }

        advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        trigger.emit(Unit)
        advanceUntilIdle()
        assertEquals(listOf("t1", "t2"), results.last().map { it.id.value })

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
        whenever(clock.now()).thenReturn(kotlinx.datetime.Instant.parse("2024-01-16T09:00:00Z"))
        whenever(zoneProvider.timeZone()).thenReturn(kotlinx.datetime.TimeZone.UTC)

        repo.delete(Id.Known("t1"))
        advanceUntilIdle()

        org.mockito.kotlin.verify(transactionRoom).softDelete(
            id = "t1",
            userId = "user1",
            deletedAt = now.toString(),
            updatedDateTime = now.toString(),
        )
    }

    // --- Criteria.ForCategory ---

    @Test
    fun `ForCategory criterion calls selectByCategory with the right userId and categoryId`() = runTest {
        val categoryId = Id.Known("cat1")
        whenever(transactionRoom.selectByCategory(userId.value, categoryId.value))
            .thenReturn(flowOf(emptyList()))

        repo.query(TransactionRepository.Criteria.ForCategory(categoryId)).first()

        org.mockito.kotlin.verify(transactionRoom).selectByCategory(userId.value, categoryId.value)
    }

    @Test
    fun `ForCategory criterion maps expense entity to Expense transaction`() = runTest {
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
        whenever(transactionRoom.selectByCategory(userId.value, categoryId.value))
            .thenReturn(flowOf(listOf(entity)))

        val result = repo.query(TransactionRepository.Criteria.ForCategory(categoryId)).first()

        assertEquals(1, result.size)
        val tx = result.first()
        org.junit.Assert.assertTrue(tx is TransactionRepository.Transaction.Expense)
        assertEquals(Id.Known("t1"), tx.id)
    }
}
