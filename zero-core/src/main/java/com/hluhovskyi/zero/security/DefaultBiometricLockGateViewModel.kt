package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.security.BiometricAuthenticator.AuthReason
import com.hluhovskyi.zero.security.BiometricAuthenticator.Result
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultBiometricLockGateViewModel(
    private val biometricLockUseCase: BiometricLockUseCase,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : BiometricLockGateViewModel {

    private val mutableState = MutableStateFlow(BiometricLockGateViewModel.State())
    override val state: Flow<BiometricLockGateViewModel.State> = mutableState

    override fun perform(action: BiometricLockGateViewModel.Action) {
        when (action) {
            is BiometricLockGateViewModel.Action.Unlock -> coroutineScope.launch {
                when (biometricAuthenticator.authenticate(AuthReason.Unlock)) {
                    Result.Success -> biometricLockUseCase.unlock()
                    Result.Failure -> Unit
                    Result.Unavailable -> biometricLockUseCase.unlock()
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                biometricLockUseCase.enabled,
                biometricLockUseCase.lockState,
            ) { enabled, lockState ->
                enabled && lockState is LockState.Locked
            }.collect { isLocked ->
                mutableState.update { it.copy(isLocked = isLocked) }
            }
        }
    }
}
