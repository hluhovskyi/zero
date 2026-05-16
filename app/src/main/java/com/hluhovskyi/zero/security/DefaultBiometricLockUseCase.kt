package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.observe
import com.hluhovskyi.zero.config.write
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class DefaultBiometricLockUseCase(
    private val configurationRepository: ConfigurationRepository,
    private val clock: Clock,
) : BiometricLockUseCase {

    private val mutableLockState = MutableStateFlow<LockState>(LockState.Unlocked)
    private val mutableAutoPromptRequests = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var lastBackgroundedAt: Instant? = null

    override val enabled: Flow<Boolean> = configurationRepository
        .observe(BiometricConfigurationKey.Enabled)
        .distinctUntilChanged()

    override val lockState: StateFlow<LockState> = mutableLockState

    override val isLocked: Flow<Boolean> = combine(enabled, mutableLockState) { enabled, lockState ->
        enabled && lockState is LockState.Locked
    }.distinctUntilChanged()

    override val autoPromptRequests: Flow<Unit> = mutableAutoPromptRequests

    override suspend fun setEnabled(value: Boolean) {
        configurationRepository.write(BiometricConfigurationKey.Enabled, value)
        if (!value) {
            mutableLockState.value = LockState.Unlocked
        }
    }

    override fun lock() {
        mutableLockState.value = LockState.Locked
    }

    override fun unlock() {
        mutableLockState.value = LockState.Unlocked
    }

    override fun onAppBackgrounded() {
        lastBackgroundedAt = clock.now()
    }

    override fun onAppForegrounded() {
        val backgroundedAt = lastBackgroundedAt
        val elapsed: Duration? = backgroundedAt?.let { clock.now() - it }
        if (elapsed == null || elapsed >= INACTIVITY_TIMEOUT) {
            mutableLockState.value = LockState.Locked
        }
        if (mutableLockState.value is LockState.Locked) {
            mutableAutoPromptRequests.tryEmit(Unit)
        }
    }

    companion object {
        private val INACTIVITY_TIMEOUT: Duration = 30.minutes
    }
}
