package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.config.ConfigurationKey
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DefaultBiometricLockUseCaseTest {

    private class MutableClock(private var current: Instant) : Clock {
        override fun now(): Instant = current
        fun advance(durationMs: Long) {
            current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + durationMs)
        }
    }

    private class FakeConfigurationRepository : ConfigurationRepository {
        private val enabled = MutableStateFlow(true)

        @Suppress("UNCHECKED_CAST")
        override fun <Value : Any> observe(
            key: ConfigurationKey<Value>,
            valueClass: KClass<Value>,
        ): Flow<Value> = enabled as Flow<Value>

        override suspend fun <Value : Any> write(
            key: ConfigurationKey<Value>,
            valueClass: KClass<Value>,
            value: Value,
        ) {
            enabled.value = value as Boolean
        }
    }

    private fun newUseCase(clock: MutableClock) = DefaultBiometricLockUseCase(
        configurationRepository = FakeConfigurationRepository(),
        clock = clock,
    )

    @Test
    fun `onAppForegrounded with no prior background locks and emits prompt request`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)

        useCase.onAppForegrounded()

        assertEquals(LockState.Locked, useCase.lockState.value)
        val emission = withTimeoutOrNull(100) { useCase.autoPromptRequests.first() }
        assertEquals(Unit, emission)
    }

    @Test
    fun `onAppForegrounded within 30 minutes of background does not lock`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.unlock()

        useCase.onAppBackgrounded()
        clock.advance(29.minutes.inWholeMilliseconds + 59.seconds.inWholeMilliseconds)
        useCase.onAppForegrounded()

        assertEquals(LockState.Unlocked, useCase.lockState.value)
    }

    @Test
    fun `onAppForegrounded at exactly 30 minutes locks`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.unlock()

        useCase.onAppBackgrounded()
        clock.advance(30.minutes.inWholeMilliseconds)
        useCase.onAppForegrounded()

        assertEquals(LockState.Locked, useCase.lockState.value)
    }

    @Test
    fun `onAppForegrounded while already locked emits prompt request even within timeout`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.lock()

        useCase.onAppBackgrounded()
        clock.advance(5.minutes.inWholeMilliseconds)
        useCase.onAppForegrounded()

        assertEquals(LockState.Locked, useCase.lockState.value)
        val emission = withTimeoutOrNull(100) { useCase.autoPromptRequests.first() }
        assertEquals(Unit, emission)
    }

    @Test
    fun `onAppForegrounded while unlocked and within timeout does not emit prompt request`() = runTest {
        val clock = MutableClock(Instant.parse("2026-05-14T12:00:00Z"))
        val useCase = newUseCase(clock)
        useCase.unlock()

        useCase.onAppBackgrounded()
        clock.advance(1.minutes.inWholeMilliseconds)
        useCase.onAppForegrounded()

        val emission = withTimeoutOrNull(50) { useCase.autoPromptRequests.first() }
        assertNull(emission)
    }
}
