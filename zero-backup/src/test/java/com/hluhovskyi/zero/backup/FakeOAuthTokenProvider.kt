package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Returns a fixed access token (or null when signed out). [signInResult] is what an on-demand
 * [signIn] returns; [signInCount] records how many times it was invoked.
 */
class FakeOAuthTokenProvider(
    private val token: String?,
    private val signInResult: OAuthTokenProvider.Result = OAuthTokenProvider.Result.Cancelled,
) : OAuthTokenProvider {

    var signInCount: Int = 0
        private set

    override suspend fun getAccessToken(): String? = token

    override suspend fun signIn(): OAuthTokenProvider.Result {
        signInCount++
        return signInResult
    }

    override suspend fun revoke() = Unit

    override val isSignedIn: Flow<Boolean> = flowOf(token != null)
}
