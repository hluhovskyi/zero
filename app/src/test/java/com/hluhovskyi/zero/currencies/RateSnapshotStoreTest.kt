package com.hluhovskyi.zero.currencies

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RateSnapshotStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `save then load round-trips`() = runTest {
        val store = RateSnapshotStore(tmp.newFile("rates.json"))
        val stored = RateSnapshotStore.Stored(
            fetchedOn = "2026-05-26",
            base = "EUR",
            rates = mapOf("USD" to 1.16, "GBP" to 0.86),
        )

        store.save(stored)

        assertEquals(stored, store.load())
    }

    @Test
    fun `load returns null when file is missing`() = runTest {
        val store = RateSnapshotStore(File(tmp.root, "missing.json"))

        assertNull(store.load())
    }
}
