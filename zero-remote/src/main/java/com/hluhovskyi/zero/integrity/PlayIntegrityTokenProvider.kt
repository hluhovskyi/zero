package com.hluhovskyi.zero.integrity

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PlayIntegrityTokenProvider(
    private val context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityTokenProvider {

    private val providerDeferred: Lazy<Task<StandardIntegrityTokenProvider>> = lazy {
        IntegrityManagerFactory.createStandard(context).prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build(),
        )
    }

    override suspend fun getToken(nonce: String): String? {
        if (cloudProjectNumber == 0L) {
            Timber.w("PlayIntegrityTokenProvider: cloudProjectNumber not configured")
            return null
        }
        return runCatching {
            val provider = providerDeferred.value.await()
            val token = provider.request(
                StandardIntegrityTokenRequest.builder()
                    .setRequestHash(nonce)
                    .build(),
            ).await()
            token.token()
        }.onFailure { Timber.w(it, "PlayIntegrityTokenProvider: token request failed") }
            .getOrNull()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
