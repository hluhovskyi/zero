package com.hluhovskyi.zero.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

interface BiometricLockUseCase {

    val enabled: Flow<Boolean>

    val lockState: StateFlow<LockState>

    val autoPromptRequests: Flow<Unit>

    suspend fun setEnabled(value: Boolean)

    fun lock()

    fun unlock()

    fun onAppBackgrounded()

    fun onAppForegrounded()

    sealed interface LockState {
        object Locked : LockState
        object Unlocked : LockState
    }

    object Noop : BiometricLockUseCase {
        override val enabled: Flow<Boolean> = flowOf(false)
        override val lockState: StateFlow<LockState> = MutableStateFlow(LockState.Unlocked)
        override val autoPromptRequests: Flow<Unit> = emptyFlow()
        override suspend fun setEnabled(value: Boolean) = Unit
        override fun lock() = Unit
        override fun unlock() = Unit
        override fun onAppBackgrounded() = Unit
        override fun onAppForegrounded() = Unit
    }
}
