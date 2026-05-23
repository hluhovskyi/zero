package com.hluhovskyi.zero.auth

import com.hluhovskyi.zero.backup.BackupError
import kotlinx.coroutines.flow.Flow

interface OAuthTokenProvider {
    suspend fun getAccessToken(): String?
    suspend fun signIn(): Result
    suspend fun revoke()
    val isSignedIn: Flow<Boolean>

    sealed interface Result {
        data class Success(val accountLabel: String) : Result
        data class Failure(val error: BackupError) : Result
        object Cancelled : Result
    }
}
