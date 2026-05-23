package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.http.HttpExecutor
import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import com.hluhovskyi.zero.http.HttpExecutor.HttpResponse

/** In-memory [HttpExecutor]: records requests, replays programmed responses/failures in order. */
class FakeHttpExecutor : HttpExecutor {

    val requests = mutableListOf<HttpRequest>()

    val lastRequest: HttpRequest get() = requests.last()

    private val responses = ArrayDeque<Result<HttpResponse>>()

    fun enqueue(status: Int, body: String = "") {
        responses.addLast(Result.success(HttpResponse(status, emptyMap(), body.toByteArray())))
    }

    fun enqueueFailure(error: Throwable) {
        responses.addLast(Result.failure(error))
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val response = responses.removeFirstOrNull()
            ?: error("FakeHttpExecutor: no response enqueued for ${request.method} ${request.url}")
        return response.getOrThrow()
    }
}
