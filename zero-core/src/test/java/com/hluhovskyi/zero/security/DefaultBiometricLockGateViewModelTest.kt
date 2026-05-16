package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.security.BiometricAuthenticator.AuthReason
import com.hluhovskyi.zero.security.BiometricAuthenticator.Result
import com.hluhovskyi.zero.security.BiometricLockUseCase.LockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultBiometricLockGateViewModelTest {

    private class FakeBiometricLockUseCase(
        initialEnabled: Boolean = true,
        initialLock: LockState = LockState.Locked,
    ) : BiometricLockUseCase {

        private val mutableEnabled = MutableStateFlow(initialEnabled)
        private val mutableLockState = MutableStateFlow(initialLock)
        val mutablePromptRequests = MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        override val enabled: Flow<Boolean> = mutableEnabled
        override val lockState: StateFlow<LockState> = mutableLockState
        override val isLocked: Flow<Boolean> = combine(mutableEnabled, mutableLockState) { e, l ->
            e && l is LockState.Locked
        }.distinctUntilChanged()
        override val autoPromptRequests: Flow<Unit> = mutablePromptRequests

        override suspend fun setEnabled(value: Boolean) { mutableEnabled.value = value }
        override fun lock() { mutableLockState.value = LockState.Locked }
        override fun unlock() { mutableLockState.value = LockState.Unlocked }
        override fun onAppBackgrounded() = Unit
        override fun onAppForegrounded() = Unit
    }

    private class FakeBiometricAuthenticator(
        private val result: Result = Result.Success,
    ) : BiometricAuthenticator {
        var calls: Int = 0
            private set
        override suspend fun authenticate(reason: AuthReason): Result {
            calls++
            return result
        }
    }

    private fun newViewModel(
        useCase: BiometricLockUseCase,
        authenticator: BiometricAuthenticator,
        scope: CoroutineScope,
    ): DefaultBiometricLockGateViewModel = DefaultBiometricLockGateViewModel(
        biometricLockUseCase = useCase,
        biometricAuthenticator = authenticator,
        coroutineScope = scope,
    )

    @Test
    fun `each autoPromptRequest increments promptToken`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = FakeBiometricLockUseCase()
        val viewModel = newViewModel(useCase, FakeBiometricAuthenticator(), CoroutineScope(dispatcher))
        viewModel.attach()
        advanceUntilIdle()

        useCase.mutablePromptRequests.tryEmit(Unit)
        advanceUntilIdle()
        useCase.mutablePromptRequests.tryEmit(Unit)
        advanceUntilIdle()

        val state = viewModel.state.dropWhile { it.promptToken < 2 }.first()
        assertEquals(2, state.promptToken)
        assertEquals(true, state.isLocked)
    }

    @Test
    fun `state reflects isLocked from useCase combine`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = FakeBiometricLockUseCase(initialLock = LockState.Unlocked)
        val viewModel = newViewModel(useCase, FakeBiometricAuthenticator(), CoroutineScope(dispatcher))
        viewModel.attach()
        advanceUntilIdle()

        val unlockedState = viewModel.state.first()
        assertEquals(false, unlockedState.isLocked)

        useCase.lock()
        advanceUntilIdle()
        val lockedState = viewModel.state.dropWhile { !it.isLocked }.first()
        assertEquals(true, lockedState.isLocked)
    }

    @Test
    fun `Unlock action delegates to authenticator and unlocks on success`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = FakeBiometricLockUseCase()
        val authenticator = FakeBiometricAuthenticator(Result.Success)
        val viewModel = newViewModel(useCase, authenticator, CoroutineScope(dispatcher))
        viewModel.attach()
        advanceUntilIdle()

        viewModel.perform(BiometricLockGateViewModel.Action.Unlock)
        advanceUntilIdle()

        assertEquals(1, authenticator.calls)
        assertEquals(LockState.Unlocked, useCase.lockState.value)
    }
}
