package com.hluhovskyi.zero.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.hluhovskyi.zero.backup.BackupError
import com.hluhovskyi.zero.http.HttpExecutor
import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import com.hluhovskyi.zero.security.SecureKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * [OAuthTokenProvider] over Android Credential Manager + the OAuth 2.0 token endpoint.
 *
 * Token management ([getAccessToken], [revoke], [isSignedIn]) is fully unit-tested against a fake
 * [HttpExecutor]. [signIn] drives the system Credential Manager UI and therefore cannot be
 * unit-tested — it is validated on-device in Phase 2's manual smoke test (Task 9), which is the
 * only place the real Google authorization grant for `drive.appdata` is exercised before merge.
 *
 * Refresh tokens are persisted via [SecureKeyValueStore] (Keystore-wrapped); access tokens live in
 * memory only.
 */
internal class GoogleOAuthTokenProvider(
    private val secureKeyValueStore: SecureKeyValueStore,
    private val httpExecutor: HttpExecutor,
    private val clientId: String,
    private val scopes: List<String>,
    private val currentActivity: () -> Activity?,
) : OAuthTokenProvider {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedAccessToken: CachedToken? = null

    private val signedIn = MutableStateFlow(false)

    override val isSignedIn: Flow<Boolean> = signedIn
        .onStart { signedIn.value = secureKeyValueStore.get(KEY_REFRESH_TOKEN) != null }

    override suspend fun signIn(): OAuthTokenProvider.Result {
        val activity = currentActivity()
            ?: return OAuthTokenProvider.Result.Failure(
                BackupError.Unknown("Sign-in requires a foreground activity"),
            )

        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)

            // Exchange the Google authorization grant for OAuth access + refresh tokens.
            // NOTE: the exact grant acquisition for `drive.appdata` offline access is validated
            // on-device in Task 9; the token-endpoint exchange below is the standard OAuth flow.
            val tokens = exchangeAuthorizationCode(credential.idToken)
                ?: return OAuthTokenProvider.Result.Failure(BackupError.AuthExpired)

            val refreshToken = tokens.refreshToken
                ?: return OAuthTokenProvider.Result.Failure(
                    BackupError.Unknown("No refresh token returned"),
                )

            val accountLabel = credential.id
            secureKeyValueStore.put(KEY_REFRESH_TOKEN, refreshToken)
            secureKeyValueStore.put(KEY_ACCOUNT_LABEL, accountLabel)
            cacheAccessToken(tokens)
            signedIn.value = true
            OAuthTokenProvider.Result.Success(accountLabel)
        } catch (e: GetCredentialCancellationException) {
            Timber.d(e, "GoogleOAuthTokenProvider: sign-in cancelled")
            OAuthTokenProvider.Result.Cancelled
        } catch (e: GetCredentialException) {
            Timber.w(e, "GoogleOAuthTokenProvider: sign-in failed")
            OAuthTokenProvider.Result.Failure(BackupError.Unknown(e.message ?: "Sign-in failed"))
        }
    }

    override suspend fun getAccessToken(): String? {
        cachedAccessToken?.let { cached ->
            if (!cached.isExpired()) return cached.value
        }

        val refreshToken = secureKeyValueStore.get(KEY_REFRESH_TOKEN) ?: return null

        val response = runCatching {
            httpExecutor.execute(
                HttpRequest(
                    method = HttpRequest.Method.POST,
                    url = TOKEN_ENDPOINT,
                    body = HttpRequest.Body.Form(
                        mapOf(
                            "client_id" to clientId,
                            "refresh_token" to refreshToken,
                            "grant_type" to "refresh_token",
                        ),
                    ),
                ),
            )
        }.getOrElse {
            Timber.w(it, "GoogleOAuthTokenProvider: token refresh failed")
            return null
        }

        if (response.status != HTTP_OK) {
            // 400 invalid_grant means the grant was revoked server-side; drop the stale access token.
            Timber.w("GoogleOAuthTokenProvider: refresh returned ${response.status}")
            cachedAccessToken = null
            return null
        }

        val tokens = runCatching { json.decodeFromString<TokenResponse>(response.bodyAsString()) }
            .getOrElse {
                Timber.w(it, "GoogleOAuthTokenProvider: malformed token response")
                return null
            }
        cacheAccessToken(tokens)
        return tokens.accessToken
    }

    override suspend fun revoke() {
        val refreshToken = secureKeyValueStore.get(KEY_REFRESH_TOKEN)
        if (refreshToken != null) {
            runCatching {
                httpExecutor.execute(
                    HttpRequest(
                        method = HttpRequest.Method.POST,
                        url = "$REVOKE_ENDPOINT?token=$refreshToken",
                    ),
                )
            }.onFailure { Timber.w(it, "GoogleOAuthTokenProvider: revoke call failed") }
        }
        // Always clear local state — we must never leave a dangling local credential.
        secureKeyValueStore.remove(KEY_REFRESH_TOKEN)
        secureKeyValueStore.remove(KEY_ACCOUNT_LABEL)
        cachedAccessToken = null
        signedIn.value = false
    }

    private suspend fun exchangeAuthorizationCode(code: String): TokenResponse? {
        val response = runCatching {
            httpExecutor.execute(
                HttpRequest(
                    method = HttpRequest.Method.POST,
                    url = TOKEN_ENDPOINT,
                    body = HttpRequest.Body.Form(
                        mapOf(
                            "client_id" to clientId,
                            "code" to code,
                            "grant_type" to "authorization_code",
                            "redirect_uri" to "",
                            "scope" to scopes.joinToString(" "),
                        ),
                    ),
                ),
            )
        }.getOrElse {
            Timber.w(it, "GoogleOAuthTokenProvider: code exchange failed")
            return null
        }
        if (response.status != HTTP_OK) {
            Timber.w("GoogleOAuthTokenProvider: code exchange returned ${response.status}")
            return null
        }
        return runCatching { json.decodeFromString<TokenResponse>(response.bodyAsString()) }
            .getOrNull()
    }

    private fun cacheAccessToken(tokens: TokenResponse) {
        val expiresAt = System.currentTimeMillis() + (tokens.expiresIn * 1000) - EXPIRY_MARGIN_MS
        cachedAccessToken = CachedToken(tokens.accessToken, expiresAt)
    }

    private data class CachedToken(val value: String, val expiresAtMillis: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMillis
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    companion object {
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val KEY_REFRESH_TOKEN = "drive.refresh_token"
        private const val KEY_ACCOUNT_LABEL = "drive.account_label"
        private const val HTTP_OK = 200
        private const val EXPIRY_MARGIN_MS = 60_000L
    }
}
