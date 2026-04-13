package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncSerializerRoundTripTest {

    private val serializer = SyncSerializer()

    @Test
    fun `round-trip empty snapshot`() {
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        assertEquals(snapshot, serializer.deserialize(serializer.serialize(snapshot)))
    }

    @Test
    fun `round-trip snapshot with category tombstone`() {
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(
                SyncCategory(
                    id = Id.Known("cat-1"),
                    name = "Groceries",
                    iconId = null,
                    colorId = null,
                    parentCategoryId = null,
                    creationDateTime = LocalDateTime.parse("2024-01-01T00:00:00"),
                    updatedDateTime = LocalDateTime.parse("2024-06-01T00:00:00"),
                    deletedAt = LocalDateTime.parse("2024-06-01T00:00:00"),
                ),
            ),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        assertEquals(snapshot, serializer.deserialize(serializer.serialize(snapshot)))
    }

    @Test
    fun `round-trip transaction with max BigDecimal precision`() {
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = listOf(
                SyncTransaction(
                    id = Id.Known("tx-1"),
                    type = SyncTransaction.Type.EXPENSE,
                    accountId = Id.Known("acc-1"),
                    currencyId = Id.Known("USD"),
                    categoryId = "cat-1",
                    amount = "123456789.123456789",
                    rate = "1.123456789012345",
                    targetAccountId = null,
                    targetAmount = null,
                    enteredDateTime = LocalDateTime.parse("2024-01-15T10:30:00"),
                    creationDateTime = LocalDateTime.parse("2024-01-15T10:30:00"),
                    updatedDateTime = LocalDateTime.parse("2024-01-15T10:30:00"),
                    deletedAt = null,
                ),
            ),
        )
        assertEquals(snapshot, serializer.deserialize(serializer.serialize(snapshot)))
    }

    @Test
    fun `serialize produces valid json string`() {
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        val json = serializer.serialize(snapshot)
        assert(json.contains("\"version\":1")) { "Expected version field, got: $json" }
        assert(json.contains("\"userId\":\"user-1\"")) { "Expected userId field, got: $json" }
    }
}
