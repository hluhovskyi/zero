package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFilterCriteriaTest {

    private val clock = object : Clock {
        override fun now() = Instant.parse("2024-06-15T12:00:00Z")
    }
    private val zoneProvider = object : ZoneProvider {
        override fun timeZone() = TimeZone.UTC
    }
    private val criteria = TransactionFilterCriteria(clock, zoneProvider)

    @Test
    fun `the all filter maps to an empty Filtered`() {
        val result = criteria.create(TransactionFilter.All)

        assertEquals(null, result.from)
        assertEquals(null, result.to)
        assertEquals(null, result.type)
        assertEquals(null, result.categoryIds)
        assertEquals(null, result.accountIds)
    }

    @Test
    fun `type maps to the api type`() {
        assertEquals(TransactionRepository.Type.Expense, criteria.create(TransactionFilter(type = TransactionFilter.TransactionType.Expense)).type)
        assertEquals(TransactionRepository.Type.Income, criteria.create(TransactionFilter(type = TransactionFilter.TransactionType.Income)).type)
        assertEquals(TransactionRepository.Type.Transfer, criteria.create(TransactionFilter(type = TransactionFilter.TransactionType.Transfer)).type)
    }

    @Test
    fun `period resolves to a concrete date range against today`() {
        val result = criteria.create(TransactionFilter(period = TransactionFilter.DatePeriod.ThisMonth))

        assertEquals(LocalDate(2024, 6, 1), result.from)
        assertEquals(LocalDate(2024, 6, 15), result.to)
    }

    @Test
    fun `category and account ids pass through`() {
        val result = criteria.create(
            TransactionFilter(categoryIds = setOf(Id.Known("c")), accountIds = setOf(Id.Known("a"))),
        )

        assertEquals(setOf(Id.Known("c")), result.categoryIds)
        assertEquals(setOf(Id.Known("a")), result.accountIds)
    }
}
