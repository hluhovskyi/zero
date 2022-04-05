package com.hluhovskyi.zero.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.reflect.KClass

interface ConfigurationRepository {

    fun <Value : Any> observe(key: ConfigurationKey<Value>, valueClass: KClass<Value>): Flow<Value>

    suspend fun <Value : Any> write(key: ConfigurationKey<Value>, valueClass: KClass<Value>, value: Value)

    object Noop : ConfigurationRepository {
        override fun <Value : Any> observe(key: ConfigurationKey<Value>, valueClass: KClass<Value>): Flow<Value> =
            emptyFlow()

        override suspend fun <Value : Any> write(
            key: ConfigurationKey<Value>,
            valueClass: KClass<Value>,
            value: Value
        ) = Unit
    }
}

inline fun <reified Value : Any> ConfigurationRepository.observe(
    key: ConfigurationKey<Value>
): Flow<Value> = observe(key, Value::class)

inline suspend fun <reified Value : Any> ConfigurationRepository.firstOrDefault(
    key: ConfigurationKey<Value>
): Value = observe(key).firstOrNull() ?: key.defaultValue

suspend inline fun <reified Value : Any> ConfigurationRepository.write(
    key: ConfigurationKey<Value>,
    value: Value
) {
    write(key, Value::class, value)
}