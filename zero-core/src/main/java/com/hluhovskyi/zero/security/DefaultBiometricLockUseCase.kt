package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.observe
import com.hluhovskyi.zero.config.write
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class DefaultBiometricLockUseCase(
    private val configurationRepository: ConfigurationRepository,
) : BiometricLockUseCase {

    private val mutableLockState = MutableStateFlow<LockState>(LockState.Unlocked)

    override val enabled: Flow<Boolean> = configurationRepository
        .observe(BiometricConfigurationKey.Enabled)
        .distinctUntilChanged()

    override val lockState: StateFlow<LockState> = mutableLockState

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
}
