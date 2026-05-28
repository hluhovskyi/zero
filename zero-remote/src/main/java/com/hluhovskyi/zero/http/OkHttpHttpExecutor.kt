package com.hluhovskyi.zero.http

import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import com.hluhovskyi.zero.http.HttpExecutor.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal class OkHttpHttpExecutor(
    private val client: OkHttpClient,
) : HttpExecutor {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val okRequest = Request.Builder()
            .url(request.url)
            .headers(request.headers.toHeaders())
            .method(request.method.name, request.body?.toOkHttpBody())
            .build()

        client.newCall(okRequest).execute().use { response ->
            HttpResponse(
                status = response.code,
                headers = response.headers.toMap(),
                body = response.body?.bytes() ?: ByteArray(0),
            )
        }
    }

    private fun HttpRequest.Body.toOkHttpBody(): RequestBody = when (this) {
        is HttpRequest.Body.Json ->
            payload.toRequestBody(JSON_MEDIA_TYPE)

        is HttpRequest.Body.Form ->
            FormBody.Builder().apply {
                fields.forEach { (name, value) -> add(name, value) }
            }.build()

        is HttpRequest.Body.Multipart ->
            MultipartBody.Builder()
                .setType(MULTIPART_RELATED)
                .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
                .addPart(content.toRequestBody(contentType.toMediaType()))
                .build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val MULTIPART_RELATED = "multipart/related".toMediaType()
    }
}
