package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.resource.ResourceResolver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    // --- Export ---

    @Test
    fun `export collects all entity types`() = runTest {
        val cat = syncCategory("cat-1", "2024-01-01T00:00:00")
        val acc = syncAccount("acc-1", "2024-01-01T00:00:00")
        val tx = syncTransaction("tx-1", "2024-01-01T00:00:00")

        val engine = engineWith(categories = listOf(cat), accounts = listOf(acc), transactions = listOf(tx))
        val snapshot = engine.export(userId = Id.Known("user-1"))

        assertEquals(listOf(cat), snapshot.categories)
        assertEquals(listOf(acc), snapshot.accounts)
        assertEquals(listOf(tx), snapshot.transactions)
    }

    @Test
    fun `export includes tombstones`() = runTest {
        val deleted = syncCategory("cat-1", "2024-06-01T00:00:00", deletedAt = LocalDateTime.parse("2024-06-01T00:00:00"))
        val engine = engineWith(categories = listOf(deleted))
        val snapshot = engine.export(userId = Id.Known("user-1"))

        assertEquals(listOf(deleted), snapshot.categories)
    }

    // --- Import ---

    @Test
    fun `import writes entities to local sink`() = runTest {
        val cat = syncCategory("cat-1", "2024-01-02T00:00:00")
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(cat),
            accounts = emptyList(),
            transactions = emptyList(),
        )

        val sink = FakeCategorySink()
        val engine = engineWith(categorySink = sink)
        engine.import(snapshot, userId = Id.Known("user-1"))

        assertEquals(listOf(cat), sink.upserted)
    }

    @Test
    fun `import applies LWW - incoming newer wins`() = runTest {
        val localCat = syncCategory("cat-1", "2024-01-01T00:00:00")
        val incomingCat = syncCategory("cat-1", "2024-01-02T00:00:00")
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(incomingCat),
            accounts = emptyList(),
            transactions = emptyList(),
        )

        val sink = FakeCategorySink()
        val engine = engineWith(
            categories = listOf(localCat),
            categorySink = sink,
        )
        engine.import(snapshot, userId = Id.Known("user-1"))

        assertEquals(listOf(incomingCat), sink.upserted)
    }

    @Test
    fun `import applies LWW - local newer wins, no write`() = runTest {
        val localCat = syncCategory("cat-1", "2024-01-03T00:00:00")
        val incomingCat = syncCategory("cat-1", "2024-01-01T00:00:00")
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(incomingCat),
            accounts = emptyList(),
            transactions = emptyList(),
        )

        val sink = FakeCategorySink()
        val engine = engineWith(
            categories = listOf(localCat),
            categorySink = sink,
        )
        engine.import(snapshot, userId = Id.Known("user-1"))

        assertTrue("Local newer: sink should not be written", sink.upserted.isEmpty())
    }

    // --- Delta ---

    @Test
    fun `delta returns empty snapshot when nothing is new`() = runTest {
        val cat = syncCategory("cat-1", "2024-01-02T00:00:00")
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(cat),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        val engine = engineWith(categories = listOf(cat))
        val delta = engine.delta(snapshot, userId = Id.Known("user-1"))

        assertTrue("No new categories expected", delta.categories.isEmpty())
    }

    @Test
    fun `delta returns only entities newer than stored`() = runTest {
        val localCat = syncCategory("cat-1", "2024-01-03T00:00:00")
        val incomingCat = syncCategory("cat-1", "2024-01-01T00:00:00")
        val newCat = syncCategory("cat-2", "2024-01-01T00:00:00")
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(incomingCat, newCat),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        val engine = engineWith(categories = listOf(localCat))
        val delta = engine.delta(snapshot, userId = Id.Known("user-1"))

        assertEquals(listOf(newCat), delta.categories)
    }

    @Test
    fun `delta returns incoming when it wins LWW`() = runTest {
        val localCat = syncCategory("cat-1", "2024-01-01T00:00:00")
        val incomingCat = syncCategory("cat-1", "2024-01-05T00:00:00")
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = listOf(incomingCat),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        val engine = engineWith(categories = listOf(localCat))
        val delta = engine.delta(snapshot, userId = Id.Known("user-1"))

        assertEquals(listOf(incomingCat), delta.categories)
    }

    @Test
    fun `delta preserves snapshot metadata`() = runTest {
        val snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("original-user"),
            exportedAt = LocalDateTime.parse("2026-04-12T10:00:00"),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        val engine = engineWith()
        val delta = engine.delta(snapshot, userId = Id.Known("current-user"))

        assertEquals(snapshot.version, delta.version)
        assertEquals(snapshot.userId, delta.userId)
        assertEquals(snapshot.exportedAt, delta.exportedAt)
    }

    // --- Helpers ---

    private fun engineWith(
        categories: List<SyncCategory> = emptyList(),
        accounts: List<SyncAccount> = emptyList(),
        transactions: List<SyncTransaction> = emptyList(),
        budgets: List<SyncBudget> = emptyList(),
        categorySink: FakeCategorySink = FakeCategorySink(),
    ): SyncEngine = DefaultSyncEngine(
        categoryPipeline = SyncPipeline(
            source = FakeCategorySource(categories),
            sink = categorySink,
            resolver = LastWriteWinsResolver(),
        ),
        accountPipeline = SyncPipeline(
            source = FakeAccountSource(accounts),
            sink = FakeAccountSink(),
            resolver = LastWriteWinsResolver(),
        ),
        transactionPipeline = SyncPipeline(
            source = FakeTransactionSource(transactions),
            sink = FakeTransactionSink(),
            resolver = LastWriteWinsResolver(),
        ),
        budgetPipeline = SyncPipeline(
            source = FakeBudgetSource(budgets),
            sink = FakeBudgetSink(),
            resolver = LastWriteWinsResolver(),
        ),
        resourceResolver = ResourceResolver.Noop,
        serializer = SyncSerializer(),
    )

    private fun syncCategory(id: String, updatedAt: String, deletedAt: LocalDateTime? = null) = SyncCategory(
        id = Id.Known(id), name = "Cat $id", iconId = null, colorId = null, parentCategoryId = null,
        creationDateTime = LocalDateTime.parse(updatedAt),
        updatedDateTime = LocalDateTime.parse(updatedAt),
        deletedAt = deletedAt,
    )

    private fun syncAccount(id: String, updatedAt: String) = SyncAccount(
        id = Id.Known(id), currencyId = Id.Known("USD"), name = "Acc $id",
        iconId = Id.Known("icon-1"), initialBalance = "0",
        category = "OTHER", details = null,
        creationDateTime = LocalDateTime.parse(updatedAt),
        updatedDateTime = LocalDateTime.parse(updatedAt),
        deletedAt = null,
    )

    private fun syncTransaction(id: String, updatedAt: String) = SyncTransaction(
        id = Id.Known(id), type = SyncTransaction.Type.EXPENSE,
        accountId = Id.Known("acc-1"), currencyId = Id.Known("USD"),
        categoryId = "cat-1", amount = "10.00", rate = "1.0",
        targetAccountId = null, targetAmount = null,
        enteredDateTime = LocalDateTime.parse(updatedAt),
        creationDateTime = LocalDateTime.parse(updatedAt),
        updatedDateTime = LocalDateTime.parse(updatedAt),
        deletedAt = null,
    )
}

// Fakes
private class FakeCategorySource(private val data: List<SyncCategory>) : EntitySyncSource<SyncCategory> {
    override suspend fun exportAll(userId: Id.Known) = data
    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime) = data.filter { it.updatedDateTime > since }
}

private class FakeCategorySink : EntitySyncSink<SyncCategory> {
    val upserted = mutableListOf<SyncCategory>()
    override suspend fun syncUpsert(entities: List<SyncCategory>) {
        upserted.addAll(entities)
    }
}

private class FakeAccountSource(private val data: List<SyncAccount>) : EntitySyncSource<SyncAccount> {
    override suspend fun exportAll(userId: Id.Known) = data
    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime) = data.filter { it.updatedDateTime > since }
}

private class FakeAccountSink : EntitySyncSink<SyncAccount> {
    override suspend fun syncUpsert(entities: List<SyncAccount>) {}
}

private class FakeTransactionSource(private val data: List<SyncTransaction>) : EntitySyncSource<SyncTransaction> {
    override suspend fun exportAll(userId: Id.Known) = data
    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime) = data.filter { it.updatedDateTime > since }
}

private class FakeTransactionSink : EntitySyncSink<SyncTransaction> {
    override suspend fun syncUpsert(entities: List<SyncTransaction>) {}
}

private class FakeBudgetSource(private val data: List<SyncBudget>) : EntitySyncSource<SyncBudget> {
    override suspend fun exportAll(userId: Id.Known) = data
    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime) = data.filter { it.updatedDateTime > since }
}

private class FakeBudgetSink : EntitySyncSink<SyncBudget> {
    override suspend fun syncUpsert(entities: List<SyncBudget>) {}
}
