package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TransactionFilterApplicatorTest {

    private val timeZone = TimeZone.UTC

    // "today" for all tests: 2024-06-05 (Wednesday, month=June, week starts Mon 2024-06-03)
    private val today = LocalDate(2024, 6, 5)
    private val todayInstant = Instant.parse("2024-06-05T12:00:00Z")

    private val fakeClock = object : Clock {
        override fun now() = todayInstant
    }
    private val fakeZoneProvider = object : ZoneProvider {
        override fun timeZone() = timeZone
    }

    // Helpers ---------------------------------------------------------------------------------

    private fun expense(date: LocalDate, id: String = "t", categoryId: String = "cat1", accountId: String = "acc1") =
        TransactionRepository.Transaction.Expense(
            id = Id.Known(id),
            dateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, 12, 0),
            updatedDateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, 12, 0),
            amount = Amount(BigDecimal.TEN),
            currencyId = Id.Known("cur"),
            accountId = Id.Known(accountId),
            categoryId = Id.Known(categoryId),
            rate = Rate.Same,
        )

    private fun income(date: LocalDate, id: String = "t", categoryId: String = "cat1", accountId: String = "acc1") =
        TransactionRepository.Transaction.Income(
            id = Id.Known(id),
            dateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, 12, 0),
            updatedDateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, 12, 0),
            amount = Amount(BigDecimal.TEN),
            currencyId = Id.Known("cur"),
            accountId = Id.Known(accountId),
            categoryId = Id.Known(categoryId),
            rate = Rate.Same,
        )

    private fun transfer(date: LocalDate, id: String = "t", accountId: String = "acc1") =
        TransactionRepository.Transaction.Transfer(
            id = Id.Known(id),
            dateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, 12, 0),
            updatedDateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, 12, 0),
            amount = Amount(BigDecimal.TEN),
            currencyId = Id.Known("cur"),
            accountId = Id.Known(accountId),
            targetAccount = Id.Known("acc2"),
            targetAmount = Amount(BigDecimal.TEN),
        )

    // DatePeriodTransactionFilterApplicator ---------------------------------------------------

    @Test
    fun `DatePeriod_Today keeps only transactions from today`() {
        val applicator = DatePeriodTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(period = TransactionFilter.DatePeriod.Today)
        val transactions = listOf(
            expense(LocalDate(2024, 6, 4), "yesterday"),
            expense(LocalDate(2024, 6, 5), "today"),
            expense(LocalDate(2024, 6, 6), "tomorrow"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(listOf("today"), result.map { (it as TransactionRepository.Transaction.Expense).id.value })
    }

    @Test
    fun `DatePeriod_ThisWeek keeps transactions from Monday through today`() {
        val applicator = DatePeriodTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(period = TransactionFilter.DatePeriod.ThisWeek)
        val transactions = listOf(
            expense(LocalDate(2024, 6, 2), "sunday-before"),  // Sun before the week
            expense(LocalDate(2024, 6, 3), "monday"),          // Mon
            expense(LocalDate(2024, 6, 5), "wednesday"),       // Wed (today)
            expense(LocalDate(2024, 6, 6), "thursday"),        // Thu (future)
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(
            listOf("monday", "wednesday"),
            result.map { (it as TransactionRepository.Transaction.Expense).id.value },
        )
    }

    @Test
    fun `DatePeriod_ThisMonth keeps transactions from first of month through today`() {
        val applicator = DatePeriodTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(period = TransactionFilter.DatePeriod.ThisMonth)
        val transactions = listOf(
            expense(LocalDate(2024, 5, 31), "last-month"),
            expense(LocalDate(2024, 6, 1), "first-of-month"),
            expense(LocalDate(2024, 6, 5), "today"),
            expense(LocalDate(2024, 6, 6), "future"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(
            listOf("first-of-month", "today"),
            result.map { (it as TransactionRepository.Transaction.Expense).id.value },
        )
    }

    @Test
    fun `DatePeriod_LastMonth keeps transactions from previous month only`() {
        val applicator = DatePeriodTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(period = TransactionFilter.DatePeriod.LastMonth)
        val transactions = listOf(
            expense(LocalDate(2024, 4, 30), "april"),
            expense(LocalDate(2024, 5, 1), "may-first"),
            expense(LocalDate(2024, 5, 31), "may-last"),
            expense(LocalDate(2024, 6, 1), "june"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(
            listOf("may-first", "may-last"),
            result.map { (it as TransactionRepository.Transaction.Expense).id.value },
        )
    }

    @Test
    fun `DatePeriod_ThisYear keeps transactions from Jan 1 through today`() {
        val applicator = DatePeriodTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(period = TransactionFilter.DatePeriod.ThisYear)
        val transactions = listOf(
            expense(LocalDate(2023, 12, 31), "last-year"),
            expense(LocalDate(2024, 1, 1), "jan-first"),
            expense(LocalDate(2024, 6, 5), "today"),
            expense(LocalDate(2024, 6, 6), "future"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(
            listOf("jan-first", "today"),
            result.map { (it as TransactionRepository.Transaction.Expense).id.value },
        )
    }

    @Test
    fun `DatePeriod passes all through when period is null`() {
        val applicator = DatePeriodTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(period = null)
        val transactions = listOf(
            expense(LocalDate(2020, 1, 1), "old"),
            expense(LocalDate(2024, 6, 5), "today"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(transactions, result)
    }

    // TypeTransactionFilterApplicator ---------------------------------------------------------

    @Test
    fun `Type_All passes everything through`() {
        val filter = TransactionFilter(type = TransactionFilter.TransactionType.All)
        val transactions = listOf(expense(today, "e"), income(today, "i"), transfer(today, "t"))

        val result = TypeTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(transactions, result)
    }

    @Test
    fun `Type_Expense keeps only expenses`() {
        val filter = TransactionFilter(type = TransactionFilter.TransactionType.Expense)
        val transactions = listOf(expense(today, "e"), income(today, "i"), transfer(today, "t"))

        val result = TypeTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(1, result.size)
        assert(result[0] is TransactionRepository.Transaction.Expense)
    }

    @Test
    fun `Type_Income keeps only income`() {
        val filter = TransactionFilter(type = TransactionFilter.TransactionType.Income)
        val transactions = listOf(expense(today, "e"), income(today, "i"), transfer(today, "t"))

        val result = TypeTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(1, result.size)
        assert(result[0] is TransactionRepository.Transaction.Income)
    }

    @Test
    fun `Type_Transfer keeps only transfers`() {
        val filter = TransactionFilter(type = TransactionFilter.TransactionType.Transfer)
        val transactions = listOf(expense(today, "e"), income(today, "i"), transfer(today, "t"))

        val result = TypeTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(1, result.size)
        assert(result[0] is TransactionRepository.Transaction.Transfer)
    }

    // CategoryTransactionFilterApplicator -----------------------------------------------------

    @Test
    fun `Category passes all through when categoryIds is null`() {
        val filter = TransactionFilter(categoryIds = null)
        val transactions = listOf(
            expense(today, "e1", categoryId = "cat1"),
            expense(today, "e2", categoryId = "cat2"),
        )

        val result = CategoryTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(transactions, result)
    }

    @Test
    fun `Category keeps matching expenses and incomes, drops transfers`() {
        val filter = TransactionFilter(categoryIds = setOf(Id.Known("cat1")))
        val transactions = listOf(
            expense(today, "e1", categoryId = "cat1"),
            expense(today, "e2", categoryId = "cat2"),
            income(today, "i1", categoryId = "cat1"),
            transfer(today, "tr1"),
        )

        val result = CategoryTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(2, result.size)
        assertEquals(setOf("e1", "i1"), result.map { it.id.value }.toSet())
    }

    @Test
    fun `Category with multiple ids keeps all matching`() {
        val filter = TransactionFilter(categoryIds = setOf(Id.Known("cat1"), Id.Known("cat2")))
        val transactions = listOf(
            expense(today, "e1", categoryId = "cat1"),
            expense(today, "e2", categoryId = "cat2"),
            expense(today, "e3", categoryId = "cat3"),
        )

        val result = CategoryTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(listOf("e1", "e2"), result.map { it.id.value })
    }

    // AccountTransactionFilterApplicator ------------------------------------------------------

    @Test
    fun `Account passes all through when accountIds is null`() {
        val filter = TransactionFilter(accountIds = null)
        val transactions = listOf(
            expense(today, "e1", accountId = "acc1"),
            expense(today, "e2", accountId = "acc2"),
        )

        val result = AccountTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(transactions, result)
    }

    @Test
    fun `Account keeps only transactions with matching account`() {
        val filter = TransactionFilter(accountIds = setOf(Id.Known("acc1")))
        val transactions = listOf(
            expense(today, "e1", accountId = "acc1"),
            expense(today, "e2", accountId = "acc2"),
            transfer(today, "tr1", accountId = "acc1"),
            transfer(today, "tr2", accountId = "acc2"),
        )

        val result = AccountTransactionFilterApplicator.apply(transactions, filter)

        assertEquals(listOf("e1", "tr1"), result.map { it.id.value })
    }

    // DefaultTransactionFilterApplicator ------------------------------------------------------

    @Test
    fun `Default passes all through when filter is inactive`() {
        val applicator = DefaultTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter()
        val transactions = listOf(
            expense(LocalDate(2020, 1, 1), "old"),
            expense(today, "today"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(transactions, result)
    }

    @Test
    fun `Default composes period and type filters`() {
        val applicator = DefaultTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(
            period = TransactionFilter.DatePeriod.Today,
            type = TransactionFilter.TransactionType.Expense,
        )
        val transactions = listOf(
            expense(today, "e-today"),
            income(today, "i-today"),
            expense(LocalDate(2024, 6, 4), "e-yesterday"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(listOf("e-today"), result.map { it.id.value })
    }

    @Test
    fun `Default composes category and account filters`() {
        val applicator = DefaultTransactionFilterApplicator(fakeClock, fakeZoneProvider)
        val filter = TransactionFilter(
            categoryIds = setOf(Id.Known("cat1")),
            accountIds = setOf(Id.Known("acc1")),
        )
        val transactions = listOf(
            expense(today, "match", categoryId = "cat1", accountId = "acc1"),
            expense(today, "wrong-cat", categoryId = "cat2", accountId = "acc1"),
            expense(today, "wrong-acc", categoryId = "cat1", accountId = "acc2"),
        )

        val result = applicator.apply(transactions, filter)

        assertEquals(listOf("match"), result.map { it.id.value })
    }
}
