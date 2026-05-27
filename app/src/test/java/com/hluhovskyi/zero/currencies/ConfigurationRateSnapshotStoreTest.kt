package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.config.write
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigurationRateSnapshotStoreTest {

    @Test
    fun `save then load round-trips`() = runTest {
        val store = ConfigurationRateSnapshotStore(FakeConfigurationRepository())
        val stored = RateSnapshotStore.Stored(
            fetchedOn = LocalDate(2026, 5, 26),
            base = "EUR",
            rates = mapOf("USD" to 1.16, "GBP" to 0.86),
        )

        store.save(stored)

        assertEquals(stored, store.load())
    }

    @Test
    fun `load returns null when unset`() = runTest {
        assertNull(ConfigurationRateSnapshotStore(FakeConfigurationRepository()).load())
    }

    @Test
    fun `corrupt value loads as null instead of crashing`() = runTest {
        val config = FakeConfigurationRepository()
        config.write(CurrencyConfigurationKey.RateSnapshot, "{ this is not valid json")

        assertNull(ConfigurationRateSnapshotStore(config).load())
    }

    @Test
    fun `save overwrites the previous snapshot`() = runTest {
        val store = ConfigurationRateSnapshotStore(FakeConfigurationRepository())
        store.save(RateSnapshotStore.Stored(LocalDate(2026, 5, 25), "EUR", mapOf("USD" to 1.10)))

        store.save(RateSnapshotStore.Stored(LocalDate(2026, 5, 26), "EUR", mapOf("USD" to 1.16)))

        assertEquals(LocalDate(2026, 5, 26), store.load()!!.fetchedOn)
    }
}
