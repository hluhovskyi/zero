package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.filterValueOf
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFilterNavigationArgumentSerializerTest {

    private val argument = filterValueOf("filter")
    private val serializer = TransactionFilterNavigationArgumentSerializer

    private fun roundTrip(filter: TransactionFilter): TransactionFilter {
        val raw = serializer.serialize(argument.withValue(filter))
        return serializer.deserialize(argument, raw).value
    }

    @Test
    fun `round-trips a full multi-dimension filter`() {
        val filter = TransactionFilter(
            period = TransactionFilter.DatePeriod.ThisMonth,
            type = TransactionFilter.TransactionType.Expense,
            categoryIds = setOf(Id.Known("cat-1"), Id.Known("cat-2"), Id.Known("cat-3")),
            accountIds = setOf(Id.Known("acc-1"), Id.Known("acc-2")),
        )
        assertEquals(filter, roundTrip(filter))
    }

    @Test
    fun `round-trips the empty filter`() {
        assertEquals(TransactionFilter.All, roundTrip(TransactionFilter.All))
    }

    @Test
    fun `null dimensions stay null - only the filtered ones come back`() {
        val filter = TransactionFilter(categoryIds = setOf(Id.Known("only-cat")))
        val result = roundTrip(filter)
        assertEquals(setOf(Id.Known("only-cat")), result.categoryIds)
        assertEquals(null, result.accountIds)
        assertEquals(null, result.period)
        assertEquals(TransactionFilter.TransactionType.All, result.type)
    }

    @Test
    fun `serialized payload is a route-safe hex string`() {
        val raw = serializer.serialize(
            argument.withValue(
                TransactionFilter(
                    period = TransactionFilter.DatePeriod.LastMonth,
                    accountIds = setOf(Id.Known("acc-1")),
                ),
            ),
        )
        assertTrue(raw.isNotEmpty())
        assertTrue(raw.all { it in "0123456789abcdef" })
    }
}
