package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.config.ConfigurationKey
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

class DefaultBiometricLockUseCaseTest {

    private val repository = InMemoryConfigurationRepository()
    private val useCase = DefaultBiometricLockUseCase(configurationRepository = repository)

    @Test
    fun `enabled emits false by default`() = runTest {
        assertEquals(false, useCase.enabled.first())
    }

    @Test
    fun `setEnabled true persists to repository and observable emits true`() = runTest {
        useCase.setEnabled(true)
        assertEquals(true, useCase.enabled.first())
    }

    @Test
    fun `setEnabled false moves lockState to Unlocked`() = runTest {
        useCase.lock()
        useCase.setEnabled(false)
        assertEquals(LockState.Unlocked, useCase.lockState.value)
    }

    @Test
    fun `lock then unlock toggles lockState`() {
        useCase.lock()
        assertEquals(LockState.Locked, useCase.lockState.value)
        useCase.unlock()
        assertEquals(LockState.Unlocked, useCase.lockState.value)
    }
}

private class InMemoryConfigurationRepository : ConfigurationRepository {

    private val store = mutableMapOf<String, MutableStateFlow<Any>>()

    @Suppress("UNCHECKED_CAST")
    override fun <Value : Any> observe(
        key: ConfigurationKey<Value>,
        valueClass: KClass<Value>,
    ): kotlinx.coroutines.flow.Flow<Value> {
        val flow = store.getOrPut(key.name) { MutableStateFlow(key.defaultValue) }
        return flow as kotlinx.coroutines.flow.Flow<Value>
    }

    override suspend fun <Value : Any> write(
        key: ConfigurationKey<Value>,
        valueClass: KClass<Value>,
        value: Value,
    ) {
        val flow = store.getOrPut(key.name) { MutableStateFlow(key.defaultValue) }
        flow.value = value
    }
}
