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

    @Test
    fun `round-trip with midnight timestamps serialized without seconds`() {
        // kotlinx.datetime serializes LocalDateTime(y,m,d,0,0) as "T00:00" (no seconds when both are zero)
        // Verifies the round-trip works for this format, which appears in ZenMoney-imported data
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
                    currencyId = Id.Known("UAH"),
                    categoryId = null,
                    amount = "184.13",
                    rate = "1.0",
                    targetAccountId = null,
                    targetAmount = null,
                    enteredDateTime = LocalDateTime(2022, 4, 1, 0, 0),
                    creationDateTime = LocalDateTime(2023, 3, 7, 21, 5, 24, 86_000_000),
                    updatedDateTime = LocalDateTime(2023, 3, 7, 21, 5, 24, 86_000_000),
                    deletedAt = null,
                ),
            ),
        )
        val json = serializer.serialize(snapshot)
        assertEquals(snapshot, serializer.deserialize(json))
    }

    @Test
    fun `deserialize real-world exported JSON with T00_00 timestamps`() {
        // Snapshot extracted from a real export (Pixel 3a XL, API 24, 2026-04-22)
        // Accounts have creationDateTime "2000-01-01T00:00", transactions have "2022-04-01T00:00"
        val json = """{"version":1,"userId":"dc6dbfe0-5e7a-46a4-a0c5-eee7e5fbf907","exportedAt":"2026-04-22T16:41:57.682","categories":[],"accounts":[{"id":"115ffa24-695b-48bc-a24d-56266afccea1","currencyId":"BTC","name":"Bitcoin","iconId":"default_account_icon","initialBalance":"0.0","category":"OTHER","details":null,"creationDateTime":"2000-01-01T00:00","updatedDateTime":"2000-01-01T00:00","deletedAt":null}],"transactions":[{"id":"tx-1","type":"EXPENSE","accountId":"115ffa24-695b-48bc-a24d-56266afccea1","currencyId":"UAH","categoryId":null,"amount":"184.13","rate":"1.0","targetAccountId":null,"targetAmount":null,"enteredDateTime":"2022-04-01T00:00","creationDateTime":"2023-03-07T21:05:24.086","updatedDateTime":"2023-03-07T21:05:24.086","deletedAt":null}]}"""
        val snapshot = serializer.deserialize(json)
        assertEquals(1, snapshot.accounts.size)
        assertEquals(1, snapshot.transactions.size)
        assertEquals(LocalDateTime(2000, 1, 1, 0, 0), snapshot.accounts[0].creationDateTime)
        assertEquals(LocalDateTime(2022, 4, 1, 0, 0), snapshot.transactions[0].enteredDateTime)
    }
}
