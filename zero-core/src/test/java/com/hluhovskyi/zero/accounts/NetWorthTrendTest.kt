package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class NetWorthTrendTest {

    @Test
    fun `income contributes positive, expense negative, transfer ignored`() {
        assertEquals(BigDecimal("100"), income("100").netWorthContribution()!!.second.value)
        assertEquals(BigDecimal("-40"), expense("40").netWorthContribution()!!.second.value)
        assertNull(transfer().netWorthContribution())
    }

    @Test
    fun `reconstruct ends at current net worth and walks deltas backward`() {
        val series = reconstructNetWorthTrend(
            currentNetWorth = Amount(BigDecimal("1000")),
            monthlyDeltas = mapOf(100 to Amount(BigDecimal("200")), 99 to Amount(BigDecimal("300"))),
            anchorMonthIndex = 100,
        )
        // nw[100]=1000, nw[99]=1000-200=800, nw[98]=800-300=500 -> oldest..newest
        assertEquals(listOf(BigDecimal("500"), BigDecimal("800"), BigDecimal("1000")), series.map { it.value })
    }

    @Test
    fun `no deltas yields a single current point`() {
        val series = reconstructNetWorthTrend(Amount(BigDecimal("1000")), emptyMap(), anchorMonthIndex = 100)
        assertEquals(listOf(BigDecimal("1000")), series.map { it.value })
    }

    @Test
    fun `window caps at 12 points even with older deltas`() {
        val deltas = (80..100).associateWith { Amount(BigDecimal("10")) }
        val series = reconstructNetWorthTrend(Amount(BigDecimal("1000")), deltas, anchorMonthIndex = 100)
        assertEquals(12, series.size)
        assertEquals(BigDecimal("1000"), series.last().value)
    }

    @Test
    fun `change is rising percent when net worth grew`() {
        assertEquals(NetWorthChange.Percent(25, rising = true), netWorthChange(trend("800", "1000")))
    }

    @Test
    fun `change is falling percent when net worth declined`() {
        assertEquals(NetWorthChange.Percent(10, rising = false), netWorthChange(trend("1000", "900")))
    }

    @Test
    fun `change is rising delta when underwater but improving`() {
        assertEquals(
            NetWorthChange.Delta(Amount(BigDecimal("5800")), rising = true),
            netWorthChange(trend("-14200", "-8400")),
        )
    }

    @Test
    fun `change is falling delta when net worth drops below zero`() {
        assertEquals(
            NetWorthChange.Delta(Amount(BigDecimal("100")), rising = false),
            netWorthChange(trend("0", "-100")),
        )
    }

    @Test
    fun `change is null for a single point or a flat window`() {
        assertNull(netWorthChange(trend("1000")))
        assertNull(netWorthChange(trend("500", "500")))
    }

    private fun trend(vararg values: String) = values.map { Amount(BigDecimal(it)) }

    private fun income(v: String) = TransactionRepository.Transaction.Income(
        id = Id.Known("i"), amount = Amount(BigDecimal(v)), accountId = Id.Known("a"),
        currencyId = Id.Known("c"), dateTime = DT, updatedDateTime = DT,
        categoryId = Id.Known("cat"), rate = Rate(BigDecimal.ONE),
    )

    private fun expense(v: String) = TransactionRepository.Transaction.Expense(
        id = Id.Known("e"), amount = Amount(BigDecimal(v)), accountId = Id.Known("a"),
        currencyId = Id.Known("c"), dateTime = DT, updatedDateTime = DT,
        categoryId = Id.Known("cat"), rate = Rate(BigDecimal.ONE),
    )

    private fun transfer() = TransactionRepository.Transaction.Transfer(
        id = Id.Known("t"), amount = Amount(BigDecimal("50")), accountId = Id.Known("a"),
        currencyId = Id.Known("c"), dateTime = DT, updatedDateTime = DT,
        targetAccount = Id.Known("b"), targetAmount = Amount(BigDecimal("50")),
    )

    private companion object {
        val DT: LocalDateTime = LocalDateTime.parse("2026-05-10T10:00:00")
    }
}
