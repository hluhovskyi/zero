package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.config.ConfigurationKey
import com.hluhovskyi.zero.config.ConfigurationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.reflect.KClass

/** In-memory [ConfigurationRepository] for tests: last write per key wins, missing → default. */
internal class FakeConfigurationRepository : ConfigurationRepository {

    private val values = mutableMapOf<String, Any>()

    override fun <Value : Any> observe(key: ConfigurationKey<Value>, valueClass: KClass<Value>): Flow<Value> {
        @Suppress("UNCHECKED_CAST")
        return flowOf((values[key.name] as? Value) ?: key.defaultValue)
    }

    override suspend fun <Value : Any> write(key: ConfigurationKey<Value>, valueClass: KClass<Value>, value: Value) {
        values[key.name] = value
    }
}
