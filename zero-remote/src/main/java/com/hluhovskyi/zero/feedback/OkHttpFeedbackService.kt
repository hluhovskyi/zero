package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.integrity.IntegrityTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal class OkHttpFeedbackService(
    private val endpoint: String,
    private val client: OkHttpClient,
    private val tokenProvider: IntegrityTokenProvider,
    private val json: Json,
) : FeedbackService {

    override suspend fun submit(report: FeedbackReport): FeedbackSubmitResult = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) {
            Timber.w("OkHttpFeedbackService: endpoint not configured")
            return@withContext FeedbackSubmitResult.Failure(FeedbackSubmitResult.Failure.Reason.NotConfigured)
        }

        val nonce = generateNonce()
        val token = tokenProvider.getToken(nonce)
        if (token == null) {
            Timber.w("OkHttpFeedbackService: integrity token unavailable")
            return@withContext FeedbackSubmitResult.Failure(FeedbackSubmitResult.Failure.Reason.Unverified)
        }

        val payload = json.encodeToString(
            FeedbackRequest(
                title = report.title,
                body = report.body,
                type = report.type.id,
                debug = report.isDebug,
            ),
        )
        val request = Request.Builder()
            .url(endpoint)
            .header("X-Integrity-Token", token)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 201) {
                    val parsed = json.decodeFromString<FeedbackResponse>(body)
                    FeedbackSubmitResult.Success(parsed.issueUrl)
                } else {
                    Timber.w("OkHttpFeedbackService: server returned ${response.code}")
                    FeedbackSubmitResult.Failure(FeedbackSubmitResult.Failure.Reason.Server(response.code))
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "OkHttpFeedbackService: network error")
            FeedbackSubmitResult.Failure(FeedbackSubmitResult.Failure.Reason.Network)
        }
    }

    private fun generateNonce(): String {
        val bytes = UUID.randomUUID().toString().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
