package com.hluhovskyi.zero.security

interface BiometricAuthenticator {

    suspend fun authenticate(reason: AuthReason): Result

    sealed interface AuthReason {
        object Unlock : AuthReason
        object EnableLock : AuthReason
        object DisableLock : AuthReason
    }

    sealed interface Result {
        object Success : Result
        object Failure : Result
        object Unavailable : Result
    }

    object Noop : BiometricAuthenticator {
        override suspend fun authenticate(reason: AuthReason): Result = Result.Unavailable
    }
}
