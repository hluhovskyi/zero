package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class NetWorthTrendTest {

    @Test
    fun `reconstruct walks net deltas backward, ending at current net worth, keeping a baseline`() {
        // deltas [0, +300, +200] from a baseline of 500 -> 500, 800, 1000.
        val series = reconstructNetWorthTrend(amount("1000"), amounts("0", "300", "200"))

        assertEquals(listOf(BigDecimal("500"), BigDecimal("800"), BigDecimal("1000")), series.map { it.value })
    }

    @Test
    fun `reconstruct trims leading inactive months, keeping one pre-activity baseline`() {
        // Only the last month moved (0 -> -100): a two-point baseline-to-current series.
        val series = reconstructNetWorthTrend(amount("-100"), amounts("0", "0", "-100"))

        assertEquals(listOf(BigDecimal("0"), BigDecimal("-100")), series.map { it.value })
    }

    @Test
    fun `reconstruct with no activity yields a single current point`() {
        assertEquals(listOf(BigDecimal("1000")), reconstructNetWorthTrend(amount("1000"), amounts("0", "0")).map { it.value })
        assertEquals(listOf(BigDecimal("1000")), reconstructNetWorthTrend(amount("1000"), emptyList()).map { it.value })
    }

    @Test
    fun `change is rising percent when net worth grew`() {
        assertEquals(NetWorthChange.Percent(25, rising = true), netWorthChange(amounts("800", "1000")))
    }

    @Test
    fun `change is falling percent when net worth declined`() {
        assertEquals(NetWorthChange.Percent(10, rising = false), netWorthChange(amounts("1000", "900")))
    }

    @Test
    fun `change is rising delta when underwater but improving`() {
        assertEquals(
            NetWorthChange.Delta(Amount(BigDecimal("5800")), rising = true),
            netWorthChange(amounts("-14200", "-8400")),
        )
    }

    @Test
    fun `change is falling delta when net worth drops below zero`() {
        assertEquals(
            NetWorthChange.Delta(Amount(BigDecimal("100")), rising = false),
            netWorthChange(amounts("0", "-100")),
        )
    }

    @Test
    fun `change is null for a single point or a flat window`() {
        assertNull(netWorthChange(amounts("1000")))
        assertNull(netWorthChange(amounts("500", "500")))
    }

    private fun amount(value: String) = Amount(BigDecimal(value))

    private fun amounts(vararg values: String) = values.map { Amount(BigDecimal(it)) }
}
