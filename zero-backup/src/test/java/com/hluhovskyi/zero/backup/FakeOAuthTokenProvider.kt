package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Returns a fixed access token (or null when signed out). [signInResult] is what an on-demand
 * [signIn] returns; a successful [signIn] flips [isSignedIn] true and [revoke] flips it false, so
 * the fake mirrors the real provider's connection-state transitions. [signInCount] / [revokeCount]
 * record invocations.
 */
class FakeOAuthTokenProvider(
    private val token: String? = null,
    private val signInResult: OAuthTokenProvider.Result = OAuthTokenProvider.Result.Cancelled,
    initiallySignedIn: Boolean = token != null,
) : OAuthTokenProvider {

    var signInCount: Int = 0
        private set
    var revokeCount: Int = 0
        private set

    private val signedIn = MutableStateFlow(initiallySignedIn)
    override val isSignedIn: Flow<Boolean> = signedIn

    override suspend fun getAccessToken(): String? = token

    override suspend fun signIn(): OAuthTokenProvider.Result {
        signInCount++
        if (signInResult is OAuthTokenProvider.Result.Success) signedIn.value = true
        return signInResult
    }

    override suspend fun revoke() {
        revokeCount++
        signedIn.value = false
    }
}
