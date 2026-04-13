package com.hluhovskyi.zero.sync

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBackwardCompatibilityTest {

    private val serializer = SyncSerializer()

    @Test
    fun `v1 full snapshot deserializes`() {
        val json = loadFixture("v1-full-snapshot.json")
        val snapshot = serializer.deserialize(json)
        assertTrue(snapshot.categories.isNotEmpty())
        assertTrue(snapshot.accounts.isNotEmpty())
        assertTrue(snapshot.transactions.isNotEmpty())
    }

    @Test
    fun `v1 category with all fields`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[${loadFixture("v1-category.json")}],
            "accounts":[],"transactions":[]}"""
        val snapshot = serializer.deserialize(json)
        val cat = snapshot.categories.single()
        assertNotNull(cat.id)
        assertNotNull(cat.name)
        assertNotNull(cat.updatedDateTime)
    }

    @Test
    fun `v1 category tombstone preserves deletedAt`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[${loadFixture("v1-category-tombstone.json")}],
            "accounts":[],"transactions":[]}"""
        val snapshot = serializer.deserialize(json)
        assertNotNull(snapshot.categories.single().deletedAt)
    }

    @Test
    fun `v1 account with all fields`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[${loadFixture("v1-account.json")}],"transactions":[]}"""
        val snapshot = serializer.deserialize(json)
        val acc = snapshot.accounts.single()
        assertNotNull(acc.id)
        assertNotNull(acc.name)
    }

    @Test
    fun `v1 account tombstone preserves deletedAt`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[${loadFixture("v1-account-tombstone.json")}],"transactions":[]}"""
        val snapshot = serializer.deserialize(json)
        assertNotNull(snapshot.accounts.single().deletedAt)
    }

    @Test
    fun `v1 transaction expense`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[],"transactions":[${loadFixture("v1-transaction-expense.json")}]}"""
        val snapshot = serializer.deserialize(json)
        val tx = snapshot.transactions.single()
        assert(tx.type == SyncTransaction.Type.EXPENSE)
    }

    @Test
    fun `v1 transaction income`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[],"transactions":[${loadFixture("v1-transaction-income.json")}]}"""
        val tx = serializer.deserialize(json).transactions.single()
        assert(tx.type == SyncTransaction.Type.INCOME)
    }

    @Test
    fun `v1 transaction transfer has targetAccountId`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[],"transactions":[${loadFixture("v1-transaction-transfer.json")}]}"""
        val tx = serializer.deserialize(json).transactions.single()
        assert(tx.type == SyncTransaction.Type.TRANSFER)
        assertNotNull(tx.targetAccountId)
        assertNotNull(tx.targetAmount)
    }

    @Test
    fun `v1 transaction tombstone preserves deletedAt`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[],"transactions":[${loadFixture("v1-transaction-tombstone.json")}]}"""
        val tx = serializer.deserialize(json).transactions.single()
        assertNotNull(tx.deletedAt)
    }

    @Test
    fun `unknown fields are ignored (forward compatibility)`() {
        val json = """{"version":1,"userId":"u","exportedAt":"2026-04-12T10:00:00",
            "categories":[],"accounts":[],"transactions":[],"futureField":"ignored"}"""
        val snapshot = serializer.deserialize(json) // must not throw
        assertNotNull(snapshot)
    }

    private fun loadFixture(name: String): String = javaClass.classLoader!!.getResourceAsStream("fixtures/sync/$name")!!
        .bufferedReader().readText()
}
