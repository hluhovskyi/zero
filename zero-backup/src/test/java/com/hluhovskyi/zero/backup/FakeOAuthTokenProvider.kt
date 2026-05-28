package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Returns a fixed access token (or null when signed out). Sign-in/revoke are no-ops. */
class FakeOAuthTokenProvider(private val token: String?) : OAuthTokenProvider {

    override suspend fun getAccessToken(): String? = token

    override suspend fun signIn(): OAuthTokenProvider.Result = OAuthTokenProvider.Result.Cancelled

    override suspend fun revoke() = Unit

    override val isSignedIn: Flow<Boolean> = flowOf(token != null)
}
