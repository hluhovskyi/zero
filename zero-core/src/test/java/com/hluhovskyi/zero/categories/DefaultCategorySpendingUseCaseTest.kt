package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class DefaultCategorySpendingUseCaseTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var currencyConvertUseCase: CurrencyConvertUseCase

    private val categoryId = Id.Known("cat1")

    // 2026-04-15 → current month April, so a 6-month trend spans Nov 2025 → Apr 2026.
    private val fixedInstant = Instant.parse("2026-04-15T12:00:00Z")
    private val fakeZonedClock = object : ZonedClock {
        override fun now() = fixedInstant
        override fun timeZone() = TimeZone.UTC
    }

    private fun createUseCase() = DefaultCategorySpendingUseCase(
        transactionRepository = transactionRepository,
        currencyConvertUseCase = currencyConvertUseCase,
        zonedClock = fakeZonedClock,
    )

    @Test
    fun `queryMonthlyTrend buckets spend by month, zero-filling empty months`() = runTest {
        whenever(currencyConvertUseCase.convertToPrimary(any(), any()))
            .thenAnswer { it.arguments[0] as Amount }
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForCategoryBetween>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        expense(amount = "100.00", date = "2026-02-10"),
                        expense(amount = "40.00", date = "2026-04-03"),
                        expense(amount = "60.00", date = "2026-04-12"),
                    ),
                ),
            )

        val result = createUseCase().queryMonthlyTrend(categoryId, months = 6).first()

        assertEquals(6, result.size)
        assertEquals(LocalDate(2025, 11, 1), result.first().month)
        assertEquals(LocalDate(2026, 4, 1), result.last().month)
        assertEquals(0, BigDecimal.ZERO.compareTo(result.first().totalAmount.value)) // Nov, empty
        assertEquals(0, BigDecimal("100.00").compareTo(result[3].totalAmount.value)) // Feb
        assertEquals(0, BigDecimal("100.00").compareTo(result.last().totalAmount.value)) // Apr (40 + 60)
    }

    @Test
    fun `queryMonthlyTrend excludes transfers`() = runTest {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForCategoryBetween>(), any()))
            .thenReturn(flowOf(listOf(transfer(amount = "500.00", date = "2026-04-05"))))

        val result = createUseCase().queryMonthlyTrend(categoryId, months = 6).first()

        assertEquals(0, BigDecimal.ZERO.compareTo(result.last().totalAmount.value))
    }

    private fun expense(amount: String, date: String) = TransactionRepository.Transaction.Expense(
        id = Id.Known("tx-$date"),
        amount = Amount(BigDecimal(amount)),
        accountId = Id.Known("acc1"),
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("${date}T10:00:00"),
        updatedDateTime = LocalDateTime.parse("${date}T10:00:00"),
        categoryId = categoryId,
        rate = Rate(BigDecimal.ONE),
    )

    private fun transfer(amount: String, date: String) = TransactionRepository.Transaction.Transfer(
        id = Id.Known("tr-$date"),
        amount = Amount(BigDecimal(amount)),
        accountId = Id.Known("acc1"),
        currencyId = Id.Known("cur1"),
        dateTime = LocalDateTime.parse("${date}T10:00:00"),
        updatedDateTime = LocalDateTime.parse("${date}T10:00:00"),
        targetAccount = Id.Known("acc2"),
        targetAmount = Amount(BigDecimal(amount)),
    )
}
