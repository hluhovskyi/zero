package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultAccountDetailSpendingUseCaseTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    private val accountId = Id.Known("acc1")
    private val fixedInstant = Instant.parse("2026-05-15T12:00:00Z")
    private val fakeClock = object : Clock {
        override fun now() = fixedInstant
    }
    private val fakeZone = object : ZoneProvider {
        override fun timeZone() = TimeZone.UTC
    }

    private fun createUseCase() = DefaultAccountDetailSpendingUseCase(
        transactionRepository = transactionRepository,
        clock = fakeClock,
        zoneProvider = fakeZone,
    )

    @Test
    fun `returns null when no transactions`() = runTest {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForAccountBetween>(), any()))
            .thenReturn(flowOf(emptyList()))

        val result = createUseCase()
            .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
            .first()

        assertNull(result)
    }

    @Test
    fun `sums income as totalIn and expense as totalOut`() = runTest {
        val income = makeIncome(amount = BigDecimal("1000.00"))
        val expense = makeExpense(amount = BigDecimal("250.00"))
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForAccountBetween>(), any()))
            .thenReturn(flowOf(listOf(income, expense)))

        val result = createUseCase()
            .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
            .first()!!

        assertEquals(0, BigDecimal("1000.00").compareTo(result.totalIn.value))
        assertEquals(0, BigDecimal("250.00").compareTo(result.totalOut.value))
    }

    @Test
    fun `counts only income and expense, not transfers`() = runTest {
        val income = makeIncome(amount = BigDecimal("500.00"))
        val transfer = makeTransfer()
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForAccountBetween>(), any()))
            .thenReturn(flowOf(listOf(income, transfer)))

        val result = createUseCase()
            .queryForAccount(accountId, AccountDetailSpendingUseCase.Period.CurrentMonth)
            .first()!!

        assertEquals(1, result.transactionCount)
    }

    private fun makeIncome(amount: BigDecimal) = TransactionRepository.Transaction.Income(
        id = Id.Known("tx-income"),
        amount = Amount(amount),
        accountId = accountId,
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("2026-05-10T10:00:00"),
        updatedDateTime = LocalDateTime.parse("2026-05-10T10:00:00"),
        categoryId = Id.Known("cat1"),
        rate = Rate(BigDecimal.ONE),
    )

    private fun makeExpense(amount: BigDecimal) = TransactionRepository.Transaction.Expense(
        id = Id.Known("tx-expense"),
        amount = Amount(amount),
        accountId = accountId,
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("2026-05-12T10:00:00"),
        updatedDateTime = LocalDateTime.parse("2026-05-12T10:00:00"),
        categoryId = Id.Known("cat1"),
        rate = Rate(BigDecimal.ONE),
    )

    private fun makeTransfer() = TransactionRepository.Transaction.Transfer(
        id = Id.Known("tx-transfer"),
        amount = Amount(BigDecimal("200.00")),
        accountId = accountId,
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("2026-05-14T10:00:00"),
        updatedDateTime = LocalDateTime.parse("2026-05-14T10:00:00"),
        targetAccount = Id.Known("acc2"),
        targetAmount = Amount(BigDecimal("200.00")),
    )
}
