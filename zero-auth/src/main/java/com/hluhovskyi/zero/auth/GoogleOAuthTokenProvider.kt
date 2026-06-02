package com.hluhovskyi.zero.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.hluhovskyi.zero.backup.BackupError
import com.hluhovskyi.zero.security.SecureKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [OAuthTokenProvider] over the Google Identity Authorization API: mints short-lived
 * `drive.appdata` access tokens (no refresh token, no secret). Play-services-backed, so validated
 * on-device rather than in unit tests.
 */
internal class GoogleOAuthTokenProvider(
    private val context: Context,
    private val secureKeyValueStore: SecureKeyValueStore,
    private val scopes: List<String>,
    private val currentActivity: () -> Activity?,
) : OAuthTokenProvider {

    @Volatile
    private var cachedAccessToken: CachedToken? = null

    private val signedIn = MutableStateFlow(false)

    override val isSignedIn: Flow<Boolean> = signedIn
        .onStart { signedIn.value = secureKeyValueStore.get(KEY_CONNECTED) == "true" }

    override suspend fun signIn(): OAuthTokenProvider.Result {
        return try {
            val result = authorize()
            val token = result.accessToken
                ?: return OAuthTokenProvider.Result.Failure(
                    BackupError.Unknown("Authorization granted no access token"),
                )
            cacheToken(token)
            secureKeyValueStore.put(KEY_CONNECTED, "true")
            signedIn.value = true
            OAuthTokenProvider.Result.Success(ACCOUNT_LABEL)
        } catch (e: ConsentCancelledException) {
            Timber.d(e, "GoogleOAuthTokenProvider: consent cancelled")
            OAuthTokenProvider.Result.Cancelled
        } catch (e: Exception) {
            Timber.w(e, "GoogleOAuthTokenProvider: sign-in failed")
            OAuthTokenProvider.Result.Failure(BackupError.Unknown(e.message ?: "Sign-in failed"))
        }
    }

    override suspend fun getAccessToken(): String? {
        cachedAccessToken?.let { if (!it.isExpired()) return it.value }

        return try {
            val result = Identity.getAuthorizationClient(context)
                .authorize(authorizationRequest())
                .await()
            val token = result.accessToken
            if (result.hasResolution() || token == null) {
                // Consent is needed again (e.g. revoked) — caller must re-run signIn().
                null
            } else {
                cacheToken(token)
                token
            }
        } catch (e: Exception) {
            Timber.w(e, "GoogleOAuthTokenProvider: getAccessToken failed")
            null
        }
    }

    override suspend fun revoke() {
        // No refresh token to revoke server-side; clear local state. The user can fully revoke the
        // grant from their Google account settings (proper remote revoke lands in Phase 7).
        secureKeyValueStore.remove(KEY_CONNECTED)
        cachedAccessToken = null
        signedIn.value = false
    }

    private suspend fun authorize(): AuthorizationResult {
        val client = Identity.getAuthorizationClient(context)
        val result = client.authorize(authorizationRequest()).await()
        if (!result.hasResolution()) return result

        val activity = currentActivity() as? ComponentActivity
            ?: throw IllegalStateException("Sign-in requires a foreground activity")
        val pendingIntent = result.pendingIntent
            ?: throw IllegalStateException("Authorization needs consent but supplied no intent")
        val data = launchConsent(activity, pendingIntent) ?: throw ConsentCancelledException()
        return client.getAuthorizationResultFromIntent(data)
    }

    private fun authorizationRequest(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(scopes.map { Scope(it) })
        .build()

    private suspend fun launchConsent(
        activity: ComponentActivity,
        pendingIntent: PendingIntent,
    ): Intent? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val key = "drive_auth_${System.nanoTime()}"
            lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>
            launcher = activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { activityResult ->
                launcher.unregister()
                if (continuation.isActive) {
                    val data = if (activityResult.resultCode == Activity.RESULT_OK) activityResult.data else null
                    continuation.resume(data)
                }
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    private fun cacheToken(token: String) {
        cachedAccessToken = CachedToken(token, System.currentTimeMillis() + TOKEN_TTL_MS)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    private data class CachedToken(val value: String, val expiresAtMillis: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMillis
    }

    private class ConsentCancelledException : Exception()

    companion object {
        private const val KEY_CONNECTED = "drive.connected"

        // TODO(Phase 3): surface the real account email (request the email scope) for the UI.
        private const val ACCOUNT_LABEL = "Google Drive"

        // Google access tokens last ~1h; refresh a little early to avoid using a stale one.
        private const val TOKEN_TTL_MS = 45L * 60L * 1000L
    }
}
