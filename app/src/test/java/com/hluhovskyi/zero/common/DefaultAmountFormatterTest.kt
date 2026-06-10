package com.hluhovskyi.zero.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class DefaultAmountFormatterTest {

    private val formatter = DefaultAmountFormatter()

    @Test
    fun `full style keeps grouping and two decimals`() {
        assertEquals("1,234.50", formatter.format(amount("1234.5")))
        assertEquals("$2,200.00", formatter.format(amount("2200"), "$"))
    }

    @Test
    fun `short style leaves values under a thousand intact`() {
        assertEquals("100", formatter.format(amount("100"), style = SHORT))
        assertEquals("105.5", formatter.format(amount("105.5"), style = SHORT))
        assertEquals("999", formatter.format(amount("999"), style = SHORT))
    }

    @Test
    fun `short style abbreviates thousands millions and billions`() {
        assertEquals("2.2K", formatter.format(amount("2200"), style = SHORT))
        assertEquals("1K", formatter.format(amount("1000"), style = SHORT))
        assertEquals("1.5K", formatter.format(amount("1500"), style = SHORT))
        assertEquals("4.5M", formatter.format(amount("4500000"), style = SHORT))
        assertEquals("105.7B", formatter.format(amount("105700000000"), style = SHORT))
    }

    @Test
    fun `short style keeps sign and currency symbol`() {
        assertEquals("$2.2K", formatter.format(amount("2200"), "$", SHORT))
        assertEquals("-$2.2K", formatter.format(amount("-2200"), "$", SHORT))
    }

    @Test
    fun `whole style drops the fractional part and keeps grouping`() {
        assertEquals("1,234", formatter.format(amount("1234.99"), style = WHOLE))
        assertEquals("100", formatter.format(amount("100.50"), style = WHOLE))
        assertEquals("$2,200", formatter.format(amount("2200"), "$", WHOLE))
        assertEquals("-$2,200", formatter.format(amount("-2200.75"), "$", WHOLE))
    }

    private fun amount(value: String): Amount = Amount(BigDecimal(value))

    private companion object {
        val SHORT = AmountFormatter.Style.Short
        val WHOLE = AmountFormatter.Style.Whole
    }
}
