package com.hluhovskyi.zero.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass

interface ConfigurationRepository {

    fun <Value : Any> observe(key: Key<Value>, valueClass: KClass<Value>): Flow<Value>

    suspend fun <Value : Any> write(key: Key<Value>, valueClass: KClass<Value>, value: Value)

    interface Key<Value> {
        val name: String
        val defaultValue: Value
    }

    object Noop : ConfigurationRepository {
        override fun <Value : Any> observe(key: Key<Value>, valueClass: KClass<Value>): Flow<Value> = emptyFlow()
        override suspend fun <Value : Any> write(key: Key<Value>, valueClass: KClass<Value>, value: Value) = Unit
    }
}

inline fun <reified Value : Any> ConfigurationRepository.observe(
    key: ConfigurationRepository.Key<Value>
): Flow<Value> = observe(key, Value::class)

suspend inline fun <reified Value : Any> ConfigurationRepository.write(
    key: ConfigurationRepository.Key<Value>,
    value: Value
) {
    write(key, Value::class, value)
}